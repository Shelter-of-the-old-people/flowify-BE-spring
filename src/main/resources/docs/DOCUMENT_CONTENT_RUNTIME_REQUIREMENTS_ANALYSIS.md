# 문서 본문 런타임 요구사항 분석

> 작성일: 2026-05-14  
> 원문: `src/main/resources/docs/DOCUMENT_CONTENT_RUNTIME_REQUIREMENTS.md`  
> 목적: Google Drive/Gmail 파일 입력이 LLM 요약/분석 노드에서 실제 문서 본문을 사용하도록 만들기 위한 백엔드 요구사항, 현재 gap, 구현 우선순위, 확인 질문 정리

---

## 1. 결론

현재 요구사항의 핵심은 파일 metadata가 아니라 **추출된 문서 본문을 canonical payload의 표준 필드로 보장**하는 것이다.

기존 `FASTAPI_CONTRACT_SPEC.md`에는 `SINGLE_FILE.content`가 존재하지만 의미가 `base64-or-text-content` 수준으로 모호하다. 이 상태로는 LLM이 “파일 업로드용 바이너리 content”와 “요약 가능한 추출 텍스트”를 구분할 수 없다. 따라서 `content`, `content_status`, `content_error`, `content_metadata`를 하나의 런타임 계약으로 확정해야 한다.

Spring Boot 쪽 현재 구현은 FE config와 choice 기반 prompt를 FastAPI runtime model로 전달하지만, 원문 요구사항의 핵심 플래그인 `runtime_config.requires_content`는 아직 생성하지 않는다. Preview도 source 노드만 지원하며, `includeContent`는 사용자가 요청한 값을 그대로 전달할 뿐 content-dependent 노드 여부를 자동 판단하지 않는다.

따라서 1차 구현은 아래 순서가 적절하다.

1. Spring `WorkflowTranslator`가 content-dependent action에 `runtime_config.requires_content=true`를 넣는다.
2. FastAPI 계약 문서의 `SINGLE_FILE`, `FILE_LIST.items[]`, Gmail attachment payload를 본문 추출 상태 중심으로 확장한다.
3. FastAPI가 Drive source, Loop, LLM strategy에서 content 필드를 보존하고 소비한다.
4. Preview는 metadata-only 기본값을 유지하되, 본문 미포함/포함 상태를 응답 metadata에 명시한다.

---

## 2. 원문 요구사항 재정의

### 2.1 해결해야 하는 사용자 문제

사용자는 “Google Drive 문서 요약”, “폴더에 새로 올라온 문서 요약”, “Gmail 첨부 문서 요약” 같은 자동화를 기대한다. 그러나 런타임 payload가 파일명, MIME type, URL만 전달하면 LLM은 문서 내부를 읽을 수 없다.

정상 동작하려면 LLM 직전 입력 payload에 아래 정보가 있어야 한다.

```json
{
  "type": "SINGLE_FILE",
  "filename": "report.pdf",
  "mime_type": "application/pdf",
  "url": "https://drive.google.com/...",
  "content": "문서에서 추출된 텍스트",
  "content_status": "available",
  "content_error": null,
  "content_metadata": {
    "extraction_method": "pdf_text",
    "content_kind": "plain_text",
    "truncated": false,
    "char_count": 1234,
    "original_char_count": 1234
  }
}
```

### 2.2 본문이 필요한 action

원문은 아래 action을 content-dependent로 본다.

| action | 본문 필요성 |
|--------|-------------|
| `summarize` | 문서/메일 본문 기반 요약 |
| `extract_info` | 본문에서 정보 추출 |
| `translate` | 본문 번역 |
| `classify_by_content` | 내용 기반 분류 |
| `describe_image` | 이미지 설명 생성 |
| `ocr` | 이미지/스캔 문서 텍스트화 |
| `ai_summarize` | AI 요약 alias |
| `ai_analyze` | AI 분석 alias |

Spring은 이 목록을 기준으로 `requires_content`를 생성할 수 있다. FastAPI는 이 값이 `true`인 경우 본문 추출 실패를 빈 요약 성공으로 처리하면 안 된다.

### 2.3 canonical payload 표준화 대상

이번 요구사항이 직접 바꾸는 payload는 아래 셋이다.

