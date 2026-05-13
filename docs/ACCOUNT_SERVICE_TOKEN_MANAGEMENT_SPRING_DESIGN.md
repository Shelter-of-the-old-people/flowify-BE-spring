# Account Service Token Management Spring Design

> 작성일: 2026-05-13
> 대상: Spring backend
> 용도: account 기반 service token 입력/검증/저장 설계
> 관련 저장소: `flowify-FE`, `flowify-BE`

---

## 1. 목적

이 문서는 사용자가 직접 입력하는 service token 기능에서 Spring이 맡아야 하는 책임과 API 구조를 정의한다.

이번 이슈의 출발점은 아래 문제를 해결하는 데 있다.

- 현재 `notion`, `github`, `canvas_lms`는 사용자 입력 token이 아니라 서버 환경변수 token을 저장한다.
- 현재 `/api/oauth-tokens/{service}/connect`는 OAuth redirect 중심 구조라 manual token 입력에 맞지 않는다.
- FE는 연결 상태는 보여줄 수 있지만, 사용자가 직접 token을 입력하거나 갱신하는 저장 API가 없다.

이번 설계의 목표는 아래와 같다.

- `account` 화면에서 manual token을 입력하고 검증한 뒤 저장할 수 있게 한다.
- 저장된 token summary를 FE가 안전하게 표시할 수 있게 한다.
- 실행 시점 FastAPI 계약은 최대한 그대로 유지한다.
- token 발급 도움창은 같은 이슈에 포함하되, backend 책임은 최소화한다.

---

## 2. Spring 책임 범위

### 2.1 Spring이 담당하는 것

- manual token 저장 API 제공
- 서비스별 token 사전 검증
- token 암호화 저장
- 연결 상태 summary 제공
- 실행/preview 시점 decrypted token 전달
- alias 서비스 정책 유지

### 2.2 Spring이 담당하지 않는 것

- token 발급 방법 도움창 렌더링
- 실제 외부 서비스 실행 로직
- token 입력 UI 상태 관리
- token 원문 재노출 UX

위 항목은 FE 또는 FastAPI가 담당한다.

---

## 3. 제품 결정

### 3.1 canonical 화면

이번 이슈의 canonical 관리 화면은 `/account`다.

이번 이슈는 dashboard용 별도 token 입력 흐름을 만들지 않고, account 화면 안에서만 완결하는 방향으로 고정한다.

이유는 아래와 같다.

- 계정/자격증명 관리 성격이 강하다.
- 현재 FE 구조상 계정 화면이 서비스 연결의 owner 역할에 더 가깝다.
- 대시보드는 운영 요약 성격이 강하므로, v1에서 token 입력 owner로 두면 책임이 흐려진다.

### 3.2 이번 이슈 대상 서비스

manual token 입력 대상으로 보는 서비스는 아래 셋이다.

- `notion`
- `github`
- `canvas_lms`

기존 OAuth redirect 서비스는 그대로 유지한다.

- `slack`
- `gmail`
- `google_drive`

`google_sheets`는 계속 `google_drive` alias 정책을 따른다.

### 3.3 도움창 범위 판단

토큰 발급 도움창은 이번 이슈에 포함하는 것이 맞다.

다만 Spring은 도움말 본문을 내려주는 별도 CMS 역할까지 맡지 않는다.

이번 v1에서 Spring이 보장할 것은 아래 정도면 충분하다.

- 어떤 서비스가 manual token 대상인지 FE가 구분 가능하다.
- 저장 실패 시 FE가 도움말 문맥을 붙일 수 있을 정도의 명확한 validation 에러를 반환한다.

---

## 4. API 설계

### 4.1 연결 상태 조회

기존 `GET /api/oauth-tokens`를 확장해, FE가 manual token과 OAuth token을 함께 렌더링할 수 있게 한다.

응답 item은 최소 아래 필드를 포함해야 한다.

- `service`
- `connected`
- `connectionMethod`
- `expiresAt`
- `aliasOf`
- `disconnectable`
- `reason`
- `maskedHint`
- `updatedAt`
- `validationStatus`
- `accountEmail` 또는 `accountLabel`

예시:

```json
{
  "service": "github",
  "connected": true,
  "connectionMethod": "manual_token",
  "maskedHint": "ghp_...9k2m",
  "updatedAt": "2026-05-13T09:10:00Z",
  "validationStatus": "valid",
  "accountLabel": "octocat",
  "disconnectable": true
}
```

