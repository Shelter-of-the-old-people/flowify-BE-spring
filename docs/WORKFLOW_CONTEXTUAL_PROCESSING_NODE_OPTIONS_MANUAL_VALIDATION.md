# Workflow Contextual Processing Node Options Manual Validation

## 목적

이 문서는 `이전 노드 문맥 기반 선택지 제한` 이슈의 실사용 흐름을 수동으로 점검할 때 기준으로 사용한다.

자동 테스트가 커버하지 못하는 항목은 아래와 같다.

- 실제 편집기에서 중간처리 선택지가 의도한 범위로 좁혀지는지
- 중간처리 내부 후속 선택지가 입력 데이터 문맥에 맞게 노출되는지
- 도착 노드 서비스 목록이 이전 노드 출력 타입과 문맥에 맞게 제한되는지

## 사전 조건

- FE가 `http://localhost:5173` 에서 실행 중이어야 한다.
- FastAPI가 `http://localhost:8000` 에서 실행 중이어야 한다.
- Spring이 `http://localhost:8080` 에서 실행 중이어야 한다.
  Spring health endpoint는 `401` 이어도 서버가 떠 있으면 정상으로 본다.
- Chrome이 `C:/Program Files/Google/Chrome/Application/chrome.exe` 경로에 설치되어 있어야 한다.
- 아래 임시 Playwright 스크립트들이 repo root에 존재해야 한다.

## 실행 명령

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-contextual-choice-manual-checks.ps1
```

특정 시나리오만 돌릴 때는 `-Scenarios` 를 넘긴다.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-contextual-choice-manual-checks.ps1 -Scenarios drive-family,github-family
```

## 포함된 시나리오

| Scenario | Script | 검증 포인트 |
| --- | --- | --- |
| `drive-family` | `.tmp-manual-ui-drive-family.mjs` | `Google Drive -> 내용 요약/정리`, `파일 정보를 표로 정리`, sink 제한 |
| `github-family` | `.tmp-manual-ui-github-family.mjs` | `GitHub -> AI로 분석/요약`, `필요한 항목만 선택`, sink 제한 |
| `gmail-family` | `.tmp-manual-ui-gmail-family.mjs` | `Gmail -> 단일 이메일 처리 선택지`, `표로 정리해서 저장`, sink 제한 |
| `sheets-family` | `.tmp-manual-ui-sheets-family.mjs` | `Google Sheets -> 한 행씩 처리`, `데이터 분석/요약`, sink 제한 |
| `news-notion-v2` | `.tmp-manual-ui-news-notion-v2.mjs` | `네이버 뉴스 -> 다시 정리/다듬기 -> Notion` |
| `seboard-notion-v2` | `.tmp-manual-ui-seboard-notion-v2.mjs` | `SE Board -> 글 하나씩 처리 -> 다시 정리/다듬기 -> Notion` |

## 산출물

- 통합 결과: [.tmp-manual-contextual-choice-summary.json](</c:/Users/김민호/CD2/flowify-BE-spring/.tmp-manual-contextual-choice-summary.json>)
- 개별 결과:
  - `.tmp-manual-ui-drive-family/summary.json`
  - `.tmp-manual-ui-github-family/summary.json`
  - `.tmp-manual-ui-news-notion-v2/result.json`
  - `.tmp-manual-ui-seboard-notion-v2/result.json`

각 시나리오는 `01-source`, `02-middle`, `03-sink`, 실패 시 `99-failure` 스냅샷을 남긴다.
각 결과 JSON의 `checks` 배열에는 실제로 통과한 `허용 선택지 / 비허용 선택지` 검증 항목이 함께 기록된다.

## 데이터 타입별 기대 선택지 매트릭스

이 섹션은 "선택지가 줄어들었는지"가 아니라 "어떤 문맥에서 정확히 무엇이 보여야 하는지"를 고정하기 위한 기준이다.

### 공통 규칙

- `runtime_status: hidden` 인 action / branch / filter는 어떤 시나리오에서도 노출되면 안 된다.
- `fields_from_data` 는 현재 데이터의 컬럼 목록을, `fields_from_service` 는 서비스별 허용 필드 목록을 따라야 한다.
- `Google Calendar` sink는 `accepted_input_types` 만으로 노출되면 안 되며, `service=google_calendar` 문맥일 때만 보여야 한다.

### `SINGLE_FILE`

문맥 예시:

- `Google Drive` 에서 가져온 일반 문서 파일
- 이미지가 아닌 `pdf`, `docx`, `pptx` 계열 단일 파일

기대 노출:

- `내용 요약/정리` (`summarize`)
- `정보 추출 (작성자/날짜/주제 등)` (`extract_info`)
- `번역` (`translate`)
- `파일 종류별로 다르게 처리` (`classify_by_type`)
- `내용에 따라 다르게 처리` (`classify_by_content`)
- `파일 정보만 추출` (`filter_metadata`)
- `파일 정보를 표로 정리` (`filter_metadata_table`)

