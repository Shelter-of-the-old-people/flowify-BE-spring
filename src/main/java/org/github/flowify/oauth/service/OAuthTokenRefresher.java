package org.github.flowify.oauth.service;

import org.github.flowify.oauth.dto.TokenRefreshResult;

public interface OAuthTokenRefresher {

    String getServiceName();

    TokenRefreshResult refresh(String refreshToken);
}
