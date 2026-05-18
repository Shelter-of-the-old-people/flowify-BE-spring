# GitHub Node Spring Design

> 작성일: 2026-05-16
> 대상: Spring backend
> 범위: GitHub 노드 1차 지원을 위한 catalog / contract / validation 설계
> 관련 레포: `flowify-BE`, `flowify-FE`

---

## 1. 목적

이 문서는 GitHub 노드 1차 지원을 위해 Spring backend가 책임져야 하는 catalog, 실행 계약, validation 범위를 정리한다.

이번 이슈의 최종 목표는 "GitHub 노드 전체"이지만, 1차 구현 범위는 아래처럼 고정한다.

- GitHub는 **source node**로만 지원한다.
- source mode는 **`new_pr`** 하나만 지원한다.
- 연결 방식은 기존 **manual token**만 사용한다.
- 결과 payload는 우선 **`API_RESPONSE`**로 유지한다.
- GitHub에 다시 쓰는 sink/action은 이번 범위에 포함하지 않는다.

즉 이번 1차에서 제품이 제공해야 하는 것은
"특정 저장소의 새 PR을 감지해서 기존 Flowify 처리/도착 노드로 연결할 수 있는 source node"다.

---

## 2. 현재 상태

### 2.1 이미 있는 것

Spring에는 이미 GitHub manual token 기반 연결/검증 경로가 존재한다.

- `GitHubTokenService`
- `GitHubManualTokenHandler`

또한 source catalog에도 GitHub 항목이 이미 존재한다.

- service key: `github`
- source mode: `new_pr`
- canonical input type: `API_RESPONSE`
- trigger kind: `event`

즉 Spring 레벨에서는 "GitHub source를 소개할 준비"는 일부 되어 있다.

### 2.2 아직 비어 있는 것

현재 catalog 항목만으로는 실제 노드가 동작한다고 보기 어렵다.

- target 입력 규칙이 저장소 식별자 수준에서 충분히 구체적이지 않다.
- source mode 설명과 사용자 기대가 1차 MVP 범위에 맞춰 다듬어지지 않았다.
- preflight validation이 GitHub 저장소 입력 형식과 manual token 연결 상태를 명확히 검증하지 않는다.
- FastAPI runtime이 아직 GitHub source를 실행하지 못하므로, Spring 계약도 그 전제를 반영해야 한다.

---

## 3. 1차 범위 정의

### 3.1 지원 기능

이번 1차에서 Spring이 전제하는 GitHub 노드 기능은 아래 하나다.

- `GitHub source`
  - mode: `new_pr`
  - target: 저장소 식별자 (`owner/repo`)
  - auth: manual token
  - output: `API_RESPONSE`

### 3.2 지원 자동화 시나리오

이 source가 들어오면 현재 Flowify 구조에서 바로 이어질 수 있는 대표 시나리오는 아래다.

- `GitHub -> Google Sheets`
- `GitHub -> AI -> Gmail`
- `GitHub -> AI -> Notion`

즉 1차 GitHub 노드는 "PR 감지 + 기록/요약/알림 자동화"에 초점을 맞춘다.

### 3.3 이번 범위 제외

이번 이슈에서 Spring 범위에 포함하지 않는 것은 아래와 같다.

- GitHub sink/action
  - PR comment 작성
  - review comment 작성
  - label/assignee 변경
  - issue/release 생성
- source mode 확장
  - issue
  - release
  - push
  - review
- webhook 자동 등록
- GitHub 전용 canonical type 신설

---

## 4. Catalog 방향

### 4.1 service 정의

service key는 기존처럼 `github`를 유지한다.

이유:

- manual token 서비스 키와 동일하다.
- FE 연결 상태 표시 및 account page token 관리와도 일치한다.
- runtime service token 맵도 기존 규칙을 재사용할 수 있다.

### 4.2 source mode 정의

1차 source mode는 `new_pr` 하나만 유지한다.

