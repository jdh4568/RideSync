package com.ridesync.server.service;

import com.ridesync.server.dto.AvailabilityResponse;
import com.ridesync.server.dto.SignupRequest;
import com.ridesync.server.dto.SignupResponse;
import com.ridesync.server.entity.User;
import com.ridesync.server.exception.ApiException;
import com.ridesync.server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AvailabilityResponse checkLoginId(String loginId) {
        return new AvailabilityResponse(!userRepository.existsByLoginId(loginId));
    }

    public AvailabilityResponse checkNickname(String nickname) {
        return new AvailabilityResponse(!userRepository.existsByNickname(nickname));
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_MISMATCH", "비밀번호가 일치하지 않습니다.");
        }
        // BCrypt의 입력 한도는 문자 수가 아닌 UTF-8 바이트 수이다.
        if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
        }
        if (userRepository.existsByLoginId(request.getLoginId())) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_LOGIN_ID", "이미 사용 중인 아이디입니다.");
        }
        if (userRepository.existsByNickname(request.getNickname())) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_NICKNAME", "이미 사용 중인 닉네임입니다.");
        }

        User user = new User();
        user.setLoginId(request.getLoginId());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setCreatedAt(Instant.now());
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            // 사전 조회 후 동시에 가입한 경우에도 DB UNIQUE 제약으로 차단한다.
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sqlException && sqlException.getErrorCode() == 1062) {
                    throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_USER", "이미 사용 중인 아이디 또는 닉네임입니다.");
                }
            }
            throw exception;
        }
        return new SignupResponse(user.getId(), user.getNickname());
    }
}
