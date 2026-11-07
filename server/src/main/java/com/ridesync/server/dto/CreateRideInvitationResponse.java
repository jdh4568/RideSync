package com.ridesync.server.dto;

public record CreateRideInvitationResponse(String invitationId, Long groupId,
                                           String status, int invitedCount) {
}
