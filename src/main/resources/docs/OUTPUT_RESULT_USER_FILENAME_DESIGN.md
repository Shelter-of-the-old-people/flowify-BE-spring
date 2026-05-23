# 출력 결과 사용자 커스터마이징 설계

## 1. 배경

이번 요구사항은 처음에는 **출력 결과의 파일명 또는 결과명을 사용자가 직접 지정할 수 있게 하는 것**으로 출발했다.

다만 요구사항을 다시 검토해보면, 사용자가 기대하는 "출력 결과 커스텀"은 파일명 하나에만 머물지 않는다. 실제 제품 관점에서는 아래 요소들이 함께 묶여 보인다.

- 어디에 저장할지
- 어떤 이름으로 저장할지
- 어떤 파일 형식으로 저장할지
- 메일이나 메시지로 보낼 때 제목과 감싸는 문구를 어떻게 할지
- 표 형태 결과를 어떤 시트, 범위, 저장 방식으로 기록할지
- 페이지나 문서형 결과의 제목을 어떻게 만들지

따라서 이 문서는 파일명 커스텀을 1차 목표로 유지하되, **출력 결과에서 사용자가 커스텀할 수 있는 축 전체를 정리하고, 어떤 항목을 이번 이슈에 포함할지**까지 함께 정의한다.

기본 원칙은 그대로 둔다.

- 사용자가 커스텀 값을 입력하지 않으면 지금 backend가 생성하던 기본 출력명을 그대로 사용한다.
- 기존 workflow config에 새 필드가 없어도 이전과 동일하게 실행되어야 한다.
- FE가 최종 파일명, 제목, 파일 형식을 독자적으로 계산하지 않고 backend runtime이 authoritative하게 처리한다.

## 2. 현재 구조 확인

### 2.1 FE sink 설정 UI는 schema 기반이다

`src/features/configure-node/ui/panels/SinkNodePanel.tsx`의 `SinkSchemaEditor`는 `sinkSchema.fields`를 순회하며 field type에 맞는 input을 렌더링한다.

이미 지원되는 field type은 다음과 같다.

- `text`
- `textarea`
- `number`
- `select`
- `email_input`
- `secret_text`
- `folder_picker`
- `channel_picker`
- `page_picker`
- `sheet_picker`

따라서 backend가 `filename_template`, `file_format`, `title_template` 같은 field를 sink schema에 내려주면 FE는 구조적으로 해당 필드를 렌더링할 수 있다.

### 2.2 빈 값은 config에 저장하지 않는다

`SinkNodePanel.tsx`의 `normalizeDraftValue()`와 `buildCommittedConfigFromDraft()`는 입력값을 trim 했을 때 빈 문자열이면 해당 field를 config에 넣지 않는다.

`src/features/configure-node/model/sink-node-draft.ts`의 `normalizeSinkDraftValue()`와 `buildSinkNodeConfigDraft()`도 같은 방식이다.

즉 optional field라면 사용자가 비워둔 경우 config에 저장되지 않고, backend가 기존 기본값 생성 로직을 그대로 사용할 수 있다.

### 2.3 Spring sink catalog에는 이미 관련 필드가 있다

`flowify-BE-spring/src/main/resources/catalog/sink_catalog.json` 기준으로, 파일명 외에도 이미 여러 커스텀 필드가 노출되어 있다.

| Sink | 현재 노출된 커스텀 필드 | 의미 |
| --- | --- | --- |
| Discord | `message_template`, `username`, `avatar_url` | 메시지 감싸는 문구, 표시 이름, 아이콘 |
| Gmail | `subject`, `body_format`, `text_delivery_mode` | 메일 제목, 본문 형식, 텍스트 결과 전달 방식 |
| Notion | `title_template` | 생성 페이지 제목 |
| Google Drive | `folder_id`, `filename_template`, `file_format` | 저장 폴더, 파일명 규칙, 파일 형식 |
| Google Sheets | `spreadsheet_id`, `sheet_name`, `range_a1`, `key_column`, `write_mode` | 저장 위치, 범위, 기준 컬럼, 저장 방식 |
| Google Calendar | `event_title_template`, `duration_minutes`, `action` | 일정 제목, 기본 길이, 동작 |

