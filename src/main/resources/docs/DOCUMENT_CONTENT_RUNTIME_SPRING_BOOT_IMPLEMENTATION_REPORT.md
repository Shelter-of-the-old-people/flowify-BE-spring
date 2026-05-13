# 문서 본문 런타임 Spring Boot 구현 보고

> 작성일: 2026-05-14  
> 브랜치: `feat/30-runtime-document`  
> 전달 대상: FE `feat#172-document_content_runtime`, FastAPI 문서 본문 런타임 담당  
> 기준 문서:
> - `DOCUMENT_CONTENT_RUNTIME_REQUIREMENTS.md`
> - `DOCUMENT_CONTENT_RUNTIME_REQUIREMENTS_ANALYSIS.md`
> - `DOCUMENT_CONTENT_RUNTIME_ANALYSIS_AND_IMPLEMENTATION_PLAN_SPRING_BOOT.md`
> - `DOCUMENT_CONTENT_RUNTIME_FRONTEND_INTEGRATION_REVIEW.md`

---

## 1. 구현 요약

Spring Boot에 문서 본문 런타임 계약의 1차 지원 코드를 반영했다.

이번 구현은 파일 본문 추출 자체가 아니라, Spring이 FastAPI와 FE 사이에서 아래 계약을 안정적으로 전달/보존하는 데 집중한다.

- content-dependent 노드에 `runtime_config.requires_content` 전달
- source preview metadata에 FE 표시용 content policy 기본값 부여
- FastAPI `DOCUMENT_CONTENT_*` error code를 Spring public error code로 매핑
- 문서 요약/파일 공유 템플릿에 본문 필요 의도 명시

---

## 2. 변경 파일

### 2.1 Runtime translator

파일:

- `src/main/java/org/github/flowify/execution/service/WorkflowTranslator.java`
- `src/test/java/org/github/flowify/execution/WorkflowTranslatorTest.java`

반영 내용:

- `runtime_config.requires_content`를 모든 `llm`, `loop`, `if_else` runtime_config에 포함한다.
- `requires_content` 판별 우선순위:
  1. node config의 명시적 `requires_content` 또는 `requiresContent`
  2. `choiceActionId` 또는 `choice_action_id`
  3. `action`, 단 `process`는 choice prompt resolver 기본값이므로 content action으로 보지 않음
  4. legacy `choiceSelections` key가 content-dependent action id와 정확히 일치하는 경우
  5. 파일/메일 계열 `dataType` + 생성형 `outputDataType` + AI/AI_FILTER 노드 조합

content-dependent action 목록:

```text
summarize
extract_info
translate
classify_by_content
describe_image
ocr
ai_summarize
ai_analyze
```

예시 runtime_config:

```json
{
  "choiceActionId": "summarize",
  "action": "process",
  "node_type": "AI",
  "output_data_type": "TEXT",
  "requires_content": true
}
```

주의:

- choice 기반 AI 노드는 `ChoicePromptResolver`가 `action=process`를 넣을 수 있으므로 FE/FastAPI는 `action`만 보면 안 된다.
- 명시적 `requires_content=false`가 있으면 자동 추론보다 우선한다.

### 2.2 Preview metadata

파일:

- `src/main/java/org/github/flowify/workflow/service/WorkflowPreviewService.java`
- `src/test/java/org/github/flowify/workflow/WorkflowPreviewServiceTest.java`

반영 내용:

- source preview 응답 metadata에 FE 표시용 기본 필드를 항상 보강한다.
- FastAPI가 metadata를 내려주면 Spring 기본값 위에 병합한다.

기본 metadata:

```json
{
  "limit": 5,
  "includeContent": false,
  "previewScope": "source_metadata",
  "contentPolicy": "metadata_only",
  "contentIncluded": false,
  "contentStatusScope": "none",
  "contentRequired": false,
  "contentRequiredReason": null,
  "nodeRole": "start",
  "nodeType": "google_drive"
}
```

