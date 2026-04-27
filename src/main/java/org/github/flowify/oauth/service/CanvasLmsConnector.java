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
public class CanvasLmsConnector implements ExternalServiceConnector {

    private final OAuthTokenService oauthTokenService;

    @Value("${app.oauth.canvas-lms.token}")
    private String canvasApiToken;

    @Override
    public String getServiceName() {
        return "canvas_lms";
    }

    @Override
    public ConnectResult connect(String userId) {
        Instant expiresAt = Instant.now().plusSeconds(365L * 24 * 3600);
        oauthTokenService.saveToken(userId, "canvas_lms", canvasApiToken, null, expiresAt,
                List.of("courses", "files"));
        log.info("Canvas LMS API token saved for userId={}", userId);
        return new ConnectResult.DirectlyConnected("canvas_lms");
    }
}