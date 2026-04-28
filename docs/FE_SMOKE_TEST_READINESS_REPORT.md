# FE Smoke Test 사전 정합 점검 보고서

> **작성일**: 2026-04-28
> **요청**: FE source/sink editor 1차 구현 기준, smoke test 시작 전 백엔드 최종 점검
> **결과**: 3건 요청 → 3건 해소, **전체 테스트 GREEN (78/78)**

---

## 1. Google Drive Redirect URI 정합

### 문제

`GoogleDriveConnector`의 서비스 키가 `google_drive`(underscore)로 정렬된 이후에도,
두 곳에서 구 경로(`google-drive`, hyphen)가 남아 있었습니다.

| 위치 | 수정 전 | 수정 후 |
|---|---|---|
| `application.yml` fallback (line 32) | `http://localhost:8080/api/oauth-tokens/google-drive/callback` | `http://localhost:8080/api/oauth-tokens/google_drive/callback` |
| `docker-compose-v2.local.yml` | `GOOGLE_DRIVE_REDIRECT_URI` **미정의** (fallback 사용) | `http://localhost:8080/api/oauth-tokens/google_drive/callback` **명시 추가** |

### 위험 시나리오

`docker-compose-v2.local.yml`에 `GOOGLE_DRIVE_REDIRECT_URI`가 없으면 `application.yml`의 fallback을 사용합니다.
수정 전 fallback이 `/google-drive/callback`이었으므로, 컨테이너 환경에서 Google OAuth callback이 404를 반환했을 것입니다.

### 조치

- `application.yml` fallback 경로를 `google_drive`로 수정
- `docker-compose-v2.local.yml`에 `GOOGLE_DRIVE_REDIRECT_URI`를 명시 추가
- `.env`에는 이미 이전 점검에서 수정 완료

### 전체 경로 정합 확인

| 설정 소스 | 경로 | 상태 |
|---|---|---|
| `.env` | `/api/oauth-tokens/google_drive/callback` | OK |
| `application.yml` fallback | `/api/oauth-tokens/google_drive/callback` | OK (수정됨) |
| `docker-compose-v2.local.yml` | `/api/oauth-tokens/google_drive/callback` | OK (추가됨) |
| `GoogleDriveConnector.getServiceName()` | `"google_drive"` | OK (이전 점검에서 수정) |
| `GoogleDriveConnector.saveToken()` | `"google_drive"` | OK (이전 점검에서 수정) |
| `OAuthTokenController` connectorMap key | `"google_drive"` (getServiceName에서 파생) | OK |
| `source_catalog.json` | `"google_drive"` | OK |
| `ExecutionService.collectServiceTokens()` | `node.getType()` = `"google_drive"` | OK |

> **참고**: Google Cloud Console의 Authorized redirect URIs도 `/google_drive/callback`으로 변경 필요
> (production: cloudtype URL, local: localhost:8080)

---

## 2. 백엔드 테스트 GREEN 달성

### 수정 전 상태: 3건 실패

| 테스트 | 실패 원인 |
|---|---|
| `ExecutionServiceTest` — 워크플로우 실행 성공 | `CatalogService`, `NodeLifecycleService`, `MongoTemplate`, `WorkflowTranslator` Mock 누락 → NPE |
| `ExecutionServiceTest` — 서비스 노드 토큰 수집 | 동일 원인 (Mock 부족으로 `@InjectMocks` 실패) |
| `FlowifyApplicationTests` — contextLoads | `ExchangeCodeRepository` bean 미주입 + `MongoTemplate` bean 부재 + `app.oauth.*` placeholder 미해결 |

### 수정 내용

#### `ExecutionServiceTest.java`
- Mock 4건 추가: `MongoTemplate`, `CatalogService`, `NodeLifecycleService`, `WorkflowTranslator`
- `executeWorkflow_success()`: `workflowValidator.validate()` → 삭제 (현재 코드는 `validateForExecution()` 사용, void 메서드이므로 mock 불필요)
- `executeWorkflow_collectsServiceTokens()`: `catalogService.isAuthRequired("google")` mock 추가, `workflowTranslator.toRuntimeModel()` mock 추가

#### `FlowifyApplicationTests.java`
- `@MockitoBean ExchangeCodeRepository` 추가 (auth 모듈에서 사용하는 repository)
- `@MockitoBean MongoTemplate` 추가 (Mongo auto-config이 test profile에서 exclude되어 있으므로)

#### `application-test.yml`
- `app.auth.front-redirect-uri` 추가
- `app.oauth.slack`, `app.oauth.google-drive`, `app.oauth.notion`, `app.oauth.github`, `app.oauth.canvas-lms` 전체 추가
- 이들 없이는 `@Value("${app.oauth.google-drive.client-id}")` 등의 placeholder가 해결되지 않아 context load 실패

### 수정 후 결과

