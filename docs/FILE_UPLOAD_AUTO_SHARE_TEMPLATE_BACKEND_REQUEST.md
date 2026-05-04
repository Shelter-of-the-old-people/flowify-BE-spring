# 파일 업로드 자동 공유 템플릿 백엔드 요청서

> **작성일:** 2026-05-04
> **대상:** Spring, FastAPI 백엔드 담당자
> **용도:** 임시 전달용 문서
> **관련 이슈:** 파일 업로드 자동 공유 템플릿 구현

---

## 1. 요청 배경

FE에서는 `파일 업로드 자동 공유` 카테고리를 시스템 템플릿군으로 구체화하고 있습니다.
다만 실제 시스템 템플릿 데이터와 실행 품질은 백엔드가 결정하므로, 아래 항목이 같이 정리되어야 템플릿 3종이 실사용 가능한 수준으로 보입니다.

현재 핵심 이슈는 다음과 같습니다.

- Spring 시더에 `파일 업로드 자동 공유` 카테고리 시스템 템플릿이 아직 없음
- Google Drive source는 runtime에 연결되어 있지만, 현재 `새 파일 업로드` semantics가 실제 event trigger보다는 `최신 파일 1건 조회`에 가까움
- `new_file`과 `folder_new_file`의 의미가 runtime상 충분히 분리되어 있지 않음
- Slack / Gmail / Notion sink는 runtime에 존재하지만, 템플릿 seed 품질과 FE 기대 계약 정렬이 필요함
- Notion 기록 템플릿은 현재 구현상 `부모 페이지 아래 새 페이지 생성` 수준이므로, title / target_type / picker 제약을 문서화해야 함
- FE는 기존 Google Drive folder picker를 그대로 재사용할 예정이라 source mode / target schema / runtime_source 구성이 일관되어야 함

이번 문서는 `파일 업로드 자동 공유` 카테고리 템플릿 3종의 시드, Google Drive 새 파일 감지 보완, sink별 결과 semantics, FE 공개 계약 정렬에 집중합니다.

---

## 2. 요청 사항

### 2.1 파일 업로드 자동 공유 템플릿 3종 시스템 시드 추가

위치:
- `src/main/java/org/github/flowify/config/TemplateSeeder.java`

1차 대상 템플릿:
- 업로드 문서 요약 후 Slack 공유
- 새 파일 업로드 알림 메일 발송
- 새 파일 업로드 후 Notion 기록

공통 기준:
- `category`: `file_upload_auto_share`
- `isSystem`: `true`
- `requiredServices`: 실제 서비스 키 사용
  - 예: `google_drive`, `slack`, `gmail`, `notion`
- `icon`: `google_drive` 권장

중요:
- 템플릿 이름/설명은 1차 runtime semantics에 맞춰 `선택한 폴더의 새 파일을 확인해 공유/기록` 수준으로 정리해야 합니다.
- `실시간 업로드 감지`, `원본 파일 자동 전달`처럼 1차 구현보다 강한 표현은 피해야 합니다.

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
  - 예: `google_drive`, `slack`, `gmail`, `notion`
- `role`은 `start`, `end`
- `config.service`도 동일하게 세팅
- 중간 AI 노드는 `category = ai`, `type = llm`
- FE 프리뷰를 위해 `position`을 함께 세팅

특히 시작 노드 config는 FE가 실제로 아래 필드를 읽으므로, 이 값을 명시적으로 넣어야 합니다.

필수 start config 필드:
- `isConfigured`
- `service`
- `source_mode`
- `target`
- `target_label`
- `target_meta`

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
- Notion: `target_type`, `target_id` 입력 전까지 false

### 2.3.1 Slack / Notion picker 공통 백엔드 의존성

이 카테고리도 sink 선택 UX 관점에서는 기존 요청과 같은 공통 picker 백엔드 작업에 의존합니다.

필요 API 방향:
- Slack channel picker용 목록 API
- Notion page picker용 목록 API

권장 endpoint 예시:
- `GET /api/editor-catalog/sinks/slack/target-options`
- `GET /api/editor-catalog/sinks/notion/target-options`

주의:
- 이 카테고리 자체를 위해 새로운 connector를 추가하자는 뜻은 아님
- 이미 연결된 Slack / Notion 토큰을 사용해 목록을 제공하는 공통 작업을 재사용하면 됨

### 2.3.2 OAuth / scope / token 관점에서 필요한 것과 불필요한 것

이번 카테고리 1차 구현에 필요한 서비스:
- `google_drive`
- `slack`
- `gmail`
- `notion`

현재 기준 판단:
- 신규 OAuth connector 구현이 꼭 필요한 것은 아님
- 다만 `기존 connector 재사용만 하면 끝`은 아니고, 서비스별 scope / token 전략 차이를 문서에 명확히 적어야 함
- 따라서 이번 카테고리에서 중요한 것은 `새 OAuth connector 추가`보다 `기존 연결 흐름 재사용 + 필요한 scope 보강 + picker 범위 명시`입니다