`includeContent=true`로 FastAPI preview를 호출하더라도 Spring은 응답 payload 또는 status를 확인한 뒤 `content_included` 여부를 보정한다. FastAPI가 더 구체적인 metadata를 내려주면 null이 아닌 값만 Spring 기본값 위에 병합한다.

```json
{
  "includeContent": true,
  "contentPolicy": "content_included",
  "contentIncluded": true
}
```

위 값은 응답 payload에 실제 `content`, `extracted_text`, `extractedText`가 있거나 `content_status=available`인 경우에만 Spring 기본값으로 설정된다. `includeContent=true` 요청이었지만 실제 content/status가 없으면 `contentPolicy=metadata_only`, `contentIncluded=false`로 유지된다.

FE 참고:

- 1차 FE는 기존대로 source preview 기본 요청을 `includeContent=false`로 유지해도 된다.
- `previewScope=source_metadata`는 API 범위이고, `contentPolicy`는 본문 포함 정책이다.
- downstream 본문 필요 여부는 향후 `contentRequired`, `contentRequiredReason`으로 표현한다.

### 2.3 FastAPI error mapping

파일:

- `src/main/java/org/github/flowify/common/exception/ErrorCode.java`
- `src/main/java/org/github/flowify/execution/service/FastApiClient.java`
- `src/test/java/org/github/flowify/execution/FastApiClientTest.java`

추가된 Spring ErrorCode:

| Spring ErrorCode | HTTP status | 기본 메시지 |
|------------------|-------------|-------------|
| `DOCUMENT_CONTENT_UNSUPPORTED` | 422 | 이 파일 형식은 아직 본문 읽기를 지원하지 않습니다. |
| `DOCUMENT_CONTENT_TOO_LARGE` | 413 | 파일이 너무 커서 본문을 읽을 수 없습니다. |
| `DOCUMENT_CONTENT_EMPTY` | 422 | 파일에서 읽을 수 있는 본문이 없습니다. |
| `DOCUMENT_CONTENT_EXTRACTION_FAILED` | 502 | 파일 본문 추출 중 오류가 발생했습니다. |
| `DOCUMENT_CONTENT_NOT_REQUESTED` | 422 | 본문이 필요한 작업이지만 본문 추출이 수행되지 않았습니다. |

FastAPI error code 매핑:

| FastAPI error_code | Spring ErrorCode |
|--------------------|------------------|
| `DOCUMENT_CONTENT_UNSUPPORTED` | `DOCUMENT_CONTENT_UNSUPPORTED` |
| `DOCUMENT_CONTENT_TOO_LARGE` | `DOCUMENT_CONTENT_TOO_LARGE` |
| `DOCUMENT_CONTENT_EMPTY` | `DOCUMENT_CONTENT_EMPTY` |
| `DOCUMENT_CONTENT_EXTRACTION_FAILED` | `DOCUMENT_CONTENT_EXTRACTION_FAILED` |
| `DOCUMENT_CONTENT_NOT_REQUESTED` | `DOCUMENT_CONTENT_NOT_REQUESTED` |

`DOCUMENT_CONTENT_NOT_REQUESTED`는 기본적으로 preview/content policy 상태다. FastAPI가 에러로 내려주는 경우는 `requires_content=true`인데 본문 추출이 요청/수행되지 않은 실패 상황으로 보고 Spring public error code도 `DOCUMENT_CONTENT_NOT_REQUESTED`로 보존한다.

### 2.4 Template config

파일:

- `src/main/java/org/github/flowify/config/TemplateSeeder.java`

반영 내용:

- 문서 요약/파일 업로드 공유 템플릿의 LLM config에 본문 필요 의도를 명시했다.

추가 필드:

```json
{
  "action": "summarize",
  "requires_content": true
}
```

Sheets 기록형 문서 분석 템플릿은 `action=ai_analyze`, `requires_content=true`로 설정했다.

### 2.5 Node log error context 보존

파일:

- `src/main/java/org/github/flowify/execution/entity/ErrorDetail.java`
- `src/main/java/org/github/flowify/execution/service/ExecutionService.java`
- `src/test/java/org/github/flowify/execution/ExecutionServiceTest.java`

