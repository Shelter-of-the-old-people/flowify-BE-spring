# 문서 본문 런타임 FE 반영 설계 검토

> 작성일: 2026-05-14  
> 대상 FE 브랜치: `feat#172-document_content_runtime`  
> BE 브랜치: `feat/30-runtime-document`  
> 기준 문서:
> - `DOCUMENT_CONTENT_RUNTIME_REQUIREMENTS.md`
> - `DOCUMENT_CONTENT_RUNTIME_REQUIREMENTS_ANALYSIS.md`
> - `DOCUMENT_CONTENT_RUNTIME_ANALYSIS_AND_IMPLEMENTATION_PLAN_SPRING_BOOT.md`

---

## 1. 검토 결론

프론트 설계 방향은 백엔드 문서 본문 런타임 계약과 대체로 일치한다.

특히 아래 판단은 맞다.

- FE는 파일 본문을 직접 다운로드/파싱/OCR하지 않는다.
- FE는 Spring/FastAPI가 내려주는 canonical payload의 `content_status`, `content_error`, `content_metadata`를 표시한다.
- 1차 FE 범위는 source preview와 실행 결과 표시를 안정화하고, AI 노드 preview는 backend capability가 열린 뒤 확장한다.
- `SINGLE_FILE`, `FILE_LIST.items[]`, Gmail attachment를 같은 파일 content 상태 표시 계층으로 처리한다.
- full content 복원/저장은 FE 범위가 아니며, Spring/FastAPI의 sanitize/truncate 결과를 표시한다.

다만 백엔드 계약과 맞추기 위해 아래 보정이 필요하다.

1. `DOCUMENT_CONTENT_NOT_REQUESTED`는 기본적으로 에러가 아니라 content policy/status다. `requires_content=true`인데 실행 단계에서 본문이 없을 때만 실패 문구로 보정한다.
2. `contentPolicy=required_by_downstream`은 “본문 포함 방식”이라기보다 “본문 필요 사유”에 가깝다. FE 표시에서는 허용하되, 백엔드는 가능하면 `contentPolicy=metadata_only|content_included|content_status_only`와 `contentRequiredReason=downstream`을 분리하는 편이 명확하다.
3. `previewScope=source_metadata`는 기존 호환 필드로 유지하고, 신규 의미는 `contentPolicy`, `contentIncluded`, `contentStatusScope`로 표현한다.
4. `content_error`는 사용자 표시 가능한 메시지여야 하지만, raw external error가 섞일 수 있으므로 FE는 error code 매핑을 우선하고 `content_error`는 보조 설명으로 표시하는 것이 안전하다.
5. `contentPolicy=content_required_but_unavailable`은 FastAPI raw/legacy fallback으로 FE가 수용할 수 있지만, Spring public API에서는 `contentPolicy=metadata_only|content_status_only`, `contentRequired=true` 조합으로 정규화하는 것이 좋다.

---

## 2. 백엔드가 보장해야 할 FE 계약

### 2.1 Canonical file payload

FE가 안정적으로 표시할 수 있도록 Spring/FastAPI는 snake_case 필드를 기본으로 내려준다. FE는 camelCase fallback을 둘 수 있다.

