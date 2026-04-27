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
public class NotionTokenService implements ExternalServiceConnector {

    private final OAuthTokenService oauthTokenService;

    @Value("${app.oauth.notion.token}")
    private String integrationToken;

    @Override
    public String getServiceName() {
        return "notion";
    }

    @Override
    public ConnectResult connect(String userId) {
        Instant expiresAt = Instant.now().plusSeconds(365L * 24 * 3600);
        oauthTokenService.saveToken(userId, "notion", integrationToken, null, expiresAt, List.of());
        log.info("Notion integration token saved for userId={}", userId);
        return new ConnectResult.DirectlyConnected("notion");
    }
}
