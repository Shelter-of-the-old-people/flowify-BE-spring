package org.github.flowify.oauth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "oauth_tokens")
@CompoundIndex(name = "user_service_idx", def = "{'userId': 1, 'service': 1}", unique = true)
public class OAuthToken {

    @Id
    private String id;

    private String userId;

    private String service;

    private String accessToken;

    private String refreshToken;

    @Builder.Default
    private String tokenType = "Bearer";

    private Instant expiresAt;

    private List<String> scopes;

    private String connectionMethod;

    private String accountEmail;

    private String accountLabel;

    private String maskedHint;

    private String validationStatus;

    private Instant lastValidatedAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}