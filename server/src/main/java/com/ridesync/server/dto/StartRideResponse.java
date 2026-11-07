package com.ridesync.server.dto;

import java.time.LocalDateTime;

public record StartRideResponse(Long rideSessionId, Long groupId, String status, LocalDateTime startedAt) {}