따라서 이번 작업의 본질은 단순히 새 schema field를 추가하는 것이 아니라, **이미 있거나 추가될 커스텀 필드가 FE 표시, Spring runtime payload, FastAPI 실행 동작에서 같은 의미로 연결되는지 맞추는 것**이다.

### 2.4 FastAPI runtime은 일부 커스텀만 반영 중이다

FastAPI `OutputNodeStrategy` 기준으로 현재 확인된 상태는 다음과 같다.

- Notion은 `title_template`을 읽어 제목을 생성한다.
- Gmail은 `subject`, `body_format`, `text_delivery_mode`를 일부 실행 경로에서 사용한다.
- Google Sheets는 `write_mode`, `range_a1`, `key_column` 등을 사용한다.
- Google Drive `TEXT` 출력은 `file_format`을 읽지만 파일명은 `output.{file_format}`으로 고정한다.
- Google Drive `SINGLE_FILE`, `FILE_LIST` 출력은 입력 payload의 원본 filename을 주로 사용한다.
- Google Drive `SPREADSHEET_DATA` 출력은 `config.filename` 또는 `{sheet_name}.csv`를 사용하고, catalog의 `filename_template`과 key가 맞지 않는다.

즉 Google Drive의 `filename_template`은 Spring catalog에는 있지만 FastAPI runtime 적용이 일관적이지 않다. 이 부분이 이번 이슈의 핵심 정렬 지점이다.

### 2.5 상태 표시 label은 일부 준비되어 있다

`src/entities/workflow/lib/node-status.ts`에는 이미 다음 label이 있다.

- `filename_template`: `파일명 규칙`
- `file_format`: `파일 형식`
- `title_template`: `제목 템플릿`
- `body_format`: `본문 포맷`
- `messageTemplate`: `메시지 내용`

다만 사용자에게 더 자연스러운 copy로 맞추려면 `filename_template`은 `출력 파일명`, `title_template`은 `출력 제목`처럼 정리할 수 있다.

## 3. 파일명 외 추가 커스터마이징 후보

### 3.1 저장 위치

예: `folder_id`, `target_id`, `spreadsheet_id`, `sheet_name`, `channel`, `calendar_id`

이미 대부분의 sink에서 필수 설정으로 다뤄지고 있다. 이번 이슈에서 새로 만들 대상은 아니다.

### 3.2 결과 이름

예: `filename_template`, `title_template`, `event_title_template`, Gmail `subject`

사용자 관점에서 가장 직접적인 커스텀이다.

이번 이슈의 1차 핵심은 Google Drive의 `filename_template`을 runtime까지 확실히 반영하는 것이다. Notion `title_template`과 Gmail `subject`는 이미 schema와 runtime 경로가 있으므로, FE copy 정리와 테스트 대상으로만 보면 된다.

### 3.3 파일 형식

예: Google Drive `file_format`

Spring catalog에는 `pdf`, `docx`, `txt`, `original` 옵션이 있다. FastAPI는 TEXT 출력에서 `file_format`을 읽지만 실제 변환 품질과 mime type 정책은 더 확인이 필요하다.

파일 형식은 파일명과 강하게 연결된다.

- 사용자가 `filename_template`에 확장자를 포함할 수 있는가?
- `file_format=pdf`일 때 파일명 확장자를 backend가 `.pdf`로 보정할 것인가?
- `file_format=original`일 때 원본 파일명과 사용자의 template 중 무엇을 우선할 것인가?

따라서 파일명 작업을 하면서 최소한의 확장자 정책은 같이 확정해야 한다.

### 3.4 전달 방식

예: Gmail `text_delivery_mode`, `body_format`, `action`

Gmail은 텍스트 결과를 본문으로 보낼지, 첨부파일로 보낼지 선택할 수 있다. `text_delivery_mode=attachment`일 때는 첨부파일명도 사용자 입장에서 파일명 커스텀 범위로 보일 수 있다.

다만 현재 FE schema renderer는 "특정 select 값일 때만 다른 field 노출" 같은 조건부 field visibility를 일반화해서 지원하지 않는다. Gmail 첨부파일명까지 1차에 넣으려면 visibility 계약을 새로 만들어야 하므로 이번 범위에서는 후순위로 둔다.

