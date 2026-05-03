# Google Drive Target Options 502 오류 수정 계획

## 1. 문제 요약

**증상**: `GET /api/editor-catalog/sources/google_drive/target-options?mode=single_file` 호출 시 502 (Bad Gateway) 반환

**근본 원인 (2가지)**:
1. `OAuthTokenService.refreshTokenIfNeeded()`가 placeholder로, 만료된 access_token을 그대로 Google API에 전달 → Google 401 → Spring 502
2. OAuth scope `drive.file`은 앱에서 생성한 파일만 접근 가능 → 사용자의 기존 파일 목록 조회 불가 → Google 403 → Spring 502

**부수 원인**:
- `GoogleDriveTargetOptionProvider`가 `WebClientResponseException`을 catch하면서 Google API의 원본 HTTP 상태 코드와 에러 메시지를 로그에 남기지 않음
- 모든 Google API 에러가 `EXTERNAL_API_ERROR` (502)로 통합되어 FE가 원인 구분 불가

---

## 2. 수정 항목

### WU1: Google API 에러 로깅 강화

**대상 파일**: `catalog/service/picker/GoogleDriveTargetOptionProvider.java`

**현재 코드** (L80-83):
```java
} catch (WebClientResponseException e) {
    throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR,
            "Google Drive target option 조회에 실패했습니다.");
}
```

**변경 계획**:
```java
} catch (WebClientResponseException e) {
    log.error("Google Drive API error: status={}, body={}",
            e.getStatusCode().value(), e.getResponseBodyAsString());

    if (e.getStatusCode().value() == 401) {
        throw new BusinessException(ErrorCode.OAUTH_TOKEN_EXPIRED,
                "Google Drive 토큰이 만료되었습니다. 재연결이 필요합니다.");
    }
    if (e.getStatusCode().value() == 403) {
        throw new BusinessException(ErrorCode.OAUTH_SCOPE_INSUFFICIENT,
                "Google Drive 접근 권한이 부족합니다. 서비스 재연결이 필요합니다.");
    }
    throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR,
            "Google Drive API 호출에 실패했습니다: " + e.getStatusCode().value());
}
```

**추가**: 클래스에 `@Slf4j` 어노테이션 추가

**효과**: Google API 원본 에러를 서버 로그에 기록하고, FE에 구분 가능한 에러 코드 반환

---

### WU2: OAuth 토큰 자동 갱신 구현

**대상 파일**: `oauth/service/OAuthTokenService.java`

**현재 코드** (L64-72):
```java
public void refreshTokenIfNeeded(OAuthToken token) {
    if (token.getRefreshToken() == null) {
        throw new BusinessException(ErrorCode.OAUTH_TOKEN_EXPIRED);
    }
    log.warn("토큰 자동 갱신이 필요합니다: ...");
}
```

**변경 계획**:

#### 방안: 서비스별 Refresher 전략 패턴

1. **신규 인터페이스**: `oauth/service/OAuthTokenRefresher.java`
```java
public interface OAuthTokenRefresher {
    String getServiceName();
    TokenRefreshResult refresh(String refreshToken);
}
```

2. **신규 DTO**: `oauth/dto/TokenRefreshResult.java`
```java
// accessToken (String), expiresIn (int), refreshToken (String, nullable)
```

3. **신규 구현체**: `oauth/service/GoogleOAuthTokenRefresher.java`
```java
@Component
public class GoogleOAuthTokenRefresher implements OAuthTokenRefresher {
    // Google token endpoint: POST https://oauth2.googleapis.com/token
    // grant_type=refresh_token, client_id, client_secret, refresh_token
    // 응답: { access_token, expires_in, token_type, scope }
    // 참고: Google은 refresh 시 새 refresh_token을 반환하지 않을 수 있음
}
```

4. **`OAuthTokenService.refreshTokenIfNeeded()` 변경**:
```java
public void refreshTokenIfNeeded(OAuthToken token) {
    if (token.getRefreshToken() == null) {
        throw new BusinessException(ErrorCode.OAUTH_TOKEN_EXPIRED,
                "Refresh token이 없습니다. 서비스 재연결이 필요합니다.");
    }

    String decryptedRefreshToken = tokenEncryptionService.decrypt(token.getRefreshToken());

    OAuthTokenRefresher refresher = refresherMap.get(token.getService());
    if (refresher == null) {
        log.warn("토큰 갱신을 지원하지 않는 서비스: {}", token.getService());
        return;
    }

    TokenRefreshResult result = refresher.refresh(decryptedRefreshToken);

    // DB 업데이트: 새 access_token, expiresAt 저장
    token.setAccessToken(tokenEncryptionService.encrypt(result.getAccessToken()));
    token.setExpiresAt(Instant.now().plusSeconds(result.getExpiresIn()));
    if (result.getRefreshToken() != null) {
        token.setRefreshToken(tokenEncryptionService.encrypt(result.getRefreshToken()));
    }
    oauthTokenRepository.save(token);

    log.info("토큰 갱신 완료: userId={}, service={}", token.getUserId(), token.getService());
}
```

**주입 방식**: `List<OAuthTokenRefresher>`를 생성자 주입, `@PostConstruct`에서 `Map<String, OAuthTokenRefresher>`로 변환

**에러 처리**: Google token endpoint가 `invalid_grant` 반환 시 → `OAUTH_TOKEN_EXPIRED` 예외 (사용자 재인증 필요)

---

### WU3: Google Drive OAuth Scope 변경

**대상 파일**: `src/main/resources/application.yml` (L33)

**현재 값**:
```yaml
scopes: https://www.googleapis.com/auth/drive.file
```

