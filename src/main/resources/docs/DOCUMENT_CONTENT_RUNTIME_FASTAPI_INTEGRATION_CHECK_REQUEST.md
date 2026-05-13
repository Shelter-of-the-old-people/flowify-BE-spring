# 문서 본문 런타임 FastAPI 정합성 확인 요청

> 작성일: 2026-05-14  
> Spring Boot 브랜치: `feat/30-runtime-document`  
> FastAPI 대상 브랜치: `feat/26-runtime-document`  
> Spring 기준 파일:
> - `src/main/java/org/github/flowify/config/WebClientConfig.java`
> - `src/main/java/org/github/flowify/execution/service/FastApiClient.java`
> - `src/main/java/org/github/flowify/execution/service/WorkflowTranslator.java`
> - `src/main/java/org/github/flowify/workflow/service/WorkflowPreviewService.java`
> - `src/main/java/org/github/flowify/execution/service/ExecutionService.java`
> - `src/main/java/org/github/flowify/execution/entity/ErrorDetail.java`
> - `src/main/resources/docs/DOCUMENT_CONTENT_RUNTIME_SPRING_BOOT_IMPLEMENTATION_REPORT.md`

---

## 1. 결론

Spring Boot 실제 코드 기준으로는 FastAPI와 즉시 충돌하는 차단급 계약 문제는 보이지 않는다.

다만 FastAPI 쪽에서 아래 경계를 맞춰야 통합 시 오표시나 error context 누락이 생기지 않는다.

- preview 응답 top-level field는 Spring이 현재 raw `snake_case`만 읽는다.
- Spring public preview metadata의 대표값은 `camelCase`이고, FastAPI raw `snake_case` metadata는 보존되지만 대표값 override로 쓰이지 않을 수 있다.
- Spring은 `content_status=available`을 실제 본문 사용 가능 신호로 본다.
- FastAPI HTTP error body는 top-level string `message`를 반드시 포함해야 한다.
- completion callback의 top-level `error`는 문자열만 저장된다. node-level `code/context`는 Mongo `nodeLogs[].error`에 저장되어야 Spring public 조회에서 보존된다.
- callback `output`은 Spring이 그대로 저장하므로 FastAPI가 저장 전 sanitize/truncate를 보장해야 한다.

---

## 2. Spring이 FastAPI로 보내는 요청 계약

### 2.1 공통 헤더

Spring `fastapiWebClient`는 아래 기본 헤더를 붙인다.

| 헤더 | 값 |
|------|----|
| `X-Internal-Token` | `${app.fastapi.internal-token}` |

각 FastAPI 호출 메서드는 사용자 컨텍스트로 아래 헤더를 추가한다.

| 헤더 | 값 |
|------|----|
| `X-User-ID` | Spring authenticated user id |

FastAPI 확인 요청:

- execute, preview, stop, rollback endpoint에서 `X-Internal-Token`과 `X-User-ID`를 동일하게 검증하는지 확인한다.
- FastAPI가 Spring callback을 호출할 때도 Spring의 `/api/internal/executions/{execId}/complete`에 같은 `X-Internal-Token`을 보내는지 확인한다.

### 2.2 Execute request

Spring 호출:

```http
POST /api/v1/workflows/{workflowId}/execute
X-Internal-Token: <shared-secret>
X-User-ID: <user-id>
```

Body:

```json
{
  "workflow": {},
  "service_tokens": {
    "google_drive": "ya29...",
    "gmail": "ya29..."
  }
}
```

Spring 기대 응답:

```json
{
  "execution_id": "exec_123"
}
```

FastAPI 확인 요청:

- 응답은 반드시 top-level `execution_id`를 포함한다. Spring은 camelCase `executionId`를 읽지 않는다.
- `service_tokens` key는 Spring node type 기준 lower snake case다. 예: `google_drive`, `google_sheets`, `gmail`, `slack`, `notion`.

### 2.3 Preview request

Spring 호출:

```http
POST /api/v1/workflows/{workflowId}/nodes/{nodeId}/preview
X-Internal-Token: <shared-secret>
X-User-ID: <user-id>
```

Body:

```json
{
  "workflow": {},
  "service_tokens": {},
  "limit": 5,
  "include_content": false
}
```

FastAPI 확인 요청:

- `include_content`를 기준으로 metadata-only/content-included preview 비용과 권한 정책을 분리한다.
- `include_content=false`일 때 full `content`를 보내지 않는다.
- `include_content=true`라도 실제 본문을 payload에 포함하지 못한 경우 `content_policy=content_status_only` 또는 `metadata_only`를 명확히 내려준다.