### 4.2 manual token 저장/갱신

새 API를 추가한다.

- `PUT /api/oauth-tokens/{service}/manual`

request body 예시:

```json
{
  "accessToken": "<user input token>"
}
```

동작 규칙:

- service가 manual token 대상이 아니면 `INVALID_REQUEST`
- token 형식이 비어 있거나 공백뿐이면 `INVALID_REQUEST`
- 서비스별 validation 성공 후에만 저장
- 저장은 upsert semantics로 동작
- 성공 시 최신 summary를 반환하거나, FE가 바로 refetch할 수 있게 최소 성공 응답을 반환

### 4.3 OAuth connect API 유지

기존 API는 유지한다.

- `POST /api/oauth-tokens/{service}/connect`

다만 manual token 서비스에 이 API를 호출하면, 아래처럼 명확히 거절하는 편이 안전하다.

- `"이 서비스는 account 화면에서 token 직접 입력이 필요합니다."`

### 4.4 연결 해제

기존 API를 유지한다.

- `DELETE /api/oauth-tokens/{service}`

manual token 서비스도 같은 해제 경로를 사용한다.

### 4.5 summary 필드 의미

FE가 안전하게 같은 카드 시스템을 재사용하려면 summary 필드 의미를 고정해야 한다.

권장 enum 값:

- `connectionMethod`: `oauth_redirect` | `manual_token` | `alias`
- `validationStatus`: `valid` | `invalid` | `scope_insufficient` | `unknown`

필드 규칙:

- `maskedHint`는 token 원문이 아니라 마지막 일부만 포함한 summary 문자열이다.
- `accountEmail`은 실제 이메일이 있을 때만 채운다.
- `accountLabel`은 이메일이 없는 서비스의 표시명 용도다.
- alias 서비스는 `connectionMethod = alias`, `disconnectable = false`를 권장한다.

### 4.6 에러 응답 계약

manual token 저장 API는 FE가 같은 다이얼로그 안에서 재시도 UX를 만들 수 있도록, 실패 원인을 명확하게 구분해 줘야 한다.

대표 케이스:

- `INVALID_REQUEST`
  - 지원하지 않는 서비스
  - 빈 token 입력
- `OAUTH_TOKEN_INVALID`
  - token 형식 또는 인증 실패
- `OAUTH_SCOPE_INSUFFICIENT`
  - 필요한 권한 부족
- `EXTERNAL_API_ERROR`
  - 외부 서비스 검증 API 호출 실패

FE가 서비스별 도움말을 다시 열 수 있게, 에러 메시지는 사람이 읽을 수 있는 문장으로 유지하는 것이 좋다.

---

## 5. Spring 내부 구조

### 5.1 OAuth redirect와 manual token을 분리한다

현재 `ExternalServiceConnector`는 redirect/OAuth 흐름과 direct connect를 한 인터페이스에 같이 두고 있다.

이번 이슈에서는 아래처럼 구조를 나누는 편이 안전하다.

- OAuth redirect 서비스
  - 기존 `ExternalServiceConnector` 유지
- manual token 서비스
  - 신규 `ManualTokenServiceHandler` 도입

권장 인터페이스 예시:

```java
public interface ManualTokenServiceHandler {
    String getServiceName();
    ManualTokenValidationResult validate(String accessToken);
}
```

`ManualTokenValidationResult`는 아래 정보를 가질 수 있다.

- `accountEmail`
- `accountLabel`
- `expiresAt`
- `scopes`
- `validationStatus`

### 5.2 저장 owner는 OAuthTokenService로 유지한다

token 저장 owner는 계속 `OAuthTokenService`가 맡는다.

추가가 필요한 것은 아래다.

- manual token upsert 메서드
- summary 확장 메서드
- masked hint 계산
- validation metadata 저장

### 5.3 entity 확장

현재 `OAuthToken`에는 raw token과 만료 정보 중심 필드만 있다.

manual token UX를 위해 아래 메타 필드 추가를 권장한다.

- `connectionMethod`
- `accountEmail`
- `accountLabel`
- `maskedHint`
- `validationStatus`
- `lastValidatedAt`

`updatedAt`은 이미 존재하므로 FE의 최근 갱신 표시 기준으로 재사용할 수 있다.

