# 문서 본문 런타임 분석 및 구현 계획

> 작성일: 2026-05-14  
> 기준 문서: `DOCUMENT_CONTENT_RUNTIME_REQUIREMENTS.md`, `DOCUMENT_CONTENT_RUNTIME_REQUIREMENTS_ANALYSIS.md`  
> 목적: 문서 본문 추출/요약 런타임을 Spring Boot와 FastAPI 책임으로 분리하고, MVP와 원문 1차 완료 범위를 혼동 없이 정의한다.

---

## 1. 범위 구분

이 문서는 두 완료 기준을 분리한다.

| 구분 | 목적 | 포함 범위 | 완료 의미 |
|------|------|-----------|-----------|
| 계약/경로 안정화 MVP | payload 계약, `requires_content`, preview/error/log 경계를 먼저 고정 | TXT/Markdown, CSV/TSV, PDF text layer, Google Workspace export, Gmail attachment metadata status | 본문 추출 경로가 동작하고 실패/미지원이 명확히 표시됨 |
| 원문 요구사항 1차 완료 | 원문에 명시된 필수 파일군까지 실제 extractor 지원 | MVP + DOCX, PPTX, HWPX | 원문 `5.1.1 1차 지원 대상 파일 타입`의 필수 지원군이 구현/검증됨 |

MVP는 원문 요구사항을 축소 완료했다는 뜻이 아니다. MVP는 FastAPI/Spring/FE 계약과 실행 경로를 먼저 안정화하기 위한 중간 마일스톤이다. 원문 요구사항의 1차 완료로 선언하려면 DOCX/PPTX/HWPX까지 필수 지원군 extractor와 테스트가 포함되어야 한다.

---

## 2. 책임 분리

### 2.1 Spring Boot 책임

- editor 모델을 FastAPI runtime 모델로 변환한다.
- content-dependent 노드에 `runtime_config.requires_content=true`를 전달한다.
- FastAPI error code를 Spring public API error/status로 변환한다.
- preview 응답의 기존 호환 필드와 신규 content 정책 필드를 같이 보존한다.
- FastAPI callback 또는 preview 응답을 저장하기 전에 저장 정책에 맞게 sanitize/truncate한다.

Spring은 파일 다운로드와 본문 추출을 직접 수행하지 않는다.

### 2.2 FastAPI 책임

- Google Drive/Gmail source payload를 canonical payload로 정규화한다.
- 파일 타입을 판별하고 본문을 추출한다.
- `content_status`, `content_error`, `content_metadata`를 생성한다.
- Loop에서 `FILE_LIST.items[]`의 content 관련 필드 또는 lazy extraction key를 보존한다.
- LLM input builder가 canonical payload의 본문 필드를 일관되게 읽는다.
- Spring으로 반환할 callback/preview payload는 저장 정책에 맞춰 full content 또는 truncated content를 구분한다.

FastAPI는 원문 bytes/base64와 LLM용 추출 텍스트를 같은 필드에 섞지 않는다.

---

## 3. Runtime Contract

### 3.1 `SINGLE_FILE.content` 의미

`content`는 LLM/preview/UI가 읽을 수 있는 canonical text representation으로 고정한다.

| 목적 | 권장 필드 |
|------|-----------|
| LLM 입력용 추출 텍스트 | `content` |
| 추출 상태 | `content_status` |
| 사용자 표시 가능한 실패 사유 | `content_error` |
| 추출 방식/길이/제한값 | `content_metadata` |
| 원본 파일 재전달 | `file_id`, `download_url`, `content_ref`, `raw_content_base64` 중 별도 계약 |

Gmail/Google Drive sink가 원본 파일 업로드나 첨부를 수행해야 할 때는 `content`를 원본 bytes로 해석하지 않는다.

### 3.2 Preview metadata 병행 정책

기존 호환 필드는 유지하고, 신규 의미 필드를 추가한다.

```json
{
  "metadata": {
    "previewScope": "source_metadata",
    "contentPolicy": "metadata_only",
    "contentIncluded": false,
    "contentStatusScope": "none",
    "limit": 5
  }
}
```

권장 값:

| 필드 | 값 | 설명 |
|------|----|------|
| `previewScope` | `source_metadata` | 기존 FE 호환 필드. source preview라는 API 범위를 뜻함 |
| `contentPolicy` | `metadata_only` | 본문 미포함 |
| `contentPolicy` | `content_included` | 본문 포함 |
| `contentPolicy` | `content_status_only` | 본문 원문 없이 추출 가능/불가 상태만 포함 |
| `contentIncluded` | boolean | 실제 `content` 포함 여부 |
| `contentStatusScope` | `none|item|attachment|node` | content status가 어느 단위에 붙었는지 |
| `contentRequired` | boolean | downstream 또는 runtime config가 본문을 요구하는지 |
| `contentRequiredReason` | `downstream|user_request|runtime_config|null` | 본문 필요 사유 |

`previewScope=source_metadata`와 `contentPolicy=metadata_only`는 충돌하지 않는다. 전자는 preview API 범위, 후자는 본문 포함 정책이다.

FE 연동 관점에서는 `DOCUMENT_CONTENT_RUNTIME_FRONTEND_INTEGRATION_REVIEW.md`의 표시 계약을 따른다. 특히 downstream 때문에 본문이 필요한 상태는 `contentPolicy` 값으로 직접 섞지 않고 `contentRequired=true`, `contentRequiredReason=downstream`으로 분리한다.

FastAPI raw response 또는 legacy FE 설계에서 `contentPolicy=required_by_downstream` 또는 `contentPolicy=content_required_but_unavailable`이 들어오더라도 Spring public API에서는 가능한 한 정규화한다.

- `required_by_downstream` -> `contentPolicy=metadata_only|content_status_only`, `contentRequired=true`, `contentRequiredReason=downstream`
- `content_required_but_unavailable` -> `contentPolicy=content_status_only`, `contentIncluded=false`, `contentRequired=true`, `contentRequiredReason=runtime_config`

### 3.3 `content_status=empty` 정책

| 상황 | 처리 |
|------|------|
| `requires_content=true`이고 action이 `summarize`, `extract_info`, `translate`, `ai_analyze` | 실패 또는 사용자 표시 가능한 partial failure. 빈 요약 성공 금지 |
| `requires_content=true`이고 action이 `ocr`, `describe_image` | extractor 결과가 빈 경우 실패 또는 `empty` 명시 |
| metadata-only action | 부분 성공 가능. 파일명/MIME/URL 기반 결과임을 metadata에 표시 |
| source preview metadata-only | `not_requested` 또는 `empty`를 성공처럼 표시하지 않고 scope를 명시 |

LLM은 `empty`, `unsupported`, `too_large`, `failed`, `not_requested`를 정상 본문으로 간주하면 안 된다.

---

## 4. Spring Implementation Plan

### 4.1 `requires_content` 판별

판별 우선순위:

1. node config의 명시적 `requires_content` 또는 `requiresContent`.
2. `choiceActionId` 또는 `choice_action_id`.
3. `action`, 단 `process`는 choice prompt resolver의 기본 runtime action이므로 content action으로 보지 않는다.
4. `choiceSelections` key 중 content-dependent action id와 정확히 일치하는 key.
5. 파일/메일 계열 `dataType` + 생성형 `outputDataType` + AI/AI_FILTER 노드 조합.

`choiceSelections` fallback은 마이그레이션 데이터 방어용이다. selection value는 style id일 수 있으므로 action id로 해석하지 않는다.

### 4.2 Spring error mapping

FastAPI error code는 Spring public API에서 사용자 표시 가능한 error/status로 변환한다.

| FastAPI error_code | Spring ErrorCode 후보 | HTTP status | node/preview status | 사용자 문구 후보 |
|--------------------|-----------------------|-------------|---------------------|------------------|
| `DOCUMENT_CONTENT_UNSUPPORTED` | `PREFLIGHT_VALIDATION_FAILED` 또는 신규 `DOCUMENT_CONTENT_UNSUPPORTED` | 422 | `unavailable` | 이 파일 형식은 아직 본문 읽기를 지원하지 않습니다. |
| `DOCUMENT_CONTENT_TOO_LARGE` | `PREFLIGHT_VALIDATION_FAILED` 또는 신규 `DOCUMENT_CONTENT_TOO_LARGE` | 413 또는 422 | `unavailable` | 파일이 너무 커서 본문을 읽을 수 없습니다. |
| `DOCUMENT_CONTENT_EMPTY` | 신규 `DOCUMENT_CONTENT_EMPTY` 또는 `EXECUTION_FAILED` | 422 | `failed` | 파일에서 읽을 수 있는 본문이 없습니다. |
| `DOCUMENT_CONTENT_EXTRACTION_FAILED` | `EXTERNAL_API_ERROR` 또는 신규 `DOCUMENT_CONTENT_EXTRACTION_FAILED` | 502 | `failed` | 파일 본문 추출 중 오류가 발생했습니다. |
| `DOCUMENT_CONTENT_NOT_REQUESTED` | 에러 아님. FastAPI가 error response로 보낸 경우 신규 `DOCUMENT_CONTENT_NOT_REQUESTED` | 200 또는 422 | `available` 또는 `failed` | 본문 미포함 미리보기입니다. 실행 실패라면 본문이 필요한 작업이지만 본문 추출이 수행되지 않았습니다. |
| `OAUTH_SCOPE_INSUFFICIENT` | `OAUTH_SCOPE_INSUFFICIENT` | 403 | `unavailable` | 파일을 읽기 위한 권한이 부족합니다. |
| `OAUTH_TOKEN_MISSING` | `OAUTH_NOT_CONNECTED` | 401 또는 403 | `unavailable` | 외부 서비스 연결이 필요합니다. |

