package com.ridesync.server.controller;

import com.ridesync.server.config.SecurityConfig;
import com.ridesync.server.entity.User;
import com.ridesync.server.repository.*;
import com.ridesync.server.security.JwtTokenProvider;
import com.ridesync.server.service.GroupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GroupController.class)
@Import({GroupService.class, SecurityConfig.class, JwtTokenProvider.class})
class GroupControllerTests {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @MockitoBean UserRepository users;
    @MockitoBean RidingGroupRepository groups;
    @MockitoBean GroupMemberRepository members;

    private String token() {
        User user = new User();
        user.setId(7L);
        when(users.existsById(7L)).thenReturn(true);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        return "Bearer " + tokens.createToken(7L);
    }

    @Test
    void createsGroupAndApprovedOwnerAtBothCapacityBoundaries() throws Exception {
        when(groups.save(any())).thenAnswer(call -> {
            com.ridesync.server.entity.RidingGroup group = call.getArgument(0);
            assertEquals(7L, group.getOwner().getId());
            assertNotNull(group.getCreatedAt());
            group.setId(12L);
            return group;
        });
        when(members.save(any())).thenAnswer(call -> {
            com.ridesync.server.entity.GroupMember member = call.getArgument(0);
            assertEquals("OWNER", member.getRole());
            assertEquals("APPROVED", member.getStatus());
            assertSame(member.getGroup().getOwner(), member.getUser());
            assertEquals(member.getGroup().getCreatedAt(), member.getJoinedAt());
            return member;
        });
        for (int capacity : new int[]{2, 50}) {
            mvc.perform(post("/api/v1/groups").header("Authorization", token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"groupName\":\"한강\",\"maxMembers\":" + capacity + "}"))
                    .andExpect(status().isCreated())
                    .andExpect(content().json("{\"groupId\":12,\"groupName\":\"한강\"}"));
        }
        verify(members, times(2)).save(any());
    }

    @Test
    void rejectsInvalidInputsBeforeSaving() throws Exception {
        for (String body : new String[]{
                "{\"groupName\":\"한강\",\"maxMembers\":1}",
                "{\"groupName\":\"한강\",\"maxMembers\":51}",
                "{\"groupName\":\"한강\"}",
                "{\"groupName\":\" \",\"maxMembers\":2}",
                "{\"groupName\":\"" + "가".repeat(101) + "\",\"maxMembers\":2}",
                "{\"groupName\":\"한강\",\"maxMembers\":2,\"description\":\"" + "가".repeat(501) + "\"}"}) {
            mvc.perform(post("/api/v1/groups").header("Authorization", token())
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(groups, members);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/groups").contentType(MediaType.APPLICATION_JSON)
                .content("{\"groupName\":\"한강\",\"maxMembers\":2}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(groups, members);
    }
}