### 5.4 기존 데이터와 호환성

이번 기능이 들어가기 전에도 `notion`, `github`, `canvas_lms`는 env token 기반으로 저장된 기존 row가 있을 수 있다.

권장 호환 전략:

- 별도 일괄 migration 없이 읽기 시점 fallback을 둔다.
- `connectionMethod`가 비어 있고 service가 manual token 대상이면 `manual_token`으로 간주할 수 있다.
- 사용자가 새 token을 저장하면 같은 row를 upsert로 덮어쓴다.
- 더 이상 `POST /connect`가 manual token 서비스를 env token으로 저장하지 않게 막는다.

이 전략이면 기존 테스트 데이터와 개발 환경을 크게 깨지 않고 기능 전환이 가능하다.

---

## 6. 서비스별 validation 원칙

### 6.1 Notion

Spring은 최소한 아래를 검증해야 한다.

- token이 유효한 Notion integration token인지
- 기본 workspace/user 식별 정보를 읽을 수 있는지

성공 시 FE에 보여줄 label 예시:

- workspace 이름
- bot 이름

### 6.2 GitHub

Spring은 최소한 아래를 검증해야 한다.

- token이 유효한 personal access token인지
- 현재 제품 요구에 필요한 scope가 충족되는지

성공 시 FE에 보여줄 label 예시:

- GitHub login
- 계정 표시 이름

### 6.3 Canvas LMS

Spring은 현재 서버 설정의 `app.oauth.canvas-lms.api-url` 기준으로 token을 검증해야 한다.

즉 이번 v1은 아래 전제를 가진다.

- 사용자는 token만 입력한다.
- Canvas base URL은 사용자별로 받지 않는다.
- 현재 서버가 바라보는 Canvas 인스턴스 안에서만 검증한다.

성공 시 FE에 보여줄 label 예시:

- 사용자 이름
- 사용자 이메일
- 현재 학기 course access 가능 여부 요약

### 6.4 validation 결과 매핑

서비스별 validator는 최종적으로 FE summary에 들어갈 값을 같은 형태로 맞춰야 한다.

최소 매핑 기준:

- 검증 성공
  - `connected = true`
  - `validationStatus = valid`
- token 무효
  - `connected = false`
  - `validationStatus = invalid`
- 권한 부족
  - `connected = false`
  - `validationStatus = scope_insufficient`
- 외부 API 일시 실패
  - 저장은 실패시키되, summary fallback은 `unknown`

이 규칙을 맞추면 FE는 서비스별 분기보다 공용 상태 렌더링을 우선 적용할 수 있다.

---

## 7. 보안 원칙

Spring은 아래 원칙을 반드시 지켜야 한다.

- raw token은 저장 후 다시 FE에 그대로 반환하지 않는다.
- summary 응답에는 마스킹된 hint만 준다.
- validation 실패 시에도 raw token 일부를 에러 메시지에 넣지 않는다.
- 해제 이후에는 실행 시점에 해당 token이 절대 전달되지 않아야 한다.

---

## 8. FE 도움창과의 관계

토큰 발급 도움창은 같은 이슈 범위다.

다만 backend가 이 도움창을 위해 별도 rich content API를 만들 필요는 없다.

이번 v1에서는 아래 방식이면 충분하다.

- FE가 서비스별 도움말 본문을 정적 콘텐츠로 가진다.
- Spring은 service key와 validation 결과만 정확히 제공한다.
- FE는 그 정보를 바탕으로 올바른 도움창을 연다.

---

## 9. Out of Scope

이번 v1에서 아래는 범위 밖으로 둔다.

- dashboard에서 token 입력을 시작하거나 관리하는 별도 흐름
- 저장된 raw token 다시 보기
- 사용자별 Canvas base URL 입력
- token rotation 자동화
- 만료 전 알림 배치

---

## 10. 결정 요약

Spring은 이번 이슈에서 service token 관리의 backend owner다.

반드시 책임져야 하는 일은 아래와 같다.

- manual token 저장 API 제공
- 서비스별 validation
- encrypted storage
- safe summary 반환
- 기존 FastAPI `service_tokens` 계약 유지

또한 token 발급 도움창은 이번 이슈에 포함해도 되며, 그 경우 Spring은 help content provider가 아니라 validation owner 역할에 집중한다.