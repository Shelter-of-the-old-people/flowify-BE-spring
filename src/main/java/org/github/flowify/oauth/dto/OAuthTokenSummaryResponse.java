package org.github.flowify.oauth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class OAuthTokenSummaryResponse {

    private String service;
    private boolean connected;
    private String connectionMethod;
    private String accountEmail;
    private String accountLabel;
    private String expiresAt;
    private String aliasOf;
    private Boolean disconnectable;
    private String reason;
    private String maskedHint;
    private String updatedAt;
    private String validationStatus;
}