#### Google Drive

현재 Spring 설정 기준 Google Drive scope:
- `https://www.googleapis.com/auth/drive.readonly`
- `https://www.googleapis.com/auth/drive.file`

판단:
- 이번 카테고리 1차의 `새 파일 읽기 / 본문 다운로드` 자체에는 추가 scope가 꼭 필요하다고 보긴 어려움
- 따라서 Google Drive는 1차 기준으로 기존 connector / scope 재사용 가능

#### Gmail

현재 Spring 설정 기준 Gmail scope:
- `https://www.googleapis.com/auth/gmail.readonly`

문제:
- 이번 카테고리의 `새 파일 업로드 알림 메일 발송` 템플릿은 Gmail sink에서 실제 발송 또는 draft 생성을 수행함
- 현재 `gmail.readonly`만으로는 `send_message`, `create_draft`가 충분하지 않음

필요 조치:
- `action=send`를 1차 기본으로 둘 경우 최소 `https://www.googleapis.com/auth/gmail.send` 필요
- `action=draft`도 계속 지원할 경우 `https://www.googleapis.com/auth/gmail.compose`까지 검토 필요

권장:
- 1차 템플릿은 `send` 기준으로 설계하고, Gmail scope를 `gmail.readonly + gmail.send` 또는 그에 준하는 안전한 조합으로 확장
- 만약 draft까지 공식 지원하려면 `gmail.compose` 범위까지 같이 확정

즉 이번 카테고리에서는 **Gmail connector 신규 구현은 필요 없지만, Gmail scope 확장은 사실상 필수 요구사항**입니다.

운영 메모:
- 기존에 `gmail.readonly`만으로 연결된 사용자 토큰은 scope 확장 후 자동으로 권한이 늘어나지 않음
- 따라서 `gmail.send`를 사용하는 템플릿 검증 전에는 테스트 계정 기준 Gmail 재연결이 필요함
- 동일하게 향후 Google Drive / Google Sheets scope가 확장되면 기존 토큰도 재동의가 필요할 수 있음

#### Slack

현재 Spring 설정 기준 Slack scope:
- `channels:read`
- `chat:write`
- `users:read`

판단:
- 1차 템플릿의 `메시지 전송`에는 현재 scope로 충분함
- `channel picker`로 공개 채널 목록을 보여주는 것도 1차 기준으로는 가능성이 높음
- 다만 private channel, DM, group conversation까지 picker 범위를 넓히려면 추가 scope가 필요할 수 있음

권장:
- 1차 picker는 **공개 채널 기준**으로 범위를 명시
- private channel / DM 지원은 2차 확장으로 분리
- 필요 시 이후 `groups:read` 등 추가 scope를 별도 검토

#### Notion

현재 Spring 구현 기준 Notion은 일반 OAuth callback형 connector가 아니라 `NOTION_INTEGRATION_TOKEN` 기반 token 저장 전략을 사용함

판단:
- 이번 카테고리 1차 구현에 별도 신규 connector는 필요 없음
- 다만 아래 제약을 문서에 명시해야 함
  - page picker는 **현재 integration token이 접근 가능한 페이지**만 보여줄 수 있음
  - 실제 기록도 **같은 token / 같은 integration 권한 범위** 안에서만 성공함

권장:
- picker 조회와 저장이 **반드시 동일한 token 주체**를 사용하도록 정리
- FE 안내 기준도 `공유된 페이지 / 접근 가능한 페이지만 선택 가능`으로 맞춤

정리:
- `google_drive`, `slack`, `gmail`, `notion`은 기존 연결 흐름 재사용
- 별도 신규 OAuth connector 요구는 없음
- 다만 이번 카테고리에서는 아래 3가지는 명시적 요구사항으로 봐야 함
  1. Gmail send/draft를 위한 scope 확장
  2. Slack picker의 1차 범위를 공개 채널로 제한할지 여부 명시
  3. Notion picker / 저장이 동일 token 및 공유 범위 안에서 동작하도록 보장
- Slack / Notion picker API는 공통 backend 작업으로 필요

### 2.4 Google Drive 새 파일 감지 runtime 보완

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
- `new_file`와 `folder_new_file` 모두 내부적으로 `list_files(..., max_results=1)` 호출에 의존함
- `list_files()`에는 현재 `orderBy`가 없음
- `last_seen_file_id`, `createdTime`, `modifiedTime` 기반 checkpoint가 없음

즉 현재 `새 파일 업로드 감지`는 엄밀한 event trigger라기보다 `폴더에서 첫 번째로 조회된 파일 1건을 가져오는 동작`에 가까울 수 있습니다.