| payload | 변경 방향 |
|---------|-----------|
| `SINGLE_FILE` | 추출 본문과 추출 상태 필드 표준화 |
| `FILE_LIST.items[]` | 각 item을 `SINGLE_FILE`과 같은 shape로 맞춤 |
| `SINGLE_EMAIL.attachments[]` | Gmail 첨부파일도 파일 payload와 같은 content 상태를 가짐 |

---

## 3. 현재 코드 기준 gap

### 3.1 `WorkflowTranslator`

파일: `src/main/java/org/github/flowify/execution/service/WorkflowTranslator.java`

현재 동작:

- input 노드에는 `runtime_source`를 만든다.
- output 노드에는 `runtime_sink`를 만든다.
- llm/loop/branch 노드에는 `runtime_config`를 만들고 기존 config, choice prompt, branch config를 병합한다.
- `node_type`, `output_data_type`은 마지막에 덮어써 안정적으로 전달한다.

gap:

- `runtime_config.requires_content` 생성 로직이 없다.
- `choiceActionId`, `action`, `node_type`, `dataType`, `outputDataType`를 조합한 content-dependent 판별이 없다.
- 파일 본문이 필요한 템플릿의 LLM 노드는 prompt만 있을 뿐 `choiceActionId`가 없는 경우도 있다. 예: `TemplateSeeder`의 folder document summary, file upload auto share 템플릿.
- choice 기반 AI 노드는 `ChoicePromptResolver`가 `action=process`를 기본값으로 넣는다. 따라서 `action`만 보고 content-dependent 여부를 판별하면 `summarize`, `extract_info`, `translate`, `ocr` 같은 선택지 기반 AI 노드를 놓친다.

영향:

- FastAPI가 “이 LLM 노드는 반드시 본문 추출이 필요하다”는 신호를 받을 수 없다.
- FastAPI가 metadata-only 입력으로도 요약을 시도할 가능성이 남는다.

판별 시 우선순위:

1. node config의 명시적 `requires_content`가 있으면 최우선으로 사용한다.
2. `choiceActionId` 또는 `choice_action_id`가 content-dependent action 목록에 있으면 `true`.
3. 명시적 `action`이 `process`가 아닌 실제 action id이고 content-dependent 목록에 있으면 `true`.
4. `summaryFormat` 또는 `prompt_source=manual`인 템플릿성 AI 노드는 `dataType`이 파일/메일 계열이고 `outputDataType`이 `TEXT` 또는 `SPREADSHEET_DATA`이면 보수적으로 `true`.
5. `PASSTHROUGH`, metadata filter, 단순 정렬/중복 제거 계열은 `false`.

### 3.2 Preview

파일:

- `src/main/java/org/github/flowify/workflow/service/WorkflowPreviewService.java`
- `src/main/java/org/github/flowify/workflow/dto/NodePreviewRequest.java`
- `src/main/java/org/github/flowify/execution/service/FastApiClient.java`

현재 동작:

- preview는 `role=start` source 노드만 지원한다.
- `includeContent` 기본값은 `false`다.
- Spring은 요청의 `includeContent` 값을 `include_content`로 FastAPI에 전달한다.
- preview metadata에는 `previewScope=source_metadata`가 고정으로 들어간다.

gap:

- LLM 노드 preview를 지원하지 않는다.
- target node가 content-dependent인지 자동 판단해 `includeContent=true`로 바꾸는 로직이 없다.
- 본문 미포함 preview임을 사용자에게 더 구체적으로 설명하는 metadata가 부족하다.

영향:

- 사용자는 source preview에서 파일 카드만 보고 “본문 요약이 가능한지”를 판단하기 어렵다.
- 실제 실행에서는 본문이 필요하지만 preview는 metadata-only라서 실행 전 검증력이 낮다.

### 3.3 `FASTAPI_CONTRACT_SPEC.md`

파일: `src/main/resources/docs/FASTAPI_CONTRACT_SPEC.md`

현재 동작:

- `SINGLE_FILE` 예시는 `content: "base64-or-text-content..."`로 되어 있다.
- `FILE_LIST.items[]` 예시는 metadata 중심이다.
- Gmail attachment 예시는 `filename`, `mime_type`, `size`만 있다.

gap:

