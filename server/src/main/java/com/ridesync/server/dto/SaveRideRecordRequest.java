package com.ridesync.server.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SaveRideRecordRequest(
        @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal distance,
        @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal averageSpeed) {}
