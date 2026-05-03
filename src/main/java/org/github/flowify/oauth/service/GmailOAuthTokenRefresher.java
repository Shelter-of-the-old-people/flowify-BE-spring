package org.github.flowify.oauth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.oauth.dto.TokenRefreshResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GmailOAuthTokenRefresher implements OAuthTokenRefresher {

    @Value("${app.oauth.gmail.client-id}")
    private String clientId;

    @Value("${app.oauth.gmail.client-secret}")
    private String clientSecret;

    @Override
    public String getServiceName() {
        return "gmail";
    }

    @SuppressWarnings("unchecked")
    @Override
    public TokenRefreshResult refresh(String refreshToken) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("refresh_token", refreshToken);
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("grant_type", "refresh_token");

        try {
            Map<String, Object> response = WebClient.create()
                    .post()
                    .uri("https://oauth2.googleapis.com/token")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .bodyValue(params)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (response == null || response.get("access_token") == null) {
                throw new BusinessException(ErrorCode.OAUTH_TOKEN_EXPIRED,
                        "Gmail 토큰 갱신 응답이 유효하지 않습니다. 재연결이 필요합니다.");
            }

            return TokenRefreshResult.builder()
                    .accessToken((String) response.get("access_token"))
                    .expiresIn(response.get("expires_in") instanceof Number expiresIn
                            ? expiresIn.intValue()
                            : 3600)
                    .refreshToken((String) response.get("refresh_token"))
                    .build();
        } catch (WebClientResponseException e) {
            log.warn("Gmail OAuth token refresh failed: status={}, body={}",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.OAUTH_TOKEN_EXPIRED,
                    "Gmail 토큰 갱신에 실패했습니다. 재연결이 필요합니다.");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Gmail OAuth token refresh failed", e);
            throw new BusinessException(ErrorCode.OAUTH_TOKEN_EXPIRED,
                    "Gmail 토큰 갱신에 실패했습니다. 재연결이 필요합니다.");
        }
    }
}