권장 의미:

- 특정 저장소에서 **새로 열린 PR**을 감지한다.
- 사용자는 이를 이벤트형 source로 이해한다.
- 실제 구현은 polling + checkpoint일 수 있지만, 제품 의미는 "새 PR 감지"로 유지한다.

### 4.3 target schema

1차 target schema는 별도 picker 없이 text input으로 유지하는 것이 현실적이다.

권장 입력 형식:

- `owner/repo`

예:

- `openai/openai-python`
- `Shelter-of-the-old-people/flowify-BE`

필요 이유:

- GitHub 저장소 picker는 이번 범위에서 과하다.
- manual token 기반에서 repo 접근 범위도 사용자마다 다르다.
- 1차는 text input이 가장 단순하고 검증 가능하다.

권장 placeholder:

- `owner/repo`

권장 help text:

- `예: Shelter-of-the-old-people/flowify-BE`

---

## 5. 실행 계약

### 5.1 FastAPI로 넘겨야 하는 최소 정보

Spring은 기존 workflow + service_tokens 계약을 유지하되,
GitHub source config가 아래 정보를 FastAPI에서 안정적으로 읽을 수 있어야 한다.

- service: `github`
- mode: `new_pr`
- target: `owner/repo`

즉 별도 계약 확장보다, source catalog / node config의 정합성을 먼저 보장하는 것이 1차 목표다.

### 5.2 output 의미

Spring은 GitHub `new_pr` source의 canonical input type을 계속 `API_RESPONSE`로 둔다.

이 판단의 이유는 아래와 같다.

- 현재 FE choice / mapping_rules가 `API_RESPONSE` 후속 흐름을 이미 지원한다.
- `Google Sheets`, `Notion` sink도 `API_RESPONSE`를 직접 받을 수 있다.
- `AI`를 거쳐 `TEXT`로 바꾸는 경로도 이미 자연스럽다.

즉 Spring은 새로운 데이터 타입을 만들지 않고 기존 Flowify 데이터 체계에 GitHub를 편입시키는 쪽이 맞다.

### 5.3 node state 전제

실제 checkpoint 상태는 FastAPI runtime이 관리하더라도,
Spring은 GitHub `new_pr`가 stateful event source라는 전제를 가져야 한다.

즉 아래가 보장되어야 한다.

- node state 저장이 가능해야 한다.
- 첫 실행과 후속 실행이 구분되어야 한다.
- workflow 재실행 시 기존 checkpoint와의 관계를 고려해야 한다.

이번 1차에서 Spring이 state schema를 상세히 소유할 필요는 없지만,
"GitHub source는 stateless manual fetch가 아니라 checkpoint 기반 source"라는 점은 문서와 validator에 반영해야 한다.

### 5.4 checkpoint 저장 경로

이번 1차에서는 GitHub source checkpoint를 **기존 workflow node state 경로**로 관리하는 것을 권장한다.

즉 개념적으로는 아래 흐름을 따른다.

- Spring `WorkflowNodeStateService`
- `WorkflowTranslator`가 runtime source에 `state` 주입
- FastAPI execution 성공 후 `nodeStateUpdates` callback
- Spring이 `workflow_node_states`에 반영

이 방향을 권장하는 이유는 아래와 같다.

- GitHub `new_pr`는 1차에서 "단일 PR event source"로 다루는 편이 자연스럽다.
- 현재 `source_checkpoints` 경로는 리스트형 new-item source에 더 가깝다.
- GitHub는 service-specific checkpoint 필드(`last_seen_pr_number`, `last_seen_pr_created_at`)를 node state로 들고 가도 충분하다.

즉 1차 GitHub source는 Spring 관점에서
"workflow node state를 사용하는 stateful source"로 보는 것이 가장 단순하다.

### 5.5 trigger / auto-run 전제

GitHub `new_pr`는 제품 의미상 event source이지만,
1차 구현은 webhook이 아니라 polling 기반이므로 **trigger settings / auto-run과 함께 동작하는 source**라는 점을 문서에 못박아야 한다.

