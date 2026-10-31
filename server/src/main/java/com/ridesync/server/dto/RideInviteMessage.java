package com.ridesync.server.dto;

public record RideInviteMessage(String type, String invitationId, Long groupId,
                                String groupName, Long leaderId, String leaderNickname) {
}
