package com.ridesync.server.dto;

public record RideStartedMessage(String type, Long rideSessionId, Long groupId) {}
