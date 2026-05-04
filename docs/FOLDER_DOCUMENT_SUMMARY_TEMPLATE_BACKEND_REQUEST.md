# 폴더 문서 자동 요약 템플릿 백엔드 요청서

> **작성일:** 2026-05-04
> **대상:** Spring, FastAPI 백엔드 담당자
> **용도:** 임시 전달용 문서
> **관련 이슈:** 폴더 문서 자동 요약 템플릿 구현

---

## 1. 요청 배경

FE에서는 `폴더 문서 자동 요약` 카테고리를 시스템 템플릿군으로 구체화하고 있습니다.
다만 실제 시스템 템플릿 데이터와 실행 품질은 백엔드가 결정하므로, 아래 항목이 같이 정리되어야 템플릿 3종이 실사용 가능한 수준으로 보입니다.

현재 핵심 이슈는 다음과 같습니다.

- Spring 시더에 `폴더 문서 자동 요약` 카테고리 시스템 템플릿이 아직 없음
- Google Drive source는 runtime에 연결되어 있지만, `folder_all_files`는 현재 파일 메타데이터 위주라 문서 내용 요약으로 바로 이어지지 않음
- 템플릿 이름/설명과 실제 source mode/runtime semantics를 맞춰야 함
- Slack / Gmail / Google Sheets sink는 runtime에 존재하지만, 템플릿 seed 품질과 FE 기대 계약 정렬이 필요함
- FE는 기존 Google Drive folder picker를 그대로 재사용할 예정이라 source mode / target schema / runtime_source 구성이 일관되어야 함

이번 문서는 폴더 문서 자동 요약 카테고리 템플릿 3종의 시드, Google Drive source runtime 보완, LLM prompt 방향, FE 공개 계약 정렬에 집중합니다.

### 1.1 1차 구현 메모

- source mode는 folder_new_file 기준으로 구현한다.
- google_sheets sink는 1차에서 google_drive 토큰 alias 전략을 사용한다.
- Google OAuth scope는 Drive + Sheets 기록과 Gmail 발송 기준으로 확장한다.
- Google Sheets 템플릿은 SPREADSHEET_DATA JSON 결과를 직접 생성하는 방향으로 구현한다.

---

## 2. 요청 사항

### 2.1 폴더 문서 자동 요약 템플릿 3종 시스템 시드 추가

위치:
- `src/main/java/org/github/flowify/config/TemplateSeeder.java`

1차 대상 템플릿:
- 신규 문서 요약 후 Slack 공유
- 신규 문서 요약 후 Gmail 전달
- 문서 요약 결과를 Google Sheets에 저장

공통 기준:
- `category`: `folder_document_summary`
- `isSystem`: `true`
- `requiredServices`: 실제 서비스 키 사용
  - 예: `google_drive`, `slack`, `gmail`, `google_sheets`
- `icon`: `google_drive` 권장

중요:
- 템플릿 이름/설명은 현재 실제 runtime semantics에 맞춰 `폴더 안 문서를 읽어 요약하고 공유/저장` 문구로 정리해야 합니다.
- `모든 문서를 완벽하게 하나씩 요약`처럼 과장되는 설명은 피해야 합니다.

### 2.2 시더 로직은 기존 증분 반영 방식 유지

현재 main에는 이름+isSystem 기준 upsert 구조가 이미 반영되어 있으므로, 이 카테고리 템플릿도 동일 규칙을 따라야 합니다.

확인 포인트:
- 기존 DB에도 새 시스템 템플릿이 추가될 것
- 동일 이름 템플릿은 id, useCount, createdAt을 유지한 채 갱신될 것

### 2.3 템플릿 노드 정의와 config를 현재 FE 계약에 맞게 구성

위치:
- `src/main/java/org/github/flowify/workflow/entity/NodeDefinition.java`

