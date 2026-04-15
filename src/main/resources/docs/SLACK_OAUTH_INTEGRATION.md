# Slack OAuth 연동 명세서

> 작성일: 2026-04-14
> 대상: 프론트엔드 / 백엔드 개발자
> 목적: Slack OAuth 연동 흐름과 API 명세를 정리한다.

---

## 1. 전체 흐름

```
[프론트엔드] (JWT 보유)
  │
  ▼ POST /api/oauth-tokens/slack/connect
  │   Header: Authorization: Bearer <accessToken>
[Spring Boot]
  └─ Slack 인증 URL 생성 (state = AES-GCM 암호화된 userId)
       │
       ▼ { "authUrl": "https://slack.com/oauth/v2/authorize?..." }
[프론트엔드]
  └─ 사용자를 authUrl로 이동시킴
       │
       ▼ 사용자가 Slack 로그인 & 권한 허용
[Slack]
  └─ 302 리다이렉트
       │
       ▼ GET /api/oauth-tokens/slack/callback?code=...&state=...
[Spring Boot] (JWT 없음 — permitAll 엔드포인트)
  ├─ state 복호화 → userId 추출
  ├─ Slack API에 code로 토큰 교환 (POST https://slack.com/api/oauth.v2.access)
  ├─ access_token을 AES-GCM 암호화하여 MongoDB 저장
  └─ 302 리다이렉트
       │
       ▼ 성공: https://{프론트주소}/oauth/callback?service=slack&connected=true
       ▼ 실패: https://{프론트주소}/oauth/callback?service=slack&error=oauth_failed
[프론트엔드]
  └─ 쿼리 파라미터로 연동 결과 처리
```

---

## 2. API 엔드포인트

### 2-1. 연동 시작

```
POST /api/oauth-tokens/slack/connect
```

**인증:** 필요 (JWT)

**응답:**
```json
{
  "success": true,
  "data": {
    "authUrl": "https://slack.com/oauth/v2/authorize?client_id=...&scope=...&redirect_uri=...&state=..."
  }
}
```

`state` 파라미터는 userId를 AES-GCM으로 암호화한 값이다. Slack이 콜백 시 이 값을 그대로 돌려준다.

---

### 2-2. Slack 콜백 (Slack → 서버)

```
GET /api/oauth-tokens/slack/callback?code={code}&state={state}
```

**인증:** 불필요 (permitAll) — Slack이 직접 리다이렉트하므로 JWT 없음

**동작:**
1. `state`를 AES-GCM 복호화하여 `userId` 추출
2. `code`를 Slack API(`https://slack.com/api/oauth.v2.access`)에 전송하여 `access_token` 교환
3. `access_token`을 AES-GCM 암호화하여 MongoDB `oauth_tokens` 컬렉션에 저장
4. 프론트엔드로 302 리다이렉트

**응답:** HTTP 302 리다이렉트 (JSON 응답 아님)

| 결과 | 리다이렉트 URL |
|------|---------------|
| 성공 | `{프론트주소}/oauth/callback?service=slack&connected=true` |
| 실패 | `{프론트주소}/oauth/callback?service=slack&error=oauth_failed` |

---

### 2-3. 연결된 서비스 목록 조회

```
GET /api/oauth-tokens
```

**인증:** 필요 (JWT)

**응답:**
```json
{
  "success": true,
  "data": [
    {
      "service": "slack",
      "connected": true,
      "expiresAt": "2027-04-14T00:00:00Z"
    }
  ]
}
```

---

### 2-4. 서비스 연결 해제

```
DELETE /api/oauth-tokens/slack
```

**인증:** 필요 (JWT)

**응답:**
```json
{
  "success": true
}
```

---

## 3. Slack 토큰 교환 상세

### 요청 (Spring Boot → Slack)

```
POST https://slack.com/api/oauth.v2.access
Content-Type: application/x-www-form-urlencoded

code={code}&client_id={SLACK_CLIENT_ID}&client_secret={SLACK_CLIENT_SECRET}&redirect_uri={SLACK_REDIRECT_URI}
```

### 응답 (Slack → Spring Boot)

