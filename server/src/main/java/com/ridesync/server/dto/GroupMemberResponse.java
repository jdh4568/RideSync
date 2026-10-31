package com.ridesync.server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GroupMemberResponse {
    private final Long userId;
    private final String nickname;
    private final String role;
    private final boolean online;
}
