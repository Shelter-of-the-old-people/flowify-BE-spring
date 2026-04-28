# Backend 정합 점검 보고서

> **작성일**: 2026-04-28
> **목적**: FE smoke test 시작 전, Spring 백엔드 정합성 사전 점검
> **점검 항목**: 6개 항목

---

## 1. `google_drive` 서비스 키 일관성

### 문제 (CRITICAL)

`GoogleDriveConnector`가 서비스 키로 `"google-drive"` (하이픈)를 사용하고 있었으나,
나머지 시스템 전체는 `"google_drive"` (언더스코어)를 사용하고 있어 **토큰 조회 실패** 위험이 있었음.

| 위치 | 수정 전 | 수정 후 |
|---|---|---|
| `GoogleDriveConnector.getServiceName()` | `"google-drive"` | `"google_drive"` |
| `GoogleDriveConnector.handleCallback()` saveToken 호출 | `"google-drive"` | `"google_drive"` |
| `.env` GOOGLE_DRIVE_REDIRECT_URI 경로 | `/google-drive/callback` | `/google_drive/callback` |

### 영향 범위

- `ExecutionService.collectServiceTokens()` : `node.getType()` = `"google_drive"`로 토큰을 조회하므로, 저장 키가 `"google-drive"`이면 토큰을 찾지 못함
- `NodeLifecycleService.checkOAuthToken()` : 동일한 키 불일치로 노드 상태가 항상 `missingFields`로 평가됨
- `OAuthTokenController.connectorMap` : FE가 `/api/oauth-tokens/google_drive/connect`로 호출해야 매칭됨

### 조치 결과

- `GoogleDriveConnector.java` 수정 완료
- `.env` redirect URI 경로 수정 완료

### 추가 필요 작업

> Google Cloud Console > OAuth 2.0 > Authorized redirect URIs에서
> `/google-drive/callback` -> `/google_drive/callback`으로 변경 필요

---

## 2. OAuth 지원 범위 vs Catalog 범위

### 현황

| Catalog 서비스 (auth_required=true) | Connector 클래스 | 상태 |
|---|---|---|
| `google_drive` | `GoogleDriveConnector` | OK |
| `gmail` | - | **미구현** |
| `google_sheets` | - | **미구현** |
| `google_calendar` | - | **미구현** |
| `canvas_lms` | `CanvasLmsConnector` | OK |
| `github` | `GitHubTokenService` | OK |
| `slack` | `SlackOAuthService` | OK |
| `notion` | `NotionTokenService` | OK |

### Catalog 서비스 (auth_required=false) - Connector 불필요

| 서비스 | 비고 |
|---|---|
| `youtube` | 공개 API |
| `naver_news` | 공개 API |
| `coupang` | 공개 API |

### 영향

FE에서 `gmail`, `google_sheets`, `google_calendar` 소스 노드를 워크플로우에 추가하고 OAuth 연결을 시도하면:

```
IllegalArgumentException: "지원하지 않는 서비스: gmail"
```

### 권장 사항

- FE smoke test에서는 위 3개 서비스를 **제외**하고 진행
- Connector 구현은 Phase 3에서 진행 (Google OAuth scope 확장 필요)

---

## 3. 테스트 컴파일 오류

### 문제

`WorkflowServiceTest.getWorkflowsByUserId()` 테스트가 컴파일 실패:

```java
// 테스트 코드 (수정 전) - 3개 파라미터, Page 반환 기대
var result = workflowService.getWorkflowsByUserId("user123", 0, 10);
assertThat(result.getContent()).hasSize(1);

// 실제 서비스 메서드 시그니처 - 1개 파라미터, List 반환
public List<WorkflowResponse> getWorkflowsByUserId(String userId)
```

### 조치 결과

```java
// 테스트 코드 (수정 후)
List<WorkflowResponse> result = workflowService.getWorkflowsByUserId("user123");
assertThat(result).hasSize(1);
```

- Mock도 `findByUserIdOrSharedWithContainingOrderByUpdatedAtDesc`로 변경
- 미사용 import (`Page`, `PageImpl`, `Pageable`) 제거
- `./gradlew testClasses` → **BUILD SUCCESSFUL** 확인

---

## 4. Schema Preview 엔드포인트

### 권위적 엔드포인트 (`WorkflowController`)

| Method | Path | 용도 | 인증 |
|---|---|---|---|
| `GET` | `/api/workflows/{id}/schema-preview` | 저장된 워크플로우 기반 스키마 미리보기 | JWT 필요 |
| `POST` | `/api/workflows/schema-preview` | 임시 노드 배열로 스키마 미리보기 | JWT 필요 |

### FE 사용 가이드

