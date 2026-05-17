# Mapping Rules Contract Audit

> 작성일: 2026-05-17  
> 대상 저장소: `flowify-BE-spring`  
> 대상 계약: `src/main/resources/docs/mapping_rules.json`  
> 검토 범위: Spring 선택지 API, Spring workflow translator, FastAPI 실행기, FE choice fallback, AI workflow generation context

## 1. 목적

`mapping_rules.json`은 단순 문서가 아니라 워크플로우 편집기의 다음 단계 선택, 중간 노드 생성, Spring 검증, FastAPI 실행, AI 워크플로우 생성 context에 영향을 주는 핵심 계약이다.

이번 문서는 즉시 수정하기 위한 설계가 아니라, 실제 코드 기준으로 현재 계약이 어디까지 맞고 어디부터 조심스럽게 정리해야 하는지 기록하기 위한 감사표다.

## 2. 중요한 정정

초기 검토에서는 `classify_intent`, `sentiment`, `ai_generate`, `ai_refine` 같은 AI action을 FastAPI LLM 실행기가 직접 지원하지 않는다고 보고 위험으로 분류했다.

하지만 실제 실행 경로를 다시 확인한 결과 이 분류는 과했다.

- Spring `ChoicePromptResolver`는 `AI`, `AI_FILTER` 노드의 `choiceActionId`를 `ai_prompt_rules.json`에서 프롬프트로 변환한다.
- 변환된 runtime config에는 대부분 `action=process`, `prompt=...`가 들어간다.
- 따라서 AI 계열 action은 FastAPI가 원래 action id를 직접 지원하지 않아도, prompt rule이 있으면 실행 가능한 계약으로 볼 수 있다.

즉 AI action의 1차 검증 기준은 FastAPI `SUPPORTED_ACTIONS` 직접 포함 여부가 아니라 `ai_prompt_rules.json`의 prompt rule 존재 여부다.

## 3. 현재 정상으로 볼 수 있는 부분

| 영역 | 판단 |
| --- | --- |
| Data type 목록 | `mapping_rules.json`, `schema_types.json`, source output, sink accepted input이 큰 틀에서 일치한다. |
| Sink catalog와 FastAPI output runtime | Slack, Discord, Gmail, Notion, Google Drive, Google Sheets, Google Calendar의 accepted input type이 일치한다. |
| AI action prompt coverage | `mapping_rules.json`의 AI/AI_FILTER action은 `ai_prompt_rules.json`에 prompt rule이 존재한다. |
| Gmail/Drive/Sheets/Canvas/Slack/Web News source | catalog와 FastAPI/Spring generation 지원 범위가 대체로 일치한다. |
| `SINGLE_FILE`, `SINGLE_EMAIL` 기본 AI/DataFilter action | 현재 편집/실행 흐름과 대체로 맞는다. |

## 4. 주의가 필요한 계약

### 4.1 Action id naming 불일치

| 의미 | Data type | 현재 id |
| --- | --- | --- |
| 요약 | `SINGLE_FILE` | `summarize` |
| 요약 | `SINGLE_EMAIL` | `summarize` |
| 요약 | `ARTICLE_LIST` | `ai_summarize` |
| 요약 | `SCHEDULE_DATA` | `ai_summarize` |

이 구조는 data type별로는 동작할 수 있지만, AI 생성 context에서는 혼동을 유발한다. 실제로 `SINGLE_EMAIL` 흐름에서 `ai_summarize`가 생성되어 Spring result normalization에서 거부된 사례가 있었다.

첫 정리 후보는 전역 rename이 아니라 아래 중 하나다.

- generation context에서 현재 data type의 action id만 선택하도록 더 강하게 제한한다.
- Spring normalization에서 좁은 alias 정책을 둔다.
- 장기적으로 `summarize` 계열 canonical id 정책을 다시 정한다.

### 4.2 DataFilter runtime 미지원 선택지

FastAPI `DataFilterNodeStrategy` 기준으로 아래 action은 현재 명시적으로 미지원이다.

| Data type | Action id | 현재 node type | 판단 |
| --- | --- | --- | --- |
| `SPREADSHEET_DATA` | `filter_condition` | `DATA_FILTER` | FastAPI에서 unsupported |
| `SCHEDULE_DATA` | `filter_type` | `DATA_FILTER` | FastAPI에서 unsupported |
| `TEXT` | `filter_content` | `DATA_FILTER` | FastAPI에서 unsupported |

