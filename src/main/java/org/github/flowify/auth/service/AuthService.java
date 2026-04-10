package org.github.flowify.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.auth.dto.LoginResponse;
import org.github.flowify.auth.jwt.JwtProvider;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.user.dto.UserResponse;
import org.github.flowify.user.entity.User;
import org.github.flowify.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String googleClientSecret;

    @Value("${spring.security.oauth2.client.registration.google.redirect-uri}")
    private String redirectUri;

    public String getGoogleLoginUrl() {
        return "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + googleClientId
                + "&redirect_uri=" + redirectUri
                + "&response_type=code"
                + "&scope=openid%20email%20profile"
                + "&access_type=offline"
                + "&prompt=consent";
    }

    @SuppressWarnings("unchecked")
    public LoginResponse processGoogleLogin(String authorizationCode) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", authorizationCode);
        params.add("client_id", googleClientId);
        params.add("client_secret", googleClientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("grant_type", "authorization_code");

        WebClient webClient = WebClient.create();
        Map<String, Object> tokenResponse = webClient.post()
                .uri("https://oauth2.googleapis.com/token")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .bodyValue(params)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (tokenResponse == null || !tokenResponse.containsKey("id_token")) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN, "Google 토큰 교환에 실패했습니다.");
        }

        String idToken = (String) tokenResponse.get("id_token");
        Map<String, Object> userInfo = webClient.get()
                .uri("https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (userInfo == null || !userInfo.containsKey("sub")) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN, "Google 사용자 정보 조회에 실패했습니다.");
        }

        String googleId = (String) userInfo.get("sub");
        String email = (String) userInfo.get("email");
        String name = (String) userInfo.get("name");
        String picture = (String) userInfo.get("picture");

        User user = userRepository.findByGoogleId(googleId)
                .orElseGet(() -> userRepository.save(User.builder()
                        .googleId(googleId)
                        .email(email)
                        .name(name)
                        .picture(picture)
                        .build()));

        user.setName(name);
        user.setPicture(picture);
        user.setLastLoginAt(Instant.now());

        String accessToken = jwtProvider.generateAccessToken(user);
        String refreshToken = jwtProvider.generateRefreshToken(user);
        user.setRefreshToken(refreshToken);
        userRepository.save(user);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(UserResponse.from(user))
                .build();
    }

    public LoginResponse refreshAccessToken(String refreshToken) {
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new BusinessException(ErrorCode.AUTH_EXPIRED_TOKEN);
        }

        String userId = jwtProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!refreshToken.equals(user.getRefreshToken())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_TOKEN, "유효하지 않은 Refresh Token입니다.");
        }

        String newAccessToken = jwtProvider.generateAccessToken(user);
        String newRefreshToken = jwtProvider.generateRefreshToken(user);
        user.setRefreshToken(newRefreshToken);
        userRepository.save(user);

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .user(UserResponse.from(user))
                .build();
    }

    public void logout(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.setRefreshToken(null);
        userRepository.save(user);
    }
}