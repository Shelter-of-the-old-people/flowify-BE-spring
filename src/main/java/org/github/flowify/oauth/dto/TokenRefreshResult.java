package org.github.flowify.oauth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class TokenRefreshResult {

    private final String accessToken;
    private final Integer expiresIn;
    private final String refreshToken;
}