이 항목은 삭제보다 먼저 UI/AI 생성 노출 경로를 확인해야 한다. 당장 정리한다면 "현재 실행 가능 목록"에서 제외하는 방식이 안전하다.

### 4.3 Branch 계약의 구현 범위

`CONDITION_BRANCH`는 여러 data type에 존재하지만, Spring `BranchRuntimeConfigResolver`는 현재 파일 타입 분기(`branch_by_file_type`) 중심으로 runtime config를 만든다.

| Action 예시 | 판단 |
| --- | --- |
| `branch_by_file_type` | 파일 타입 분기 runtime config가 있어 비교적 명확하다. |
| `classify_by_type` |
| `classify_by_content` |
| `classify_by_field` |
| `condition_value` |
| `classify` |

위 일반 조건 분기 계열은 선택지 계약은 있으나, 제품 UX와 runtime branch 처리 범위를 별도로 검증해야 한다.

### 4.4 `LOOP`의 위치가 애매한 항목

`API_RESPONSE.loop`는 action 목록 안에 `node_type=LOOP`로 들어 있다.

현재 구조상 `LOOP`는 action이라기보다 `processing_method`에 더 가깝다. 이 항목은 계약상 어색하므로 후속 검토가 필요하다.

### 4.5 `requires_processing_method=true`와 direct actions 혼재

| Data type | 현재 상태 |
| --- | --- |
| `SPREADSHEET_DATA` | `requires_processing_method=true`이면서 direct actions도 존재 |
| `ARTICLE_LIST` | `requires_processing_method=true`이면서 direct actions도 존재 |

Spring `ChoiceMappingService.getOptionsForNode()`는 `requires_processing_method=true`면 processing method를 우선 반환한다. 따라서 direct actions가 어떤 경로에서 노출되어야 하는지 정책이 불명확하다.

정책 결정이 필요하다.

- 반드시 처리 방식을 먼저 선택하는 타입인지
- "전체를 한 번에 처리" 같은 direct action도 허용할 타입인지
- processing method 이후 output data type에서 action을 다시 선택하게 할 타입인지

## 5. Source catalog와 generation 지원 범위

| 서비스 | 상태 |
| --- | --- |
| `google_drive` | catalog와 generation/runtime 지원 일치 |
| `gmail` | catalog와 generation/runtime 지원 일치 |
| `google_sheets` | catalog와 generation/runtime 지원 일치 |
| `canvas_lms` | catalog와 generation/runtime 지원 일치 |
| `slack` | catalog와 generation/runtime 지원 일치 |
| `web_news` | catalog와 generation/runtime 지원 일치 |
| `naver_news` | `keyword_search`, `periodic_collect`는 generation/runtime 지원 밖 |
| `youtube` | catalog에는 있으나 generation/runtime 지원 밖 |
| `coupang` | catalog에는 있으나 generation/runtime 지원 밖 |
| `github` | catalog에는 있으나 generation/runtime 지원 밖 |
| `notion` source | catalog에는 있으나 generation/runtime 지원 밖 |
| `google_calendar` source | catalog에는 있으나 generation/runtime 지원 밖 |

AI 생성 context는 `WorkflowGenerationSupport`로 일부 서비스만 필터링하므로 당장 생성 경로는 비교적 안전하다. 다만 catalog 자체에는 미래 기능과 현재 기능이 섞여 있다.

## 6. 권장 정리 순서

1. 코드 수정 없이 감사표를 유지한다.
2. `SINGLE_EMAIL`에서 `ai_summarize`가 생성되는 문제를 가장 좁게 보정한다.
3. DataFilter 미지원 선택지의 노출 경로를 확인한다.
4. 일반 branch action의 runtime 지원 범위를 별도 검증한다.
5. `LOOP`가 action에 들어간 항목을 processing method 정책으로 정리할지 결정한다.
6. `requires_processing_method=true`와 direct actions 혼재 정책을 정한다.
7. 마지막에만 action id canonical rename 또는 alias 정책을 진행한다.

## 7. 결론

`mapping_rules.json`은 현재 서비스의 핵심 흐름을 어느 정도 담고 있지만, 완전히 정돈된 authoritative contract라고 보기는 어렵다.

다만 전체를 한 번에 갈아엎으면 편집기, 저장 데이터, Spring translator, FastAPI runtime, AI 생성이 동시에 흔들릴 수 있다. 따라서 가장 안전한 접근은 "계약 감사표 유지 -> 명확한 충돌 1개씩 수정 -> runtime 미지원 노출 축소 -> 장기 canonical 정리" 순서다.
