package com.ridesync.server.controller;

import com.ridesync.server.config.SecurityConfig;
import com.ridesync.server.dto.CreateRideInvitationResponse;
import com.ridesync.server.repository.UserRepository;
import com.ridesync.server.security.JwtTokenProvider;
import com.ridesync.server.service.RideInvitationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RideInvitationController.class)
@Import({SecurityConfig.class, JwtTokenProvider.class})
class RideInvitationControllerTests {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider jwt;
    @MockitoBean UserRepository users;
    @MockitoBean RideInvitationService invitations;

    String token() {
        when(users.existsById(1L)).thenReturn(true);
        return "Bearer " + jwt.createToken(1L);
    }

    @Test
    void startsWithJwtPrincipalAndNoRequestBody() throws Exception {
        when(invitations.start(1L, "inv-test")).thenReturn(new com.ridesync.server.dto.StartRideResponse(
                50L, 10L, "RIDING", java.time.LocalDateTime.of(2026, 11, 2, 9, 0)));
        mvc.perform(post("/api/v1/ride-invitations/inv-test/start").header("Authorization", token()))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"rideSessionId\":50,\"groupId\":10,\"status\":\"RIDING\",\"startedAt\":\"2026-11-02T09:00:00\"}"));
        verify(invitations).start(1L, "inv-test");
    }

    @Test
    void startRequiresAuthenticationAndUsesCommonErrors() throws Exception {
        mvc.perform(post("/api/v1/ride-invitations/inv-test/start"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(invitations);
        when(invitations.start(1L, "inv-test")).thenThrow(new com.ridesync.server.exception.ApiException(
                org.springframework.http.HttpStatus.CONFLICT, "INSUFFICIENT_PARTICIPANTS", "인원 부족"));
        mvc.perform(post("/api/v1/ride-invitations/inv-test/start").header("Authorization", token()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_PARTICIPANTS"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/ride-invitations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"groupId\":10,\"inviteeIds\":[2]}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(invitations);
    }

    @Test
    void rejectsMissingEmptyNonpositiveNullAndOversizedInputs() throws Exception {
        for (String body : new String[]{"{}", "{\"groupId\":10}",
                "{\"groupId\":0,\"inviteeIds\":[2]}", "{\"groupId\":10,\"inviteeIds\":[]}",
                "{\"groupId\":10,\"inviteeIds\":[null]}", "{\"groupId\":10,\"inviteeIds\":[-1]}",
                "{\"groupId\":10,\"inviteeIds\":[" + "2,".repeat(49) + "2]}"}) {
            mvc.perform(post("/api/v1/ride-invitations").header("Authorization", token())
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(invitations);
    }

    @Test
    void returnsCreatedAndUsesJwtPrincipal() throws Exception {
        when(invitations.create(eq(1L), any())).thenReturn(new CreateRideInvitationResponse("inv-test", 10L, "PENDING", 1));
        mvc.perform(post("/api/v1/ride-invitations").header("Authorization", token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"groupId\":10,\"inviteeIds\":[2]}"))
                .andExpect(status().isCreated())
                .andExpect(content().json("{\"invitationId\":\"inv-test\",\"groupId\":10,\"status\":\"PENDING\",\"invitedCount\":1}"));
        verify(invitations).create(eq(1L), any());
    }
}