```json
{
  "ok": true,
  "access_token": "xoxb-...",
  "token_type": "bot",
  "scope": "channels:read,chat:write,users:read",
  "bot_user_id": "U0KRQLJ9H",
  "app_id": "A0KRD7HC3",
  "team": {
    "name": "Workspace Name",
    "id": "T9TK3CUKW"
  },
  "authed_user": {
    "id": "U1234"
  }
}
```

- `ok == false`이면 토큰 교환 실패로 처리
- Slack Bot Token(`xoxb-`)은 만료되지 않으므로 `expiresAt`은 1년 후로 설정
- `refresh_token`은 저장하지 않음 (Slack Bot Token에는 해당 없음)

---

## 4. 보안

### state 파라미터
- userId를 AES-256-GCM으로 암호화 (`TokenEncryptionService` 사용)
- 12바이트 랜덤 IV + 128비트 GCM 태그 → Base64 인코딩 → URL 인코딩
- 콜백 수신 시 복호화하여 userId 검증 — 위변조 불가능
- 암호화 키: `${ENCRYPTION_SECRET_KEY}` 환경변수 (Base64 인코딩된 AES-256 키)

### 토큰 저장
- `access_token`은 AES-GCM으로 암호화하여 MongoDB에 저장
- 평문 토큰은 메모리에서만 존재하며 DB에는 저장되지 않음
- 워크플로우 실행 시 `OAuthTokenService.getDecryptedToken()`으로 복호화하여 사용

---

## 5. 환경 변수

| 변수명 | 설명 | 예시 |
|--------|------|------|
| `SLACK_CLIENT_ID` | Slack App Client ID | `1234567890.1234567890` |
| `SLACK_CLIENT_SECRET` | Slack App Client Secret | `abcdef1234567890` |
| `SLACK_REDIRECT_URI` | Slack 콜백 URL (서버 주소) | `https://서버주소/api/oauth-tokens/slack/callback` |

### Slack App 설정 (api.slack.com)
1. OAuth & Permissions → Redirect URLs에 `SLACK_REDIRECT_URI` 등록
2. Bot Token Scopes에 필요한 권한 추가:
   - `channels:read` — 채널 목록 조회
   - `chat:write` — 메시지 전송
   - `users:read` — 사용자 정보 조회
3. 필요에 따라 추가 scope 설정 가능 (`app.oauth.slack.scopes`에 쉼표 구분으로 추가)

---

## 6. 프론트엔드 구현 가이드

### 연동 시작
```javascript
// 1. 연동 시작 API 호출
const res = await fetch('/api/oauth-tokens/slack/connect', {
  method: 'POST',
  headers: { 'Authorization': `Bearer ${accessToken}` }
});
const { authUrl } = res.data;

// 2. Slack 인증 페이지로 이동
window.location.href = authUrl;
```

### 콜백 처리 (`/oauth/callback` 페이지)
```javascript
// URL 파라미터 읽기
const params = new URLSearchParams(window.location.search);
const service = params.get('service');   // "slack"
const connected = params.get('connected'); // "true" 또는 null
const error = params.get('error');        // "oauth_failed" 또는 null

if (connected === 'true') {
  // 연동 성공 처리 (예: 토스트 메시지, 설정 페이지로 이동)
} else if (error) {
  // 연동 실패 처리
}
```

---

## 7. 변경된 파일 목록

| 파일 | 작업 | 설명 |
|------|------|------|
| `oauth/controller/OAuthTokenController.java` | 수정 | Slack 콜백 엔드포인트 구현, connect에 state 파라미터 추가 |
| `oauth/service/SlackOAuthService.java` | 생성 | Slack 인증 URL 생성, 토큰 교환 로직 |
| `config/SecurityConfig.java` | 수정 | `/api/oauth-tokens/*/callback` permitAll 추가 |
| `application.yml` | 수정 | `app.oauth.slack.*` 설정 추가 |

---

## 8. 변경 이력

| 날짜 | 변경 내용 |
|------|-----------|
| 2026-04-14 | 최초 작성. Slack OAuth 콜백 흐름 전체 구현. |
