# Workflow Source/Sink 통합 명세

> 최종 갱신: 2026-04-21
> 통합 대상: WORKFLOW_SOURCE_SINK_PLAN, WORKFLOW_SOURCE_SINK_BACKEND_ISSUES,
> WORKFLOW_SOURCE_SINK_BACKEND_REQUEST, WORKFLOW_SOURCE_SINK_FLOW_DESIGN,
> WORKFLOW_SOURCE_SINK_BACKEND_FINAL_REQUEST, BACKEND_2ND_UPDATE_PLAN,
> FRONTEND_BACKEND_GAP_ANALYSIS
> 상태: 1차 구현 완료 (2026-04-20), 2차 보강 완료 (2026-04-21)

---

## 1. 아키텍처 결정

### 1.1 선택된 접근: Translation Layer (Option B)

Spring이 editor 모델과 runtime 모델을 분리하고, `WorkflowTranslator`가 변환을 담당한다.

```
[FE Editor] → [Spring Public API] → [WorkflowTranslator] → [FastAPI Runtime]
                  │                         │
          editor contract            runtime contract
         (camelCase, UI 중심)       (snake_case, 실행 중심)
```

- **Spring** = editor/public contract owner (catalog API, workflow CRUD)
- **FastAPI** = runtime/execution owner (실행, 롤백, 중지)
- **WorkflowTranslator** = 변환 계층 (editor → runtime payload)

### 1.2 runtime_type 5종 매핑

| runtime_type | 조건 | FastAPI 전략 |
|-------------|------|-------------|
| `input` | role = "start" | InputNodeStrategy |
| `output` | role = "end" | OutputNodeStrategy |
| `llm` | AI, DATA_FILTER, AI_FILTER, PASSTHROUGH | LLMNodeStrategy |
| `if_else` | CONDITION_BRANCH | IfElseNodeStrategy |
| `loop` | LOOP | LoopNodeStrategy |

---

## 2. Source/Sink 데이터 흐름 목표 상태

```
[Source Node] → canonical_input_type → [Processing Nodes] → [Sink Node]
  google_drive     SINGLE_FILE              AI 요약           slack
  gmail            EMAIL_LIST             데이터 필터          notion
  google_sheets    SPREADSHEET_DATA         조건 분기         google_drive
```

### 2.1 Canonical Data Types (8종)

| type | 설명 | is_list | display |
|------|------|---------|---------|
| SINGLE_FILE | 단일 파일 | false | document |
| FILE_LIST | 파일 목록 | true | table |
| SINGLE_EMAIL | 단일 이메일 | false | document |
| EMAIL_LIST | 이메일 목록 | true | table |
| SPREADSHEET_DATA | 스프레드시트 데이터 | true | table |
| API_RESPONSE | API 응답 | false | json |
| SCHEDULE_DATA | 일정 데이터 | true | table |
| TEXT | 텍스트 | false | document |

> 상세 필드 정의: `catalog/schema_types.json` 참조

---

## 3. Source Catalog

> 출처: `source_catalog.json` (version 1.0.0, 2026-04-20)

### 3.1 전체 서비스/모드 목록 (10 서비스, 26 모드)

