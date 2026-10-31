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
    record Invitation(RideInviteMessage invite, RideInviteStatusMessage status, Instant createdAt) {
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
        invitations.put(id, new Invitation(invite, status, clock.instant()));
        for (Long inviteeId : ids) onlineUsers.sendToUser(inviteeId, inviteJson);
        onlineUsers.sendToUser(userId, statusJson);
        return new CreateRideInvitationResponse(id, group.getId(), "PENDING", ids.size());
    }

    Optional<Invitation> findActive(String id) {
        Invitation invitation = invitations.get(id);
        if (invitation != null && invitation.expired(clock.instant())) {
            invitations.remove(id, invitation);
            invitation = null;
        }
        return Optional.ofNullable(invitation);
    }

    @Scheduled(fixedDelay = 60000)
    public void removeExpiredInvitations() {
        Instant now = clock.instant();
        invitations.entrySet().removeIf(entry -> entry.getValue().expired(now));
    }
}
