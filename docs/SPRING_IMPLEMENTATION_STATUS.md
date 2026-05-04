# Spring Backend 구현 현황 (FastAPI 팀 공유용)

> **작성일**: 2026-05-03
> **Spring 기준 브랜치**: `main`
> **Base URL**: `http://localhost:8080`

---

## 목차

1. [아키텍처 개요](#1-아키텍처-개요)
2. [실행 라이프사이클 (Execution Lifecycle)](#2-실행-라이프사이클)
3. [런타임 모델 변환 (WorkflowTranslator)](#3-런타임-모델-변환)
4. [FastAPI 연동 API (Spring → FastAPI)](#4-fastapi-연동-api)
5. [콜백 API (FastAPI → Spring)](#5-콜백-api)
6. [전체 API 엔드포인트 목록](#6-전체-api-엔드포인트-목록)
7. [데이터 모델 (MongoDB)](#7-데이터-모델)
8. [OAuth 토큰 관리](#8-oauth-토큰-관리)
9. [카탈로그 시스템](#9-카탈로그-시스템)
10. [시스템 템플릿](#10-시스템-템플릿)
11. [웹훅 시스템](#11-웹훅-시스템)
12. [보안 설정](#12-보안-설정)
13. [환경 변수](#13-환경-변수)

---

## 1. 아키텍처 개요

```
┌──────────┐     ┌───────────────────┐     ┌──────────────┐
│ Frontend │────▶│  Spring (8080)    │────▶│ FastAPI(8000)│
│ (3000)   │◀────│  - REST API       │◀────│ - 실행 엔진   │
│          │     │  - OAuth 관리      │     │ - AI/LLM     │
│          │     │  - 워크플로우 CRUD  │     │ - 크롤링      │
│          │     │  - 카탈로그        │     │              │
│          │     └───────┬───────────┘     └──────────────┘
│          │             │
│          │     ┌───────▼───────────┐
│          │     │    MongoDB        │
│          │     │ - workflows       │
│          │     │ - templates       │
│          │     │ - workflow_executions │
│          │     │ - oauth_tokens    │
│          │     │ - users           │
│          │     └───────────────────┘
```

**역할 분담**:
- **Spring**: 워크플로우 CRUD, 사용자 인증, OAuth 토큰 관리, 카탈로그, 실행 기록 관리
- **FastAPI**: 워크플로우 실행 엔진, AI/LLM 호출, 외부 서비스 크롤링, 데이터 처리

**통신 방식**:
- Spring → FastAPI: HTTP REST (WebClient), `X-Internal-Token` 인증
- FastAPI → Spring: HTTP POST 콜백 (`/api/internal/executions/{execId}/complete`)

---

## 2. 실행 라이프사이클

### 2.1 실행 흐름

```
사용자 → POST /api/workflows/{id}/execute
         │
         ├─ 1. 워크플로우 조회 + 소유권 검증
         ├─ 2. 실행 가능성 검증 (WorkflowValidator)
         │     - 사이클 검출, 고립 노드, 데이터 타입 호환성
         │     - 필수 설정 필드 검증, OAuth 연결 상태
         ├─ 3. 서비스 토큰 수집 (collectServiceTokens)
         │     - 노드 type별로 auth_required인 서비스의 토큰 조회
         │     - 토큰 만료 임박 시 자동 갱신 (5분 이내)
         ├─ 4. 런타임 모델 변환 (WorkflowTranslator)
         ├─ 5. FastAPI에 실행 요청 (POST /api/v1/workflows/{id}/execute)
         ├─ 6. execution_id 수신 → DB에 실행 기록 저장 (state: "running")
         └─ 7. execution_id 반환

         ... FastAPI가 비동기로 실행 ...

FastAPI → POST /api/internal/executions/{execId}/complete
         │
         ├─ X-Internal-Token 검증 (timing-safe comparison)
         ├─ 상태 정규화: "completed" → "success"
         └─ MongoDB $set partial update (state, finishedAt, error, output, durationMs)
```

### 2.2 실행 트리거 종류

| 트리거 | 진입점 | 메서드 |
|--------|--------|--------|
| 수동 실행 | `POST /api/workflows/{id}/execute` | `executeWorkflow(userId, workflowId)` |
| 스케줄 실행 | 내부 스케줄러 호출 | `executeScheduled(workflowId)` |
| 웹훅 실행 | `POST /api/webhooks/{webhookId}` | `executeFromWebhook(workflowId, eventPayload)` |

### 2.3 상태 정규화

FastAPI가 보내는 `status` → Spring이 저장하는 `state`:

| FastAPI status | Spring state |
|----------------|-------------|
| `"completed"` | `"success"` |
| `"failed"` | `"failed"` |
| `"stopped"` | `"stopped"` |
| 기타 | 그대로 저장 |

### 2.4 completeExecution 구현 (중요)

```java
// MongoDB $set partial update 사용 (전체 document save 아님)
Query query = Query.query(Criteria.where("_id").is(execId));
Update update = new Update()
        .set("state", normalizedState)
        .set("finishedAt", Instant.now())
        .set("error", error)
        .set("output", output)
        .set("durationMs", durationMs);
mongoTemplate.updateFirst(query, update, WorkflowExecution.class);
```

> **주의**: `executionRepository.save()`가 아닌 `MongoTemplate.$set` partial update를 사용합니다. MongoDB Atlas의 `id_1` 레거시 인덱스 충돌을 피하기 위함입니다.

---

## 3. 런타임 모델 변환

### 3.1 WorkflowTranslator

Spring의 에디터 모델(NodeDefinition)을 FastAPI가 이해하는 런타임 모델로 변환합니다.

**변환 결과 구조**:

```json
{
  "id": "workflow_id",
  "name": "워크플로우 이름",
  "userId": "user_id",
  "nodes": [ /* 변환된 노드 배열 */ ],
  "edges": [
    { "id": "edge_1", "source": "node_1", "target": "node_2" }
  ],
  "trigger": {
    "type": "webhook | schedule | manual",
    "config": { /* 트리거 설정 */ }
  }
}
```

### 3.2 runtime_type 결정 규칙

| 조건 | runtime_type |
|------|-------------|
| `role == "start"` | `"input"` |
| `role == "end"` | `"output"` |
| `type.toUpperCase() == "LOOP"` | `"loop"` |
| `type.toUpperCase() == "CONDITION_BRANCH"` | `"if_else"` |
| 그 외 (middle 노드) | `"llm"` |

> LLM으로 분류되는 type들: `AI`, `DATA_FILTER`, `AI_FILTER`, `PASSTHROUGH`

### 3.3 노드별 런타임 구조

**runtime_type = "input"** 인 노드:
```json
{
  "id": "node_1",
  "category": "service",
  "type": "gmail",
  "runtime_type": "input",
  "role": "start",
  "config": { /* 에디터 원본 config */ },
  "dataType": null,
  "outputDataType": "EMAIL_LIST",
  "runtime_source": {
    "service": "gmail",
    "canonical_input_type": "EMAIL_LIST",
    "mode": "label_emails",
    "target": "UNREAD"
  }
}
```

**runtime_type = "output"** 인 노드:
```json
{
  "id": "node_3",
  "category": "service",
  "type": "slack",
  "runtime_type": "output",
  "role": "end",
  "config": { /* 에디터 원본 config */ },
  "dataType": "TEXT",
  "runtime_sink": {
    "service": "slack",
    "config": { "channel": "C12345", "message_format": "markdown" }
  }
}
```

**runtime_type = "llm" | "loop" | "if_else"** 인 노드:
```json
{
  "id": "node_2",
  "category": "ai",
  "type": "llm",
  "runtime_type": "llm",
  "role": "middle",
  "config": { "prompt": "...", "model": "gpt-4.1-mini" },
  "dataType": "SINGLE_EMAIL",
  "outputDataType": "TEXT",
  "runtime_config": {
    "node_type": "llm",
    "output_data_type": "TEXT",
    "prompt": "...",
    "model": "gpt-4.1-mini",
    "action": ""
  }
}
```

> **참고**: `runtime_config`에는 `node.getConfig()`의 모든 필드가 putAll로 포함됩니다.

---

## 4. FastAPI 연동 API (Spring → FastAPI)

Spring이 FastAPI에 보내는 요청들입니다.

### 4.1 워크플로우 실행

```
POST {FASTAPI_URL}/api/v1/workflows/{workflowId}/execute
Headers:
  X-User-ID: {userId}
  X-Internal-Token: {INTERNAL_API_SECRET}
Body:
{
  "workflow": { /* 3절의 런타임 모델 */ },
  "service_tokens": {
    "google_drive": "decrypted_access_token",
    "gmail": "decrypted_access_token",
    "slack": "decrypted_access_token"
  }
}
Response:
{
  "execution_id": "exec_xxxxxxxx"
}
```

### 4.2 AI 워크플로우 생성

```
POST {FASTAPI_URL}/api/v1/workflows/generate
Headers:
  X-User-ID: {userId}
Body:
{
  "prompt": "읽지 않은 메일을 요약해서 Slack으로 보내줘"
}
Response:
{
  "name": "...",
  "nodes": [...],
  "edges": [...]
}
```

### 4.3 실행 중지

```
POST {FASTAPI_URL}/api/v1/executions/{executionId}/stop
Headers:
  X-User-ID: {userId}
```

### 4.4 실행 롤백

```
POST {FASTAPI_URL}/api/v1/executions/{executionId}/rollback
Headers:
  X-User-ID: {userId}
Body (optional):
{
  "node_id": "node_2"
}
```

### 4.5 FastApiClient WebClient 설정

- Base URL: `${FASTAPI_URL}` (기본값: `http://localhost:8000`)
- 타임아웃: 30초
- Bean 이름: `fastapiWebClient` (`@Qualifier("fastapiWebClient")`)

---

## 5. 콜백 API (FastAPI → Spring)

### 5.1 실행 완료 콜백

```
POST /api/internal/executions/{execId}/complete
Headers:
  X-Internal-Token: {INTERNAL_API_SECRET}  ← 필수!
Body:
{
  "status": "completed" | "failed" | "stopped",
  "output": { /* 실행 결과 데이터 (nullable) */ },
  "durationMs": 12345,
  "error": "에러 메시지 (nullable)"
}
```

**인증**: `X-Internal-Token` 헤더를 `MessageDigest.isEqual()`로 timing-safe 비교합니다. 불일치 시 `403 AUTH_FORBIDDEN` 반환.

**상태 정규화**: `status: "completed"` → Spring에서 `state: "success"`로 저장.

**저장 방식**: `MongoTemplate.$set` partial update (전체 document save 아님).

---

## 6. 전체 API 엔드포인트 목록

### 6.1 워크플로우 (`/api/workflows`)

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| `POST` | `/api/workflows` | 워크플로우 생성 | JWT |
| `GET` | `/api/workflows` | 내 워크플로우 목록 | JWT |
| `GET` | `/api/workflows/{id}` | 워크플로우 상세 (nodeStatuses 포함) | JWT |
| `PUT` | `/api/workflows/{id}` | 워크플로우 수정 | JWT |
| `DELETE` | `/api/workflows/{id}` | 워크플로우 삭제 | JWT |
| `POST` | `/api/workflows/{id}/share` | 워크플로우 공유 | JWT |
| `POST` | `/api/workflows/generate` | AI 워크플로우 생성 | JWT |
| `GET` | `/api/workflows/{id}/schema-preview` | 출력 스키마 프리뷰 | JWT |
| `POST` | `/api/workflows/schema-preview` | 드래프트 스키마 프리뷰 | JWT |
| `GET` | `/api/workflows/{id}/nodes/{nodeId}/schema-preview` | 노드 스키마 프리뷰 | JWT |
| `GET` | `/api/workflows/{id}/choices/{prevNodeId}` | 노드 선택지 조회 | JWT |
| `POST` | `/api/workflows/{id}/choices/{prevNodeId}/select` | 노드 선택지 확정 | JWT |
| `POST` | `/api/workflows/{id}/nodes` | 노드 추가 | JWT |
| `PUT` | `/api/workflows/{id}/nodes/{nodeId}` | 노드 수정 | JWT |
| `DELETE` | `/api/workflows/{id}/nodes/{nodeId}` | 노드 삭제 (캐스케이드) | JWT |

### 6.2 실행 (`/api/workflows`)

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| `POST` | `/api/workflows/{id}/execute` | 워크플로우 실행 | JWT |
| `GET` | `/api/workflows/{id}/executions` | 실행 이력 목록 | JWT |
| `GET` | `/api/workflows/{id}/executions/latest` | 최신 실행 조회 | JWT |
| `GET` | `/api/workflows/{id}/executions/{execId}` | 실행 상세 (nodeLogs 포함) | JWT |
| `GET` | `/api/workflows/{id}/executions/latest/nodes/{nodeId}/data` | 최신 실행 노드 데이터 | JWT |
| `GET` | `/api/workflows/{id}/executions/{execId}/nodes/{nodeId}/data` | 특정 실행 노드 데이터 | JWT |
| `POST` | `/api/workflows/{id}/executions/{execId}/stop` | 실행 중지 | JWT |
| `POST` | `/api/workflows/{id}/executions/{execId}/rollback` | 실행 롤백 | JWT |

### 6.3 내부 콜백 (`/api/internal`)

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| `POST` | `/api/internal/executions/{execId}/complete` | 실행 완료 콜백 | X-Internal-Token |

### 6.4 템플릿 (`/api/templates`)

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| `GET` | `/api/templates` | 템플릿 목록 (?category=) | 없음 |
| `GET` | `/api/templates/{id}` | 템플릿 상세 | 없음 |
| `POST` | `/api/templates/{id}/instantiate` | 템플릿으로 워크플로우 생성 | JWT |
| `POST` | `/api/templates` | 사용자 템플릿 생성 | JWT |

### 6.5 OAuth 토큰 (`/api/oauth-tokens`)

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| `GET` | `/api/oauth-tokens` | 연결된 서비스 목록 | JWT |
| `POST` | `/api/oauth-tokens/{service}/connect` | 서비스 연결 시작 | JWT |
| `GET` | `/api/oauth-tokens/{service}/callback` | OAuth 콜백 (→ FE 리다이렉트) | 없음 |
| `DELETE` | `/api/oauth-tokens/{service}` | 서비스 연결 해제 | JWT |

### 6.6 카탈로그 (`/api/editor-catalog`)

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| `GET` | `/api/editor-catalog/sources` | Source 카탈로그 | 없음 |
| `GET` | `/api/editor-catalog/sinks` | Sink 카탈로그 | 없음 |
| `GET` | `/api/editor-catalog/sinks/{serviceKey}/schema` | Sink 설정 스키마 (?inputType=) | 없음 |
| `GET` | `/api/editor-catalog/sources/{serviceKey}/target-options` | Target 선택지 (?mode=&parentId=&query=&cursor=) | JWT |
| `POST` | `/api/editor-catalog/sinks/google_drive/folders` | Google Drive 폴더 생성 | JWT |
| `GET` | `/api/editor-catalog/mapping-rules` | Mapping Rules 조회 | 없음 |

### 6.7 웹훅 (`/api/workflows/{id}/webhook`, `/api/webhooks`)

| Method | Path | 설명 | 인증 |
|--------|------|------|------|
| `POST` | `/api/workflows/{id}/webhook` | 웹훅 발급 | JWT |
| `DELETE` | `/api/workflows/{id}/webhook` | 웹훅 무효화 | JWT |
| `GET` | `/api/workflows/{id}/webhook` | 웹훅 정보 조회 | JWT |
| `POST` | `/api/webhooks/{webhookId}` | 웹훅 이벤트 수신 | X-Hub-Signature-256 |

---

## 7. 데이터 모델

### 7.1 Workflow (collection: `workflows`)

```json
{
  "_id": "ObjectId",
  "name": "워크플로우 이름",
  "description": "설명",
  "userId": "user_id",
  "sharedWith": ["user_id_1", "user_id_2"],
  "isTemplate": false,
  "templateId": "template_id (nullable)",
  "nodes": [/* NodeDefinition[] */],
  "edges": [/* EdgeDefinition[] */],
  "trigger": {
    "type": "webhook | schedule | manual",
    "config": {}
  },
  "isActive": true,
  "createdAt": "2026-05-03T...",
  "updatedAt": "2026-05-03T..."
}
```

### 7.2 NodeDefinition (embedded)

```json
{
  "id": "node_1",
  "category": "service | ai | control | web_crawl | spreadsheet | storage",
  "type": "gmail | slack | google_drive | notion | AI | llm | loop | naver_news | ...",
  "label": "노드 라벨 (nullable)",
  "config": {
    "isConfigured": true,
    "service": "gmail",
    "source_mode": "label_emails",
    "target": "UNREAD",
    "target_label": "읽지 않은 메일",
    "target_meta": { "systemLabel": true }
  },
  "position": { "x": 80, "y": 180 },
  "dataType": "EMAIL_LIST",
  "outputDataType": "SINGLE_EMAIL",
  "role": "start | middle | end",
  "authWarning": false
}
```

### 7.3 EdgeDefinition (embedded)

```json
{
  "id": "edge_1_2",
  "source": "node_1",
  "target": "node_2"
}
```

### 7.4 WorkflowExecution (collection: `workflow_executions`)

```json
{
  "_id": "exec_xxxxxxxx (FastAPI가 생성한 ID)",
  "workflowId": "workflow_id",
  "userId": "user_id",
  "state": "running | success | failed | stopped",
  "nodeLogs": [
    {
      "nodeId": "node_1",
      "status": "success | failed | skipped",
      "inputData": { /* 노드 입력 데이터 */ },
      "outputData": { /* 노드 출력 데이터 */ },
      "snapshot": {
        "capturedAt": "2026-05-03T...",
        "stateData": { /* 롤백용 상태 스냅샷 */ }
      },
      "error": {
        "code": "ERROR_CODE",
        "message": "에러 메시지",
        "stackTrace": "..."
      },
      "startedAt": "2026-05-03T...",
      "finishedAt": "2026-05-03T..."
    }
  ],
  "error": "전체 실행 에러 메시지 (nullable)",
  "output": { /* 전체 실행 출력 (nullable) */ },
  "durationMs": 12345,
  "startedAt": "2026-05-03T...",
  "finishedAt": "2026-05-03T..."
}
```

> **중요**: `_id`는 FastAPI가 생성한 `execution_id`를 그대로 사용합니다. Spring이 자체 생성하지 않습니다.

### 7.5 Template (collection: `templates`)

```json
{
  "_id": "ObjectId",
  "name": "읽지 않은 메일 요약 후 Slack 공유",
  "description": "읽지 않은 메일을 하나씩 요약해 Slack 채널로 공유합니다.",
  "category": "mail_summary_forward",
  "icon": "gmail",
  "nodes": [/* NodeDefinition[] */],
  "edges": [/* EdgeDefinition[] */],
  "requiredServices": ["gmail", "slack"],
  "isSystem": true,
  "authorId": null,
  "useCount": 0,
  "createdAt": "2026-05-03T..."
}
```

### 7.6 OAuthToken (collection: `oauth_tokens`)

```json
{
  "_id": "ObjectId",
  "userId": "user_id",
  "service": "google_drive | gmail | slack | notion | github | canvas_lms",
  "accessToken": "AES256 encrypted",
  "refreshToken": "AES256 encrypted (nullable)",
  "expiresAt": "2026-05-03T...",
  "scopes": ["https://www.googleapis.com/auth/drive.readonly", "..."]
}
```

---

## 8. OAuth 토큰 관리

### 8.1 지원 서비스 및 인증 방식

| 서비스 | 인증 방식 | Connector | Token Refresher |
|--------|----------|-----------|----------------|
| `google_drive` | OAuth 2.0 (redirect) | `GoogleDriveConnector` | `GoogleOAuthTokenRefresher` |
| `gmail` | OAuth 2.0 (redirect) | `GmailConnector` | `GmailOAuthTokenRefresher` |
| `slack` | OAuth 2.0 (redirect) | `SlackConnector` | (없음) |
| `notion` | Integration Token (direct) | `NotionConnector` | (없음) |
| `github` | Personal Token (direct) | `GitHubConnector` | (없음) |
| `canvas_lms` | API Token (direct) | `CanvasLmsConnector` | (없음) |

### 8.2 OAuth 흐름

```
FE → POST /api/oauth-tokens/{service}/connect
     → Spring이 authUrl 반환
     → FE가 authUrl로 리다이렉트
     → 사용자 인증
     → OAuth provider가 callback으로 리다이렉트

GET /api/oauth-tokens/{service}/callback?code=xxx&state=yyy
     → Spring이 code로 token 교환
     → access_token, refresh_token 암호화 저장
     → FE로 리다이렉트 (/{frontBaseUrl}/oauth/callback?service=xxx&connected=true)
```

### 8.3 토큰 자동 갱신

- **갱신 임계값**: 만료 5분 전 (`REFRESH_THRESHOLD_SECONDS = 300`)
- **전략 패턴**: `OAuthTokenRefresher` 인터페이스 → `@PostConstruct`에서 `Map<String, OAuthTokenRefresher>` 초기화
- `getDecryptedToken()` 호출 시 자동으로 갱신 여부 판단
- 현재 갱신 지원: `google_drive`, `gmail` (Google OAuth2 토큰 엔드포인트 사용)
- 갱신 미지원 서비스: `OAUTH_TOKEN_EXPIRED` 예외 발생

### 8.4 토큰 수집 (실행 시)

`ExecutionService.collectServiceTokens(userId, nodes)`:

```
1. 모든 노드의 type을 추출 (distinct)
2. catalogService.isAuthRequired(type) == true인 서비스만 필터
3. oauthTokenService.getDecryptedToken(userId, service) 호출
   → 만료 임박 시 자동 갱신
   → 복호화된 access_token 반환
4. Map<String, String> { "service_name": "decrypted_token" } 구성
5. FastAPI 실행 요청에 service_tokens로 전달
```

### 8.5 토큰 암호화

- AES-256 암호화 (`TokenEncryptionService`)
- 환경 변수: `ENCRYPTION_SECRET_KEY` (Base64 인코딩된 32바이트 키)
- access_token, refresh_token 모두 암호화 저장
- state 파라미터도 userId 암호화에 사용 (CSRF 방지)

---

## 9. 카탈로그 시스템

### 9.1 개요

카탈로그는 JSON 파일로 정의되며, 애플리케이션 시작 시 `@PostConstruct`로 메모리에 로딩됩니다.

- `src/main/resources/catalog/source_catalog.json` — Source 서비스 정의
- `src/main/resources/catalog/sink_catalog.json` — Sink 서비스 정의
- `src/main/resources/catalog/schema_types.json` — 데이터 타입 스키마

### 9.2 Source 서비스 목록

| key | label | auth_required | source_modes |
|-----|-------|--------------|-------------|
| `google_drive` | Google Drive | true | single_file, file_changed, new_file, folder_new_file, folder_all_files |
| `gmail` | Gmail | true | single_email, new_email, sender_email, starred_email, label_emails, attachment_email |
| `google_sheets` | Google Sheets | true | sheet_all, new_row, row_updated |
| `google_calendar` | Google Calendar | true | daily_schedule, weekly_schedule |
| `canvas_lms` | Canvas LMS | true | course_files, course_new_file, term_all_files |
| `youtube` | YouTube | false | search, channel_new_video, video_comments |
| `naver_news` | 네이버 뉴스 | false | keyword_search, periodic_collect |
| `coupang` | 쿠팡 | false | product_price, product_reviews |
| `github` | GitHub | true | new_pr |
| `slack` | Slack | true | channel_messages |
| `notion` | Notion | true | page_content |

### 9.3 Sink 서비스 목록

| key | label | auth_required | accepted_input_types |
|-----|-------|--------------|---------------------|
| `slack` | Slack | true | TEXT |
| `gmail` | Gmail | true | TEXT, SINGLE_FILE, FILE_LIST |
| `notion` | Notion | true | TEXT, SPREADSHEET_DATA, API_RESPONSE |
| `google_drive` | Google Drive | true | TEXT, SINGLE_FILE, FILE_LIST, SPREADSHEET_DATA |
| `google_sheets` | Google Sheets | true | TEXT, SPREADSHEET_DATA, API_RESPONSE |
| `google_calendar` | Google Calendar | true | TEXT, SCHEDULE_DATA |

### 9.4 Canonical Data Types

워크플로우 노드 간 데이터 흐름에서 사용하는 표준 데이터 타입:

| 타입 | 설명 |
|------|------|
| `TEXT` | 텍스트 데이터 |
| `SINGLE_FILE` | 단일 파일 |
| `FILE_LIST` | 파일 목록 |
| `SINGLE_EMAIL` | 단일 이메일 |
| `EMAIL_LIST` | 이메일 목록 |
| `SPREADSHEET_DATA` | 스프레드시트 데이터 |
| `SCHEDULE_DATA` | 캘린더 일정 |
| `API_RESPONSE` | API 응답 데이터 |

### 9.5 isAuthRequired 판단 로직

```
1. source_catalog에서 serviceKey 검색 → auth_required 반환
2. 없으면 sink_catalog에서 검색 → auth_required 반환
3. 둘 다 없으면 false
```

---

## 10. 시스템 템플릿

서버 시작 시 `TemplateSeeder`가 시스템 템플릿을 upsert합니다.

### 10.1 Upsert 로직

```
for each seedTemplate:
  1. findByNameAndIsSystem(name, true) 조회
  2. 존재하면: id, useCount, createdAt 보존하고 나머지 덮어쓰기
  3. 미존재: 새로 저장
```

### 10.2 현재 시스템 템플릿 (7개)

| 이름 | 카테고리 | 노드 흐름 | requiredServices |
|------|---------|----------|-----------------|
| 학습 노트 자동 생성 | storage | google_drive → AI → notion | google_drive, notion |
| 회의록 요약 및 공유 | communication | google_drive → AI → slack | google_drive, slack |
| 뉴스 수집 및 정리 | web_crawl | naver_news → AI → google_sheets | google_drive |
| 구글 시트 → 리포트 생성 | spreadsheet | google_sheets → AI → google_drive | google_drive |
| **읽지 않은 메일 요약 후 Slack 공유** | mail_summary_forward | gmail → loop → llm → slack | gmail, slack |
| **중요 메일 요약 후 Notion 저장** | mail_summary_forward | gmail → loop → llm → notion | gmail, notion |
| **중요 메일 할 일 추출 후 Notion 저장** | mail_summary_forward | gmail → loop → llm → notion | gmail, notion |

### 10.3 메일 템플릿 노드 구조 (FastAPI가 실행해야 하는 데이터 흐름)

```
[gmail] → [loop] → [llm] → [slack/notion]
  │          │        │          │
  │          │        │          └─ role: end
  │          │        │             dataType: TEXT
  │          │        │             runtime_type: output
  │          │        │
  │          │        └─ role: middle
  │          │           type: llm
  │          │           dataType: SINGLE_EMAIL → outputDataType: TEXT
  │          │           runtime_type: llm
  │          │           config.prompt: "아래 메일의 핵심 내용을 3줄로 요약..."
  │          │           config.model: "gpt-4.1-mini"
  │          │
  │          └─ role: middle
  │             type: loop
  │             dataType: EMAIL_LIST → outputDataType: SINGLE_EMAIL
  │             runtime_type: loop
  │             config.targetField: "items"
  │             config.maxIterations: 100
  │             config.timeout: 300
  │
  └─ role: start
     type: gmail
     outputDataType: EMAIL_LIST
     runtime_type: input
     config.source_mode: "label_emails"
     config.target: "UNREAD" 또는 "IMPORTANT"
```

---

## 11. 웹훅 시스템

### 11.1 웹훅 발급

- `POST /api/workflows/{id}/webhook` → `webhookId` + `webhookSecret` 반환
- 워크플로우에 webhook trigger가 설정됨

### 11.2 웹훅 수신

```
POST /api/webhooks/{webhookId}
Headers:
  X-Hub-Signature-256: sha256=xxxx (optional)
Body:
  { /* 임의 JSON payload */ }
```

- HMAC-SHA256 서명 검증 (optional)
- 검증 성공 시 → `executeFromWebhook(workflowId, eventPayload)`
- `eventPayload`는 `trigger.config.event_payload`에 주입되어 FastAPI로 전달

---

## 12. 보안 설정

### 12.1 공개 엔드포인트 (인증 불필요)

```
/api/auth/**
/api/health
/api/oauth-tokens/*/callback
/api/webhooks/**
/api/internal/**
/api/editor-catalog/sources
/api/editor-catalog/sinks
/api/editor-catalog/sinks/*/schema
/api/editor-catalog/mapping-rules
/api/templates (GET)
/api/templates/* (GET)
/swagger-ui/**
/v3/api-docs/**
```

### 12.2 인증 필요 엔드포인트

위 목록 외 모든 엔드포인트 → JWT Bearer 토큰 필요

### 12.3 내부 API 인증

- `/api/internal/**` 경로는 Spring Security에서 `permitAll()`
- 대신 컨트롤러 레벨에서 `X-Internal-Token` 헤더를 직접 검증
- `MessageDigest.isEqual()` 사용 (timing-safe comparison)

---

## 13. 환경 변수

### 13.1 필수

| 변수 | 설명 | 예시 |
|------|------|------|
| `MONGODB_URL` | MongoDB 연결 URI | `mongodb://localhost:27017/flowify` |
| `GOOGLE_CLIENT_ID` | Google OAuth 클라이언트 ID | `xxx.apps.googleusercontent.com` |
| `GOOGLE_CLIENT_SECRET` | Google OAuth 클라이언트 시크릿 | |
| `GOOGLE_REDIRECT_URI` | Google 로그인 콜백 URI | `http://localhost:8080/api/auth/google/callback` |
| `FRONT_REDIRECT_URI` | 프론트엔드 인증 콜백 | `http://localhost:3000/auth/callback` |
| `JWT_SECRET` | JWT 서명 키 (32자 이상) | |
| `ENCRYPTION_SECRET_KEY` | AES-256 암호화 키 (Base64) | |
| `INTERNAL_API_SECRET` | Spring ↔ FastAPI 내부 토큰 | |

### 13.2 OAuth 서비스별

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `SLACK_CLIENT_ID` | Slack OAuth 클라이언트 ID | (필수) |
| `SLACK_CLIENT_SECRET` | Slack OAuth 클라이언트 시크릿 | (필수) |
| `SLACK_REDIRECT_URI` | Slack 콜백 URI | (필수) |
| `GMAIL_CLIENT_ID` | Gmail OAuth 클라이언트 ID | `${GOOGLE_CLIENT_ID}` |
| `GMAIL_CLIENT_SECRET` | Gmail OAuth 시크릿 | `${GOOGLE_CLIENT_SECRET}` |
| `GMAIL_REDIRECT_URI` | Gmail 콜백 URI | `http://localhost:8080/api/oauth-tokens/gmail/callback` |
| `GOOGLE_DRIVE_REDIRECT_URI` | Google Drive 콜백 URI | `http://localhost:8080/api/oauth-tokens/google_drive/callback` |
| `NOTION_INTEGRATION_TOKEN` | Notion Integration Token | (필수) |
| `GITHUB_TOKEN` | GitHub Personal Token | (필수) |
| `CANVAS_API_URL` | Canvas LMS API URL | `https://canvas.kumoh.ac.kr` |
| `CANVAS_TOKEN` | Canvas API 토큰 | (빈 문자열) |

### 13.3 FastAPI 연동

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `FASTAPI_URL` | FastAPI 서버 URL | `http://localhost:8000` |
| `INTERNAL_API_SECRET` | 내부 API 토큰 (양쪽 동일해야 함) | (필수) |

### 13.4 기타

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `SERVER_PORT` | Spring 서버 포트 | `8080` |
| `CORS_ALLOWED_ORIGINS` | CORS 허용 Origin | `http://localhost:3000` |

### 13.5 OAuth Scope 현황

| 서비스 | Scope |
|--------|-------|
| Google Drive | `drive.readonly`, `drive.file` |
| Gmail | `gmail.readonly` |
| Slack | `channels:read`, `chat:write`, `users:read` |
| Google 로그인 | `openid`, `email`, `profile` |

---

## 변경 이력

| 날짜 | 변경 내용 |
|------|----------|
| 2026-05-03 | Gmail OAuth 연동 추가 (GmailConnector, GmailOAuthTokenRefresher) |
| 2026-05-03 | 메일 요약/전달 시스템 템플릿 3종 추가 |
| 2026-05-03 | Google Drive 폴더 생성 API 추가 (`POST /api/editor-catalog/sinks/google_drive/folders`) |
| 2026-05-03 | Google Drive scope 변경: `drive.readonly` + `drive.file` |
| 2026-05-03 | OAuth 토큰 자동 갱신 전략 패턴 적용 |