즉 사용자는 아래처럼 이해해야 한다.

- source mode 이름: `새 PR 감지`
- 실제 동작: 주기 실행 시 새 PR 여부를 polling
- auto-run이 꺼져 있으면 지속 감지는 일어나지 않음

따라서 Spring 쪽에서도 GitHub source를 단순 manual source처럼 설명하면 안 되고,
"event semantics를 가진 polling source"라는 전제를 유지해야 한다.

### 5.6 `new_pr` 의미 정의

1차에서 `new_pr`는 아래 의미로 고정한다.

- `opened` 상태의 신규 PR만 감지
- `reopened`는 포함하지 않음
- 기존 PR의 `updated_at` 변경은 감지하지 않음
- draft -> ready for review 전환도 이번 범위에 포함하지 않음
- 여러 신규 PR이 동시에 있더라도 1회 실행에서는 PR 1건만 처리

즉 1차 `new_pr`는 말 그대로 "새로 열린 PR"에만 반응하는 source다.

이 정의를 문서에 못박아야 이후 `reopened_pr`, `pr_updated`, `ready_for_review` 같은 확장 이슈와 경계가 명확해진다.

---

## 6. Validation 방향

### 6.1 target 형식 검증

Spring preflight에서 최소한 아래는 잡는 것이 좋다.

- 빈 문자열 금지
- `/` 없는 값 금지
- `owner/repo` 2세그먼트 형식 강제

이유:

- 잘못된 repo 입력을 FastAPI나 GitHub API까지 보내지 않는 것이 좋다.
- 1차에서는 간단한 형식 검증만으로도 오류 메시지를 크게 개선할 수 있다.

### 6.2 token 연결 상태 검증

GitHub source는 auth required source이므로, 실행 전 아래를 검증해야 한다.

- 사용자에게 `github` manual token이 연결되어 있는가

가능하면 후속으로 아래도 고려할 수 있다.

- 최소 repository access scope가 있는가

다만 scope 세부 검증은 token 저장 시점에도 일부 처리되므로,
1차 preflight에서는 "token 존재 + service 연결됨" 위주로 시작해도 된다.

### 6.3 token 종류 정책

GitHub token 정책도 문서에 명확히 남겨둘 필요가 있다.

1차 권장 정책:

- classic PAT는 정식 지원 대상으로 본다.
- fine-grained PAT는 **best effort**로 본다.

이유:

- 현재 manual token 검증은 `X-OAuth-Scopes` 헤더의 classic scope 표현(`repo`, `public_repo`, `repo:*`)에 친화적이다.
- fine-grained PAT는 동일한 header 표현을 항상 보장하지 않을 수 있다.

따라서 1차 문서/가이드에서는
"repository read access가 가능한 classic PAT 권장"으로 적는 것이 가장 안전하다.

### 6.3 unsupported graph와의 관계

GitHub 노드 자체보다 중요한 것은,
이 source가 현재 런타임에서 안전한 graph에만 들어가야 한다는 점이다.

즉 Spring validator는 이번 이슈에서도 기존 원칙을 유지해야 한다.

- merge node 없는 fan-in 금지
- output/end node outgoing 금지
- loop direct multi-out 금지

GitHub source가 들어온다고 해서 graph validation 정책을 완화하면 안 된다.

---

## 7. FE/BE와의 책임 분리

### 7.1 FE 책임

FE는 아래를 책임진다.

- GitHub source 카드/노드 노출
- `new_pr` source mode 선택 UX
- `owner/repo` 입력 UX
- GitHub source 이후 가능한 후속 처리 선택지 표시

### 7.2 Spring 책임

Spring은 아래를 책임진다.

- catalog 정합성
- manual token 기반 source 연결 계약
- source target validation
- workflow 저장/번역 시 GitHub source 정의 유지