- `content`가 추출 텍스트인지, 원본 바이너리/base64인지 불명확하다.
- `content_status`가 없어 unsupported/too_large/failed/not_requested를 구분할 수 없다.
- `content_metadata.extraction_method`, `content_kind`, truncate 여부, char count, limit 정보가 없다.

영향:

- Spring, FastAPI, FE가 같은 `SINGLE_FILE`을 다르게 해석할 수 있다.
- LLM 소비용 payload와 sink 업로드용 payload가 충돌할 수 있다.

### 3.4 템플릿

파일: `src/main/java/org/github/flowify/config/TemplateSeeder.java`

관찰:

- 폴더 문서 요약 템플릿들은 `google_drive(folder_new_file) -> llm(SINGLE_FILE) -> slack/gmail/sheets` 구조다.
- 파일 업로드 자동 공유 템플릿도 `SINGLE_FILE`을 LLM으로 넘긴다.
- LLM prompt는 “입력된 문서/파일 내용을 바탕으로”라고 명시한다.

gap:

- 이 템플릿들의 LLM config에는 `choiceActionId=summarize` 같은 표준 action id가 없다.
- 따라서 Spring이 `choiceActionId`만 보고 `requires_content`를 판별하면 템플릿 기반 workflow가 누락된다.

권장:

- `choiceActionId`, `action`, `summaryFormat`, `dataType`, `outputDataType`, prompt pattern 중 최소한 `action` 또는 명시적 `requires_content`를 템플릿 config에 넣는 것이 안정적이다.

### 3.5 기존 source/sink 계약과의 충돌

파일: `src/main/resources/docs/WORKFLOW_SOURCE_SINK_SPEC.md`

현재 source/sink 명세는 `SINGLE_FILE`을 단일 파일 데이터 타입으로 정의하고, sink 쪽에서는 Gmail/Google Drive가 `SINGLE_FILE.content`를 첨부파일 또는 업로드 파일의 본문처럼 소비할 수 있다고 본다. 반면 이번 요구사항은 `SINGLE_FILE.content`를 LLM이 읽을 수 있는 추출 텍스트로 표준화하려 한다.

이 충돌을 해결하지 않으면 다음 문제가 생긴다.

- LLM 노드는 `content`를 추출 텍스트로 해석한다.
- Drive/Gmail sink는 같은 `content`를 원본 파일 bytes/base64로 해석할 수 있다.
- 파일 요약 후 원본 파일을 재전달하는 workflow에서 추출 텍스트가 원본 파일처럼 업로드될 위험이 있다.

권장 해결안:

- `content`는 canonical text representation, 즉 LLM/preview/UI용 추출 텍스트로 고정한다.
- 원본 파일 전달이 필요한 sink는 `file_id`, `url`, `download_url`, `raw_content_base64`, `content_ref` 중 하나를 명시적으로 사용한다.
- FastAPI sink 계약에서 `SINGLE_FILE` 업로드/첨부 시 어떤 필드를 소비하는지 다시 정의한다.

### 3.6 데이터 보존과 로깅 위험

문서 본문은 개인정보, 내부 자료, 첨부파일 원문을 포함할 수 있다. 현재 Spring은 FastAPI가 보내는 node data를 `inputData`, `outputData`, `snapshot` 형태로 저장하고 반환할 수 있으므로, 본문 추출을 도입하면 저장소에 원문이 남는 범위가 커진다.

필요한 결정:

- 실행 로그에 `content` 전체를 저장할지, preview/최근 실행 조회용으로 truncate해서 저장할지 정해야 한다.
- 보존 목적이 없는 대용량 본문은 FastAPI 내부 실행 컨텍스트에서만 사용하고 Spring에는 metadata와 일부 preview만 보내는 선택지도 있다.
- 최소한 로그에는 OAuth token, download URL signed token, 원문 bytes/base64를 남기지 않아야 한다.
- `content_metadata`에 `stored_content_truncated`, `stored_char_count` 같은 저장 관점 metadata를 추가할지 검토한다.

---

## 4. 요구사항을 구현 단위로 분해

### 4.1 Spring Boot