```
./gradlew test
78 tests completed, 0 failed
BUILD SUCCESSFUL
```

---

## 3. OAuth 지원 범위 — Smoke Test 대상 서비스 명시

### 이번 Phase에서 지원되는 서비스 (connect + execute 가능)

| 서비스 key | Connector | connect 방식 | execute 시 토큰 제공 | Smoke Test 대상 |
|---|---|---|---|---|
| `google_drive` | `GoogleDriveConnector` | OAuth redirect | O | **YES** |
| `slack` | `SlackOAuthService` | OAuth redirect | O | **YES** |
| `notion` | `NotionTokenService` | Direct (서버 토큰) | O | **YES** |
| `github` | `GitHubTokenService` | Direct (서버 토큰) | O | **YES** |
| `canvas_lms` | `CanvasLmsConnector` | Direct (서버 토큰) | O | **YES** |

### auth_required=false — Connector 불필요 (공개 API)

| 서비스 key | connect 필요 | execute 시 토큰 | Smoke Test 대상 |
|---|---|---|---|
| `youtube` | 불필요 | 불필요 | **YES** (토큰 없이 실행 가능) |
| `naver_news` | 불필요 | 불필요 | **YES** |
| `coupang` | 불필요 | 불필요 | **YES** |

### 이번 Phase에서 미지원 서비스 (Connector 미구현)

| 서비스 key | catalog 존재 | auth_required | Connector | Smoke Test |
|---|---|---|---|---|
| `gmail` | O | true | **없음** | **NO — 제외** |
| `google_sheets` | O | true | **없음** | **NO — 제외** |
| `google_calendar` | O | true | **없음** | **NO — 제외** |

### FE 대응 권장

| 시나리오 | 권장 처리 |
|---|---|
| `gmail` / `google_sheets` / `google_calendar` 노드를 에디터에서 선택 | UI에서 선택은 가능하되, connect 시도 시 "이번 버전에서는 지원되지 않습니다" 안내 |
| 해당 노드가 포함된 워크플로우 실행 | `nodeStatuses`에서 `executable: false` + `missingFields` 표시됨 → 실행 버튼 비활성화로 자연스럽게 차단됨 |

---

## FE 전제 사항 확인

| FE 전제 | 백엔드 확인 |
|---|---|
| `nodeStatuses`를 authoritative lifecycle source로 사용 | **정확함** — `GET /api/workflows/{id}` 응답에 `NodeLifecycleService.evaluate()` 결과 포함 |
| mutation 후 detail refetch로 lifecycle 재동기화 | **올바른 패턴** — POST/PUT 응답에는 nodeStatuses 미포함, GET에서만 제공 |
| schema preview endpoint: `/api/workflows/schema-preview` | **정확함** — `POST /api/workflows/schema-preview` (임시 노드 기반), `GET /api/workflows/{id}/schema-preview` (저장된 워크플로우 기반) |

---

## 수정된 파일 목록

| 파일 | 변경 내용 |
|---|---|
| `src/main/resources/application.yml` | google-drive redirect-uri fallback → `google_drive` 경로로 수정 |
| `src/main/resources/docs/docker-compose-v2.local.yml` | `GOOGLE_DRIVE_REDIRECT_URI` 명시 추가 |
| `src/test/resources/application-test.yml` | `app.auth`, `app.oauth.*` 전체 섹션 추가 |
| `src/test/java/.../FlowifyApplicationTests.java` | `ExchangeCodeRepository`, `MongoTemplate` MockitoBean 추가 |
| `src/test/java/.../ExecutionServiceTest.java` | 4개 Mock 추가, 테스트 메서드 현재 서비스 시그니처에 맞게 수정 |

---

## Smoke Test 진입 판정

| 항목 | 상태 |
|---|---|
| Google Drive redirect URI 전체 정합 | **PASS** |
| 백엔드 테스트 전체 GREEN (78/78) | **PASS** |
| 지원 서비스 범위 명시 | **PASS** |
| FE 전제 사항 검증 | **PASS** |

> **결론: FE smoke test 진입 가능.**
> 지원 서비스 5종 (google_drive, slack, notion, github, canvas_lms) + 공개 API 3종 (youtube, naver_news, coupang)을 대상으로
> "editor contract 확인" → "실제 연결/실행 시나리오 확인"으로 진행할 수 있습니다.

---

## 참고: docker-compose 파일 위치

| 파일 | 용도 |
|---|---|
| `docker-compose.yml` (루트) | 기본 compose — `env_file: .env` 사용, MongoDB 포함 |
| `src/main/resources/docs/docker-compose-v2.local.yml` | Spring Boot 단독 로컬 실행용 — 환경변수 inline 명시 |
| `src/main/resources/docs/docker-compose-fastapi-v2.local.yml` | FastAPI 단독 로컬 실행용 |
