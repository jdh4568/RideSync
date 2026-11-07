package com.ridesync.server.dto;

public record RideInviteCancelledMessage(String type, String invitationId, Long groupId) {}
