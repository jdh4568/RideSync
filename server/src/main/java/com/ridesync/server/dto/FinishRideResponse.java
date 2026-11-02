package com.ridesync.server.dto;

import java.time.LocalDateTime;

public record FinishRideResponse(Long rideSessionId, String status, LocalDateTime endedAt) {}
