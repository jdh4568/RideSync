package com.ridesync.server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MyGroupResponse {
    private final Long groupId;
    private final String groupName;
    private final String role;
    private final Long memberCount;
}
