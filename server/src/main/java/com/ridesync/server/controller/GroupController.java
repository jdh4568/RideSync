package com.ridesync.server.controller;

import com.ridesync.server.dto.CreateGroupRequest;
import com.ridesync.server.dto.CreateGroupResponse;
import com.ridesync.server.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/groups")
@RequiredArgsConstructor
public class GroupController {
    private final GroupService groupService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateGroupResponse createGroup(@AuthenticationPrincipal Long userId,
                                           @Valid @RequestBody CreateGroupRequest request) {
        return groupService.createGroup(userId, request);
    }
}
