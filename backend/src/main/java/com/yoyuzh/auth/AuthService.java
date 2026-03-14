package com.yoyuzh.auth;

import com.yoyuzh.auth.dto.AuthResponse;
import com.yoyuzh.auth.dto.LoginRequest;
import com.yoyuzh.auth.dto.RegisterRequest;
import com.yoyuzh.auth.dto.UserProfileResponse;
import com.yoyuzh.common.BusinessException;
import com.yoyuzh.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "用户名已存在");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.UNKNOWN, "邮箱已存在");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        User saved = userRepository.save(user);
        return new AuthResponse(jwtTokenProvider.generateToken(saved.getId(), saved.getUsername()), toProfile(saved));
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (BadCredentialsException ex) {
            throw new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户名或密码错误");
        }

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));
        return new AuthResponse(jwtTokenProvider.generateToken(user.getId(), user.getUsername()), toProfile(user));
    }

    @Transactional
    public AuthResponse devLogin(String username) {
        String candidate = username == null ? "" : username.trim();
        if (candidate.isEmpty()) {
            candidate = "1";
        }

        final String finalCandidate = candidate;
        User user = userRepository.findByUsername(finalCandidate).orElseGet(() -> {
            User created = new User();
            created.setUsername(finalCandidate);
            created.setEmail(finalCandidate + "@dev.local");
            created.setPasswordHash(passwordEncoder.encode("1"));
            return userRepository.save(created);
        });
        return new AuthResponse(jwtTokenProvider.generateToken(user.getId(), user.getUsername()), toProfile(user));
    }

    public UserProfileResponse getProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_LOGGED_IN, "用户不存在"));
        return toProfile(user);
    }

    private UserProfileResponse toProfile(User user) {
        return new UserProfileResponse(user.getId(), user.getUsername(), user.getEmail(), user.getCreatedAt());
    }
}