기대 비노출:

- `이미지 내용 설명 생성` (`describe_image`)
- `이미지 내 텍스트 추출` (`ocr`)

후속 선택 기대:

- `summarize`: `핵심 3줄 요약`, `한 문단 요약`, `상세 요약`, `보고용 문장`, `표처럼 정리`
- `extract_info`: `작성자와 날짜`, `주요 수치와 성과`, `할 일 목록`, `직접 입력`
- `filter_metadata` / `filter_metadata_table`: `파일명`, `링크`, `업로드 시간`, `파일 크기`

### `SINGLE_FILE` with `file_subtype=image`

문맥 예시:

- `Google Drive` 에서 가져온 이미지 파일

추가 기대 노출:

- `이미지 내용 설명 생성` (`describe_image`)
- `이미지 내 텍스트 추출` (`ocr`)

### `ARTICLE_LIST`

문맥 예시:

- `네이버 뉴스 검색`
- `SE Board 새 글 가져오기`

기대 노출:

- processing method: `글 하나씩 처리` (`one_by_one`)
- action: `AI로 요약` (`ai_summarize`)
- action: `필요한 정보만 사용` (`filter_fields`)

후속 선택 기대:

- `ai_summarize`: `짧게 핵심만`, `뉴스레터 형식`, `보고서 문장`, `표처럼 정리`
- `filter_fields`: `제목`, `출처`, `작성일`, `원문 링크`, `요약`

### `SINGLE_EMAIL`

문맥 예시:

- `Gmail -> 별표(중요) 메일 사용`
- `Gmail -> 특정 메일 사용`

기대 노출:

- `내용 요약` (`summarize`)
- `번역` (`translate`)
- `의도/주제에 따라 분류` (`classify_intent`)
- `긍정/부정 분류` (`sentiment`)
- `긴급도 분석` (`urgency`)
- `할 일 추출` (`extract_todos`)
- `답장 초안 작성` (`draft_reply`)
- `내용에 따라 다르게 처리` (`classify_by_content`)
- `필요한 정보만 추출` (`filter_fields`)
- `표로 정리해서 저장` (`filter_fields_table`)

후속 선택 기대:

- `summarize`: `핵심 3줄 요약`, `보고용 문장`, `표처럼 정리`, `한 문단 요약`
- `filter_fields`: `제목`, `발신자`, `수신 날짜`, `본문 미리보기`
- `filter_fields_table`: `메일 ID`, `대화 스레드 ID`, `제목`, `발신자`, `받는 사람 목록`, `수신 날짜`, `본문 미리보기`, `라벨 목록`, `첨부파일 이름 목록`

### `API_RESPONSE`

문맥 예시:

- `GitHub` source 결과
- 기타 API 수집형 source 결과

기대 노출:

- `필요한 항목만 선택` (`filter_fields`)
- `AI로 분석/요약` (`ai_analyze`)
- `하나씩 처리` (`loop`)

기대 비노출:

- `조건에 맞는 것만 골라내기` (`ai_filter`)
- `특정 조건에 맞을 때만 실행` (`condition_value`)
- `전체를 하나로 합치기` (`merge`)

후속 선택 기대:

- `filter_fields`: 서비스별 필드 목록만 노출
  - `GitHub` 기준 예: `저장소`, `PR 번호`, `PR 제목`, `작성자`, `PR 링크`
- `ai_analyze`: `트렌드 요약 리포트`, `감성 분석 (긍정/부정)`, `키워드 추출`, `뉴스레터 형식으로 정리`, `표처럼 정리`, `한 문단 요약`

### `SPREADSHEET_DATA`

문맥 예시:

- `API_RESPONSE` 에서 `filter_fields` 를 거쳐 표형 데이터가 된 경우
- `Google Sheets` source 결과

기대 노출:

- processing method: `한 행씩 처리` (`one_by_one`)
- `맞춤 문서/메일 작성` (`ai_generate`)
- `데이터 분석/요약` (`ai_analyze`)
- `필요한 항목만 선택` (`filter_fields`)
- `표 컬럼으로 정리` (`filter_fields_table`)

기대 비노출:

- `특정 항목별로 다르게 처리` (`classify_by_field`)
- `특정 조건에 맞는 것만 사용` (`filter_condition`)

후속 선택 기대:

- `filter_fields` / `filter_fields_table`: 현재 데이터 컬럼만 노출
- `ai_generate`: `공식적/비즈니스`, `친근한/캐주얼`, `간결한 안내`, `직접 입력`
- `ai_analyze`: `변화 추이 분석`, `감정 분석`, `짧고 간단하게 요약`, `한 문단 요약`

### `TEXT`