| 시나리오 | 사용 엔드포인트 |
|---|---|
| 편집 중 실시간 미리보기 (저장 전) | `POST /api/workflows/schema-preview` |
| 저장된 워크플로우 확인 | `GET /api/workflows/{id}/schema-preview` |

### POST 요청 Body 형식

```json
{
  "nodes": [
    {
      "id": "node_1",
      "type": "google_drive",
      "category": "source",
      "sourceMode": "single_file",
      "config": { "targetId": "file-id-here" }
    }
  ]
}
```

---

## 5. nodeStatuses 라이프사이클

### API별 nodeStatuses 포함 여부

| API | nodeStatuses 포함 | 비고 |
|---|---|---|
| `POST /api/workflows` (생성) | **미포함** | 생성 직후 응답에 없음 |
| `PUT /api/workflows/{id}` (수정) | **미포함** | 수정 직후 응답에 없음 |
| `GET /api/workflows/{id}` (상세 조회) | **포함** | `NodeLifecycleService.evaluate()` 호출 |
| `GET /api/workflows` (목록 조회) | **미포함** | 목록에서는 평가하지 않음 |

### nodeStatuses 평가 기준 (`NodeLifecycleService`)

| 상태 | 조건 |
|---|---|
| `saveable` | 노드가 저장 가능한 최소 조건 충족 |
| `configured` | sourceMode/sinkMode + config 완전 설정 |
| `choiceable` | choice 노드의 매핑이 완료됨 |
| `executable` | configured + OAuth 토큰 존재 (auth_required인 경우) |
| `missingFields` | 부족한 필드 목록 |

### FE 권장 패턴

```
POST/PUT (생성/수정) → GET /{id} (refetch) → nodeStatuses 갱신
```

이 **refetch 패턴이 올바른 접근**입니다. 생성/수정 응답에 nodeStatuses를 넣지 않은 이유는
evaluate()가 DB 조회 + 외부 토큰 확인을 수반하므로 쓰기 작업에서는 불필요한 오버헤드이기 때문입니다.

---

## 6. FE Smoke Test 준비 상태

### 바로 테스트 가능한 시나리오

| 시나리오 | 엔드포인트 | 비고 |
|---|---|---|
| 워크플로우 CRUD | `POST/GET/PUT/DELETE /api/workflows` | 정상 |
| 노드/엣지 편집 | `PUT /api/workflows/{id}` | 정상 |
| nodeStatuses 확인 | `GET /api/workflows/{id}` | refetch 패턴 |
| Schema preview | `GET/POST` schema-preview | 정상 |
| 소스/싱크 카탈로그 조회 | `GET /api/catalog/sources`, `GET /api/catalog/sinks` | 정상 |
| Notion 연결 | `POST /api/oauth-tokens/notion/connect` | DirectlyConnected |
| GitHub 연결 | `POST /api/oauth-tokens/github/connect` | DirectlyConnected |
| Canvas LMS 연결 | `POST /api/oauth-tokens/canvas_lms/connect` | DirectlyConnected |
| Google Drive 연결 | `POST /api/oauth-tokens/google_drive/connect` | OAuth redirect (Cloud Console URI 변경 필요) |
| Slack 연결 | `POST /api/oauth-tokens/slack/connect` | OAuth redirect |
| 연결된 서비스 목록 | `GET /api/oauth-tokens` | 정상 |

### 테스트 불가 / 보류 시나리오

| 시나리오 | 사유 | 해결 방법 |
|---|---|---|
| Gmail 소스 노드 | Connector 미구현 | Phase 3 구현 |
| Google Sheets 소스 노드 | Connector 미구현 | Phase 3 구현 |
| Google Calendar 소스 노드 | Connector 미구현 | Phase 3 구현 |
| Slack OAuth 콜백 | `.env` redirect URI에 더블 슬래시(`//api/`) | URI 수정 필요 |
| 워크플로우 실행 (Execution) | FastAPI 서버 가동 필요 | FastAPI 서버 기동 후 테스트 |

### 알려진 이슈

1. **`.env` Slack redirect URI 더블 슬래시**
   ```
   # 현재 (문제)
   SLACK_REDIRECT_URI=...sel3.cloudtype.app//api/oauth-tokens/slack/callback
   # 수정 필요
   SLACK_REDIRECT_URI=...sel3.cloudtype.app/api/oauth-tokens/slack/callback
   ```

2. **Google Cloud Console redirect URI 업데이트 필요**
   - `/google-drive/callback` → `/google_drive/callback`

---

## 수정 내역 요약

| 파일 | 변경 내용 |
|---|---|
| `GoogleDriveConnector.java` | `"google-drive"` → `"google_drive"` (getServiceName, saveToken) |
| `.env` | GOOGLE_DRIVE_REDIRECT_URI 경로 수정 |
| `WorkflowServiceTest.java` | 메서드 시그니처 불일치 수정, 미사용 import 제거 |
