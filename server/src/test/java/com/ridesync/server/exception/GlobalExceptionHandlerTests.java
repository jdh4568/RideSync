package com.ridesync.server.exception;

import com.ridesync.server.dto.SignupRequest;
import com.ridesync.server.dto.SignupResponse;
import jakarta.validation.Valid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTests {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validRequestReturnsOnlySpecifiedResponseFields() throws Exception {
        mockMvc.perform(post("/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"rider","password":"1234","passwordConfirm":"1234","nickname":"라이더"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"userId\":1,\"nickname\":\"라이더\"}"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordConfirm").doesNotExist());
    }

    @Test
    void blankLoginIdReturnsValidationError() throws Exception {
        mockMvc.perform(post("/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":" ","password":"1234","passwordConfirm":"1234","nickname":"라이더"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("아이디를 입력해주세요."));
    }

    @Test
    void oversizedNicknameReturnsValidationError() throws Exception {
        String body = """
                {"loginId":"rider","password":"1234","passwordConfirm":"1234","nickname":"%s"}
                """.formatted("가".repeat(51));
        mockMvc.perform(post("/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("닉네임은 50자 이하여야 합니다."));
    }

    @Test
    void malformedJsonUsesCommonErrorFormat() throws Exception {
        mockMvc.perform(post("/signup").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void missingQueryParameterUsesCommonErrorFormat() throws Exception {
        mockMvc.perform(get("/check"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void businessExceptionPreservesStatusAndCode() throws Exception {
        mockMvc.perform(get("/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_LOGIN_ID"))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 아이디입니다."));
    }

    @Test
    void unexpectedExceptionDoesNotExposeInternalMessage() throws Exception {
        mockMvc.perform(get("/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));
    }

    // 실제 인증 API를 추가하지 않고 MVC 검증 및 오류 응답만 확인한다.
    @RestController
    static class TestController {
        @PostMapping("/signup")
        SignupResponse signup(@Valid @RequestBody SignupRequest request) {
            return new SignupResponse(1L, request.getNickname());
        }

        @GetMapping("/check")
        String check(@RequestParam("loginId") String loginId) {
            return loginId;
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_LOGIN_ID", "이미 사용 중인 아이디입니다.");
        }

        @GetMapping("/failure")
        void failure() {
            throw new IllegalStateException("internal detail");
        }
    }
}