### 7.3 FastAPI 책임

FastAPI는 아래를 책임진다.

- GitHub source 실제 polling 실행
- checkpoint state 관리
- PR payload normalize
- `API_RESPONSE` output 생성

---

## 8. 테스트 관점

Spring 쪽 최소 확인 항목은 아래가 적절하다.

- source catalog에 `github:new_pr`가 의도한 target schema로 노출된다.
- invalid target (`repoonly`, `owner/`, `/repo`)가 preflight에서 거부된다.
- `github` manual token이 없으면 source 실행 전 validation에서 막힌다.
- valid workflow는 FastAPI contract로 정상 전달된다.
- GitHub source가 auto-run/event source 성격으로 문서/설명에 일관되게 노출된다.

---

## 8.2 GitHub AI 출력 정책

GitHub source 이후 AI를 거치는 출력은 목적지별로 기본 구조를 분리하는 것이 맞다.
이 원칙은 일반적인 자동화 도구 패턴과도 맞닿아 있다.

- 알림/메일/문서형 출력은 사람이 바로 읽는 요약문이 적합하다.
- 시트/DB/기록형 출력은 고정 컬럼과 짧은 값이 적합하다.

즉, `GitHub -> AI -> Gmail`과 `GitHub -> AI -> Google Sheets`를 같은 출력 형태로 다듬으면 안 된다.

### 8.2.1 텍스트/문서형 기본 구조

대상:

- Gmail
- Notion 문서/페이지
- Discord

기본 원칙:

- plain text를 기본으로 한다.
- Markdown 제목/굵게(`**`)는 기본 출력에서 사용하지 않는다.
- 이모지는 기본 출력에서 사용하지 않는다.
- 길게 늘어지는 서론 없이 핵심만 짧게 정리한다.
- URL은 raw link로 유지한다.
- 사실만 사용하고 추측하지 않는다.
- 각 섹션 bullet은 최대 3개로 제한한다.

권장 구조:

기본 정보
- 저장소
- PR 번호
- 작성자
- 브랜치
- 링크

한 줄 요약
- 이 PR이 무엇을 바꾸는지 한 문장

주요 변경점
- 최대 3개 bullet

확인 포인트
- 최대 3개 bullet

권장 세부 규칙:

- 섹션 제목은 짧은 한국어 label로만 쓴다.
- 목록은 `-` bullet만 사용한다.
- 사실 근거는 GitHub PR payload에 있는 정보만 사용한다.
- body가 길어도 전체를 반복 복사하지 않고 핵심만 재구성한다.

현재 구현 한계:

- 1차 구현에서 LLM 노드는 정확한 도착 노드(`gmail`, `discord`, `notion` 페이지)를 직접 구분하지 않는다.
- 따라서 GitHub 기본 프롬프트는 `TEXT` 출력 공통 digest를 생성하며, Gmail / Notion 문서에 우선 최적화한다.
- Discord처럼 더 짧은 알림형이 필요한 경우에는 explicit prompt 또는 후속 목적지별 분기 확장이 필요하다.

### 8.2.2 시트/기록형 기본 구조

대상:

- Google Sheets
- Notion DB형 저장

기본 원칙:

- 장문 요약보다 구조화된 필드가 우선이다.
- 각 셀은 가능하면 짧고 검색 가능한 값으로 유지한다.
- 표 형태 기록과 후속 필터링/정렬에 유리해야 한다.

권장 컬럼 예시:

- `repository`
- `pr_number`
- `title`
- `author`
- `state`
- `draft`
- `created_at`
- `base_branch`
- `head_branch`
- `changed_files_count`
- `url`

선택 컬럼 예시:

- `labels`
- `requested_reviewers`
- `short_summary`
- `review_points`

주의:

- 시트형 출력은 메일형처럼 긴 문단 요약을 그대로 넣지 않는다.
- 1차에서는 `GitHub -> 필요한 항목만 선택 -> Google Sheets` 흐름을 기본 경로로 본다.
- AI를 사용하더라도 시트형 출력은 schema/column 중심으로 제한하는 것이 안전하다.

