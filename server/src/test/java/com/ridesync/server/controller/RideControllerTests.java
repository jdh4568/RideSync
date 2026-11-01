package com.ridesync.server.controller;

import com.ridesync.server.config.SecurityConfig;
import com.ridesync.server.dto.RideResponse;
import com.ridesync.server.exception.ApiException;
import com.ridesync.server.repository.UserRepository;
import com.ridesync.server.security.JwtTokenProvider;
import com.ridesync.server.service.RideService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RideController.class)
@Import({SecurityConfig.class, JwtTokenProvider.class})
class RideControllerTests {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider jwt;
    @MockitoBean UserRepository users;
    @MockitoBean RideService rides;

    String token() {
        when(users.existsById(2L)).thenReturn(true);
        return "Bearer " + jwt.createToken(2L);
    }

    @Test void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/rides/10"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        verifyNoInteractions(rides);
    }

    @Test void returnsOnlySpecifiedFieldsAndUsesJwtUser() throws Exception {
        when(rides.getRide(2L, 10L)).thenReturn(new RideResponse("한강모임", 5));
        mvc.perform(get("/api/v1/rides/10").header("Authorization", token()).param("userId", "99"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.groupName").value("한강모임"))
                .andExpect(jsonPath("$.participantCount").value(5));
        verify(rides).getRide(2L, 10L);
    }

    @Test void returnsForbiddenForNonParticipant() throws Exception {
        when(rides.getRide(2L, 10L)).thenThrow(new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "참여자만 조회 가능합니다."));
        mvc.perform(get("/api/v1/rides/10").header("Authorization", token()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test void returnsNotFoundForMissingSession() throws Exception {
        when(rides.getRide(2L, 999L)).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "RIDE_SESSION_NOT_FOUND", "존재하지 않는 라이딩 세션입니다."));
        mvc.perform(get("/api/v1/rides/999").header("Authorization", token()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RIDE_SESSION_NOT_FOUND"));
    }

    @Test void invalidPathReturnsCommonBadRequest() throws Exception {
        mvc.perform(get("/api/v1/rides/invalid").header("Authorization", token()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(rides);
    }
}
