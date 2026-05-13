package org.github.flowify.oauth;

import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.oauth.dto.OAuthTokenSummaryResponse;
import org.github.flowify.oauth.dto.TokenRefreshResult;
import org.github.flowify.oauth.entity.OAuthToken;
import org.github.flowify.oauth.repository.OAuthTokenRepository;
import org.github.flowify.oauth.service.ManualTokenServiceHandler;
import org.github.flowify.oauth.service.ManualTokenValidationResult;
import org.github.flowify.oauth.service.OAuthTokenRefresher;
import org.github.flowify.oauth.service.OAuthTokenService;
import org.github.flowify.oauth.service.TokenEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthTokenServiceTest {

    @Mock
    private OAuthTokenRepository oauthTokenRepository;
    @Mock
    private TokenEncryptionService tokenEncryptionService;
    @Mock
    private OAuthTokenRefresher tokenRefresher;
    @Mock
    private ManualTokenServiceHandler manualTokenServiceHandler;

    private OAuthTokenService oauthTokenService;
    private OAuthToken testToken;

    @BeforeEach
    void setUp() {
        oauthTokenService = new OAuthTokenService(
                oauthTokenRepository,
                tokenEncryptionService,
                List.of(tokenRefresher),
                List.of(manualTokenServiceHandler));

        testToken = OAuthToken.builder()
                .id("token1")
                .userId("user123")
                .service("google_drive")
                .accessToken("encrypted-access-token")
                .refreshToken("encrypted-refresh-token")
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .scopes(List.of(
                        "https://www.googleapis.com/auth/drive",
                        "https://www.googleapis.com/auth/spreadsheets"))
                .build();

        lenient().when(oauthTokenRepository.save(any(OAuthToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("연결된 서비스 목록에 기본 summary 필드를 담아 반환한다")
    void getConnectedServices_includesSummaryFields() {
        testToken.setConnectionMethod("oauth_redirect");
        testToken.setMaskedHint("****1234");
        testToken.setValidationStatus("valid");
        testToken.setUpdatedAt(Instant.parse("2026-05-13T09:10:00Z"));
        when(oauthTokenRepository.findByUserId("user123")).thenReturn(List.of(testToken));

        List<OAuthTokenSummaryResponse> services = oauthTokenService.getConnectedServices("user123");

        assertThat(services).hasSize(2);
        assertThat(services.get(0).getService()).isEqualTo("google_drive");
        assertThat(services.get(0).isConnected()).isTrue();
        assertThat(services.get(0).getConnectionMethod()).isEqualTo("oauth_redirect");
        assertThat(services.get(0).getMaskedHint()).isEqualTo("****1234");
        assertThat(services.get(0).getUpdatedAt()).isEqualTo("2026-05-13T09:10:00Z");
    }

    @Test
    @DisplayName("legacy manual token row도 manual_token으로 해석한다")
    void getConnectedServices_legacyManualTokenFallsBackToManualConnectionMethod() {
        OAuthToken notionToken = OAuthToken.builder()
                .userId("user123")
                .service("notion")
                .accessToken("encrypted-notion-token")
                .build();
        when(oauthTokenRepository.findByUserId("user123")).thenReturn(List.of(notionToken));

        List<OAuthTokenSummaryResponse> services = oauthTokenService.getConnectedServices("user123");

        assertThat(services).singleElement().satisfies(summary -> {
            assertThat(summary.getService()).isEqualTo("notion");
            assertThat(summary.getConnectionMethod()).isEqualTo("manual_token");
            assertThat(summary.getValidationStatus()).isEqualTo("valid");
        });
    }

    @Test
    @DisplayName("manual token 저장은 검증 결과를 summary와 entity에 함께 반영한다")
    void upsertManualToken_savesValidatedToken() {
        when(manualTokenServiceHandler.getServiceName()).thenReturn("github");
        when(oauthTokenRepository.findByUserIdAndService("user123", "github"))
                .thenReturn(Optional.empty());
        when(tokenEncryptionService.encrypt("ghp_test_token_1234")).thenReturn("encrypted-manual-token");
        when(manualTokenServiceHandler.validate("ghp_test_token_1234")).thenReturn(
                new ManualTokenValidationResult(
                        "octocat@example.com",
                        "octocat",
                        Instant.parse("2026-06-13T09:10:00Z"),
                        List.of("repo")
                )
        );

        OAuthTokenSummaryResponse summary = oauthTokenService.upsertManualToken(
                "user123",
                "github",
                "ghp_test_token_1234"
        );

        verify(oauthTokenRepository).save(any(OAuthToken.class));
        assertThat(summary.getService()).isEqualTo("github");
        assertThat(summary.getConnectionMethod()).isEqualTo("manual_token");
        assertThat(summary.getAccountEmail()).isEqualTo("octocat@example.com");
        assertThat(summary.getAccountLabel()).isEqualTo("octocat");
        assertThat(summary.getMaskedHint()).isEqualTo("****1234");
        assertThat(summary.getValidationStatus()).isEqualTo("valid");
    }

    @Test
    @DisplayName("manual token 저장은 지원 서비스에만 허용한다")
    void upsertManualToken_rejectsUnsupportedService() {
        assertThatThrownBy(() -> oauthTokenService.upsertManualToken("user123", "gmail", "token"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        verifyNoInteractions(oauthTokenRepository, tokenEncryptionService, manualTokenServiceHandler);
    }

    @Test
    @DisplayName("access token과 refresh token은 저장 전에 암호화한다")
    void saveToken_encryptsTokens() {
        when(oauthTokenRepository.findByUserIdAndService("user123", "gmail"))
                .thenReturn(Optional.empty());
        when(tokenEncryptionService.encrypt("access-token")).thenReturn("encrypted-access");
        when(tokenEncryptionService.encrypt("refresh-token")).thenReturn("encrypted-refresh");

        oauthTokenService.saveToken(
                "user123",
                "gmail",
                "access-token",
                "refresh-token",
                Instant.now().plus(1, ChronoUnit.HOURS),
                List.of("https://www.googleapis.com/auth/gmail.send")
        );

        verify(tokenEncryptionService).encrypt("access-token");
        verify(tokenEncryptionService).encrypt("refresh-token");
        verify(oauthTokenRepository).save(any(OAuthToken.class));
    }

    @Test
    @DisplayName("복호화된 access token을 정상 반환한다")
    void getDecryptedToken_success() {
        when(oauthTokenRepository.findByUserIdAndService("user123", "google_drive"))
                .thenReturn(Optional.of(testToken));
        when(tokenEncryptionService.decrypt("encrypted-access-token"))
                .thenReturn("decrypted-access-token");

        String result = oauthTokenService.getDecryptedToken("user123", "google_drive");

        assertThat(result).isEqualTo("decrypted-access-token");
    }

    @Test
    @DisplayName("필요한 scope가 부족하면 OAUTH_SCOPE_INSUFFICIENT 예외를 던진다")
    void getDecryptedToken_withRequiredScopes_insufficient() {
        OAuthToken gmailToken = OAuthToken.builder()
                .userId("user123")
                .service("gmail")
                .accessToken("encrypted-gmail-token")
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .scopes(List.of("https://www.googleapis.com/auth/gmail.readonly"))
                .build();
        when(oauthTokenRepository.findByUserIdAndService("user123", "gmail"))
                .thenReturn(Optional.of(gmailToken));

        assertThatThrownBy(() -> oauthTokenService.getDecryptedToken(
                "user123",
                "gmail",
                List.of("https://www.googleapis.com/auth/gmail.send")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_SCOPE_INSUFFICIENT);
    }

    @Test
    @DisplayName("status check는 decrypt 없이 metadata만 검증한다")
    void validateTokenForStatusCheck_doesNotDecrypt() {
        when(oauthTokenRepository.findByUserIdAndService("user123", "google_drive"))
                .thenReturn(Optional.of(testToken));

        oauthTokenService.validateTokenForStatusCheck(
                "user123",
                "google_sheets",
                List.of("https://www.googleapis.com/auth/spreadsheets")
        );

        verifyNoInteractions(tokenEncryptionService, manualTokenServiceHandler);
    }

    @Test
    @DisplayName("status check는 refresh token 없는 만료 토큰을 expired로 본다")
    void validateTokenForStatusCheck_expiredWithoutRefreshToken() {
        OAuthToken expiredToken = OAuthToken.builder()
                .userId("user123")
                .service("gmail")
                .accessToken("encrypted-access-token")
                .expiresAt(Instant.now().minus(1, ChronoUnit.MINUTES))
                .scopes(List.of("https://www.googleapis.com/auth/gmail.send"))
                .build();
        when(oauthTokenRepository.findByUserIdAndService("user123", "gmail"))
                .thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> oauthTokenService.validateTokenForStatusCheck(
                "user123",
                "gmail",
                List.of("https://www.googleapis.com/auth/gmail.send")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("google_sheets alias는 google_drive token을 사용한다")
    void getDecryptedToken_aliasLookupStillWorks() {
        when(oauthTokenRepository.findByUserIdAndService("user123", "google_drive"))
                .thenReturn(Optional.of(testToken));
        when(tokenEncryptionService.decrypt("encrypted-access-token")).thenReturn("drive-token");

        String result = oauthTokenService.getDecryptedToken("user123", "google_sheets");

        assertThat(result).isEqualTo("drive-token");
    }

    @Test
    @DisplayName("google_sheets alias는 disconnectable=false metadata를 내려준다")
    void getConnectedServices_aliasMetadataConsistency() {
        when(oauthTokenRepository.findByUserId("user123")).thenReturn(List.of(testToken));

        List<OAuthTokenSummaryResponse> services = oauthTokenService.getConnectedServices("user123");
        OAuthTokenSummaryResponse sheetsSummary = services.stream()
                .filter(service -> "google_sheets".equals(service.getService()))
                .findFirst()
                .orElseThrow();

        assertThat(sheetsSummary.isConnected()).isTrue();
        assertThat(sheetsSummary.getAliasOf()).isEqualTo("google_drive");
        assertThat(sheetsSummary.getConnectionMethod()).isEqualTo("alias");
        assertThat(sheetsSummary.getDisconnectable()).isFalse();
    }

    @Test
    @DisplayName("google_sheets alias 직접 해제는 예외를 던진다")
    void deleteToken_aliasService_throwsException() {
        assertThatThrownBy(() -> oauthTokenService.deleteToken("user123", "google_sheets"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("만료 임박 토큰은 refresh token으로 갱신 후 반환한다")
    void getDecryptedToken_refreshesExpiringToken() {
        OAuthToken expiringToken = OAuthToken.builder()
                .id("token1")
                .userId("user123")
                .service("gmail")
                .accessToken("old-encrypted-access")
                .refreshToken("encrypted-refresh-token")
                .expiresAt(Instant.now().plus(1, ChronoUnit.MINUTES))
                .build();
        when(oauthTokenRepository.findByUserIdAndService("user123", "gmail"))
                .thenReturn(Optional.of(expiringToken));
        when(tokenRefresher.getServiceName()).thenReturn("gmail");
        when(tokenEncryptionService.decrypt("encrypted-refresh-token")).thenReturn("refresh-token");
        when(tokenRefresher.refresh("refresh-token")).thenReturn(TokenRefreshResult.builder()
                .accessToken("new-access-token")
                .expiresIn(3600)
                .build());
        when(tokenEncryptionService.encrypt("new-access-token")).thenReturn("new-encrypted-access");
        when(tokenEncryptionService.decrypt("new-encrypted-access")).thenReturn("decrypted-new-access");

        String result = oauthTokenService.getDecryptedToken("user123", "gmail");

        assertThat(result).isEqualTo("decrypted-new-access");
        verify(oauthTokenRepository).save(expiringToken);
    }
}