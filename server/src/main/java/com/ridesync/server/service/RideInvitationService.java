package com.ridesync.server.service;

import com.ridesync.server.dto.*;
import com.ridesync.server.entity.User;
import com.ridesync.server.entity.RideSession;
import com.ridesync.server.entity.RideSessionMember;
import com.ridesync.server.repository.RideSessionRepository;
import com.ridesync.server.repository.RideSessionMemberRepository;
import com.ridesync.server.exception.ApiException;
import com.ridesync.server.repository.GroupMemberRepository;
import com.ridesync.server.repository.RidingGroupRepository;
import com.ridesync.server.websocket.OnlineUserRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.web.socket.WebSocketSession;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RideInvitationService {
    private final RidingGroupRepository groups;
    private final GroupMemberRepository members;
    private final OnlineUserRegistry onlineUsers;
    private final JsonMapper json;
    private final Clock clock;
    private final RideSessionRepository rides;
    private final RideSessionMemberRepository rideMembers;
    private final TransactionTemplate startTransaction;
    private final Map<String, Invitation> invitations = new ConcurrentHashMap<>();

    @Autowired
    public RideInvitationService(RidingGroupRepository groups, GroupMemberRepository members,
                                 OnlineUserRegistry onlineUsers, JsonMapper json,
                                 RideSessionRepository rides, RideSessionMemberRepository rideMembers,
                                 PlatformTransactionManager transactionManager) {
        this(groups, members, onlineUsers, json, Clock.systemUTC(), rides, rideMembers, transactionManager);
    }

    RideInvitationService(RidingGroupRepository groups, GroupMemberRepository members,
                          OnlineUserRegistry onlineUsers, JsonMapper json, Clock clock,
                          RideSessionRepository rides, RideSessionMemberRepository rideMembers,
                          PlatformTransactionManager transactionManager) {
        this.groups = groups;
        this.members = members;
        this.onlineUsers = onlineUsers;
        this.json = json;
        this.clock = clock;
        this.rides = rides;
        this.rideMembers = rideMembers;
        this.startTransaction = new TransactionTemplate(transactionManager);
        // 외부 트랜잭션이 있어도 execute 반환 전에 실제 커밋을 완료한다.
        this.startTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // Entity를 메모리에 보관하지 않는다. 생성 시점의 이름과 참여 상태만 저장한다.
    static final class Invitation {
        private final RideInviteMessage invite;
        private RideInviteStatusMessage status;
        private final Instant createdAt;

        Invitation(RideInviteMessage invite, RideInviteStatusMessage status, Instant createdAt) {
            this.invite = invite;
            this.status = status;
            this.createdAt = createdAt;
        }

        synchronized RideInviteStatusMessage status() { return status; }

        boolean expired(Instant now) {
            return !now.isBefore(createdAt.plusSeconds(1800));
        }
    }

    @Transactional(readOnly = true)
    public CreateRideInvitationResponse create(Long userId, CreateRideInvitationRequest request) {
        List<Long> ids = request.inviteeIds();
        if (new HashSet<>(ids).size() != ids.size() || ids.contains(userId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "본인 또는 중복된 초대 대상은 허용하지 않습니다.");
        }
        var group = groups.findById(request.groupId()).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "존재하지 않는 그룹입니다."));
        if (!group.getOwner().getId().equals(userId)
                || !members.existsByGroup_IdAndUser_IdAndStatus(group.getId(), userId, "APPROVED")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "승인된 그룹장만 초대를 생성할 수 있습니다.");
        }
        Map<Long, User> approved = new HashMap<>();
        members.findApprovedMembers(group.getId()).forEach(m -> approved.put(m.getUser().getId(), m.getUser()));
        for (Long id : ids) {
            if (!approved.containsKey(id)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INVITEE", "승인된 그룹원만 초대할 수 있습니다.");
            }
        }
        List<Long> allIds = new ArrayList<>();
        allIds.add(userId);
        allIds.addAll(ids);
        for (Long id : allIds) {
            if (!onlineUsers.isOnline(id)) {
                throw new ApiException(HttpStatus.CONFLICT, "USER_OFFLINE", "선두와 초대 대상 모두 온라인이어야 합니다.");
            }
        }

        String id = "inv-" + UUID.randomUUID();
        var invite = new RideInviteMessage("RIDE_INVITE", id, group.getId(), group.getGroupName(),
                userId, group.getOwner().getNickname());
        List<RideInviteStatusMessage.Participant> participants = new ArrayList<>();
        participants.add(new RideInviteStatusMessage.Participant(userId, group.getOwner().getNickname(), "ACCEPTED"));
        for (Long inviteeId : ids) {
            participants.add(new RideInviteStatusMessage.Participant(inviteeId, approved.get(inviteeId).getNickname(), "PENDING"));
        }
        var status = new RideInviteStatusMessage("RIDE_INVITE_STATUS", id, participants, 1, false);
        // 직렬화 실패 시 저장하거나 일부 알림을 보내지 않는다.
        String inviteJson = json.writeValueAsString(invite);
        String statusJson = json.writeValueAsString(status);
        var invitation = new Invitation(invite, status, clock.instant());
        // 최초 현황보다 빠른 응답/재접속이 오래된 현황을 뒤늦게 보내지 않도록 한다.
        synchronized (invitation) {
            invitations.put(id, invitation);
            for (Long inviteeId : ids) onlineUsers.sendToUser(inviteeId, inviteJson);
            onlineUsers.sendToUser(userId, statusJson);
        }
        return new CreateRideInvitationResponse(id, group.getId(), "PENDING", ids.size());
    }

    private record StartedRide(StartRideResponse response, String payload) {}

    public StartRideResponse start(Long userId, String id) {
        Invitation invitation = invitations.get(id);
        if (invitation == null) throw notFound();
        synchronized (invitation) {
            if (!isActive(id, invitation)) throw notFound();
            if (!invitation.invite.leaderId().equals(userId)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "초대 생성자만 라이딩을 시작할 수 있습니다.");
            }
            var acceptedIds = invitation.status.participants().stream()
                    .filter(p -> p.status().equals("ACCEPTED")).map(RideInviteStatusMessage.Participant::userId).toList();
            // 응답/복구/만료 정리와 같은 잠금을 커밋 및 알림까지 유지한다.
            StartedRide started = Objects.requireNonNull(startTransaction.execute(transaction -> {
                var group = groups.findByIdForUpdate(invitation.invite.groupId()).orElseThrow(() ->
                        new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "존재하지 않는 그룹입니다."));
                if (!group.getOwner().getId().equals(userId)
                        || !members.existsByGroup_IdAndUser_IdAndStatus(group.getId(), userId, "APPROVED")) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "승인된 그룹장만 라이딩을 시작할 수 있습니다.");
                }
                if (acceptedIds.size() < 2) {
                    throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_PARTICIPANTS", "선두 포함 2명 이상 수락해야 합니다.");
                }
                Map<Long, User> approved = new HashMap<>();
                members.findApprovedMembers(group.getId()).forEach(m -> approved.put(m.getUser().getId(), m.getUser()));
                if (!approved.keySet().containsAll(acceptedIds)) {
                    throw new ApiException(HttpStatus.CONFLICT, "INVALID_PARTICIPANT", "참여자는 승인된 그룹원이어야 합니다.");
                }
                Instant now = clock.instant();
                var ride = new RideSession();
                ride.setGroup(group);
                ride.setLeader(approved.get(userId));
                ride.setStatus("RIDING");
                ride.setStartedAt(now);
                ride.setCreatedAt(now);
                rides.save(ride);
                for (Long participantId : acceptedIds) {
                    var member = new RideSessionMember();
                    member.setRideSession(ride);
                    member.setUser(approved.get(participantId));
                    member.setRole(participantId.equals(userId) ? "LEADER" : "MEMBER");
                    member.setJoinedAt(now);
                    rideMembers.save(member);
                }
                // 직렬화도 커밋 전에 끝내 실패 시 DB 저장을 롤백한다.
                String payload = json.writeValueAsString(new RideStartedMessage("RIDE_STARTED", ride.getId(), group.getId()));
                return new StartedRide(new StartRideResponse(ride.getId(), group.getId(), "RIDING",
                        LocalDateTime.ofInstant(now, ZoneId.of("Asia/Seoul"))), payload);
            }));
            // 저장/커밋 예외는 여기까지 도달하지 않으므로 유효한 초대가 그대로 남는다.
            invitations.remove(id, invitation);
            for (Long participantId : acceptedIds) onlineUsers.sendToUser(participantId, started.payload());
            return started.response();
        }
    }

    public void respond(Long userId, String id, boolean accepted) {
        Invitation invitation = invitations.get(id);
        if (invitation == null) throw notFound();
        synchronized (invitation) {
            if (!isActive(id, invitation)) throw notFound();
            var participant = invitation.status.participants().stream()
                    .filter(p -> p.userId().equals(userId)).findFirst().orElse(null);
            if (invitation.invite.leaderId().equals(userId) || participant == null) {
                throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "초대 대상만 응답할 수 있습니다.");
            }
            String decision = accepted ? "ACCEPTED" : "REJECTED";
            if (participant.status().equals(decision)) return;
            if (!participant.status().equals("PENDING")) {
                throw new ApiException(HttpStatus.CONFLICT, "INVITATION_RESPONSE_CONFLICT", "이미 확정된 응답은 변경할 수 없습니다.");
            }
            var participants = invitation.status.participants().stream()
                    .map(p -> p.userId().equals(userId)
                            ? new RideInviteStatusMessage.Participant(p.userId(), p.nickname(), decision) : p)
                    .toList();
            int count = (int) participants.stream().filter(p -> p.status().equals("ACCEPTED")).count();
            var status = new RideInviteStatusMessage("RIDE_INVITE_STATUS", id, participants, count, count >= 2);
            String payload = json.writeValueAsString(status);
            invitation.status = status;
            onlineUsers.sendToUser(invitation.invite.leaderId(), payload);
        }
    }

    public void replay(Long userId, WebSocketSession session) {
        invitations.forEach((id, invitation) -> {
            synchronized (invitation) {
                if (!isActive(id, invitation)) return;
                if (invitation.invite.leaderId().equals(userId)) {
                    onlineUsers.sendToSession(session, json.writeValueAsString(invitation.status));
                } else if (invitation.status.participants().stream()
                        .anyMatch(p -> p.userId().equals(userId) && p.status().equals("PENDING"))) {
                    onlineUsers.sendToSession(session, json.writeValueAsString(invitation.invite));
                }
            }
        });
    }

    // 초대별 잠금 안에서 조회/응답/복구/정리가 같은 만료 기준을 사용한다.
    private boolean isActive(String id, Invitation invitation) {
        if (invitation.expired(clock.instant())) {
            invitations.remove(id, invitation);
            return false;
        }
        return invitations.get(id) == invitation;
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND", "존재하지 않는 라이딩 초대입니다.");
    }

    Optional<Invitation> findActive(String id) {
        Invitation invitation = invitations.get(id);
        if (invitation == null) return Optional.empty();
        synchronized (invitation) {
            return isActive(id, invitation) ? Optional.of(invitation) : Optional.empty();
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void removeExpiredInvitations() {
        invitations.forEach((id, invitation) -> {
            synchronized (invitation) { isActive(id, invitation); }
        });
    }
}