| 우선순위 | 작업 | 설명 |
|----------|------|------|
| P0 | content-dependent 판별 함수 추가 | `choiceActionId`, `action`, `summaryFormat`, `dataType` 기준으로 본문 필요 여부 판별 |
| P0 | `runtime_config.requires_content` 생성 | llm/loop/branch runtime_config에 boolean 추가. 최소 llm 노드부터 시작 |
| P0 | translator 테스트 추가 | `summarize`/`ai_analyze`는 true, `passthrough`는 false 검증 |
| P0 | 명시값 보존 정책 확정 | node config에 이미 `requires_content`가 있으면 자동 추론보다 우선 |
| P1 | 템플릿 config 보강 | 문서 요약/파일 공유 LLM 노드에 `action=summarize` 또는 `requires_content=true` 추가 |
| P1 | preview 정책 개선 | source preview metadata에 `content_policy`, `preview_scope` 명시 |
| P1 | node data 보존 정책 정리 | FastAPI가 반환한 `content_*` 필드를 저장/응답에서 보존하되, 원문 저장 범위는 제한값과 함께 합의 |
| P2 | LLM 노드 preview 지원 검토 | 현재 source-only preview 제약을 확장할지 결정 |

Spring의 1차 책임은 파일 추출 자체가 아니라, FastAPI가 추출해야 한다는 의도를 잃지 않게 전달하는 것이다.

### 4.2 FastAPI

| 우선순위 | 작업 | 설명 |
|----------|------|------|
| P0 | `SINGLE_FILE` content 계약 구현 | `content_status`, `content_error`, `content_metadata` 포함 |
| P0 | `content` 의미 분리 | `content`는 추출 텍스트로 고정하고 원본 bytes/base64는 별도 필드 또는 ref로 분리 |
| P0 | Google Drive single file 추출 | Google Docs/Workspace export, plain text, PDF text layer 등 필수 지원부터 |
| P0 | LLM input builder 수정 | `SINGLE_FILE.content`, `FILE_LIST.items[].content`, `SINGLE_EMAIL.body` 우선순위 적용 |
| P0 | 실패 처리 | `requires_content=true`인데 본문 없음이면 빈 요약 성공 금지 |
| P1 | Loop item content 보존 | `FILE_LIST.items[] -> SINGLE_FILE` 변환 시 content 관련 필드 유지 |
| P1 | extractor metadata 기록 | `extraction_method`, `content_kind`, char count, truncate 기록 |
| P2 | Gmail attachment content | 지원 여부 확정 후 payload 확장 또는 명시적 unsupported 반환 |

FastAPI의 핵심 책임은 “파일을 읽는 것”과 “읽을 수 없을 때 그 사실을 payload로 명확히 표현하는 것”이다.

### 4.3 FE

| 우선순위 | 작업 | 설명 |
|----------|------|------|
| P1 | content status 표시 | `content_status`, `content_error`, `truncated`를 preview/result UI에 표시 |
| P1 | LLM preview 요청 정책 | content-dependent 노드 preview 시 `includeContent=true` 전달 또는 Spring 자동화에 맞춤 |
| P2 | 템플릿 설명 정리 | 실제 지원 파일 타입/제한과 템플릿 문구 일치 |

FE는 본문을 직접 읽지 않는다. FE의 주된 책임은 런타임이 내려준 상태를 사용자가 이해할 수 있게 표시하는 것이다.

---

## 5. 권장 runtime contract

### 5.1 `SINGLE_FILE`

```json
{
  "type": "SINGLE_FILE",
  "file_id": "string",
  "filename": "string",
  "mime_type": "string",
  "size": 12345,
  "url": "string|null",
  "content": "string|null",
  "content_status": "available|empty|unsupported|too_large|failed|not_requested",
  "content_error": "string|null",
  "content_metadata": {
    "extraction_method": "google_export|pdf_text|csv_parse|pptx_text|word_text|hwpx_xml|plain_text|ocr|vision|gmail_attachment|none",
    "content_kind": "plain_text|table_text|slide_text|ocr_text|image_description|mixed|none",
    "truncated": false,
    "char_count": 0,
    "original_char_count": 0,
    "limits": {
      "max_download_bytes": 0,
      "max_extracted_chars": 0,
      "max_llm_input_chars": 0
    }
  }
}
```

권장 해석:

- `content`는 LLM이 바로 읽을 수 있는 추출 텍스트다.
- 원본 파일 업로드/전달용 바이너리가 필요하면 별도 필드명을 사용한다. 예: `raw_content_base64`, `download_url`, `attachment_bytes_ref`.
- `content_status=available`이면 `content`는 null이 아니어야 한다. 단, 빈 문서는 `empty`로 구분한다.

