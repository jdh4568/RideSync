package com.ridesync.server.service;

import com.ridesync.server.dto.*;
import com.ridesync.server.entity.User;
import com.ridesync.server.exception.ApiException;
import com.ridesync.server.repository.GroupMemberRepository;
import com.ridesync.server.repository.RidingGroupRepository;
import com.ridesync.server.websocket.OnlineUserRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.web.socket.WebSocketSession;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RideInvitationService {
    private final RidingGroupRepository groups;
    private final GroupMemberRepository members;
    private final OnlineUserRegistry onlineUsers;
    private final JsonMapper json;
    private final Clock clock;
    private final Map<String, Invitation> invitations = new ConcurrentHashMap<>();

    @Autowired
    public RideInvitationService(RidingGroupRepository groups, GroupMemberRepository members,
                                 OnlineUserRegistry onlineUsers, JsonMapper json) {
        this(groups, members, onlineUsers, json, Clock.systemUTC());
    }

    RideInvitationService(RidingGroupRepository groups, GroupMemberRepository members,
                          OnlineUserRegistry onlineUsers, JsonMapper json, Clock clock) {
        this.groups = groups;
        this.members = members;
        this.onlineUsers = onlineUsers;
        this.json = json;
        this.clock = clock;
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