| service | mode | canonical_input_type | trigger_kind | auth |
|---------|------|---------------------|-------------|------|
| `google_drive` | `single_file` | SINGLE_FILE | manual | O |
| `google_drive` | `file_changed` | SINGLE_FILE | event | O |
| `google_drive` | `new_file` | SINGLE_FILE | event | O |
| `google_drive` | `folder_new_file` | SINGLE_FILE | event | O |
| `google_drive` | `folder_all_files` | FILE_LIST | manual | O |
| `gmail` | `single_email` | SINGLE_EMAIL | manual | O |
| `gmail` | `new_email` | SINGLE_EMAIL | event | O |
| `gmail` | `sender_email` | SINGLE_EMAIL | event | O |
| `gmail` | `starred_email` | SINGLE_EMAIL | manual | O |
| `gmail` | `label_emails` | EMAIL_LIST | manual | O |
| `gmail` | `attachment_email` | FILE_LIST | event | O |
| `google_sheets` | `sheet_all` | SPREADSHEET_DATA | manual | O |
| `google_sheets` | `new_row` | SPREADSHEET_DATA | event | O |
| `google_sheets` | `row_updated` | SPREADSHEET_DATA | event | O |
| `google_calendar` | `daily_schedule` | SCHEDULE_DATA | schedule | O |
| `google_calendar` | `weekly_schedule` | SCHEDULE_DATA | schedule | O |
| `youtube` | `search` | API_RESPONSE | manual | X |
| `youtube` | `channel_new_video` | API_RESPONSE | event | X |
| `youtube` | `video_comments` | API_RESPONSE | manual | X |
| `naver_news` | `keyword_search` | API_RESPONSE | manual | X |
| `naver_news` | `periodic_collect` | API_RESPONSE | schedule | X |
| `coupang` | `product_price` | API_RESPONSE | manual | X |
| `coupang` | `product_reviews` | API_RESPONSE | manual | X |
| `github` | `new_pr` | API_RESPONSE | event | O |
| `slack` | `channel_messages` | TEXT | manual | O |
| `notion` | `page_content` | TEXT | manual | O |

---

## 4. Sink Catalog

> 출처: `sink_catalog.json` (version 1.0.0, 2026-04-20)

| service | accepted_input_types | 주요 required config 필드 | auth |
|---------|---------------------|--------------------------|------|
| `slack` | TEXT | `channel` | O |
| `gmail` | TEXT, SINGLE_FILE, FILE_LIST | `to`, `subject`, `action` | O |
| `notion` | TEXT, SPREADSHEET_DATA, API_RESPONSE | `target_type`, `target_id` | O |
| `google_drive` | TEXT, SINGLE_FILE, FILE_LIST, SPREADSHEET_DATA | `folder_id` | O |
| `google_sheets` | TEXT, SPREADSHEET_DATA, API_RESPONSE | `spreadsheet_id`, `write_mode` | O |
| `google_calendar` | TEXT, SCHEDULE_DATA | `calendar_id`, `event_title_template`, `action` | O |

> sink는 mode가 없고, `config_schema_scope: "per_service"` 단위로 설정 필드가 결정된다.

---

## 5. Public API Contracts (Spring → FE)

### 5.1 Catalog API

| endpoint | 설명 |
|----------|------|
| `GET /api/editor-catalog/sources` | 전체 source 서비스 + 모드 목록 |
| `GET /api/editor-catalog/sinks` | 전체 sink 서비스 목록 |
| `GET /api/editor-catalog/sinks/{serviceKey}/schema?inputType=X` | sink 서비스별 config schema |
| `GET /api/editor-catalog/mapping-rules` | 데이터 타입 처리 규칙 |

### 5.2 Schema Preview

| endpoint | 설명 |
|----------|------|
| `POST /api/editor-catalog/schema-preview` | source 모드 기반 스키마 미리보기 (로컬 추론, FastAPI 호출 없음) |

### 5.3 Node Lifecycle

노드 상태는 3단계로 평가된다:

| 상태 | 의미 | 조건 |
|------|------|------|
| `configured` | 필수 설정 완료 | start: type + source_mode + target + outputDataType. end: type + required config fields. middle: category + type + outputDataType |
| `saveable` | 저장 가능 | `configured == true` |
| `executable` | 실행 가능 | `configured == true` AND auth 연결 완료 (auth_required인 경우) |

---

## 6. Wire Contract Naming Policy

| 영역 | convention | 예시 |
|------|-----------|------|
| editor/workflow 필드 | camelCase | `userId`, `dataType`, `outputDataType` |
| runtime 추가 필드 | snake_case | `runtime_type`, `runtime_source`, `canonical_input_type` |
| catalog API 응답 | snake_case | `source_modes`, `config_schema`, `accepted_input_types` |

