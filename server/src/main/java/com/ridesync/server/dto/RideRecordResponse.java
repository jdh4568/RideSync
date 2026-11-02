package com.ridesync.server.dto;

import java.math.BigDecimal;

public record RideRecordResponse(Long rideSessionId, BigDecimal distance, BigDecimal averageSpeed) {}