---

## 3. FastAPI 응답에서 확인할 부분

### 3.1 Preview top-level casing

Spring `FastApiClient.toNodePreviewResponse()`는 현재 아래 raw key를 읽는다.

| FastAPI raw key | Spring public field |
|-----------------|---------------------|
| `workflow_id` | `workflowId` |
| `node_id` | `nodeId` |
| `input_data` | `inputData` |
| `output_data` | `outputData` |
| `preview_data` | `previewData` |
| `missing_fields` | `missingFields` |
| `metadata` | `metadata` |

FastAPI 확인 요청:

- preview raw response top-level은 계속 `snake_case`로 유지한다.
- camelCase만 내려주면 Spring public response에서 workflow/node/data 필드가 누락된다.

### 3.2 Preview metadata 대표값

Spring은 public metadata에 기본적으로 아래 camelCase 값을 보강한다.

```json
{
  "previewScope": "source_metadata",
  "contentPolicy": "metadata_only",
  "contentIncluded": false,
  "contentStatusScope": "none",
  "contentRequired": false,
  "contentRequiredReason": null
}
```

FastAPI raw metadata는 null이 아닌 값만 Spring metadata에 병합된다.

주의:

- FastAPI가 `content_policy`만 내려주면 Spring public metadata에는 `content_policy`가 보존되지만, 대표값 `contentPolicy`는 Spring이 payload를 보고 계산한 값으로 남는다.
- FastAPI가 Spring 대표값을 반드시 override해야 하는 상황이면 `contentPolicy`, `contentIncluded` camelCase도 함께 내려주는 방식을 협의해야 한다.

FastAPI 확인 요청:

- 기본 raw 값은 `content_policy=metadata_only|content_included|content_status_only`로 유지한다.
- Spring public 대표값과 반드시 일치해야 하는 케이스가 있는지 알려준다.
- `required_by_downstream`은 FastAPI raw 표준으로 직접 생성하지 않는다. downstream 의미는 Spring public layer에서 `contentRequired=true`, `contentRequiredReason=downstream`으로 표현한다.

### 3.3 `content_status=available` 의미

Spring preview 보정 로직은 payload 안에서 아래 중 하나를 찾으면 `contentIncluded=true`, `contentPolicy=content_included`로 판단한다.

- `content_status` 또는 `contentStatus`가 `available`
- `content`, `extracted_text`, `extractedText` 중 하나에 non-blank text 존재

FastAPI 확인 요청:

- `content_status=available`은 실제 본문이 추출되어 LLM/input 또는 preview payload에서 사용할 수 있다는 의미로만 사용한다.
- status-only preview에서 실제 본문을 포함하지 않는데 `available`만 내려주는 정책이 필요하다면 Spring 보정 로직과 추가 협의가 필요하다.
- 본문을 요청하지 않은 경우는 `not_requested`, 지원 불가/크기 초과/실패/빈 본문은 각각 `unsupported|too_large|failed|empty`로 내려준다.

---

## 4. Error 및 callback 경계

### 4.1 HTTP error body

Spring은 FastAPI HTTP error body에서 아래 key를 읽는다.

| 목적 | Spring이 읽는 key |
|------|-------------------|
| code | `error_code`, `errorCode`, `code` |
| message | `message`, `detail`, `error` |

Spring public `ErrorCode`로 보존되는 FastAPI code:

| FastAPI code | Spring code |
|--------------|-------------|
| `DOCUMENT_CONTENT_UNSUPPORTED` | `DOCUMENT_CONTENT_UNSUPPORTED` |
| `DOCUMENT_CONTENT_TOO_LARGE` | `DOCUMENT_CONTENT_TOO_LARGE` |
| `DOCUMENT_CONTENT_EMPTY` | `DOCUMENT_CONTENT_EMPTY` |
| `DOCUMENT_CONTENT_EXTRACTION_FAILED` | `DOCUMENT_CONTENT_EXTRACTION_FAILED` |
| `DOCUMENT_CONTENT_NOT_REQUESTED` | `DOCUMENT_CONTENT_NOT_REQUESTED` |

FastAPI 확인 요청:

- HTTP error body에는 항상 top-level string `message`를 포함한다.
- `detail`이 object인 경우에도 `message`는 별도 string으로 내려준다. Spring은 message가 없으면 `detail`을 문자열화할 수 있어 사용자 문구가 약해진다.
- raw parser exception, stack trace, token, signed URL은 `message` 또는 `content_error`에 노출하지 않는다.

