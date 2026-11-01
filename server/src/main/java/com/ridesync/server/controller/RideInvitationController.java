package com.ridesync.server.controller;

import com.ridesync.server.dto.CreateRideInvitationRequest;
import com.ridesync.server.dto.CreateRideInvitationResponse;
import com.ridesync.server.dto.StartRideResponse;
import com.ridesync.server.service.RideInvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ride-invitations")
@RequiredArgsConstructor
public class RideInvitationController {
    private final RideInvitationService invitations;

    @PostMapping("/{invitationId}/start")
    public StartRideResponse start(@AuthenticationPrincipal Long userId, @PathVariable String invitationId) {
        return invitations.start(userId, invitationId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateRideInvitationResponse create(@AuthenticationPrincipal Long userId,
                                               @Valid @RequestBody CreateRideInvitationRequest request) {
        return invitations.create(userId, request);
    }
}