문맥 예시:

- `AI` 요약 결과
- `ARTICLE_LIST -> one_by_one` 이후 생성된 텍스트

기대 노출:

- `다시 정리/다듬기` (`ai_refine`)
- `내용에 따라 다르게 처리` (`classify_by_content`)

기대 비노출:

- `필요한 부분만 추출` (`filter_content`)

후속 선택 기대:

- `ai_refine`: `더 짧게 줄이기`, `보고용 문장으로`, `표처럼 정리`, `뉴스레터 스타일로`, `블로그 포스팅용으로`, `핵심만 짧게`, `직접 입력`
- `classify_by_content`: `긍정 / 부정`, `중요 / 참고용`, `중요 / 확인 필요 / 참고용`, `직접 입력`

### `SCHEDULE_DATA`

문맥 예시:

- `Google Calendar` source 결과

기대 노출:

- `AI로 정리/요약` (`ai_summarize`)
- `필요한 항목만 선택` (`filter_fields`)

기대 비노출:

- `특정 일정만 골라내기` (`filter_type`)
- `종류별로 다르게 처리` (`classify`)

추가 sink 기대:

- `Google Calendar` sink는 이 문맥에서만 노출 허용

## 시나리오별 상세 체크리스트

아래 체크리스트는 위 데이터 타입 매트릭스를 대표 사용자 흐름으로 다시 풀어쓴 것이다.

## 확인 기준

- `drive-family`
  - source 결과는 `SINGLE_FILE` 일반 문서 문맥으로 본다.
  - middle 선택 단계에서 `describe_image`, `ocr` 가 보이면 실패다.
  - `summarize`, `extract_info`, `translate`, `filter_metadata`, `filter_metadata_table` 는 보여야 한다.
  - `filter_metadata_table` 후속 선택지에는 `파일명`, `링크`, `업로드 시간`, `파일 크기` 만 보여야 한다.
  - `TEXT` 결과 뒤 sink 목록에는 `Gmail`, `Notion`, `Discord`, `Google Drive`, `Google Sheets` 계열이 자연스럽게 보일 수 있다.
  - `Google Calendar` 가 보이면 실패다.
  - `SPREADSHEET_DATA` 결과 뒤 sink 목록에는 `Google Sheets`, `Notion`, `Google Drive` 가 우선 기대 대상이다.
  - `Gmail` 이 `SPREADSHEET_DATA` sink 후보로 보이면 실패다.

- `github-family`
  - source 결과는 `API_RESPONSE` 문맥으로 본다.
  - middle 선택 단계에서 `필요한 항목만 선택`, `AI로 분석/요약`, `하나씩 처리` 는 보여야 한다.
  - `조건에 맞는 것만 골라내기`, `특정 조건에 맞을 때만 실행`, `전체를 하나로 합치기` 가 보이면 실패다.
  - `필요한 항목만 선택` 후속 선택지에는 GitHub payload 기준 필드만 보여야 한다.
  - 현재 대표 검증 필드는 `저장소`, `PR 번호`, `PR 제목`, `작성자`, `PR 링크` 다.
  - `TEXT` 결과 뒤 sink 목록에서 `Google Calendar` 가 보이면 실패다.

- `gmail-family`
  - source 결과는 `SINGLE_EMAIL` 문맥으로 본다.
  - middle 선택 단계에서 `내용 요약`, `번역`, `의도/주제에 따라 분류`, `긍정/부정 분류`, `긴급도 분석`, `할 일 추출`, `답장 초안 작성`, `내용에 따라 다르게 처리`, `필요한 정보만 추출`, `표로 정리해서 저장` 이 보여야 한다.
  - `내용 요약` follow-up에는 `핵심 3줄 요약`, `보고용 문장`, `표처럼 정리`, `한 문단 요약` 이 보여야 한다.
  - `표로 정리해서 저장` follow-up에는 `메일 ID`, `대화 스레드 ID`, `제목`, `발신자`, `받는 사람 목록`, `수신 날짜`, `본문 미리보기`, `라벨 목록`, `첨부파일 이름 목록` 이 보여야 한다.
  - `TEXT` 결과 뒤 sink 목록에는 `Gmail`, `Notion`, `Discord`, `Google Drive`, `Google Sheets` 가 보여야 하고 `Google Calendar` 는 보이면 실패다.
  - `SPREADSHEET_DATA` 결과 뒤 sink 목록에는 `Notion`, `Google Drive`, `Google Sheets` 가 보여야 하고 `Gmail`, `Discord`, `Google Calendar` 가 보이면 실패다.

