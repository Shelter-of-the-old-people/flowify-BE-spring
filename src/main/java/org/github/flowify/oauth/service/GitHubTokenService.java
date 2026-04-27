package org.github.flowify.oauth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubTokenService implements ExternalServiceConnector {

    private final OAuthTokenService oauthTokenService;

    @Value("${app.oauth.github.token}")
    private String personalAccessToken;

    @Override
    public String getServiceName() {
        return "github";
    }

    @Override
    public ConnectResult connect(String userId) {
        Instant expiresAt = Instant.now().plusSeconds(365L * 24 * 3600);
        oauthTokenService.saveToken(userId, "github", personalAccessToken, null, expiresAt, List.of("repo"));
        log.info("GitHub PAT saved for userId={}", userId);
        return new ConnectResult.DirectlyConnected("github");
    }
}