### 8.2.3 도착 노드별 기본 출력 정책표

| 도착 노드 | 권장 출력 형식 | 기본 구조 | 길이/톤 가이드 | 비고 |
|---|---|---|---|---|
| `gmail` | plain text digest | `기본 정보 -> 한 줄 요약 -> 주요 변경점 -> 확인 포인트 -> 링크` | 짧고 바로 읽히는 요약형, `**` 같은 마크다운 강조 금지 | 메일 본문은 "한 번에 훑고 판단"하는 용도에 맞춘다. |
| `discord` | chat-short alert | `PR 번호/제목 -> 저장소/작성자 -> 한 줄 요약 -> 링크` | Gmail보다 더 짧고 즉시 읽히는 알림형 | 채팅창 전용으로 3~5줄 안쪽을 기본으로 본다. |
| `notion` 문서/페이지 | document summary | `제목 -> 기본 정보 -> 한 줄 요약 -> 주요 변경점 -> 확인 포인트 -> 링크` | 메일보다 약간 더 풍부하지만 여전히 짧은 문서형 | 나중에 다시 읽는 협업 문서 용도에 맞춘다. |
| `google_sheets` | structured table | 고정 컬럼 기반 row | 긴 문장보다 짧은 값 우선 | `DataFilter -> Sheets`를 기본 경로로 유지한다. |
| `notion` DB | structured record | 속성/컬럼 기반 record | Sheets와 동일하게 정렬/필터 친화적 구조 | 짧은 속성값과 선택적 `short_summary`만 권장한다. |

정리:

- `gmail`, `discord`, `notion` 문서는 사람이 읽는 결과물이므로 plain text 중심의 요약형이 맞다.
- `google_sheets`, `notion` DB는 정렬/필터/누적 기록 용도이므로 구조화된 컬럼형이 맞다.

### 8.2.4 구현 원칙

1차 구현에서는 아래 원칙으로 반영한다.

1. GitHub `new_pr` + custom prompt 없음 + `TEXT` 출력일 때만 GitHub 전용 기본 프롬프트를 적용한다.
2. 다른 source의 LLM 기본 동작은 바꾸지 않는다.
3. `SPREADSHEET_DATA`는 기존처럼 구조화 출력과 explicit prompt를 우선한다.
4. `GitHub -> Google Sheets`의 일반 경로는 `DataFilter -> Sheets`를 기본으로 유지한다.

즉, 이번 품질 보정의 목표는 "모든 AI 출력 형식을 하나로 통일"하는 것이 아니라,
"목적지에 맞는 기본 형태를 GitHub source에 한해 안전하게 분리"하는 것이다.

### 8.2.5 GitHub 전용 도착 노드 후보 제한은 후속 과제

실제 사용자 클릭 테스트 기준으로 보면, 현재 FE는 GitHub source 전용 목적지 제한을 아직 적용하지 않는다.

- `AI -> 보낼 곳 설정`에서는 `Discord`, `Gmail`, `Notion`, `Google Drive`, `Google Sheets`, `Google Calendar`가 함께 보일 수 있다.
- `필요한 항목만 선택 -> 보낼 곳 설정`에서는 `Notion`, `Google Drive`, `Google Sheets`가 함께 보일 수 있다.

이유는 현재 sink 후보 필터가 아래 두 조건만 보기 때문이다.

1. FE rollout allowlist에 포함되는가
2. 현재 노드의 output data type을 sink가 accepted input type으로 받을 수 있는가

즉 "이 흐름이 GitHub source에서 시작되었는가"는 아직 sink 후보 계산에 반영되지 않는다.

이 제한은 구현 가능하지만, 이번 1차 범위에서는 문서화만 하고 실제 제품 로직은 변경하지 않는다.

가능한 구현 방식은 아래와 같다.