### 5.2 `FILE_LIST`

```json
{
  "type": "FILE_LIST",
  "items": [
    {
      "type": "SINGLE_FILE",
      "file_id": "file-1",
      "filename": "doc.pdf",
      "mime_type": "application/pdf",
      "size": 12345,
      "url": "https://drive.google.com/...",
      "content": null,
      "content_status": "not_requested",
      "content_error": null,
      "content_metadata": {
        "extraction_method": "none",
        "content_kind": "none",
        "truncated": false,
        "char_count": 0,
        "original_char_count": 0
      }
    }
  ]
}
```

목록 preview에서는 `content_status=not_requested`를 허용한다. 단, 다음 노드가 content-dependent이면 실행 시점에 eager extraction 또는 lazy extraction을 수행해야 한다.

### 5.3 LLM runtime config

```json
{
  "runtime_type": "llm",
  "runtime_config": {
    "node_type": "AI",
    "output_data_type": "TEXT",
    "action": "summarize",
    "choiceActionId": "summarize",
    "requires_content": true
  }
}
```

권장 규칙:

- Spring은 명시적 `requires_content`가 config에 있으면 그 값을 우선한다.
- 없으면 `choiceActionId` 또는 `action`을 content-dependent 목록과 비교한다.
- `choiceActionId`가 없고 `choiceSelections` 내부에 action id가 key로 저장된 마이그레이션 데이터는 제한적으로 fallback한다.
- 그래도 없으면 `dataType=SINGLE_FILE|FILE_LIST|SINGLE_EMAIL|EMAIL_LIST`이고 `outputDataType=TEXT|SPREADSHEET_DATA`인 AI/LLM 노드는 보수적으로 `requires_content=true`로 둘 수 있다.

### 5.4 Spring 판별 로직 초안

아래는 구현 의도를 고정하기 위한 pseudo-code다. 실제 코드는 `WorkflowTranslator` 내부 private method 또는 별도 resolver로 분리할 수 있다.

```java
boolean requiresContent(NodeDefinition node, String semanticNodeType, Map<String, Object> runtimeConfig) {
    Object explicit = firstPresent(
            node.getConfig().get("requires_content"),
            node.getConfig().get("requiresContent"),
            runtimeConfig.get("requires_content"),
            runtimeConfig.get("requiresContent"));
    if (explicit != null) {
        return Boolean.parseBoolean(String.valueOf(explicit));
    }

    String choiceActionId = firstText(
            node.getConfig().get("choiceActionId"),
            node.getConfig().get("choice_action_id"));
    if (CONTENT_ACTIONS.contains(choiceActionId)) {
        return true;
    }

    String action = firstText(node.getConfig().get("action"), runtimeConfig.get("action"));
    if (!"process".equals(action) && CONTENT_ACTIONS.contains(action)) {
        return true;
    }

    if (containsContentActionKey(node.getConfig().get("choiceSelections"))) {
        return true;
    }

    boolean contentCarrier = Set.of("SINGLE_FILE", "FILE_LIST", "SINGLE_EMAIL", "EMAIL_LIST")
            .contains(node.getDataType());
    boolean generatedOutput = Set.of("TEXT", "SPREADSHEET_DATA").contains(node.getOutputDataType());
    boolean promptNode = Set.of("AI", "AI_FILTER").contains(semanticNodeType);
    return promptNode && contentCarrier && generatedOutput;
}
```

주의점:

- `ChoicePromptResolver`가 choice 기반 AI 노드의 `action`을 `process`로 치환하므로 `choiceActionId`를 먼저 본다.
- `choiceSelections` fallback은 legacy/migration 방어용이다. 모든 selection value를 action으로 해석하면 style id나 option id를 오판할 수 있으므로, key가 content-dependent action id와 정확히 일치하는 경우만 허용한다.
- prompt text 기반 fallback은 마지막 방어선으로만 둔다. 예를 들어 prompt에 `요약`, `번역`, `OCR`, `문서 내용` 같은 단어가 있어도 사용자가 metadata-only 요약을 의도했을 수 있으므로, 기본 정책은 `dataType`/`outputDataType`/prompt node 여부를 함께 만족할 때만 `true`로 추론한다.
- Loop 노드 자체는 본문을 생성하지 않지만, 다음 노드가 content-dependent이면 item content 보존 또는 lazy extraction key 보존이 필요하다. 이 판단은 FastAPI graph-level 분석으로 처리하는 편이 자연스럽다.
- Branch 노드는 `classify_by_content`일 때만 본문이 필요하고, `classify_by_type`처럼 metadata 기반 분기는 필요하지 않다.