FastAPI execution log의 node-level error가 아래처럼 저장되는 경우 Spring public 조회 API에서 `code/message/context`를 보존한다.

```json
{
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
```

Spring completion callback의 top-level `error`는 현재 문자열을 유지한다. 사용자 표시용 문서 본문 오류 code/context는 node log의 `error.code`, `error.context`를 조회하는 경로에서 보존한다.

---

## 3. FE 전달 사항

FE는 아래 값을 그대로 사용할 수 있다.

### 3.1 Preview metadata

Spring source preview 응답은 최소 아래 metadata를 포함한다.

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

FE 표시 우선순위 권장:

1. `contentPolicy`
2. `contentIncluded`
3. `contentStatusScope`
4. payload 내부의 `content_status`, `content_error`, `content_metadata`

### 3.2 Runtime config

AI/LLM 노드 preview 또는 실행 debug 화면에서 runtime model을 표시한다면 `requires_content`를 볼 수 있다.

```json
{
  "runtime_config": {
    "requires_content": true
  }
}
```

`choiceActionId=summarize`인 노드는 `action=process`여도 `requires_content=true`가 된다.

### 3.3 Error code

FE는 Spring public API error code로 `DOCUMENT_CONTENT_*`를 받을 수 있다.

```json
{
  "code": "DOCUMENT_CONTENT_TOO_LARGE",
  "message": "파일이 너무 커서 본문을 읽을 수 없습니다."
}
```

Spring이 FastAPI message를 받으면 해당 message를 우선 전달한다. message가 없으면 Spring ErrorCode 기본 메시지가 사용된다.

---

## 4. 미구현/후속 범위

이번 Spring 구현에는 아래가 포함되지 않았다.

- 파일 다운로드/본문 추출 extractor 구현
- `content_status`, `content_error`, `content_metadata` 생성
- FastAPI callback payload sanitize/truncate 실제 필터링. FastAPI sanitize가 1차 방어선이지만, Spring 저장 전 full content/token/signed URL 방어 필터는 운영 안정화 전 별도 후속으로 유지한다.
- start node 외 AI/중간 노드 preview capability API
- `contentRequiredReason=downstream`을 graph 분석으로 자동 계산. 구현 전까지 Spring 기본값은 `contentRequired=false`, `contentRequiredReason=null`이다.
- FastAPI callback payload와 Mongo node log가 실제 운영 DB에서 같은 execution document에 병합되는지 확인하는 더 넓은 통합 테스트. 현재는 service 단위 contract test로 저장 update, node data 조회, execution detail 조회의 문서 content 상태/error context 보존을 검증한다.

위 항목은 FastAPI 구현 또는 후속 Spring 작업으로 남긴다.

---

## 5. 검증

실행한 테스트:

```bash
./gradlew test --tests org.github.flowify.execution.WorkflowTranslatorTest --tests org.github.flowify.workflow.WorkflowPreviewServiceTest --tests org.github.flowify.execution.FastApiClientTest --tests org.github.flowify.execution.ExecutionServiceTest
./gradlew test
```

결과:

```text
BUILD SUCCESSFUL
```

검증된 항목:

- `choiceActionId=summarize` + `action=process` -> `requires_content=true`
- 명시적 `requires_content=false` 우선
- legacy `choiceSelections` action key fallback
- source preview metadata 기본값 보강
- `includeContent=true` preview metadata가 실제 content/status 기반으로 보정됨
- FastAPI metadata의 null 값은 Spring 기본 metadata를 덮지 않음
- `DOCUMENT_CONTENT_UNSUPPORTED`, `DOCUMENT_CONTENT_TOO_LARGE`, `DOCUMENT_CONTENT_NOT_REQUESTED` FastAPI error mapping
- node data 조회에서 `content_status`, `content_error`, `content_metadata` 보존
- execution detail/node data 조회에서 node log `error.code`, `error.context` 보존
- execution complete update에서 output의 문서 content 상태 필드 보존
