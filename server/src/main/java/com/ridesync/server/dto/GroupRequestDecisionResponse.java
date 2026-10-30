package com.ridesync.server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GroupRequestDecisionResponse {
    private final Long groupId;
    private final Long userId;
    private final String status;
}
