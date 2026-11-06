package com.ridesync.server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class GroupMembersResponse {
    private final Long groupId;
    private final String groupName;
    private final Long memberCount;
    private final String description;
    private final List<GroupMemberResponse> members;
}