권장 error body:

```json
{
  "success": false,
  "error_code": "DOCUMENT_CONTENT_UNSUPPORTED",
  "message": "이 파일 형식은 아직 본문 읽기를 지원하지 않습니다.",
  "detail": {
    "filename": "archive.zip",
    "content_status": "unsupported",
    "content_error": "이 파일 형식은 아직 본문 읽기를 지원하지 않습니다."
  }
}
```

### 4.2 Completion callback

Spring callback DTO가 현재 저장하는 필드:

```json
{
  "status": "failed",
  "output": {},
  "durationMs": 1234,
  "error": "이 파일 형식은 아직 본문 읽기를 지원하지 않습니다.",
  "nodeStateUpdates": []
}
```

Spring 동작:

- `status=completed`는 Spring에서 `success`로 normalize된다.
- top-level `error`는 문자열로 `WorkflowExecution.error`에 저장된다.
- top-level callback에 `error_code`, `error_context`가 있어도 현재 Spring public error model로 사용하지 않는다.
- callback `output`은 그대로 `WorkflowExecution.output`에 저장된다.

FastAPI 확인 요청:

- 사용자 표시용 node-level code/context는 callback top-level이 아니라 Mongo `nodeLogs[].error`에 저장한다.
- Spring public execution detail/node data 조회는 `nodeLogs[].error.code`, `nodeLogs[].error.context`를 보존한다.
- callback `output`은 FastAPI 저장 전 sanitize/truncate된 payload만 보낸다. Spring은 현재 output을 추가 sanitize하지 않는다.

권장 node log error shape:

```json
{
  "nodeLogs": [
    {
      "nodeId": "node_1",
      "status": "failed",
      "error": {
        "code": "DOCUMENT_CONTENT_UNSUPPORTED",
        "message": "이 파일 형식은 아직 본문 읽기를 지원하지 않습니다.",
        "context": {
          "filename": "archive.zip",
          "content_status": "unsupported",
          "content_error": "이 파일 형식은 아직 본문 읽기를 지원하지 않습니다."
        }
      }
    }
  ]
}
```

---

## 5. Runtime model에서 확인할 부분

### 5.1 `requires_content`

Spring은 `llm`, `loop`, `if_else` runtime node에 `runtime_config.requires_content`를 항상 넣는다.

`true`가 되는 주요 조건:

- 명시적 `requires_content=true` 또는 `requiresContent=true`
- `choiceActionId=summarize`
- `action=summarize|extract_info|translate|classify_by_content|describe_image|ocr|ai_summarize|ai_analyze`
- legacy `choiceSelections` key가 위 content action과 정확히 일치
- AI/AI_FILTER prompt node가 `SINGLE_FILE|FILE_LIST|SINGLE_EMAIL|EMAIL_LIST` 입력으로 `TEXT|SPREADSHEET_DATA`를 생성

명시적 `requires_content=false`는 자동 추론보다 우선한다.

FastAPI 확인 요청:

- FastAPI도 `runtime_config.requires_content`를 우선 읽고, 없을 때만 `requiresContent` 또는 action fallback을 본다.
- `requires_content=false`는 action fallback보다 우선한다.
- FastAPI의 content-dependent action 목록이 Spring 목록과 다르면 알려준다.

### 5.2 FILE_LIST/LOOP content 보존

Spring은 파일 payload 내부를 변환하지 않고 FastAPI가 만든 canonical payload를 저장/조회 경로에서 Map으로 보존한다.

FastAPI 확인 요청:

- `FILE_LIST.items[]`의 `content_status`, `content_error`, `content_metadata`, `content`를 LOOP 이후 `SINGLE_FILE` 단계까지 보존한다.
- LLM input 구성 시 `SINGLE_FILE.content`와 `FILE_LIST.items[].content`를 사용한다.
- content-dependent action에서 `unsupported|too_large|failed|empty|not_requested`는 빈 요약 성공으로 처리하지 않는다.

---

## 6. Content metadata 및 저장 정책

Spring은 nested map key를 변환하지 않고 보존한다.

FastAPI 권장 payload:

```json
{
  "content_metadata": {
    "extraction_method": "docx_xml",
    "content_kind": "plain_text",
    "truncated": false,
    "char_count": 1200,
    "original_char_count": 1200,
    "limits": {
      "max_download_bytes": 10485760,
      "max_extracted_chars": 60000,
      "max_llm_input_chars": 60000
    },
    "stored_content_truncated": true,
    "stored_char_count": 4000,
    "truncated_for_log": true
  }
}
```