중요 포인트:
- 시작/도착 서비스 노드는 `category = service`
- `type`은 실제 서비스 키 사용
  - 예: `google_drive`, `slack`, `gmail`, `google_sheets`
- `role`은 `start`, `end`
- `config.service`도 동일하게 세팅
- 중간 AI 노드는 `category = ai`, `type = llm`
- 필요 시 반복 노드는 `category = control`, `type = loop`
- FE 프리뷰를 위해 `position`을 함께 세팅

특히 시작 노드 config는 FE가 실제로 아래 필드를 읽으므로, 이 값을 명시적으로 넣어야 합니다.

필수 start config 필드:
- `isConfigured`
- `service`
- `source_mode`
- `target`
- `target_label`
- `target_meta`

주의:
- FE는 `target_label`, `target_meta`를 source summary와 node data panel에서 그대로 사용합니다.
- 실행 엔진이 내부적으로 `runtime_source`를 별도로 만들더라도, FE에 저장되는 start node config에는 위 필드가 유지되어야 합니다.

권장 start node config 예시:

```json
{
  "isConfigured": false,
  "service": "google_drive",
  "source_mode": "folder_new_file",
  "target": "",
  "target_label": "",
  "target_meta": {
    "pickerType": "folder"
  }
}
```

sink node 기본 원칙:
- 사용자 입력이 필요한 sink는 기본 `isConfigured = false`
- Slack: `channel` 입력 전까지 false
- Gmail: `to`, `subject`, `action` 입력 전까지 false
- Google Sheets: `spreadsheet_id`, `write_mode` 입력 전까지 false

이 부분이 맞지 않으면 템플릿 상세 프리뷰와 가져오기 결과가 FE에서 어색하게 보이거나, 아직 미설정인 워크플로우가 실행 가능한 상태처럼 보일 수 있습니다.

### 2.3.1 Google Sheets OAuth / token 전략 명시

이번 카테고리의 3번째 템플릿은 `google_sheets` sink를 사용합니다.

현재 확인된 상태:
- Spring OAuth connector는 `google_drive`, `gmail`, `slack`, `notion`, `canvas_lms` 등은 있지만 `google_sheets` 전용 connector는 없음
- `ExecutionService.collectServiceTokens(...)`는 node `type` 그대로 토큰을 찾음
- 즉 `google_sheets` node가 있으면 현재 구조상 `oauthTokenService.getDecryptedToken(userId, "google_sheets")`를 찾게 됨

이 상태 그대로면 `google_sheets` sink 템플릿은 실행 시 `OAUTH_NOT_CONNECTED`로 실패할 가능성이 큽니다.

따라서 아래 중 하나를 1차 구현 전에 반드시 결정해야 합니다.

1. `google_sheets` 전용 OAuth connector 추가
- 서비스 키를 `google_sheets`로 저장
- Sheets 관련 scope를 별도로 관리

2. 기존 Google / Drive 토큰을 `google_sheets`에 재사용하는 alias 전략
- `collectServiceTokens()`에서 `google_sheets` 요청 시 `google_drive` 또는 공용 Google 토큰을 주입
- picker / target option lookup 등 Sheets 관련 경로도 같은 alias 규칙을 사용

권장:
- 1차는 별도 connector보다 alias 전략이 현실적입니다.
- 다만 alias 전략을 쓰려면 OAuth scope도 Sheets API 호출에 맞게 확장되어야 합니다.

추가로 명시되어야 할 점:
- 현재 `google_drive` OAuth scope만으로 Google Sheets `read_range`, `append_rows`, `write_range`가 충분한지 검증 필요
- 필요한 경우 아래 scope 추가 검토
  - `https://www.googleapis.com/auth/spreadsheets`
  - 또는 읽기 전용이면 `https://www.googleapis.com/auth/spreadsheets.readonly`

중요:
- 템플릿의 `requiredServices`를 계속 `google_sheets`로 둘지,
- 아니면 연결 UX 기준으로 `google_drive`와 묶어 보여줄지
백엔드/프론트 해석을 같이 맞춰야 합니다.

