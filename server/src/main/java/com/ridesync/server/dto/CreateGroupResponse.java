package com.ridesync.server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CreateGroupResponse {
    private final Long groupId;
    private final String groupName;
}
