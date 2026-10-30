package com.ridesync.server.service;

import com.ridesync.server.dto.CreateGroupRequest;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupService {
    private final RidingGroupRepository groupRepository;
    private final GroupMemberRepository memberRepository;
    private final UserRepository userRepository;

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
