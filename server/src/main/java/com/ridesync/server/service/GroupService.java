package com.ridesync.server.service;

import com.ridesync.server.dto.CreateGroupRequest;
import com.ridesync.server.dto.JoinGroupResponse;
import com.ridesync.server.dto.CreateGroupResponse;
import com.ridesync.server.entity.GroupMember;
import com.ridesync.server.entity.RidingGroup;
import com.ridesync.server.entity.User;
import com.ridesync.server.exception.ApiException;
import com.ridesync.server.repository.GroupMemberRepository;
import com.ridesync.server.repository.RidingGroupRepository;
import com.ridesync.server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import com.ridesync.server.dto.GroupListResponse;
import com.ridesync.server.dto.MyGroupResponse;
import com.ridesync.server.dto.GroupJoinRequestResponse;
import com.ridesync.server.dto.GroupRequestDecisionResponse;
import com.ridesync.server.dto.GroupMemberResponse;
import com.ridesync.server.dto.GroupMembersResponse;
import com.ridesync.server.websocket.OnlineUserRegistry;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupService {
    private final RidingGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final OnlineUserRegistry onlineUsers;

    public GroupMembersResponse getMembers(Long userId, Long groupId) {
        RidingGroup group = groupRepository.findById(groupId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "존재하지 않는 그룹입니다."));
        if (!memberRepository.existsByGroup_IdAndUser_IdAndStatus(groupId, userId, "APPROVED")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "승인된 그룹원만 조회할 수 있습니다.");
        }
        List<GroupMemberResponse> members = memberRepository.findApprovedMembers(groupId).stream()
                .map(member -> new GroupMemberResponse(member.getUser().getId(),
                        member.getUser().getNickname(), member.getRole(),
                        onlineUsers.isOnline(member.getUser().getId())))
                .toList();
        return new GroupMembersResponse(group.getId(), group.getGroupName(),
                (long) members.size(), group.getDescription(), members);
    }

    public List<GroupJoinRequestResponse> getJoinRequests(Long ownerId) {
        if (!groupRepository.existsByOwner_Id(ownerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "소유한 그룹이 없습니다.");
        }
        return memberRepository.findPendingRequests(ownerId);
    }

    @Transactional
    public GroupRequestDecisionResponse approveRequest(Long ownerId, Long groupId, Long userId) {
        return decideRequest(ownerId, groupId, userId, true);
    }

    @Transactional
    public GroupRequestDecisionResponse rejectRequest(Long ownerId, Long groupId, Long userId) {
        return decideRequest(ownerId, groupId, userId, false);
    }

    private GroupRequestDecisionResponse decideRequest(Long ownerId, Long groupId, Long userId,
                                                       boolean approve) {
        RidingGroup group = groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "GROUP_NOT_FOUND", "존재하지 않는 그룹입니다."));
        if (!group.getOwner().getId().equals(ownerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "해당 그룹장만 처리할 수 있습니다.");
        }
        GroupMember member = memberRepository.findByGroup_IdAndUser_Id(groupId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "JOIN_REQUEST_NOT_FOUND", "존재하지 않는 가입 신청입니다."));
        if (!"PENDING".equals(member.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "JOIN_REQUEST_CONFLICT",
                    "대기 중인 가입 신청만 처리할 수 있습니다.");
        }
        if (approve && memberRepository.findByGroup_IdAndStatus(groupId, "APPROVED").size()
                >= group.getMaxMembers()) {
            throw new ApiException(HttpStatus.CONFLICT, "GROUP_FULL", "그룹 최대 인원에 도달했습니다.");
        }
        member.setStatus(approve ? "APPROVED" : "REJECTED");
        member.setJoinedAt(approve ? Instant.now() : null);
        return new GroupRequestDecisionResponse(groupId, userId, member.getStatus());
    }

    @Transactional
    public JoinGroupResponse joinGroup(Long userId, Long groupId) {
        // 그룹 행을 먼저 잠가 신규 신청에도 같은 그룹 내 중복 검사와 저장을 직렬화한다.
        RidingGroup group = groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "GROUP_NOT_FOUND", "존재하지 않는 그룹입니다."));
        GroupMember member = memberRepository.findByGroup_IdAndUser_Id(groupId, userId).orElse(null);
        if (member != null && !"REJECTED".equals(member.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "GROUP_JOIN_CONFLICT",
                    "이미 가입했거나 처리 중인 가입 신청이 있습니다.");
        }
        if (member == null) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                            "INVALID_TOKEN", "유효하지 않은 사용자입니다."));
            member = new GroupMember();
            member.setGroup(group);
            member.setUser(user);
        }
        member.setRole("MEMBER");
        member.setStatus("PENDING");
        member.setJoinedAt(null);
        memberRepository.save(member);
        return new JoinGroupResponse(groupId, "PENDING");
    }

    public List<GroupListResponse> getGroups(Long userId) {
        return groupRepository.findGroupList(userId);
    }

    public List<MyGroupResponse> getMyGroups(Long userId) {
        return groupRepository.findMyGroups(userId);
    }

    public List<GroupListResponse> getOwnedGroups(Long userId) {
        return groupRepository.findOwnedGroups(userId);
    }

    @Transactional
    public CreateGroupResponse createGroup(Long userId, CreateGroupRequest request) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                        "INVALID_TOKEN", "유효하지 않은 사용자입니다."));
        Instant now = Instant.now();
        RidingGroup group = new RidingGroup();
        group.setGroupName(request.getGroupName());
        group.setDescription(request.getDescription());
        group.setMaxMembers(request.getMaxMembers());
        group.setOwner(owner);
        group.setCreatedAt(now);
        groupRepository.save(group);

        GroupMember member = new GroupMember();
        member.setGroup(group);
        member.setUser(owner);
        member.setRole("OWNER");
        member.setStatus("APPROVED");
        member.setJoinedAt(now);
        memberRepository.save(member);
        return new CreateGroupResponse(group.getId(), group.getGroupName());
    }
}