신규 Spring `ErrorCode`를 추가하지 않는 MVP에서는 기존 code로 매핑하되, `metadata.fastApiErrorCode` 또는 `reason`에 원본 error code를 보존한다. FE가 사용자 문구를 안정적으로 표시해야 한다면 신규 ErrorCode 추가가 더 낫다.

FE 표시 정책상 `DOCUMENT_CONTENT_NOT_REQUESTED`는 기본적으로 실패가 아니라 info 성격의 content policy다. 단, FastAPI가 `requires_content=true`인 실행 경로에서 본문 추출이 수행되지 않아 error response로 내려준 경우에는 Spring public error code도 `DOCUMENT_CONTENT_NOT_REQUESTED`로 보존한다.

### 4.3 저장/sanitize 경계

실행 중 full content가 필요하더라도 저장/조회 payload는 별도 정책을 따른다.

| 단계 | full content 허용 | sanitize 책임 | 정책 |
|------|-------------------|---------------|------|
| FastAPI 내부 실행 컨텍스트 | 허용 | FastAPI | LLM 입력 생성에 사용 |
| FastAPI -> Spring callback | 기본적으로 truncated 권장 | FastAPI 우선 | `content`는 최대 저장 길이로 자르고 metadata에 표시 |
| Spring 저장 전 | full content 수신 가능성 방어 | Spring | `content`, `raw_content_base64`, signed URL/token 제거 또는 truncate |
| Spring public 조회 API | full content 비권장 | Spring | preview snippet/status/metadata 중심 |

권장 metadata:

```json
{
  "content_metadata": {
    "truncated": true,
    "char_count": 4000,
    "original_char_count": 82000,
    "stored_content_truncated": true,
    "stored_char_count": 1000,
    "limits": {
      "max_download_bytes": 10485760,
      "max_extracted_chars": 80000,
      "max_llm_input_chars": 20000
    }
  }
}
```

FastAPI가 반환 전 sanitize하는 것이 1차 방어선이고, Spring 저장 전 sanitize는 2차 방어선이다.
`limits`는 FE가 모든 값을 기본 노출하기 위한 필드가 아니라, `too_large` 또는 truncate 상태의 보조 설명과 디버깅에 사용할 수 있는 metadata다.

---

## 5. FastAPI Implementation Plan

### 5.1 Extractor MVP

계약/경로 안정화 MVP 대상:

| 파일군 | 구현 |
|--------|------|
| TXT/Markdown | UTF-8, UTF-8 BOM, CP949 인코딩 처리 |
| CSV/TSV | header와 row를 table text로 변환 |
| PDF text layer | OCR 없이 text layer 추출 |
| Google Docs | text/plain export |
| Google Sheets | CSV export 후 table text |
| Google Slides | text 또는 pptx export 가능 경로 우선 |
| Gmail attachment | content 미지원이면 metadata + `content_status=unsupported|not_requested` |

### 5.2 원문 1차 필수 extractor

원문 요구사항 1차 완료에는 아래가 추가로 필요하다.

| 파일군 | 필수 테스트 기준 |
|--------|------------------|
| DOCX | paragraph, table text, header/footer 중 최소 paragraph/table 추출 |
| PPTX | slide 순서 유지, title/body/speaker note 구분 |
| HWPX | zip 내부 XML body text 추출 |

DOCX/PPTX/HWPX를 구현하지 않은 상태는 MVP일 수는 있지만 원문 1차 완료가 아니다.

### 5.3 LLM input policy