**변경 계획**:
```yaml
scopes: https://www.googleapis.com/auth/drive.readonly
```

**Scope 비교**:

| Scope | 범위 | 용도 |
|-------|------|------|
| `drive.file` | 앱이 생성/열은 파일만 | 파일 생성/편집 앱 |
| `drive.readonly` | 전체 Drive 읽기 전용 | **파일 목록 탐색/선택** |
| `drive.metadata.readonly` | 메타데이터만 (내용 불가) | 목록만 필요한 경우 |

**`drive.readonly` 선택 이유**:
- Picker에서 사용자의 전체 파일 목록을 탐색해야 함 (앱이 생성한 파일만으로는 불충분)
- 워크플로우 실행 시 파일 내용을 읽어야 할 수 있으므로 `metadata.readonly`로는 부족
- 쓰기 권한은 불필요하므로 `drive`(full access)보다 `readonly`가 적합

**주의사항**:
- Scope 변경 후 **기존 사용자는 Google Drive 서비스를 재연결해야 함** (기존 토큰의 scope가 `drive.file`이므로)
- `GoogleDriveConnector`에서 `prompt=consent`를 이미 사용 중이므로 재연결 시 새 scope로 동의 화면이 표시됨
- 테스트 설정도 동일하게 변경: `src/test/resources/application-test.yml`

---

### WU4: 에러 코드 추가

**대상 파일**: `common/exception/ErrorCode.java`

**추가할 에러 코드**:
```java
// OAuth (기존 OAUTH_TOKEN_EXPIRED 아래에 추가)
OAUTH_SCOPE_INSUFFICIENT(HttpStatus.FORBIDDEN, "외부 서비스 접근 권한이 부족합니다. 재연결이 필요합니다."),
```

**기존 에러 코드 수정**:
```java
// 변경 전
OAUTH_TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "외부 서비스 토큰 갱신에 실패했습니다."),

// 변경 후
OAUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "외부 서비스 토큰이 만료되었습니다. 재연결이 필요합니다."),
```

**FE 에러 처리 매핑**:

| ErrorCode | HTTP Status | FE 대응 |
|-----------|-------------|---------|
| `OAUTH_TOKEN_EXPIRED` | 401 | 서비스 재연결 안내 → OAuth 재인증 플로우 |
| `OAUTH_SCOPE_INSUFFICIENT` | 403 | 서비스 재연결 안내 (scope 업그레이드) |
| `OAUTH_NOT_CONNECTED` | 400 | 서비스 연결 안내 → OAuth 최초 인증 플로우 |
| `EXTERNAL_API_ERROR` | 502 | 일시적 오류 안내 → 재시도 |

---

## 3. 신규/수정 파일 목록

### 신규 파일 (3개)

| 파일 | WU | 설명 |
|------|-----|------|
| `oauth/service/OAuthTokenRefresher.java` | 2 | 토큰 갱신 전략 인터페이스 |
| `oauth/dto/TokenRefreshResult.java` | 2 | 갱신 결과 DTO |
| `oauth/service/GoogleOAuthTokenRefresher.java` | 2 | Google 토큰 갱신 구현체 |

### 수정 파일 (5개)

| 파일 | WU | 변경 |
|------|-----|------|
| `catalog/service/picker/GoogleDriveTargetOptionProvider.java` | 1 | 에러 로깅 + 상태별 에러 코드 분기 |
| `oauth/service/OAuthTokenService.java` | 2 | refreshTokenIfNeeded() 실제 구현 |
| `src/main/resources/application.yml` | 3 | scope → drive.readonly |
| `src/test/resources/application-test.yml` | 3 | scope → drive.readonly |
| `common/exception/ErrorCode.java` | 4 | OAUTH_SCOPE_INSUFFICIENT 추가, OAUTH_TOKEN_EXPIRED 수정 |

---

## 4. 구현 순서

1. **WU4** (에러 코드 추가) — 다른 WU에서 참조하므로 먼저
2. **WU1** (에러 로깅 강화) — 즉시 디버깅 가능하도록
3. **WU2** (토큰 갱신 구현) — 핵심 수정
4. **WU3** (Scope 변경) — 배포 후 기존 사용자 재연결 필요

---

## 5. 검증 방법

- `./gradlew compileJava` — 빌드 성공
- `./gradlew test` — 기존 테스트 통과
- WU1 검증: 만료 토큰으로 Drive API 호출 → 서버 로그에 Google 401 상세 기록, FE에 `OAUTH_TOKEN_EXPIRED` (401) 반환
- WU2 검증: 만료된 access_token + 유효한 refresh_token → 자동 갱신 후 정상 응답
- WU3 검증: 재연결 후 사용자의 기존 파일 목록 조회 가능
- WU4 검증: FE에서 에러 코드별 분기 처리 확인

---

## 6. FE 공유 사항

FE 팀에 아래 내용을 공유해야 합니다:

1. **에러 코드 변경**: `EXTERNAL_API_ERROR` (502) 외에 `OAUTH_TOKEN_EXPIRED` (401), `OAUTH_SCOPE_INSUFFICIENT` (403) 추가
2. **Scope 변경 후 재연결 필요**: 배포 후 기존 Google Drive 연결 사용자는 서비스 재연결이 필요함. FE에서 `OAUTH_SCOPE_INSUFFICIENT` (403) 수신 시 재연결 안내 UI 표시 권장
3. **`OAUTH_TOKEN_EXPIRED` 자동 복구**: 대부분의 경우 서버에서 자동 갱신되므로 FE에 도달하지 않음. 도달한 경우는 refresh_token도 만료된 상태이므로 재연결 필요
