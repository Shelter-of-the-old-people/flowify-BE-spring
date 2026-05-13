package org.github.flowify.oauth.service;

import java.time.Instant;
import java.util.List;

public record ManualTokenValidationResult(
        String accountEmail,
        String accountLabel,
        Instant expiresAt,
        List<String> scopes
) {
}