### 2.4.1 1차 구현 기준 source mode 확정

이번 카테고리의 1차 시스템 템플릿 3종은 모두 `folder_new_file` 기준으로 맞추는 것을 권장합니다.

대상:
- 업로드 문서 요약 후 Slack 공유
- 새 파일 업로드 알림 메일 발송
- 새 파일 업로드 후 Notion 기록

이유:
- FE UX가 Google Drive folder picker와 가장 잘 맞음
- `new_file`보다 `folder_new_file`가 의미가 명확함
- 1차 시스템 템플릿 설명과 target selection이 깔끔해짐

### 2.4.2 1차 필수 보완 사항

`folder_new_file`를 실제 `새 파일 업로드 자동 공유` 템플릿으로 쓰려면 최소한 아래가 필요합니다.

1. `list_files()` 정렬 기준 명시
- 권장: `createdTime desc` 또는 `modifiedTime desc`

2. workflow/source node 단위 checkpoint 도입
- `last_seen_file_id`
- 또는 `last_seen_created_time`

3. checkpoint보다 새로운 파일이 없으면 no-op 처리
- 새 결과를 만들지 않음
- 중복 알림 / 중복 기록 방지

권장 item shape 예시:

```json
{
  "type": "SINGLE_FILE",
  "file_id": "drive-file-id",
  "filename": "업무자료.txt",
  "content": "문서 본문",
  "mime_type": "text/plain",
  "url": "https://drive.google.com/file/d/...",
  "created_time": "2026-05-04T10:00:00Z"
}
```

### 2.4.3 `new_file` 모드 취급 방향

이번 카테고리 1차 기준에서는 `new_file` 모드는 사용하지 않는 편이 안전합니다.

이유:
- source catalog 상으로도 `new_file`과 `folder_new_file` 모두 folder picker 성격이 섞여 있음
- 템플릿 설명과 FE target UX를 맞출 때 `folder_new_file`가 더 명확함

정리:
- 1차 시드에서는 `new_file` 미사용
- `folder_new_file`만 공식 템플릿 기준으로 사용

### 2.5 파일 형식 제약과 fallback 정책 명시

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
  - placeholder 알림
  - 에러 반환
중 어떤 정책으로 갈지 명시

권장:
- 1차는 `text/plain`, `text/csv`, Google Docs 계열 위주 우선 지원
- 비텍스트 파일은 `파일 본문을 직접 추출하지 못해 메타데이터 중심으로 공유`로 fallback

### 2.6 템플릿별 sink semantics 확정