1. FE 제한 방식
- `ServiceSelectionPanel`에서 sink 후보를 만들 때 immediate upstream node만 보지 않고,
  `edges`를 따라 source origin node까지 추적한다.
- origin source가 `service=github` + `source_mode=new_pr`이면 GitHub 전용 sink allowlist를 추가 적용한다.
- 예:
  - GitHub + `TEXT` -> `gmail`, `discord`, `notion`
  - GitHub + `SPREADSHEET_DATA` -> `google_sheets`

2. catalog 계약 확장 방식
- sink catalog에 `source_service_allowlist`, `source_mode_allowlist` 같은 메타데이터를 추가한다.
- FE는 공통 규칙 대신 catalog 계약을 읽어 source-specific 후보 제한을 적용한다.

현재 판단:

- 기술적으로는 충분히 가능하다.
- 다만 1차 구현에서는 GitHub source 자체 동작과 기본 후속 자동화 검증이 우선이므로,
  sink 후보 축소는 후속 UX 정리 이슈로 분리하는 것이 맞다.

---

## 9. 최종 권고

## 8.1 후속 품질 보정 원칙

GitHub 노드 1차 구현 이후 `GitHub -> Google Sheets`, `GitHub -> AI -> Gmail` 같은
대표 조합에서 "동작은 하지만 출력이 거칠다"는 문제가 발생할 수 있다.

이때 가장 중요한 원칙은 **공용 로직 전면 수정으로 해결하지 않는 것**이다.

특히 아래 컴포넌트는 여러 서비스가 함께 사용하는 공용 경로이므로,
GitHub 품질 보정 때문에 전체 규칙을 바꾸는 것은 피해야 한다.

- Spring choice / mapping rules의 공용 해석 로직
- FastAPI `DataFilterNodeStrategy`
- FastAPI `LLMNodeStrategy`

1차 후속 보정은 아래처럼 **GitHub 전용 보정만 추가하는 방식**으로 제한한다.

1. Spring choice 계층
- `service=GitHub`일 때만 field option의 `id`를 실제 payload key와 맞춘다.
- 예: `changed_files`, `author`, `url`, `title`
- 사용자에게 보여주는 `label`은 한국어 설명을 유지한다.

2. FastAPI DataFilter
- 이미 저장된 워크플로우와의 호환을 위해, `source_service=github`일 때만
  한국어 표시 필드를 실제 payload key로 해석하는 alias fallback을 둔다.
- 예: `변경 파일 -> changed_files`, `작성자 -> author`, `PR 링크 -> url`

3. FastAPI LLM 입력 가공
- `source_service=github` + `event=new_pr`일 때만
  JSON 전체를 그대로 넘기지 않고 PR 맥락에 맞는 정리된 텍스트를 만든다.
- 예: repository, PR 번호, 제목, 작성자, base/head branch, 본문, changed files

즉 후속 품질 보정의 기준은 아래 한 줄로 요약된다.

- **공용 규칙 변경보다 GitHub source 전용 보정을 우선한다.**

이 원칙을 지키면 Gmail, Google Sheets, Notion, Discord 등
기존 source/sink 조합의 안정성을 해치지 않고 GitHub 노드만 개선할 수 있다.

---

## 9. 최종 권고

Spring 기준 1차 GitHub 노드는 **"manual token 기반 GitHub PR 감지 source"**로 고정하는 것이 맞다.

핵심은 아래 세 가지다.

1. source catalog를 1차 MVP 의미에 맞게 정리한다.
2. `owner/repo` target validation을 추가한다.
3. FastAPI runtime이 구현될 수 있도록 계약을 단순하고 안정적으로 유지한다.

즉 Spring은 이번 이슈에서 GitHub를 "다양한 GitHub 기능의 진입점"으로 넓게 열기보다,
"실행 가능한 source node MVP"를 정확히 고정하는 역할을 맡는 것이 가장 적절하다.