FastAPI 확인 요청:

- `content_metadata.limits`를 항상 nested map으로 유지한다.
- log/callback truncate 시 기존 extraction metadata를 덮지 않고 `stored_content_truncated`, `stored_char_count`, `truncated_for_log`를 병합한다.
- Spring/FE가 callback output의 `content`를 전체 본문으로 오해하지 않도록 truncate metadata를 남긴다.

---

## 7. 파일 지원 범위 표현

Spring 템플릿은 문서 요약형 AI 노드에 `requires_content=true`를 명시한다. 실제 extractor 지원 여부는 FastAPI가 결정한다.

FastAPI 확인 요청:

- 현재 지원 완료 범위와 미지원 범위를 `content_status`로 일관되게 표현한다.
- Gmail attachment 본문 다운로드/추출이 미연결이면 `content_status=not_requested` 또는 `unsupported`와 사용자 표시 가능한 `content_error`를 내려준다.
- scan PDF OCR/image OCR/vision 미구현 상태가 `empty`로 오해되지 않도록 `unsupported` 또는 명확한 error message를 사용한다.
- DOCX/PPTX/HWPX는 원문 1차 요구사항 필수군이므로 extractor와 regression test가 실제 브랜치에 포함되어 있는지 확인한다.

---

## 8. FastAPI 팀 확인 체크리스트

| 우선순위 | 확인 항목 | 기대 결과 |
|----------|-----------|-----------|
| P0 | preview response top-level casing | `workflow_id/node_id/output_data/preview_data` snake_case 유지 |
| P0 | execute response id | `execution_id` top-level 제공 |
| P0 | HTTP error body message | `error_code`와 top-level string `message` 제공 |
| P0 | node log error context | `nodeLogs[].error.code/message/context` Mongo 저장 |
| P0 | callback output sanitize | full content/token/signed URL 저장 전 제거 또는 truncate |
| P0 | `content_status=available` 의미 | 실제 본문 사용 가능 상태로만 사용 |
| P1 | metadata 대표값 | raw `content_policy`와 Spring public `contentPolicy` 의미 불일치 여부 확인 |
| P1 | content action 목록 | Spring `CONTENT_ACTIONS`와 FastAPI content-dependent action 목록 일치 |
| P1 | `requires_content=false` 우선순위 | action fallback보다 명시 false 우선 |
| P1 | nested `content_metadata.limits` | 저장/조회 후에도 nested map 보존 |
| P1 | Gmail attachment/OCR 미지원 표현 | `empty`가 아닌 명확한 `unsupported/not_requested/failed` 상태 |

---

## 9. FastAPI 팀 전달 문구

```text
Spring Boot 실제 코드 기준으로 FastAPI와 큰 계약 충돌은 보이지 않습니다.

다만 통합 안정화를 위해 아래를 확인 부탁드립니다.

1. Spring은 preview top-level 응답에서 snake_case만 읽습니다. workflow_id, node_id, input_data, output_data, preview_data, missing_fields를 유지해 주세요.
2. execute 응답은 execution_id를 top-level로 내려주세요.
3. HTTP error body에는 error_code와 top-level string message를 항상 포함해 주세요. detail이 object여도 message는 별도 string이어야 합니다.
4. completion callback의 top-level error는 Spring에서 문자열로만 저장합니다. DOCUMENT_CONTENT_* code/context는 Mongo nodeLogs[].error.code/context에 저장해 주세요.
5. callback output은 Spring이 그대로 저장하므로, FastAPI에서 full content/token/signed URL sanitize와 content truncate metadata 병합을 보장해 주세요.
6. Spring은 content_status=available 또는 content text 존재를 contentIncluded=true 판단 신호로 봅니다. status-only preview에서 available만 내려주는 정책이 있다면 Spring 보정 로직과 추가 협의가 필요합니다.
7. FastAPI content-dependent action 목록이 Spring의 summarize/extract_info/translate/classify_by_content/describe_image/ocr/ai_summarize/ai_analyze와 다른지 확인해 주세요.
8. Gmail attachment/OCR 미지원 상태가 empty로 오해되지 않도록 unsupported/not_requested/failed와 사용자 표시 가능한 content_error를 내려주세요.
```

---

## 10. FastAPI 확인 결과

FastAPI `feat/26-runtime-document` 기준 회신으로 아래 항목을 확인했다.