```json
{
  "type": "SINGLE_FILE",
  "file_id": "file-1",
  "filename": "report.pdf",
  "mime_type": "application/pdf",
  "size": 1024,
  "url": "https://drive.google.com/...",
  "content": "저장 정책에 따라 truncate된 본문 미리보기",
  "content_status": "available",
  "content_error": null,
  "content_metadata": {
    "extraction_method": "pdf_text",
    "content_kind": "plain_text",
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

`FILE_LIST.items[]`와 Gmail `attachments[]`도 가능한 한 같은 shape를 사용한다.

### 2.2 Content status 의미

| status | FE 표시 | 백엔드 의미 |
|--------|---------|-------------|
| `available` | 본문 읽기 완료 | `content`가 LLM/UI용 텍스트로 존재 |
| `empty` | 읽을 수 있는 본문 없음 | 추출은 수행했으나 텍스트가 없음 |
| `unsupported` | 지원하지 않는 파일 형식 | extractor 미지원 또는 조건부 지원군 미구현 |
| `too_large` | 파일 크기 제한 초과 | 다운로드/추출/LLM 입력 제한 초과 |
| `failed` | 본문 읽기 실패 | 외부 API/파서/변환 오류 |
| `not_requested` | 본문 미포함 | preview 또는 metadata-only 경로에서 본문 추출을 요청하지 않음 |

FE 설계의 상태 문구는 이 의미와 맞다.

### 2.3 Preview metadata 병행 정책

프론트 설계의 `previewScope` 유지 방향은 맞다. 백엔드는 아래 형태를 우선한다.

```json
{
  "metadata": {
    "previewScope": "source_metadata",
    "contentPolicy": "metadata_only",
    "contentIncluded": false,
    "contentStatusScope": "item",
    "contentRequired": false,
    "contentRequiredReason": null
  }
}
```

필드 의미:

| 필드 | 값 | 의미 |
|------|----|------|
| `previewScope` | `source_metadata` | 기존 source preview 범위. FE 호환용 |
| `contentPolicy` | `metadata_only` | 본문 추출/본문 미리보기 없음 |
| `contentPolicy` | `content_included` | `content`가 포함됨 |
| `contentPolicy` | `content_status_only` | 본문 없이 상태만 포함됨 |
| `contentIncluded` | boolean | 응답에 실제 content text가 포함됐는지 |
| `contentStatusScope` | `none|item|attachment|node` | status가 붙은 단위 |
| `contentRequired` | boolean | downstream 또는 target node가 본문을 필요로 하는지 |
| `contentRequiredReason` | `downstream|user_request|runtime_config|null` | 본문 필요 사유 |

`required_by_downstream`은 FE가 문구로 표시할 수는 있지만, 백엔드 신규 계약에서는 `contentRequiredReason=downstream`으로 분리하는 것을 권장한다.
`content_required_but_unavailable`도 같은 이유로 canonical `contentPolicy` 값으로 두지 않는다. Spring이 FastAPI raw response를 public API로 재노출해야 한다면, 가능한 한 아래처럼 정규화한다.

```json
{
  "metadata": {
    "previewScope": "source_metadata",
    "contentPolicy": "content_status_only",
    "contentIncluded": false,
    "contentRequired": true,
    "contentRequiredReason": "runtime_config"
  }
}
```

---

## 3. FE 설계 항목별 검토

### 3.1 Preview API 타입

검토 결과: 적절하다.

`NodePreviewRequest.includeContent?: boolean`은 이미 백엔드 `NodePreviewRequest.includeContent`와 맞는다. `NodePreviewResponse.metadata`가 확장 가능한 map이면 신규 metadata를 받을 수 있다.

백엔드 보강:

- source preview 기본값은 `includeContent=false`.
- metadata에 `previewScope=source_metadata`, `contentPolicy=metadata_only`, `contentIncluded=false`를 명시한다.
- `includeContent=true` 요청 시 FastAPI가 본문을 포함하지 못하면 `content_status`와 `content_error`를 내려준다.

### 3.2 AI 노드 preview

검토 결과: 1차 제외가 맞다.

현재 Spring preview service는 start node만 지원한다. FE가 AI 노드 preview를 먼저 열면 UX가 backend capability와 어긋난다.

1차 정책:

- FE는 source preview 버튼을 유지한다.
- AI/content preview는 backend가 start node 외 dry-run을 지원할 때 연다.
- 백엔드 capability가 열리면 FE의 `canRequestPreview`를 node role 고정 조건에서 capability 기반으로 바꾼다.

### 3.3 `DataPreviewBlock`

검토 결과: 적절하다.

`previewMetadata` props 추가는 백엔드 응답 구조와 맞다. Spring의 public API 응답은 이미 `metadata`를 포함할 수 있으므로, FE가 이 값을 `InputPanel`/`OutputPanel`에서 전달하는 방식이 자연스럽다.

주의:

- `content_error`는 너무 긴 raw error일 수 있으므로 FE는 한 줄/짧은 영역에 표시하고, 백엔드는 가능하면 사용자 표시 가능한 message로 정규화한다.
- `content_metadata.limits`는 표시 대상이 될 수 있으나 1차 UI 필수는 아니다. 단, `too_large` 또는 truncate 상태에서는 제한값을 그대로 나열하기보다 “현재 처리 가능한 크기를 초과했습니다”, “본문 일부만 표시됩니다” 같은 보조 문구로 쓰는 방향이 적절하다.
- `content_metadata.truncated=true` 또는 `stored_content_truncated=true`이면 FE가 보여주는 본문은 전체 원문이 아니라 저장/표시 정책에 따라 잘린 preview일 수 있다.

### 3.4 Legacy fallback

검토 결과: 허용 가능하다.

FE의 `content -> extracted_text -> extractedText` fallback은 하위 호환에 유리하다. 다만 백엔드 신규 계약의 canonical field는 `content`다. 신규 payload에서 `extracted_text`를 새로 만들 필요는 없다.

### 3.5 Gmail attachment

검토 결과: 적절하다.

Gmail attachment가 `FileItemCard`를 공유하는 구조는 백엔드 payload shape와 잘 맞는다. 1차에서 attachment content가 미지원이면 아래처럼 표시된다.

```json
{
  "filename": "agenda.pdf",
  "mime_type": "application/pdf",
  "size": 512,
  "content": null,
  "content_status": "unsupported",
  "content_error": "Gmail 첨부파일 본문 추출은 현재 지원하지 않습니다."
}
```

### 3.6 Error UX

검토 결과: 방향은 맞지만 `DOCUMENT_CONTENT_NOT_REQUESTED`는 주의가 필요하다.

권장 FE 매핑:

| code/status | FE 문구 | severity |
|-------------|---------|----------|
| `DOCUMENT_CONTENT_UNSUPPORTED` | 이 파일 형식은 아직 본문 읽기를 지원하지 않습니다. | warning |
| `DOCUMENT_CONTENT_TOO_LARGE` | 파일이 너무 커서 본문을 읽을 수 없습니다. | warning |
| `DOCUMENT_CONTENT_EMPTY` | 파일에서 읽을 수 있는 본문이 없습니다. | warning |
| `DOCUMENT_CONTENT_EXTRACTION_FAILED` | 파일 본문을 읽는 중 오류가 발생했습니다. | error |
| `DOCUMENT_CONTENT_NOT_REQUESTED` | 본문 미포함 미리보기입니다. | info |

`DOCUMENT_CONTENT_NOT_REQUESTED`가 실행 실패 code로 내려오는 경우에는 backend가 `requires_content=true` 상황에서 실패로 판단한 것이다. Spring public error code도 `DOCUMENT_CONTENT_NOT_REQUESTED`로 보존되므로, FE는 “본문이 필요한 작업이지만 본문 추출이 수행되지 않았습니다.”로 보정할 수 있다.

`DataStateNotice`와 `getExecutionStatusNotice`가 서로 다른 문구를 만들지 않도록 같은 error helper를 재사용하는 FE 보강안은 적절하다. 백엔드는 가능하면 `error.code`, `error.message`, `metadata.fastApiErrorCode` 중 적어도 하나로 `DOCUMENT_CONTENT_*`를 보존한다.

---

## 4. Backend 문서/구현 반영 항목

FE 설계를 지원하려면 Spring Boot 설계에 아래 항목을 반영한다.

| 우선순위 | 항목 | 설명 |
|----------|------|------|
| P0 | preview metadata 명시 | `previewScope`, `contentPolicy`, `contentIncluded`, `contentStatusScope` |
| P0 | content status 보존 | FastAPI preview/execution payload의 `content_*` 필드 보존 |
| P0 | error code 매핑 | `DOCUMENT_CONTENT_*`를 Spring reason/metadata/user message로 보존 |
| P1 | sanitize/truncate metadata | `stored_content_truncated`, `stored_char_count` 보존 |
| P1 | AI preview capability | start node 외 preview 지원 시 FE capability로 노출 |
| P1 | raw content policy 정규화 | FastAPI raw `required_by_downstream`, `content_required_but_unavailable`을 Spring public metadata의 `contentRequired*` 필드로 정규화 |

---

## 5. FE 1차 구현 수용 기준

FE 1차 구현은 아래를 만족하면 백엔드 계약과 호환된다.

- source preview는 기본 `includeContent=false`를 유지한다.
- `DataPreviewBlock`은 `previewMetadata`를 받아 `contentPolicy`를 표시한다.
- `SINGLE_FILE`, `FILE_LIST.items[]`, Gmail attachment에서 `content_status`, `content_error`, `content_metadata`를 표시한다.
- `content_status=available`이고 `content`가 있으면 본문 preview를 표시한다.
- `not_requested`는 오류가 아니라 본문 미포함 상태로 표시한다.
- `unsupported|too_large|failed|empty`는 빈 본문 성공처럼 보이지 않게 상태/오류를 표시한다.
- `DOCUMENT_CONTENT_*` code를 사용자 문구로 매핑한다.
- AI 노드 preview는 backend capability가 열리기 전까지 노출하지 않는다.
- `content_metadata.limits`는 `too_large`/truncate 상태의 보조 설명에만 사용하고, 기본 카드에 모든 제한값을 노출하지 않는다.
- `content_required_but_unavailable` 또는 `required_by_downstream`을 받더라도 신규 백엔드 계약의 `contentRequired`, `contentRequiredReason`가 있으면 그 값을 우선한다.

---

## 6. 추가 확인 필요

1. FE 브랜치명은 `feat#172-document_content_runtime`이고 BE 브랜치는 `feat/30-runtime-document`다. PR/문서에서 서로 참조할 때 브랜치명을 명확히 구분한다.
2. FE가 이미 `contentPolicy=required_by_downstream` 또는 `content_required_but_unavailable`을 구현했다면 legacy/raw fallback 표시값으로만 처리한다. 신규 백엔드 계약은 `contentRequired=true`, `contentRequiredReason=downstream|runtime_config` 분리다.
3. `content_error`를 FE가 그대로 노출할지, code 매핑 문구를 항상 우선할지 결정해야 한다. 권장은 code 매핑 우선, `content_error` 보조 표시다.
4. backend가 `includeContent=true` preview에서 full content를 내려줄 수 있는지, 저장용 truncate content만 내려줄지 결정해야 한다. 현재 권장은 truncate/sanitize된 content만 public API에 반환하는 것이다.
5. `Google Drive source preview content included` QA는 FE가 `includeContent=true`를 호출하는 UI를 추가하거나 backend 안정화가 끝난 뒤 확장 검증으로 둔다. 1차 필수 검증은 metadata-only preview와 실행 결과 content status 표시다.
