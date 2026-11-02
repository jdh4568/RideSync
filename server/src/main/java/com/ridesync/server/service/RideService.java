package com.ridesync.server.service;

import com.ridesync.server.dto.RideResponse;
import com.ridesync.server.exception.ApiException;
import com.ridesync.server.repository.RideSessionMemberRepository;
import com.ridesync.server.repository.RideSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RideService {
    private final RideSessionRepository rides;
    private final RideSessionMemberRepository members;

    public String requireRidingParticipant(Long userId, Long rideSessionId) {
        var ride = rides.findById(rideSessionId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "RIDE_SESSION_NOT_FOUND", "존재하지 않는 라이딩 세션입니다."));
        var member = members.findByRideSession_IdAndUser_Id(rideSessionId, userId).orElseThrow(() ->
                new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "해당 라이딩 참여자만 연결할 수 있습니다."));
        if (!"RIDING".equals(ride.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "RIDE_NOT_ACTIVE", "주행 중인 세션이 아닙니다.");
        }
        return member.getRole();
    }

    public RideResponse getRide(Long userId, Long rideSessionId) {
        var ride = rides.findById(rideSessionId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "RIDE_SESSION_NOT_FOUND", "존재하지 않는 라이딩 세션입니다."));
        // 그룹 가입 여부나 선두 필드가 아닌 확정된 세션 참여 기록으로 권한을 검사한다.
        if (!members.existsByRideSession_IdAndUser_Id(rideSessionId, userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "해당 라이딩 참여자만 조회할 수 있습니다.");
        }
        return new RideResponse(ride.getGroup().getGroupName(), members.countByRideSession_Id(rideSessionId));
    }
}