이것은 의도된 dual-convention이다. editor 필드는 Jackson이 Java 엔티티를 직렬화한 것이고,
runtime/catalog 필드는 Map key로 직접 생성한 snake_case다.

---

## 7. 구현 갭 해결 이력

### 7.1 FE-BE 연동 갭 (2026-04-13 식별, 모두 해결됨)

| 심각도 | 항목 | 해결 상태 |
|--------|------|----------|
| P0 | OAuth 콜백 플로우 불일치 (exchange_code) | 해결 (commit 14c6770) |
| P0 | isActive vs active JSON 키 불일치 | 해결 (commit d19fca6) |
| P1 | NodeDefinition에 label 필드 없음 | 해결 (commit d19fca6) |
| P1 | EdgeDefinition에 id 필드 없음 | 해결 (commit d19fca6) |
| P1 | WorkflowResponse에 sharedWith/isTemplate/templateId 누락 | 해결 (commit d19fca6) |
| P2 | NodeChoiceSelectRequest 필드명 불일치 | 해결 (commit d19fca6) |

### 7.2 Source/Sink 아키텍처 갭 A-I (2026-04-20 식별, 모두 해결됨)

| 갭 | 내용 | 해결 방법 |
|----|------|----------|
| A | Translator runtime_type 매핑 | `WorkflowTranslator.resolveRuntimeType()` 구현 |
| B | FastAPI InputNode/OutputNode strategy | FastAPI 팀 범위 (FASTAPI_CONTRACT_SPEC.md 참조) |
| C | Preflight validation | `WorkflowValidator.validateForExecution()` 구현 |
| D | Lifecycle 상태 모델 (nodeStatuses) | `NodeLifecycleService.evaluateAll()` 구현 |
| E | Schema preview 초안 지원 | `POST /api/editor-catalog/schema-preview` 구현 |
| F | Sink configSchema per_input_type | `per_service` 단일 schema로 확정 |
| G | Service key 통합 | source/sink catalog JSON에 10+6 서비스 통합 |
| H | mapping_rules.json API | `GET /api/editor-catalog/mapping-rules` 구현 |
| I | Wire contract (camelCase vs snake_case) | dual-convention 확정 (섹션 6 참조) |

---

## 8. 구현된 파일 목록

### 신규 생성

| 파일 | 역할 |
|------|------|
| `catalog/source_catalog.json` | Source 서비스 10종, 모드 26개 정의 |
| `catalog/sink_catalog.json` | Sink 서비스 6종 정의 |
| `catalog/schema_types.json` | Canonical data type 8종 필드 정의 |
| `catalog/mapping_rules.json` | 데이터 타입 처리 규칙 |
| `catalog/dto/SourceService.java` | Source 서비스 DTO |
| `catalog/dto/SourceMode.java` | Source 모드 DTO |
| `catalog/dto/SinkService.java` | Sink 서비스 DTO |
| `catalog/dto/SchemaPreviewRequest.java` | Schema 미리보기 요청 DTO |
| `catalog/service/CatalogService.java` | Catalog 로딩/조회 서비스 |
| `catalog/service/NodeLifecycleService.java` | 노드 상태 평가 서비스 |
| `catalog/controller/CatalogController.java` | Catalog API 컨트롤러 |
| `execution/service/WorkflowTranslator.java` | Editor → Runtime 변환기 |
| `workflow/dto/NodeStatusResponse.java` | 노드 상태 응답 DTO |

### 수정

| 파일 | 변경 내용 |
|------|----------|
| `workflow/service/WorkflowValidator.java` | `validateForExecution()` 추가 (service/mode preflight 검증) |
| `execution/service/ExecutionService.java` | Translator 통합, 서비스 토큰 수집, preflight 검증 |

---

## 9. 미구현 API 현황

| 엔드포인트 | 상태 | 우선순위 |
|-----------|------|---------|
| `PUT /api/users/me` | 미구현 | P3 |
| `DELETE /api/users/me` | 미구현 | P3 |
| `POST /api/templates` | 미구현 | P3 |
