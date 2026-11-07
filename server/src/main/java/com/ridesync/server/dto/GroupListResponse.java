package com.ridesync.server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GroupListResponse {
    private final Long groupId;
    private final String groupName;
    private final String description;
    private final Long memberCount;
    private final Integer maxMembers;
    private final String membershipStatus;
    private final String myRole;
}