### 2.3.2 Gmail 발송 scope 요구사항

이번 카테고리의 2번째 템플릿은 `gmail` sink를 사용합니다.

현재 Spring 설정 기준 Gmail scope:
- `https://www.googleapis.com/auth/gmail.readonly`

문제:
- `신규 문서 요약 후 Gmail 전달` 템플릿은 Gmail sink에서 실제 발송 또는 draft 생성을 수행함
- 현재 `gmail.readonly`만으로는 `send_message`, `create_draft`가 충분하지 않음

필요 조치:
- `action=send`를 1차 기본으로 둘 경우 최소 `https://www.googleapis.com/auth/gmail.send` 필요
- `action=draft`도 공식 지원 대상으로 유지할 경우 `https://www.googleapis.com/auth/gmail.compose`까지 검토 필요

권장:
- 1차 템플릿은 `send` 기준으로 설계하고, Gmail scope를 `gmail.readonly + gmail.send` 또는 그에 준하는 안전한 조합으로 확장
- 만약 draft까지 공식 지원하려면 `gmail.compose` 범위까지 같이 확정

정리:
- `gmail` connector 신규 구현은 필요 없음
- 다만 이번 카테고리에서는 **Gmail 발송 scope 확장**이 명시적 요구사항으로 들어가야 함

### 2.4 Google Drive source runtime 보완

관련 위치:
- `app/core/nodes/input_node.py`
- `app/services/integrations/google_drive.py`

현재 runtime 기준 지원 source mode:
- `single_file`
- `file_changed`
- `new_file`
- `folder_new_file`
- `folder_all_files`

현재 문제:
- `single_file`, `new_file`, `folder_new_file`는 파일 본문을 다운로드해 `SINGLE_FILE`로 반환함
- `folder_all_files`는 현재 `FILE_LIST` 메타데이터만 반환하고, 문서 본문 `content`는 포함하지 않음

현재 `folder_all_files` item 예시:
- `filename`
- `mime_type`
- `size`
- `url`

즉 `folder_all_files`를 그대로 LLM에 연결하면, 문서 내용 요약이 아니라 파일 목록/메타데이터 요약에 가까워질 가능성이 큽니다.

### 2.4.1 1차 구현 기준 source mode 확정

이번 카테고리의 1차 시스템 템플릿 3종은 모두 `folder_new_file` 기준으로 맞추는 것을 권장합니다.

대상:
- 신규 문서 요약 후 Slack 공유
- 신규 문서 요약 후 Gmail 전달
- 문서 요약 결과를 Google Sheets에 저장

이유:
- 현재 runtime에서 `folder_new_file`은 실제 파일 본문을 내려줌
- `folder_all_files`는 본문 enrich 없이는 진짜 문서 요약 템플릿으로 보기 어려움
- FE 설명과 실행 품질을 빠르게 맞추기 쉬움

### 2.4.2 2차 확장 기준

`folder_all_files`를 진짜 폴더 전체 문서 요약용으로 쓰려면 아래 중 하나가 필요합니다.

1. `FILE_LIST.items[*]`에 본문 enrich
2. loop 또는 helper step에서 각 file id 기준 추가 다운로드

권장 item shape 예시:

```json
{
  "file_id": "drive-file-id",
  "filename": "회의록.txt",
  "mime_type": "text/plain",
  "size": 1024,
  "content": "문서 본문",
  "url": "https://drive.google.com/file/d/..."
}
```

### 2.4.3 파일 형식 제약 명시

`google_drive.download_file()` 기준으로 현재 처리는 다음과 같습니다.

- Google Docs -> `text/plain` export
- Google Sheets -> `text/csv` export
- Google Slides -> `text/plain` export
- 그 외 파일 -> `alt=media`

즉 PDF, 이미지, 바이너리 파일은 내용 추출 품질이 흔들릴 수 있습니다.