#### 1. 업로드 문서 요약 후 Slack 공유

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
  "header": "파일 업로드 공유"
}
```

#### 2. 새 파일 업로드 알림 메일 발송

- source: `google_drive`
- source mode: `folder_new_file`
- canonical input: `SINGLE_FILE`
- sink: `gmail`
- sink 필수 config: `to`, `subject`, `action`
- 기본 `isConfigured`: `false`

권장 graph:
- `google_drive -> llm -> gmail`

중요:
- 1차는 `원본 파일 첨부 전달`보다 `요약/알림 메일 1건 발송`을 기준으로 잡는 것이 안전함
- 메일 본문에는 `filename`, `summary`, `source_url`이 들어가는 형태를 권장

권장 sink config 예시:

```json
{
  "isConfigured": false,
  "service": "gmail",
  "to": "",
  "subject": "새 파일 업로드 알림",
  "action": "send"
}
```

#### 3. 새 파일 업로드 후 Notion 기록

- source: `google_drive`
- source mode: `folder_new_file`
- canonical input: `SINGLE_FILE`
- sink: `notion`
- sink 필수 config: `target_type`, `target_id`
- 기본 `isConfigured`: `false`

권장 graph:
- `google_drive -> llm -> notion`

중요:
- 1차는 `target_type=page` 기준이 안전함
- 저장 방식은 `선택한 부모 페이지 아래에 새 페이지 생성`
- `database`는 2차 확장으로 두는 편이 안전함
- 현재 FastAPI runtime은 고정 제목(`Flowify Output`)을 쓰므로, 템플릿에서 의미 있는 기록을 만들려면 `title_template` 지원이 필요함

권장 sink config 예시:

```json
{
  "isConfigured": false,
  "service": "notion",
  "target_type": "page",
  "target_id": "",
  "title_template": "업로드 파일 기록 - {{filename}}"
}
```

### 2.7 Notion title_template 실제 반영 필요

관련 위치:
- `app/core/nodes/output_node.py`
- `app/services/integrations/notion.py`

현재 문제:
- Notion sink는 현재 `Flowify Output`, `Flowify Data` 같은 고정 제목으로 page를 생성함
- seed에 `title_template`를 넣어도 실제 runtime에는 반영되지 않음

이번 카테고리에서는 파일명 기반 기록 제목이 중요하므로, 아래 수준의 반영이 필요합니다.

예시:
- `업로드 파일 기록 - {{filename}}`
- `새 파일 알림 - {{filename}}`

권장:
- `title_template`에 `filename`, `date` 정도의 기본 치환을 지원

### 2.8 FastAPI 프롬프트 구체화

Drive 기반 공유 템플릿은 `파일 내용을 짧고 명확하게 공유/기록`하는 것이 핵심이므로, sink별 결과 형식도 구체적으로 정리되어야 합니다.

필요 작업:
- `SINGLE_FILE` 입력 기준 파일 공유용 prompt 정리
- sink별 재사용이 가능한 출력 필드 정리

권장 중간 결과 항목 예시:
- `filename`
- `summary`
- `highlights`
- `source_url`
- `share_message`

sink별 권장 출력 방향:

- Slack
  - 짧은 markdown 공유 메시지
  - 파일명 + 핵심 내용 + 링크 중심

- Gmail
  - 제목/본문이 분리된 알림 메일용 텍스트
  - 파일명 + 핵심 내용 + 링크 중심

- Notion
  - 기록용 본문 텍스트
  - 파일명 / 요약 / 주요 포인트 / 링크가 드러나는 구조

예시 prompt 방향:
- 입력된 파일 내용을 바탕으로 팀에 공유할 수 있는 짧은 요약을 만든다
- 파일명이 있으면 결과에 반드시 포함한다
- 본문이 비어 있거나 비텍스트 파일이면 그 사실을 명시한다
- 불필요한 서론/결론 없이 바로 공유 가능한 결과를 출력한다

### 2.9 FE 공개 응답 계약 유지

FE는 현재 템플릿 상세/가져오기 응답에서 기존 graph 구조를 그대로 기대하고 있습니다.

따라서 1차 구현에서는 public response를 아래와 같이 단순하게 유지하는 것이 안전합니다.

- `google_drive -> llm -> slack`
- `google_drive -> llm -> gmail`
- `google_drive -> llm -> notion`

주의:
- checkpoint helper, file classifier, formatter 등 내부 step이 runtime에 추가되더라도 public template response에는 바로 노출하지 않는 편이 안전합니다.
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
- `file_upload_auto_share` 카테고리 조회 가능 여부
- 템플릿 3종 상세 응답 shape 검증
- 기존 DB에도 새 템플릿이 증분 시드되는지
- `folder_new_file`가 동일 파일 중복 감지 없이 동작하는지
- 새 파일이 없을 때 no-op 처리되는지
- 지원 MIME 타입과 비지원 파일 처리 정책이 문서와 맞는지
- Slack / Gmail / Notion sink로 연결되는 기본 흐름이 깨지지 않는지
- Notion 템플릿이 `title_template` 기준으로 페이지 제목을 생성하는지

### 4.1 2026-05-04 구현 메모

현재 브랜치 기준으로 반영된 항목:

- Spring 시더에 `file_upload_auto_share` 템플릿 3종 추가
- Slack / Notion sink picker용 공통 backend API 추가
- Gmail scope에 `gmail.send` 추가
- FastAPI `folder_new_file` 정렬 기준을 `createdTime desc`로 보강
- FastAPI에서 `title_template`를 실제 Notion page title에 반영
- FastAPI에서 `filename`, `mime_type`, `source_url` 같은 파일 메타데이터를 LLM 출력 이후 sink까지 유지

현재 브랜치 기준으로 남아 있는 후속 작업:

- workflow/source node 단위 `checkpoint` 도입
- `last_seen_file_id` 또는 동등한 상태 기반 `no-op` 처리
- 새 파일이 없을 때 중복 공유/중복 기록을 막는 실행 전략 고도화
- 운영 환경에서 확장된 Google scope를 실제로 받기 위한 계정 재연결 및 재검증

---

## 5. 정리

이번 요청의 핵심은 다음과 같습니다.

1. `file_upload_auto_share` 카테고리 시스템 템플릿 3종 추가
2. 기존 증분 시드 방식에 맞게 안정 반영
3. 시작 노드 config와 sink config를 FE 계약에 맞게 구체적으로 세팅
4. 1차 source mode는 `folder_new_file`로 통일
5. `new_file`는 1차 시드에서 사용하지 않음
6. `folder_new_file`가 실제 새 파일 감지처럼 동작하도록 정렬 + checkpoint + no-op 처리 보완
7. 신규 OAuth connector 요구 없이 기존 `google_drive`, `slack`, `gmail`, `notion` 연결 흐름 재사용
8. Slack / Notion picker용 목록 API는 공통 backend 작업으로 재사용
9. Notion 기록 템플릿은 `title_template` 실제 반영이 필요
10. 공유/알림/기록 목적에 맞는 sink별 prompt와 결과 형식을 구체화

