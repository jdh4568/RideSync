package com.ridesync.server.dto;

import java.util.List;

public record RideInviteStatusMessage(String type, String invitationId,
                                     List<Participant> participants, int acceptedCount, boolean canStart) {
    public RideInviteStatusMessage {
        participants = List.copyOf(participants);
    }

    public record Participant(Long userId, String nickname, String status) {
    }
}