- `sheets-family`
  - source 결과는 `SPREADSHEET_DATA` 문맥으로 본다.
  - 첫 middle 단계에서 processing method로 `한 행씩 처리` 가 보여야 한다.
  - 그 다음 middle 선택 단계에서 `맞춤 문서/메일 작성`, `데이터 분석/요약`, `필요한 항목만 선택`, `표 컬럼으로 정리` 가 보여야 한다.
  - `특정 항목별로 다르게 처리`, `특정 조건에 맞는 것만 사용` 이 보이면 실패다.
  - `데이터 분석/요약` follow-up에는 `변화 추이 분석`, `감정 분석`, `짧고 간단하게 요약`, `한 문단 요약` 이 보여야 한다.
  - `TEXT` 결과 뒤 sink 목록에는 `Gmail`, `Notion`, `Discord`, `Google Drive`, `Google Sheets` 가 보여야 하고 `Google Calendar` 는 보이면 실패다.

- `news-notion-v2`
  - source 결과는 `ARTICLE_LIST` 문맥으로 본다.
  - 첫 middle 단계에서 processing method로 `글 하나씩 처리` 만 보여야 한다.
  - `글 하나씩 처리` 뒤 생성된 `TEXT` 문맥에서는 `다시 정리/다듬기` 가 보여야 한다.
  - `다시 정리/다듬기` 후속 선택지에는 `뉴스레터 스타일로` 가 포함되어야 한다.
  - 마지막 sink에서 `Notion` 연결이 정상적으로 이어져야 한다.
  - `TEXT` 문맥에서 `filter_content` 가 보이면 실패다.

- `seboard-notion-v2`
  - source 결과는 `ARTICLE_LIST` 문맥으로 본다.
  - 첫 middle 단계에서 processing method로 `글 하나씩 처리` 만 보여야 한다.
  - 루프 이후 `TEXT` 문맥에서 `다시 정리/다듬기` 가 이어져야 한다.
  - `뉴스레터 스타일로` 후속 선택지가 보여야 한다.
  - 마지막 sink에서 `Notion` 연결이 정상적으로 이어져야 한다.
  - `TEXT` 문맥에서 `filter_content` 가 보이면 실패다.

## 주의 사항

- 이 러너는 기존 임시 스크립트를 재사용한다. 스크립트 이름은 임시 형식이지만, 이번 이슈 범위에서는 위 시나리오들을 기준 세트로 본다.
- 일부 시나리오는 실제 OAuth 연결이나 대상 데이터 존재 여부에 영향을 받을 수 있다.
- `Google Calendar` source는 catalog에는 존재하지만, 현재 테스트 환경 UI source 목록에는 노출되지 않아 `calendar-family`는 기본 세트에서 제외했다.
- 이 문서는 `문맥 기반 선택지 제한` 이슈 기준의 수동검증 인덱스다. 템플릿, 대시보드, OAuth 일반 회귀는 별도 검증 범위다.

## 이슈 종료 판단

이번 이슈는 아래 세 조건을 모두 만족하면 `완료`로 본다.

- 자동 테스트가 모두 통과한다.
  - FE: `pnpm test`, `pnpm build`
  - Spring: `gradlew test`
  - FastAPI: `python -m pytest`
- 공식 수동검증 기준 세트가 모두 통과한다.
  - `drive-family`
  - `github-family`
  - `gmail-family`
  - `sheets-family`
  - `news-notion-v2`
  - `seboard-notion-v2`
- 실제 sink 실행 E2E가 모두 성공한다.
  - Gmail
  - Discord
  - Notion
  - Google Sheets

최신 기준 결과 파일:

- 공식 수동검증: `.tmp-manual-contextual-choice-summary.json`
- sink 실행 E2E: `.tmp-sink-e2e-user-values/summary.json`

## 레거시 스크립트 분류

아래 스크립트는 저장소에 남아 있어도, 이번 이슈의 `완료/실패` 판단 기준에는 포함하지 않는다.

- `.tmp-manual-ui-calendar-family.mjs`
  - 현재 UI source 목록에 `Google Calendar`가 노출되지 않아 기본 세트에서 제외한다.
- `.tmp-manual-ui-canvas-notion.mjs`
  - 구형 UI 문구와 예전 진입 흐름을 기다리는 스크립트다.
  - 현재 기준 스크립트는 `.tmp-manual-ui-canvas-notion-v2.mjs`다.
- `.tmp-manual-ui-seboard-notion.mjs`
  - 예전 `AI로 요약` 흐름을 기대하는 스크립트다.
  - 현재 기준 스크립트는 `.tmp-manual-ui-seboard-notion-v2.mjs`다.

참고 결과 파일:

- 전체 임시 manual-ui sweep: `.tmp-all-manual-ui-results.json`

해석 원칙:

- 공식 기준 세트와 E2E가 통과하면 이번 이슈는 `요구사항 충족`으로 본다.
- 레거시 스크립트 실패는 `구형 검증 자산 정리 필요`로 분류하고, 이번 이슈의 기능 실패로 간주하지 않는다.