문서에 반드시 반영되어야 할 정책:
- 1차 지원 MIME 타입
- 비텍스트 파일 처리 방식
  - skip
  - placeholder 요약
  - 에러 반환
중 어떤 정책으로 갈지 명시

### 2.5 템플릿별 권장 source / sink 구성

#### 1. 신규 문서 요약 후 Slack 공유

- source: `google_drive`
- source mode: `folder_new_file`
- canonical input: `SINGLE_FILE`
- sink: `slack`
- sink 필수 config: `channel`
- 기본 `isConfigured`: `false`

권장 graph:
- `google_drive -> llm -> slack`

권장 sink config 예시:

```json
{
  "isConfigured": false,
  "service": "slack",
  "channel": "",
  "message_format": "markdown",
  "header": "문서 요약"
}
```

#### 2. 신규 문서 요약 후 Gmail 전달

- source: `google_drive`
- source mode: `folder_new_file`
- canonical input: `SINGLE_FILE`
- sink: `gmail`
- sink 필수 config: `to`, `subject`, `action`
- 기본 `isConfigured`: `false`

권장 graph:
- `google_drive -> llm -> gmail`

권장 sink config 예시:

```json
{
  "isConfigured": false,
  "service": "gmail",
  "to": "",
  "subject": "문서 요약",
  "action": "send"
}
```

#### 3. 문서 요약 결과를 Google Sheets에 저장

- source: `google_drive`
- source mode: `folder_new_file`
- canonical input: `SINGLE_FILE`
- sink: `google_sheets`
- sink 필수 config: `spreadsheet_id`, `write_mode`
- 기본 `isConfigured`: `false`

권장 graph:
- `google_drive -> llm -> google_sheets`

권장 sink config 예시:

```json
{
  "isConfigured": false,
  "service": "google_sheets",
  "spreadsheet_id": "",
  "write_mode": "append",
  "sheet_name": "Sheet1"
}
```

### 2.6 FastAPI 프롬프트 구체화

Drive 기반 템플릿은 `문서 내용 요약`이 핵심이므로, LLM prompt도 파일명/메타데이터 수준이 아니라 문서 내용 중심으로 작성되어야 합니다.

필요 작업:
- `SINGLE_FILE` 입력 기준 문서 요약 prompt 정리
- sink별 재사용이 가능한 출력 형식 정리

권장 출력 항목 예시:
- `document_name`
- `summary`
- `highlights`
- `share_message`

예시 prompt 방향:
- 입력된 문서의 핵심 내용을 3~5개 bullet로 요약한다
- 문서 제목과 주요 결론을 분리한다
- Slack / Gmail / Google Sheets에 그대로 붙일 수 있도록 불필요한 서론/결론 없이 정리한다
- 문서 본문이 비어 있거나 비텍스트 파일이면 그 사실을 명시한다

### 2.7 Google Sheets sink 결과 포맷 확정

`문서 요약 결과를 Google Sheets에 저장` 템플릿은 1차 기준으로 `SPREADSHEET_DATA`를 목표 형식으로 명시하는 것이 좋습니다.

이유:
- 현재 sink는 `TEXT`도 받을 수 있지만, 그러면 시트 한 칸 append 수준으로 끝남
- FE 설명은 `요약 결과를 Google Sheets에 기록`이므로 컬럼형 구조가 더 자연스러움

1차 권장 컬럼 예시:
- `document_name`
- `summary`
- `highlights`
- `source_url`

권장 payload 예시:

```json
{
  "type": "SPREADSHEET_DATA",
  "headers": ["document_name", "summary", "highlights", "source_url"],
  "rows": [
    [
      "회의록.txt",
      "핵심 요약",
      "- 포인트 1\n- 포인트 2",
      "https://drive.google.com/file/d/..."
    ]
  ]
}
```