### 3.5 메시지 또는 본문 wrapper

예: Discord `message_template`, Gmail `body`

Discord는 `message_template`을 통해 결과 앞뒤 문구를 커스텀할 수 있다. Gmail도 runtime에는 `body` fallback 경로가 있지만 현재 Spring catalog에는 명시 field로 보이지 않는다.

이 영역은 "결과 내용 자체를 바꾸는 것"과 "결과를 감싸는 문구를 바꾸는 것"이 섞이기 쉽다. 이번 이슈에서는 파일명/제목/형식 중심으로 두고, 본문 템플릿 고도화는 별도 이슈로 분리하는 것이 좋다.

### 3.6 표 기록 방식

예: Google Sheets `write_mode`, `range_a1`, `key_column`

이미 Google Sheets sink의 핵심 커스텀으로 구현되어 있다. 파일명 기능과 직접 충돌하지 않는다.

### 3.7 중복 파일명 처리 방식

예: 덮어쓰기, suffix 추가, timestamp 추가

이건 사용자가 눈치채는 결과 커스텀에 가깝지만, 외부 저장소별 정책과 안정성이 중요하다. 1차에서는 사용자 설정으로 열기보다 backend 기본 정책으로 고정하는 것을 권장한다.

문서상 권장 기본값:

- 기존 파일을 덮어쓰지 않는다.
- 동일 이름이 있으면 backend가 suffix 또는 timestamp를 붙인다.
- 중복 처리 결과는 execution result나 preview metadata에 포함한다.

## 4. 이번 이슈 범위 제안

### 4.1 1차 포함

이번 이슈에서는 아래까지만 포함하는 것을 권장한다.

- Google Drive `filename_template`을 사용자 입력으로 확실히 저장하고 runtime에 반영
- Google Drive `file_format`과 파일명 확장자 정책 정리
- Notion `title_template`, Gmail `subject`, Discord `message_template` 등 기존 커스텀 필드는 문서상 현황으로 정리
- FE는 `filename_template`, `file_format`, `title_template` 표시 copy와 테스트 보강
- 기본값 미입력 시 기존 동작 유지

### 4.2 1차 제외

아래는 별도 이슈로 분리하는 것이 좋다.

- Gmail 첨부파일명 조건부 커스텀
- 본문 template editor 고도화
- PDF/DOCX 변환 품질 개선
- 중복 파일명 처리 방식을 사용자가 직접 선택하는 UI
- FILE_LIST의 각 파일마다 다른 이름을 지정하는 고급 template
- template 변수 자동완성 UI

## 5. Backend 계약 제안

### 5.1 Config field

Google Drive 파일명 커스텀은 이미 catalog에 있는 `runtime_sink.config.filename_template`을 authoritative key로 사용한다.

```json
{
  "runtime_sink": {
    "service": "google_drive",
    "config": {
      "folder_id": "drive-folder-id",
      "filename_template": "{{workflowName}}_{{date}}",
      "file_format": "txt"
    }
  }
}
```

제목 기반 결과물은 `title_template`을 유지한다.

```json
{
  "runtime_sink": {
    "service": "notion",
    "config": {
      "target_id": "notion-page-id",
      "title_template": "{{workflowName}} 결과"
    }
  }
}
```

Google Drive `SPREADSHEET_DATA`에서 현재 FastAPI가 쓰는 `config.filename`은 catalog와 맞지 않는다. 새 계약에서는 `filename_template`으로 통일하고, 기존 `filename`은 backward compatibility fallback으로만 읽는 것을 권장한다.

### 5.2 Default 처리

다음 경우에는 모두 기존 기본 출력명을 사용한다.

- field가 schema에 없음
- config에 `filename_template`이 없음
- 값이 `null`
- trim 후 빈 문자열

이 규칙을 backend와 FE가 동일하게 이해해야 기존 workflow 호환성이 보장된다.

### 5.3 파일명 우선순위

Google Drive sink의 파일명 우선순위는 input type별로 통일해야 한다.

권장 우선순위:

1. `config.filename_template`이 있으면 template 결과 사용
2. 없으면 input payload의 `filename` 사용
3. 둘 다 없으면 기존 기본값 사용

기존 기본값 예:

- `TEXT`: `output.{file_format}`
- `SINGLE_FILE`: input `filename` 또는 `output`
- `FILE_LIST`: 각 item의 `filename` 또는 `file_{index}`
- `SPREADSHEET_DATA`: `{sheet_name}.csv`

### 5.4 확장자 정책

권장 정책은 backend가 `file_format`과 input data type을 기준으로 확장자를 보장하는 것이다.

- 사용자가 확장자를 생략하면 backend가 기본 확장자를 붙인다.
- 사용자가 확장자를 입력하면 `file_format`과 호환되는지 확인한다.
- 호환되지 않으면 backend가 보정하거나 명확한 validation error를 반환한다.
- `file_format=original`이면 원본 확장자를 우선하고, template은 base name으로 해석한다.

FE는 확장자 보정 로직을 직접 갖지 않는다.

### 5.5 Template 변수

1차에서는 backend가 안정적으로 제공할 수 있는 값만 허용한다.

현재 Notion title template runtime에서 확인되는 변수:

- `{{date}}`
- `{{filename}}`
- `{{subject}}`
- `{{mime_type}}`
- `{{sheet_name}}`
- `{{source_url}}`

Google Drive `filename_template`도 같은 변수 집합을 우선 재사용하는 것이 좋다. 이후 workflow context가 안정화되면 아래 변수를 추가할 수 있다.

- `{{workflowName}}`
- `{{nodeLabel}}`
- `{{datetime}}`
- `{{runId}}`
- `{{index}}`

`FILE_LIST`에서는 `{{index}}`가 없으면 여러 item이 같은 이름으로 충돌할 수 있으므로, FILE_LIST에 template을 허용할 때는 backend가 자동 suffix를 붙이거나 `{{index}}`를 지원해야 한다.

### 5.6 Sanitization 책임

최종 sanitization은 backend 책임이다.

FE도 사용자 경험을 위해 obvious invalid character 정도는 막을 수 있지만, 보안과 실제 저장소별 제약은 backend에서 반드시 다시 검증해야 한다.

Backend 필수 처리:

- path separator 제거 또는 명시적 정책 적용
- control character 제거
- `.` 또는 `..` 단독 파일명 방지
- 저장소별 최대 길이 제한
- 예약어 또는 외부 서비스 제한 처리
- template 결과가 빈 문자열이면 default fallback 적용

