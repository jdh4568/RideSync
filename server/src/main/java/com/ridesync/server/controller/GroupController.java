package com.ridesync.server.controller;

import com.ridesync.server.dto.CreateGroupRequest;
import com.ridesync.server.dto.JoinGroupResponse;
import com.ridesync.server.dto.CreateGroupResponse;
import com.ridesync.server.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.ridesync.server.dto.GroupListResponse;
import com.ridesync.server.dto.MyGroupResponse;
import com.ridesync.server.dto.GroupJoinRequestResponse;
import com.ridesync.server.dto.GroupRequestDecisionResponse;
import com.ridesync.server.dto.GroupMemberResponse;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {
    private final GroupService groupService;

    @GetMapping("/{groupId}/members")
    public List<GroupMemberResponse> getMembers(@AuthenticationPrincipal Long userId,
                                                @PathVariable Long groupId) {
        return groupService.getMembers(userId, groupId);
    }

    @GetMapping("/join-requests")
    public List<GroupJoinRequestResponse> getJoinRequests(@AuthenticationPrincipal Long ownerId) {
        return groupService.getJoinRequests(ownerId);
    }

    @PostMapping("/{groupId}/requests/{userId}/approve")
    public GroupRequestDecisionResponse approveRequest(@AuthenticationPrincipal Long ownerId,
            @PathVariable Long groupId, @PathVariable Long userId) {
        return groupService.approveRequest(ownerId, groupId, userId);
    }

    @PostMapping("/{groupId}/requests/{userId}/reject")
    public GroupRequestDecisionResponse rejectRequest(@AuthenticationPrincipal Long ownerId,
            @PathVariable Long groupId, @PathVariable Long userId) {
        return groupService.rejectRequest(ownerId, groupId, userId);
    }

    @PostMapping("/{groupId}/join")
    public JoinGroupResponse joinGroup(
            @AuthenticationPrincipal Long userId, @PathVariable Long groupId) {
        return groupService.joinGroup(userId, groupId);
    }

    @GetMapping
    public List<GroupListResponse> getGroups(@AuthenticationPrincipal Long userId) {
        return groupService.getGroups(userId);
    }

    @GetMapping("/my")
    public List<MyGroupResponse> getMyGroups(@AuthenticationPrincipal Long userId) {
        return groupService.getMyGroups(userId);
    }

    @GetMapping("/owned")
    public List<GroupListResponse> getOwnedGroups(@AuthenticationPrincipal Long userId) {
        return groupService.getOwnedGroups(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateGroupResponse createGroup(@AuthenticationPrincipal Long userId,
                                           @Valid @RequestBody CreateGroupRequest request) {
        return groupService.createGroup(userId, request);
    }
}
