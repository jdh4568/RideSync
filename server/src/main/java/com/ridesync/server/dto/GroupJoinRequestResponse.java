package com.ridesync.server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GroupJoinRequestResponse {
    private final Long groupMemberId;
    private final Long groupId;
    private final String groupName;
    private final Long userId;
    private final String nickname;
    private final String status;
}
