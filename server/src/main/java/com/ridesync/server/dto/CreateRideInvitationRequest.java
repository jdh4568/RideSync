package com.ridesync.server.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record CreateRideInvitationRequest(
        @NotNull @Positive Long groupId,
        @NotNull @Size(min = 1, max = 49) List<@NotNull @Positive Long> inviteeIds) {
}