따라서 이 템플릿은 아래 중 하나가 필요합니다.
- LLM이 직접 `SPREADSHEET_DATA` 형태를 만들기
- output 직전 helper step이 `TEXT -> SPREADSHEET_DATA`로 변환하기

### 2.8 FE 공개 응답 계약 유지

FE는 현재 템플릿 상세/가져오기 응답에서 기존 graph 구조를 그대로 기대하고 있습니다.

따라서 1차 구현에서는 public response를 아래와 같이 단순하게 유지하는 것이 안전합니다.

- `google_drive -> llm -> slack`
- `google_drive -> llm -> gmail`
- `google_drive -> llm -> google_sheets`

주의:
- 새로운 내부 helper step이 runtime에 추가되더라도, public template response에는 바로 노출하지 않는 편이 안전합니다.
- FE가 아직 알지 못하는 data type이나 node type을 노출하면, 목록/상세 프리뷰와 가져오기 이후 에디터에서 추가 대응이 필요합니다.

---

## 3. FE가 기대하는 템플릿 응답 형태

### 3.1 템플릿 목록 응답 (`GET /api/templates`)

필수 필드:
- `id`
- `name`
- `description`
- `category`
- `icon`
- `requiredServices`
- `isSystem`
- `authorId`
- `useCount`
- `createdAt`

### 3.2 템플릿 상세 응답 (`GET /api/templates/{id}`)

추가 필드:
- `nodes`
- `edges`

중요:
- FE는 `requiredServices`와 `category`를 사람이 읽기 쉬운 문구로 매핑해서 보여줍니다.
- `nodes`, `edges`는 템플릿 상세 프리뷰와 가져오기 이후 워크플로우 초기 구성을 위해 필요합니다.

---

## 4. 테스트 권장 사항

위치:
- `src/test/java/org/github/flowify/template/TemplateServiceTest.java`
- 관련 FastAPI 테스트 파일

확인 포인트:
- `folder_document_summary` 카테고리 조회 가능 여부
- 템플릿 3종 상세 응답 shape 검증
- 기존 DB에도 새 템플릿이 증분 시드되는지
- `folder_new_file`가 템플릿 의도와 맞는 payload를 만드는지
- 지원 MIME 타입과 비지원 파일 처리 정책이 문서와 맞는지
- Slack / Gmail / Google Sheets sink로 연결되는 기본 흐름이 깨지지 않는지
- Google Sheets 템플릿이 실제로 `SPREADSHEET_DATA` 기준으로 저장되는지

### 4.1 2026-05-04 실데이터 검증 메모

- `신규 문서 요약 후 Slack 공유`는 실데이터 실행 기준으로 성공했다.
- `신규 문서 요약 후 Gmail 전달`은 현재 저장된 Gmail OAuth 토큰에 `gmail.send`가 없어 실패했다.
- `문서 요약 결과를 Google Sheets에 저장`은 현재 저장된 Google Drive OAuth 토큰에 `spreadsheets` scope가 없어 실패했다.
- 따라서 Gmail / Google Sheets 관련 최종 실데이터 검증은 테스트 계정이 새 scope로 Google 재연결을 완료한 뒤 다시 수행해야 한다.

---

## 5. 정리

이번 요청의 핵심은 다음과 같습니다.

1. `folder_document_summary` 카테고리 시스템 템플릿 3종 추가
2. 기존 증분 시드 방식에 맞게 안정 반영
3. 시작 노드 config와 sink config를 FE 계약에 맞게 구체적으로 세팅
4. 1차 source mode는 `folder_new_file`로 통일
5. `folder_all_files`는 본문 enrich 이후 2차 확장 대상으로 분리
6. Google Sheets용 OAuth / token 전략과 scope 정책을 먼저 확정
7. 파일 형식 제약과 fallback 정책을 문서화
8. Google Sheets 템플릿은 `SPREADSHEET_DATA` 포맷 기준으로 구현
9. 문서 내용 중심 LLM prompt로 품질 보완

