package org.github.flowify.oauth;

import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.oauth.dto.TokenRefreshResult;
import org.github.flowify.oauth.entity.OAuthToken;
import org.github.flowify.oauth.repository.OAuthTokenRepository;
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
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthTokenServiceTest {

    @Mock
    private OAuthTokenRepository oauthTokenRepository;
    @Mock
    private TokenEncryptionService tokenEncryptionService;
    @Mock
    private OAuthTokenRefresher tokenRefresher;

    private OAuthTokenService oauthTokenService;

    private OAuthToken testToken;

    @BeforeEach
    void setUp() {
        oauthTokenService = new OAuthTokenService(
                oauthTokenRepository,
                tokenEncryptionService,
                List.of(tokenRefresher));

        testToken = OAuthToken.builder()
                .id("token1")
                .userId("user123")
                .service("google")
                .accessToken("encrypted-access-token")
                .refreshToken("encrypted-refresh-token")
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .scopes(List.of("drive.readonly"))
                .build();
    }

    @Test
    @DisplayName("연결된 서비스 목록 조회")
    void getConnectedServices() {
        when(oauthTokenRepository.findByUserId("user123")).thenReturn(List.of(testToken));

        List<Map<String, Object>> services = oauthTokenService.getConnectedServices("user123");

        assertThat(services).hasSize(1);
        assertThat(services.get(0).get("service")).isEqualTo("google");
        assertThat(services.get(0).get("connected")).isEqualTo(true);
    }

    @Test
    @DisplayName("토큰 저장 시 암호화 후 저장")
    void saveToken_encrypts() {
        when(oauthTokenRepository.findByUserIdAndService("user123", "google"))
                .thenReturn(Optional.empty());
        when(tokenEncryptionService.encrypt("access-token")).thenReturn("encrypted-access");
        when(tokenEncryptionService.encrypt("refresh-token")).thenReturn("encrypted-refresh");

        oauthTokenService.saveToken("user123", "google", "access-token",
                "refresh-token", Instant.now().plus(1, ChronoUnit.HOURS), List.of("drive"));

        verify(tokenEncryptionService).encrypt("access-token");
        verify(tokenEncryptionService).encrypt("refresh-token");
        verify(oauthTokenRepository).save(any(OAuthToken.class));
    }

    @Test
    @DisplayName("기존 토큰이 있으면 업데이트")
    void saveToken_updatesExisting() {
        when(oauthTokenRepository.findByUserIdAndService("user123", "google"))
                .thenReturn(Optional.of(testToken));
        when(tokenEncryptionService.encrypt(anyString())).thenReturn("new-encrypted");

        oauthTokenService.saveToken("user123", "google", "new-access",
                "new-refresh", Instant.now().plus(2, ChronoUnit.HOURS), List.of("drive"));

        verify(oauthTokenRepository).save(testToken);
    }

    @Test
    @DisplayName("복호화된 토큰 조회 성공 (만료 전)")
    void getDecryptedToken_success() {
        when(oauthTokenRepository.findByUserIdAndService("user123", "google"))
                .thenReturn(Optional.of(testToken));
        when(tokenEncryptionService.decrypt("encrypted-access-token"))
                .thenReturn("decrypted-access-token");

        String result = oauthTokenService.getDecryptedToken("user123", "google");

        assertThat(result).isEqualTo("decrypted-access-token");
    }

    @Test
    @DisplayName("미연결 서비스 토큰 조회 시 예외")
    void getDecryptedToken_notConnected() {
        when(oauthTokenRepository.findByUserIdAndService("user123", "slack"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> oauthTokenService.getDecryptedToken("user123", "slack"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_NOT_CONNECTED);
    }

    @Test
    @DisplayName("토큰 삭제")
    void deleteToken() {
        oauthTokenService.deleteToken("user123", "google");

        verify(oauthTokenRepository).deleteByUserIdAndService("user123", "google");
    }

    @Test
    @DisplayName("refreshToken이 없으면 갱신 시 예외")
    void refreshTokenIfNeeded_noRefreshToken() {
        OAuthToken tokenWithoutRefresh = OAuthToken.builder()
                .userId("user123")
                .service("google")
                .accessToken("encrypted")
                .refreshToken(null)
                .build();

        assertThatThrownBy(() -> oauthTokenService.refreshTokenIfNeeded(tokenWithoutRefresh))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_TOKEN_EXPIRED);
    }

    // ===== Alias 관련 테스트 =====

    @Test
    @DisplayName("Google Drive 토큰만 있고 spreadsheets scope 포함 시 google_sheets 연결 상태 노출")
    void getConnectedServices_aliasWithSufficientScope() {
        OAuthToken driveToken = OAuthToken.builder()
                .id("token-drive")
                .userId("user123")
                .service("google_drive")
                .accessToken("encrypted-access")
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .scopes(List.of("https://www.googleapis.com/auth/drive",
                        "https://www.googleapis.com/auth/spreadsheets"))
                .build();

        when(oauthTokenRepository.findByUserId("user123")).thenReturn(List.of(driveToken));

        List<Map<String, Object>> services = oauthTokenService.getConnectedServices("user123");

        assertThat(services).hasSize(2);

        Map<String, Object> sheetsEntry = services.stream()
                .filter(s -> "google_sheets".equals(s.get("service")))
                .findFirst()
                .orElseThrow();

        assertThat(sheetsEntry.get("connected")).isEqualTo(true);
        assertThat(sheetsEntry.get("aliasOf")).isEqualTo("google_drive");
        assertThat(sheetsEntry.get("disconnectable")).isEqualTo(false);
    }

    @Test
    @DisplayName("Google Drive 토큰에 spreadsheets scope 없으면 google_sheets 연결 상태 connected=false")
    void getConnectedServices_aliasWithoutScope() {
        OAuthToken driveToken = OAuthToken.builder()
                .id("token-drive")
                .userId("user123")
                .service("google_drive")
                .accessToken("encrypted-access")
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .scopes(List.of("https://www.googleapis.com/auth/drive"))
                .build();

        when(oauthTokenRepository.findByUserId("user123")).thenReturn(List.of(driveToken));

        List<Map<String, Object>> services = oauthTokenService.getConnectedServices("user123");

        Map<String, Object> sheetsEntry = services.stream()
                .filter(s -> "google_sheets".equals(s.get("service")))
                .findFirst()
                .orElseThrow();

        assertThat(sheetsEntry.get("connected")).isEqualTo(false);
        assertThat(sheetsEntry.get("reason")).isEqualTo("OAUTH_SCOPE_INSUFFICIENT");
    }

    @Test
    @DisplayName("google_sheets 토큰 조회 시 alias를 통해 google_drive 토큰 사용 + scope 검증 성공")
    void getDecryptedToken_aliasWithSufficientScope() {
        OAuthToken driveToken = OAuthToken.builder()
                .userId("user123")
                .service("google_drive")
                .accessToken("encrypted-access")
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .scopes(List.of("https://www.googleapis.com/auth/drive",
                        "https://www.googleapis.com/auth/spreadsheets"))
                .build();

        when(oauthTokenRepository.findByUserIdAndService("user123", "google_drive"))
                .thenReturn(Optional.of(driveToken));
        when(tokenEncryptionService.decrypt("encrypted-access")).thenReturn("decrypted-access");

        String result = oauthTokenService.getDecryptedToken("user123", "google_sheets");

        assertThat(result).isEqualTo("decrypted-access");
    }

    @Test
    @DisplayName("google_sheets 토큰 조회 시 scope 부족하면 OAUTH_SCOPE_INSUFFICIENT 예외")
    void getDecryptedToken_aliasWithInsufficientScope() {
        OAuthToken driveToken = OAuthToken.builder()
                .userId("user123")
                .service("google_drive")
                .accessToken("encrypted-access")
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .scopes(List.of("https://www.googleapis.com/auth/drive"))
                .build();

        when(oauthTokenRepository.findByUserIdAndService("user123", "google_drive"))
                .thenReturn(Optional.of(driveToken));

        assertThatThrownBy(() -> oauthTokenService.getDecryptedToken("user123", "google_sheets"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_SCOPE_INSUFFICIENT);
    }

    @Test
    @DisplayName("google_sheets alias 직접 삭제 시도 시 예외")
    void deleteToken_aliasService_throwsException() {
        assertThatThrownBy(() -> oauthTokenService.deleteToken("user123", "google_sheets"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("google_sheets alias 응답에 aliasOf, disconnectable 정책이 일관되게 내려옴")
    void getConnectedServices_aliasMetadataConsistency() {
        OAuthToken driveToken = OAuthToken.builder()
                .id("token-drive")
                .userId("user123")
                .service("google_drive")
                .accessToken("encrypted-access")
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .scopes(List.of("https://www.googleapis.com/auth/drive",
                        "https://www.googleapis.com/auth/spreadsheets"))
                .build();

        when(oauthTokenRepository.findByUserId("user123")).thenReturn(List.of(driveToken));

        List<Map<String, Object>> services = oauthTokenService.getConnectedServices("user123");

        Map<String, Object> sheetsEntry = services.stream()
                .filter(s -> "google_sheets".equals(s.get("service")))
                .findFirst()
                .orElseThrow();

        assertThat(sheetsEntry).containsKey("aliasOf");
        assertThat(sheetsEntry).containsKey("disconnectable");
        assertThat(sheetsEntry.get("aliasOf")).isEqualTo("google_drive");
        assertThat(sheetsEntry.get("disconnectable")).isEqualTo(false);
        assertThat(sheetsEntry).doesNotContainKey("reason");
    }

    @Test
    @DisplayName("google_sheets alias 기존 조회 동작 유지 확인")
    void getDecryptedToken_aliasLookupStillWorks() {
        OAuthToken driveToken = OAuthToken.builder()
                .userId("user123")
                .service("google_drive")
                .accessToken("encrypted-access")
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .scopes(List.of("https://www.googleapis.com/auth/drive",
                        "https://www.googleapis.com/auth/spreadsheets"))
                .build();

        when(oauthTokenRepository.findByUserIdAndService("user123", "google_drive"))
                .thenReturn(Optional.of(driveToken));
        when(tokenEncryptionService.decrypt("encrypted-access")).thenReturn("the-token");

        // google_sheets 조회가 google_drive 토큰을 반환하는지 확인
        String result = oauthTokenService.getDecryptedToken("user123", "google_sheets");
        assertThat(result).isEqualTo("the-token");
    }

    // ===== 기존 테스트 =====

    @Test
    @DisplayName("만료 임박 토큰은 refresh token으로 갱신 후 저장")
    void getDecryptedToken_refreshesExpiringToken() {
        OAuthToken expiringToken = OAuthToken.builder()
                .id("token1")
                .userId("user123")
                .service("google")
                .accessToken("old-encrypted-access")
                .refreshToken("encrypted-refresh-token")
                .expiresAt(Instant.now().plus(1, ChronoUnit.MINUTES))
                .build();

        when(oauthTokenRepository.findByUserIdAndService("user123", "google"))
                .thenReturn(Optional.of(expiringToken));
        when(tokenRefresher.getServiceName()).thenReturn("google");
        when(tokenEncryptionService.decrypt("encrypted-refresh-token")).thenReturn("refresh-token");
        when(tokenRefresher.refresh("refresh-token")).thenReturn(TokenRefreshResult.builder()
                .accessToken("new-access-token")
                .expiresIn(3600)
                .build());
        when(tokenEncryptionService.encrypt("new-access-token")).thenReturn("new-encrypted-access");
        when(tokenEncryptionService.decrypt("new-encrypted-access")).thenReturn("decrypted-new-access");

        String result = oauthTokenService.getDecryptedToken("user123", "google");

        assertThat(result).isEqualTo("decrypted-new-access");
        assertThat(expiringToken.getAccessToken()).isEqualTo("new-encrypted-access");
        verify(oauthTokenRepository).save(expiringToken);
    }
}