현재 FastAPI Google Drive는 filename 안의 `/` 또는 `\`를 path segment로 해석해 폴더 경로를 만들 수 있다. 사용자 입력 template에서는 이 동작이 의도치 않은 폴더 생성을 만들 수 있으므로, `filename_template` 결과에는 path separator를 허용하지 않는 쪽을 권장한다.

## 6. FE 수정 설계

### 6.1 목표

FE는 backend schema가 내려준 커스텀 field를 자연스럽게 보여주고 저장한다.

FE가 새 runtime 규칙을 직접 만들지는 않는다.

### 6.2 수정 대상

1. `src/features/configure-node/model/sink-field-presentation.ts`

   다음 field에 대한 presentation을 추가하거나 개선한다.

   - `filename_template`
     - label: `출력 파일명`
     - placeholder: `예: {{filename}}_{{date}}`
     - helpText: `비워두면 기본 파일명을 사용합니다. 확장자는 파일 형식에 맞춰 자동 보정될 수 있습니다.`

   - `file_format`
     - label: `파일 형식`
     - placeholder: `파일 형식 선택`
     - helpText: `저장할 파일 형식입니다. original은 원본 형식을 유지합니다.`

   - `title_template`
     - label: `출력 제목`
     - placeholder: `예: {{filename}} 결과`
     - helpText: `비워두면 기본 제목을 사용합니다.`

2. `src/features/configure-node/model/sink-node-draft.ts`

   선택 사항이지만 권장한다.

   `filename_template`, `title_template`은 저장 전 앞뒤 공백을 제거한다. 현재 generic text field는 빈 값 판단만 trim하고 저장값은 원문을 유지한다. 파일명 field에서는 leading/trailing space가 의도치 않은 결과를 만들 수 있으므로 해당 key만 trim 저장하는 것이 안전하다.

3. `src/features/configure-node/ui/panels/SinkNodePanel.tsx`

   현재 이 컴포넌트에는 model helper와 중복되는 legacy draft commit logic이 있다. 실제 저장 경로가 여기에도 있으므로, `filename_template`, `title_template` trim 저장 또는 invalid character validation을 넣는다면 이 파일에도 동일하게 반영해야 한다.

4. `src/entities/workflow/lib/node-status.ts`

   표시 label을 제품 copy와 맞춘다.

   - `filename_template`: `출력 파일명`
   - `title_template`: `출력 제목`
   - `file_format`: `파일 형식`

### 6.3 FE validation 범위

1차 FE validation은 과하게 하지 않는다.

권장:

- optional field이므로 빈 값은 허용
- 입력값이 있으면 `/`, `\`, null character, 단독 `.` 또는 `..` 정도만 막기
- 지원 변수 목록에 없는 `{{...}}`를 막을지는 backend 계약 확정 후 결정

Backend가 template 변수 validation error를 반환할 수 있다면 FE는 그 메시지를 노드 설정 오류로 표시하는 쪽이 좋다.

### 6.4 Default 유지 방식

FE에서 default 유지를 위해 별도 default value를 주입하지 않는다.

중요한 원칙:

- placeholder는 예시일 뿐 실제 config 값이 아니다.
- 사용자가 입력하지 않으면 `filename_template` key를 저장하지 않는다.
- 기존 workflow config에 없는 field를 FE가 자동으로 채워 넣지 않는다.
- `file_format`도 backend default를 유지해야 한다면 FE가 임의 기본값을 저장하지 않는다.

현재 `normalizeDraftValue()` 계열 함수가 빈 값을 `undefined`로 처리하기 때문에 이 원칙과 잘 맞는다.

## 7. Sink별 적용 범위

### 7.1 Google Drive

이번 이슈의 1차 적용 대상이다.

필요 backend 처리:

- `folder_id`와 함께 `filename_template`, `file_format` 수신
- `TEXT`, `SINGLE_FILE`, `FILE_LIST`, `SPREADSHEET_DATA`에서 `filename_template` 적용 정책 통일
- `config.filename` fallback은 유지하되 새 저장 계약은 `filename_template`으로 맞춤
- output payload type과 file format에 맞춰 확장자 결정
- Google Drive upload name에 최종 파일명 반영
- preview가 있다면 생성 예정 파일명 표시

### 7.2 Gmail

이미 `subject`, `body_format`, `text_delivery_mode`가 있다.

파일명 커스텀은 `text_delivery_mode=attachment`일 때만 의미가 있다. 현재 FE schema renderer는 조건부 field visibility를 일반화해서 지원하지 않으므로 1차에서는 Gmail 첨부파일명 커스텀을 제외한다.

후속으로 진행한다면 `attachment_filename_template` 같은 별도 key를 고려한다.

### 7.3 Notion

이미 `title_template`이 있고 FastAPI runtime에서도 적용된다.

이번 이슈에서는 FE copy와 테스트만 보강하고, runtime 변경은 필수로 보지 않는다.

### 7.4 Google Sheets

이미 `spreadsheet_id`, `sheet_name`, `range_a1`, `key_column`, `write_mode`가 있다.

파일명 개념은 기존 spreadsheet 선택 중심 sink에는 직접 해당하지 않는다. 신규 spreadsheet 생성 flow가 생기면 `spreadsheet_title_template` 같은 별도 이슈로 분리한다.

### 7.5 Discord

이미 `message_template`, `username`, `avatar_url`이 있다.

파일명 개념은 없으므로 이번 이슈 범위에서 제외한다.

### 7.6 Google Calendar

이미 `event_title_template`이 있다.

출력 "파일명"과 직접 연결되지는 않지만, 결과 이름 커스텀의 같은 계열이다. 이번 이슈에서는 변경하지 않는다.

## 8. 구현 순서

1. Backend 계약 확정

   - Google Drive `filename_template`을 authoritative key로 확정
   - `config.filename`은 backward compatibility fallback으로만 유지
   - 지원 template 변수 목록 확정
   - `file_format`과 확장자 보정 정책 확정
   - FILE_LIST 중복 이름 처리 정책 확정

2. Spring 확인 및 보강

   - 현재 catalog의 `filename_template`, `file_format` 유지
   - 필요하면 helper text나 metadata로 허용 변수 목록 제공
   - translator가 `runtime_sink.config.filename_template`과 `file_format`을 그대로 전달하는지 테스트로 고정
   - configured 판단에서 optional field가 기존 workflow를 막지 않는지 테스트 추가

3. FastAPI 수정

   - Google Drive output runtime에서 `filename_template` 적용
   - `TEXT`, `SINGLE_FILE`, `FILE_LIST`, `SPREADSHEET_DATA` naming 경로 통일
   - `config.filename` fallback 유지
   - default fallback 유지
   - sanitization, extension handling, duplicate handling 반영
   - preview가 있다면 생성 예정 파일명 반영
   - 빈 값, invalid template, FILE_LIST 중복 이름 테스트 추가

4. FE 수정

   - `sink-field-presentation.ts`에 `filename_template`, `file_format`, `title_template` copy 추가
   - 필요 시 해당 key에 한정해 trim 저장 및 obvious invalid character validation 추가
   - `node-status.ts` label copy 정리
   - draft helper와 panel 저장 경로 테스트 추가

5. 통합 검증

   - 파일명을 비워두면 기존 기본 파일명으로 생성되는지 확인
   - 파일명을 입력하면 해당 이름으로 저장되는지 확인
   - `file_format`과 확장자가 일관되게 맞는지 확인
   - 앞뒤 공백이 제거되는지 확인
   - `/`, `\`, `..` 같은 위험 입력이 backend에서 차단되는지 확인
   - 기존 workflow가 새 필드 없이도 정상 실행되는지 확인

## 9. FE 테스트 계획

추가할 테스트:

- `sink-field-presentation.test.ts`
  - `filename_template` label, placeholder, helpText 확인
  - `file_format` label, placeholder, helpText 확인
  - `title_template` label, placeholder, helpText 확인

- `sink-node-draft.test.ts`
  - optional `filename_template`이 빈 값이면 config에 저장되지 않는지 확인
  - 값이 있으면 config에 저장되는지 확인
  - 앞뒤 공백을 제거하기로 결정하면 trim 저장 확인
  - required field 설정 완료 여부에 영향을 주지 않는지 확인

UI 컴포넌트 테스트는 backend schema field가 text/select로 내려오면 기존 renderer 경로를 타므로 1차 필수는 아니다. 다만 조건부 노출이나 변수 추천 UI까지 추가한다면 별도 테스트가 필요하다.

## 10. 남은 질문

Backend와 구현 전에 확정해야 할 질문이다.

1. Google Drive `filename_template`에서 허용할 변수 목록은 Notion `title_template`과 동일하게 갈 것인가?
2. 사용자가 확장자를 입력할 수 있는가, backend가 항상 `file_format` 기준으로 붙이는가?
3. `file_format=original`일 때 template은 base name만 바꾸고 원본 확장자는 유지하는가?
4. `FILE_LIST`에서 같은 template 결과가 여러 번 나오면 suffix를 붙일 것인가?
5. `filename_template` 결과에 `/` 또는 `\`가 있으면 폴더 경로로 해석할 것인가, invalid로 막을 것인가?
6. preview 화면에서 생성 예정 파일명을 보여줄 것인가?
7. invalid template은 설정 저장 시점에 막을 것인가, 실행 시점에 실패시킬 것인가?

## 11. 최종 제안

1차 구현은 다음 범위가 가장 안전하다.

- Google Drive sink의 기존 optional `filename_template`을 실제 runtime naming에 연결
- `file_format`과 확장자 보정 정책을 함께 확정
- 값이 비어 있으면 기존 기본 파일명 사용
- FE는 schema field를 렌더링하고 표시 copy, optional trim, 최소 validation만 담당
- FastAPI가 최종 파일명 생성, 확장자 보정, sanitization, 중복 처리 담당
- Spring은 schema 노출과 runtime config 전달 계약을 테스트로 고정

이 방식이면 기존 source/sink editor 구조를 크게 흔들지 않고, 사용자가 원하는 “출력 결과 이름 지정”을 실제 실행 결과까지 가장 작은 변경으로 연결할 수 있다.