### 5.5 Preview 응답 metadata 권장값

현재 Spring preview metadata는 `previewScope=source_metadata`로 고정된다. 본문 추출 요구사항 이후에는 아래처럼 상태를 더 명확히 나누는 것이 좋다.

| key | 예시 | 의미 |
|-----|------|------|
| `preview_scope` | `source_metadata` | 기존 source preview API 범위 |
| `content_policy` | `metadata_only` | 본문 미포함 preview |
| `content_policy` | `content_included` | 본문 추출 포함 preview |
| `content_policy` | `content_status_only` | 본문 원문 없이 추출 가능/불가 상태만 포함 |
| `content_included` | `true|false` | 응답에 실제 `content` text가 포함됐는지 |
| `content_status_scope` | `none|item|attachment|node` | content status가 어느 단위에 붙었는지 |
| `content_limit` | `{...}` | 적용된 다운로드/추출/LLM 제한값 |
| `content_required` | `true|false` | 다음 노드 또는 runtime config가 본문을 요구하는지 |
| `content_required_reason` | `downstream|user_request|runtime_config|null` | 본문 필요 사유 |

Spring public API는 camelCase를 쓰는 경향이 있으므로 FE 응답 metadata에서는 `previewScope`, `contentPolicy`, `contentRequired`, `contentRequiredReason`를 유지할 수도 있다. FastAPI runtime request/response의 내부 필드는 snake_case로 유지해도 된다. 이 dual-convention은 기존 source/sink 명세와 일치한다.

주의: downstream 때문에 본문이 필요한 상태는 content policy 자체가 아니라 필요 사유다. 따라서 `contentPolicy=required_by_downstream`처럼 섞기보다 `contentRequired=true`, `contentRequiredReason=downstream`으로 분리한다.

---

## 6. 파일 타입 지원 기준

원문 기준 1차 완료에 포함해야 하는 필수 지원군은 아래다.

| 파일군 | 1차 기준 |
|--------|----------|
| Google Docs/Slides/Sheets | export 후 추출 |
| PDF text layer | `pdf_text`로 텍스트 추출 |
| CSV/TSV | UTF-8, UTF-8 BOM, CP949 처리 |
| PPTX | slide 순서 유지, title/body/note 구분 |
| HWPX | zip 내부 XML 본문 추출 |
| DOCX | paragraph/table/header/footer 추출 |
| TXT/Markdown | 인코딩 감지 후 원문 사용 |

조건부 지원군은 아래처럼 처리한다.

| 파일군 | 미구현 시 응답 |
|--------|----------------|
| 스캔 PDF | `content_status=unsupported`, OCR 미지원 사유 |
| PPT | `content_status=unsupported`, legacy 변환 미지원 사유 |
| DOC | `content_status=unsupported`, legacy 변환 미지원 사유 |
| 이미지 | OCR/vision 미지원이면 `unsupported` |
| GIF/HEIC | 대표 frame/변환 미지원이면 `unsupported` |

중요한 점은 미지원 파일도 실패를 삼키지 않고 `content_error`로 사용자 표시 가능한 사유를 내려야 한다는 것이다.

---

## 7. 구현 순서 제안

### Phase 1: 계약과 Spring 신호 보강

1. `FASTAPI_CONTRACT_SPEC.md`의 `SINGLE_FILE`, `FILE_LIST`, Gmail attachment schema를 본문 추출 상태 중심으로 개정한다.
2. `WorkflowTranslator`에 `requires_content` 판별을 추가한다. 이때 `choiceActionId`를 `action`보다 우선한다.
3. 문서 요약/파일 공유 템플릿의 LLM config에 명시적 `action` 또는 `requires_content`를 추가한다.
4. `WorkflowTranslatorTest`에 content-dependent action 테스트를 추가한다.
5. `WorkflowPreviewService` metadata에 본문 미포함 preview임을 나타내는 `contentPolicy` 또는 동등 필드를 추가한다.