| 확인 항목 | FastAPI 회신 | Spring 판단 |
|-----------|--------------|-------------|
| preview top-level casing | `workflow_id`, `node_id`, `input_data`, `output_data`, `preview_data`, `missing_fields` snake_case 유지 | 정합 |
| execute response id | top-level `execution_id` 반환 | 정합 |
| `include_content=false` preview | full content 미포함, metadata/status 중심 반환 | 정합 |
| `include_content=true` content policy | 실제 content가 없으면 `content_included`로 표시하지 않음 | 정합 |
| raw preview metadata | `content_policy=metadata_only|content_included|content_status_only` 사용 | 정합 |
| downstream alias | FastAPI raw에서 `required_by_downstream` 생성하지 않음 | 정합 |
| `content_status=available` 의미 | 실제 추출 content가 있는 extraction result에서만 사용 | 정합 |
| 빈 추출 결과 | helper에서 `empty`로 보정 | 정합 |
| HTTP error body | `error_code`와 top-level string `message` 포함, `detail`은 context object | 정합 |
| safe error message | parser exception/stack trace/token/signed URL 미노출 | 정합 |
| Spring callback auth | `X-Internal-Token` 전송 | 정합 |
| callback top-level error | 문자열만 전송 | Spring 현재 DTO와 정합 |
| node-level error code/context | Mongo `nodeLogs[].error.code/context` 저장 | 정합 |
| callback output sanitize | sanitized `outputData`에서 추출, content truncate 및 metadata 병합 | 정합 |
| `requires_content` 판별 | `requires_content` 우선, `requiresContent`/action fallback, 명시 false 우선 | 정합 |
| content action 목록 | Spring 목록 포함, 추가로 `extract` alias 지원 | 정합. Spring은 명시 `requires_content`를 내려주므로 alias 차이는 차단 요소 아님 |
| FILE_LIST/LOOP content 보존 | LOOP 이후 `SINGLE_FILE` 단계까지 content fields 보존 | 정합 |
| content-dependent failure | `unsupported/too_large/failed/empty/not_requested`를 빈 요약 성공으로 처리하지 않음 | 정합 |
| DOCX/PPTX/HWPX extractor | extractor와 regression test 포함 | 원문 1차 필수군 충족으로 판단 가능 |
| Gmail attachment/OCR/vision | 후속 범위, `unsupported/not_requested` 정책 유지 | 제품 문구에서 후속 범위로 표시 필요 |

### 10.1 추가 FastAPI 보강 확인

FastAPI 팀은 추가 확인 중 LOOP body 실패 시 원래 `FlowifyException.context`가 iteration context로 덮일 수 있는 부분을 발견해 보강했다.

보강 후 기대 context:

```json
{
  "filename": "archive.zip",
  "content_status": "unsupported",
  "content_error": "이 파일 형식은 아직 본문 읽기를 지원하지 않습니다.",
  "iteration": 2,
  "body_node_id": "node_body"
}
```

Spring은 `ErrorDetail.context`를 `Map<String, Object>`로 보존하므로 위 병합 context와 정합된다.

### 10.2 남은 후속 범위

차단급 Spring-FastAPI 계약 충돌은 현재 없다.

남은 항목은 기능 범위/운영 안정화 작업으로 분류한다.

| 우선순위 | 항목 | 담당/메모 |
|----------|------|-----------|
| P1 | Gmail attachment download + extractor 연결 | FastAPI 후속. 현재는 metadata/status 또는 `unsupported/not_requested` |
| P1 | scan PDF OCR | FastAPI 후속. OCR 미구현이면 `unsupported` 유지 |
| P1 | image OCR/vision | FastAPI 후속. vision 미구현이면 `unsupported` 유지 |
| P1 | Spring 저장 전 2차 sanitize 방어 | Spring 후속. FastAPI sanitize가 1차 방어선 |
| P1 | 실제 Mongo 통합 fixture 기반 E2E 검증 | Spring/FastAPI 공동 후속 |
| P2 | FastAPI `extract` alias를 Spring `CONTENT_ACTIONS`에 추가할지 여부 | 현재 Spring 템플릿은 `requires_content=true`를 명시하므로 차단 아님 |

### 10.3 최종 판단

Spring Boot `feat/30-runtime-document`와 FastAPI `feat/26-runtime-document`는 문서 본문 런타임 계약 관점에서 정합하다.

현재 상태에서 통합 리뷰를 막는 충돌은 없다. 단, 사용자/릴리즈 문구에서는 Gmail attachment 본문 추출, scan PDF OCR, image OCR/vision을 완료 기능으로 표현하지 않는다.