입력 우선순위:

| payload | 본문 필드 우선순위 |
|---------|-------------------|
| `TEXT` | `content` |
| `SINGLE_FILE` | `content` only |
| `FILE_LIST` | `items[].content`; 없으면 metadata 보조 정보 |
| `SINGLE_EMAIL` | `body`, `bodyPreview`, `body_preview`, `snippet` |
| `EMAIL_LIST` | 각 item의 `body`, `bodyPreview`, `body_preview`, `snippet` |
| Gmail attachment | `attachments[].content`가 있을 때만 본문 사용 |

`requires_content=true`인데 status가 `empty|unsupported|too_large|failed|not_requested`이면 LLM 호출 전에 실패/partial failure로 중단한다.

---

## 6. Test Plan

### 6.1 Spring unit tests

- `choiceActionId=summarize` + `action=process`인 AI 노드는 `requires_content=true`.
- `choiceSelections` key가 `summarize`인 legacy config는 `requires_content=true`.
- `choiceSelections` value가 `brief` 같은 style id인 경우에는 action으로 오판하지 않음.
- `PASSTHROUGH`는 `requires_content=false`.
- 명시적 `requires_content=false`는 자동 추론보다 우선.
- preview metadata는 `previewScope=source_metadata`와 `contentPolicy=metadata_only`를 함께 반환.
- FastAPI `DOCUMENT_CONTENT_UNSUPPORTED` 응답은 Spring reason/public error로 보존.

### 6.2 FastAPI unit tests

MVP:

- TXT UTF-8, UTF-8 BOM, CP949 추출.
- CSV/TSV header와 row text 변환.
- PDF text layer 추출.
- Google Docs text/plain export 결과 정규화.
- Google Sheets CSV export 결과 header 보존.
- Gmail attachment metadata-only일 때 `content_status=unsupported|not_requested`.

원문 1차 필수:

- DOCX paragraph와 table text 추출.
- PPTX slide order, slide title/body/note 추출.
- HWPX zip XML body text 추출.

공통:

- unsupported MIME type은 `content_status=unsupported`와 사용자 표시 가능한 `content_error`.
- too large 파일은 `content_status=too_large`.
- 추출 결과 truncate 시 `available` + `content_metadata.truncated=true`.
- LLM input builder는 `empty|unsupported|too_large|failed|not_requested`를 정상 본문으로 사용하지 않음.

### 6.3 Integration scenarios

- Google Drive single_file -> AI summarize -> Slack.
- Google Drive folder_new_file -> Loop/lazy extraction -> AI summarize.
- Google Drive folder_all_files -> FILE_LIST item content status 보존.
- Gmail attachment_email -> AI summarize는 미지원이면 명확한 제한 상태를 반환.
- Preview metadata-only와 실제 실행 content-included 결과가 구분됨.

---

## 7. Completion Criteria

### 7.1 계약/경로 안정화 MVP 완료

- Spring runtime model에 `requires_content`가 안정적으로 포함된다.
- `choiceActionId` 기반 AI 노드는 `action=process`여도 content-dependent로 판별된다.
- FastAPI payload에 `content_status`, `content_error`, `content_metadata`가 포함된다.
- TXT/CSV/PDF text layer/Google Workspace 경로에서 본문 추출 또는 명확한 실패가 동작한다.
- Preview는 `previewScope=source_metadata`를 유지하면서 `contentPolicy`로 본문 포함 정책을 구분한다.
- FastAPI error code가 Spring public API에서 사용자 표시 가능한 reason/status로 보존된다.
- Spring 저장/조회 payload에는 full content 저장 여부와 truncate/sanitize 정책이 적용된다.

### 7.2 원문 요구사항 1차 완료

MVP 완료 기준에 더해 아래가 모두 필요하다.

- DOCX paragraph/table 추출이 구현되고 테스트된다.
- PPTX slide 순서와 slide text/note 추출이 구현되고 테스트된다.
- HWPX XML body 추출이 구현되고 테스트된다.
- 필수 지원군 전체가 `content_status=available` 또는 명확한 파일별 실패 사유를 반환한다.
- `FILE_LIST -> LOOP -> LLM` 경로에서 content 또는 lazy extraction key가 손실되지 않는다.
- `content_status=empty` 정책이 요약/분석 실패 또는 partial failure로 일관되게 처리된다.
- Gmail 첨부파일 요약은 지원 또는 미지원 범위가 FE에 명확히 표시된다.