### Phase 2: FastAPI 본문 추출과 소비

1. Google Drive `single_file`, `folder_new_file`, `folder_all_files`에서 content policy를 적용한다.
2. LLM input builder가 `SINGLE_FILE.content`를 최우선으로 읽도록 수정한다.
3. Loop strategy가 content 관련 필드를 유지한다.
4. 추출 실패/미지원/크기 초과를 node data와 preview data에 남긴다.
5. 원본 파일 전달용 필드와 LLM 추출 텍스트 필드를 분리한다.

### Phase 3: Preview와 사용자 표시

1. source preview metadata에 `preview_scope=source_metadata`를 유지하고, 본문 포함 정책은 `content_policy=metadata_only|content_included|content_status_only`로 명확히 둔다.
2. FE가 `content_status`와 `content_error`를 표시한다.
3. LLM 노드 preview를 지원할지, source preview만 유지할지 결정한다.

---

## 8. 확인 필요 질문

아래 항목은 구현 전에 결정하면 재작업을 줄일 수 있다.

1. `SINGLE_FILE.content`를 “LLM용 추출 텍스트”로 고정해도 되는가?
   현재 기존 계약에는 `base64-or-text-content`라고 되어 있어, 파일 업로드 sink가 원본 바이너리를 기대할 가능성이 있다. 원본 파일 전달이 필요하면 `raw_content_base64` 같은 별도 필드로 분리하는 편이 안전하다.

2. Spring이 `requires_content`를 보수적으로 자동 추론해도 되는가?
   예를 들어 `dataType=SINGLE_FILE`, `runtime_type=llm`, `outputDataType=TEXT`이면 action id가 없어도 `requires_content=true`로 볼 수 있다. 이 규칙은 템플릿 누락에는 강하지만, 파일 metadata만 요약하려는 특수 workflow에는 과할 수 있다.

3. Preview는 source 노드만 계속 지원할 것인가, LLM 노드 preview까지 확장할 것인가?
   현재 Spring preview는 source-only다. 원문은 “LLM 노드 preview 요청 시 includeContent true”도 언급하므로 FE/BE 범위를 맞춰야 한다.

4. Gmail 첨부파일 본문 추출은 1차 범위인가?
   원문은 기존 Gmail 계획에서 attachment content가 제외였다고 분석한다. 1차에서 제외한다면 Gmail 첨부 문서 요약 템플릿/선택지는 제한 문구를 표시해야 한다.

5. 파일 크기와 추출 문자 수 제한값은 어디서 관리할 것인가?
   FastAPI 환경변수/설정으로 관리하고 payload metadata에 내려주는 방식이 자연스럽다. Spring도 UI 표시를 위해 그대로 보존해야 한다.

6. 실행 로그에 문서 본문 전체를 저장해도 되는가?
   저장한다면 조회 편의성은 높지만 개인정보/대용량 저장 위험이 커진다. 저장하지 않거나 truncate한다면 디버깅 정보가 줄어든다.

---

## 9. 완료 기준

아래가 모두 충족되면 원문 요구사항의 1차 완료로 볼 수 있다.

- `SINGLE_FILE`과 `FILE_LIST.items[]`에 `content_status`, `content_error`, `content_metadata` 계약이 문서화되어 있다.
- Spring runtime model의 content-dependent LLM 노드에 `runtime_config.requires_content=true`가 포함된다.
- `choiceActionId=summarize`처럼 choice 기반 AI 노드는 `action=process`여도 `requires_content=true`가 된다.
- Google Drive 문서 요약 실행 시 LLM inputData에 실제 추출 텍스트가 존재한다.
- `FILE_LIST -> LOOP -> LLM` 경로에서 content 또는 lazy extraction key가 손실되지 않는다.
- 본문 추출 실패가 빈 요약 성공으로 처리되지 않는다.
- Preview와 실제 실행 결과에서 본문 포함/미포함 상태가 구분된다.
- Gmail 첨부파일 요약은 지원 또는 미지원 상태가 사용자에게 명확히 표시된다.
- 원본 파일 업로드/첨부용 데이터와 LLM 추출 텍스트의 필드 의미가 충돌하지 않는다.
