package com.ridesync.server.service;

import com.ridesync.server.dto.FinishRideResponse;
import com.ridesync.server.dto.RideFinishedMessage;
import com.ridesync.server.exception.ApiException;
import com.ridesync.server.repository.RideSessionRepository;
import com.ridesync.server.repository.RideSessionMemberRepository;
import com.ridesync.server.websocket.RideConnectionRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.util.Objects;

@Service
public class RideFinishService {
    private final RideSessionRepository rides;
    private final RideSessionMemberRepository members;
    private final RideConnectionRegistry connections;
    private final JsonMapper json;
    private final TransactionTemplate transaction;

    public RideFinishService(RideSessionRepository rides, RideSessionMemberRepository members,
                             RideConnectionRegistry connections, JsonMapper json, PlatformTransactionManager manager) {
        this.rides = rides; this.members = members; this.connections = connections; this.json = json;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private record Finished(FinishRideResponse response, String payload) {}

    public FinishRideResponse finish(Long userId, Long rideId) {
        synchronized (connections.lifecycleLock(rideId)) {
            Finished result = Objects.requireNonNull(transaction.execute(status -> {
                var ride = rides.findByIdForUpdate(rideId).orElseThrow(() ->
                        new ApiException(HttpStatus.NOT_FOUND, "RIDE_SESSION_NOT_FOUND", "존재하지 않는 라이딩 세션입니다."));
                var member = members.findByRideSession_IdAndUser_Id(rideId, userId).orElseThrow(() ->
                        new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "선두만 라이딩을 종료할 수 있습니다."));
                if (!ride.getLeader().getId().equals(userId) || !"LEADER".equals(member.getRole())) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "선두만 라이딩을 종료할 수 있습니다.");
                }
                boolean changed = !"FINISHED".equals(ride.getStatus());
                if (changed) {
                    if (!"RIDING".equals(ride.getStatus())) {
                        throw new ApiException(HttpStatus.CONFLICT, "RIDE_NOT_ACTIVE", "주행 중인 세션이 아닙니다.");
                    }
                    ride.setStatus("FINISHED");
                    ride.setEndedAt(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
                    rides.saveAndFlush(ride);
                }
                var response = new FinishRideResponse(rideId, "FINISHED",
                        LocalDateTime.ofInstant(ride.getEndedAt(), ZoneId.of("Asia/Seoul")));
                return new Finished(response, changed ? json.writeValueAsString(new RideFinishedMessage("RIDE_FINISHED", rideId)) : null);
            }));
            // execute가 반환되면 커밋 완료. 실패하면 연결과 알림을 건드리지 않는다.
            if (result.payload() != null) connections.finish(rideId, result.payload());
            return result.response();
        }
    }
}
