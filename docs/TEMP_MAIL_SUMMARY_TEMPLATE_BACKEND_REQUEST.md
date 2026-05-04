# 메일 요약 후 전달 템플릿 백엔드 요청서

> **작성일:** 2026-05-04
> **대상:** Spring, FastAPI 백엔드 담당자
> **용도:** 임시 전달용 문서
> **관련 이슈:** 메일 요약 후 전달 템플릿 구체화 및 시스템 템플릿 구현

---

## 1. 요청 배경

FE에서는 템플릿 목록, 템플릿 상세, 템플릿 가져오기 흐름이 이미 준비되어 있습니다.
다만 실제 시스템 템플릿 데이터와 실행 품질은 백엔드가 결정하기 때문에, 아래 항목이 같이 정리되어야 템플릿 3종이 실사용 가능한 수준으로 보입니다.

현재 핵심 이슈는 다음과 같습니다.

- 메일 템플릿 3종의 이름/설명과 실제 runtime semantics를 `메일 목록 요약형` 기준으로 맞춰야 함
- Gmail 템플릿 3종의 노드 정의와 FE 기대 계약을 맞춰야 함
- FastAPI 메일 요약 품질이 아직 템플릿 설명과 완전히 맞지 않음
- Slack 채널 / Notion 페이지를 ID 직접 입력 없이 선택할 수 있는 백엔드 지원이 없음
- Gmail fetch 개수는 템플릿 의도와 달리 현재 20개 하드코딩임
- 장기적으로는 `메일별 구조화 -> 최종 결과 1개 집계` 구조가 필요함

참고:
- Gmail OAuth 자체는 현재 구현과 로컬 테스트가 이미 되어 있으므로, 이번 문서에서는 신규 구현 요구사항으로 다루지 않습니다.
- Spring main에는 시스템 템플릿 증분 반영(upsert) 구조가 이미 있으므로, 이번 카테고리도 그 규칙을 그대로 따라야 합니다.
- 이번 문서는 템플릿 시드, picker API, FastAPI 메일 구조화 품질, FE 공개 계약 유지에 집중합니다.

---

## 2. 요청 사항

### 2.1 메일 템플릿 3종 시스템 시드 추가

위치:
- `src/main/java/org/github/flowify/config/TemplateSeeder.java`

1차 대상 템플릿:
- 읽지 않은 메일 목록 요약 후 Slack 공유
- 중요 메일 목록 요약 후 Notion 저장
- 중요 메일 목록에서 할 일 추출 후 Notion 저장

공통 기준:
- `category`: `mail_summary_forward`
- `isSystem`: `true`
- `requiredServices`: 실제 서비스 키 사용
  - 예: `gmail`, `slack`, `notion`
- `icon`: `gmail` 권장

중요:
- 템플릿 이름/설명은 현재 실제 runtime semantics에 맞춰 `메일 목록 요약형` 문구로 정리해야 합니다.
- `메일 하나씩 요약 후 ...`처럼 오해되는 문구는 피해야 합니다.

### 2.2 시더 로직은 기존 증분 반영 방식 유지

현재 main에는 이름+isSystem 기준 upsert 구조가 이미 반영되어 있으므로, 이 카테고리 템플릿도 동일 규칙을 따라야 합니다.

확인 포인트:
- 기존 DB에도 새 시스템 템플릿이 추가될 것
- 동일 이름 템플릿은 id, useCount, createdAt을 유지한 채 갱신될 것

현재 기준:
- `TemplateRepository`는 `findByNameAndIsSystem(String name, boolean isSystem)`를 제공함
- 이번 카테고리도 별도 `findByName(...)` 추가 없이 기존 방식을 재사용하는 것이 맞음

### 2.3 템플릿 노드 정의를 현재 FE 계약에 맞게 구성

위치:
- `src/main/java/org/github/flowify/workflow/entity/NodeDefinition.java`

중요 포인트:
- 시작/도착 서비스 노드는 `category = service`
- `type`은 실제 서비스 키 사용
  - 예: `gmail`, `slack`, `notion`
- `role`은 `start`, `end`
- `config.service`도 동일하게 세팅
- 중간 AI 노드는 `category = ai`, `type = llm`
- 반복 노드는 `category = control`, `type = loop`
- 프리뷰를 위해 `position`도 함께 세팅 필요

특히 시작 노드 config는 FE가 실제로 아래 필드를 읽으므로, 이 값을 명시적으로 넣어야 합니다.

필수 start config 필드:
- `isConfigured`
- `service`
- `source_mode`
- `target`
- `target_label`
- `target_meta`
- `maxResults`

주의:
- FE는 `target_label`, `target_meta`를 source summary와 node data panel에서 그대로 사용합니다.
- 실행 엔진이 내부적으로 runtime payload를 별도로 만들더라도, FE에 저장되는 start node config에는 위 필드가 유지되어야 합니다.

sink node 기본 원칙:
- 사용자 입력이 필요한 sink는 기본 `isConfigured = false`
- Slack: `channel` 입력 전까지 false
- Notion: `target_type`, `target_id` 입력 전까지 false

이 부분이 맞지 않으면 템플릿 상세 프리뷰와 가져오기 결과가 FE에서 어색하게 보이거나, 아직 미설정인 워크플로우가 실행 가능한 상태처럼 보일 수 있습니다.

### 2.4 Slack / Notion picker용 백엔드 목록 API 추가

현재 FE는 sink 설정에서 다음 타입을 실제 picker로 쓰고 싶습니다.

- Slack: `channel_picker`
- Notion: `page_picker`

하지만 현재는 ID 직접 입력만 가능한 수준이라 실사용성이 매우 낮습니다.

필요 작업:
- Slack 채널 목록 조회 provider 추가
- Notion 페이지 목록 조회 provider 추가
- 현재 source 중심 `TargetOptionService`를 sink picker도 지원하도록 확장하거나, sink picker 전용 endpoint를 추가

권장 endpoint:

```http
GET /api/editor-catalog/sinks/{serviceKey}/target-options
```

query parameters 제안:

```ts
interface SinkTargetOptionsParameters {
  type: string;
  parentId?: string;
  query?: string;
  cursor?: string;
}
```

응답 형식은 FE가 기존 remote picker를 재사용할 수 있도록 source target option과 동일한 shape를 권장합니다.

```ts
interface TargetOptionResponse {
  items: TargetOptionItem[];
  nextCursor: string | null;
}

interface TargetOptionItem {
  id: string;
  label: string;
  description: string | null;
  type: string;
  metadata: Record<string, unknown>;
}
```

Slack 채널 item 예시:

```json
{
  "id": "C0123456789",
  "label": "flowify-test",
  "description": "공개 채널",
  "type": "channel",
  "metadata": {
    "isPrivate": false,
    "isMember": true,
    "memberCount": 12
  }
}
```

Notion 페이지 item 예시:

```json
{
  "id": "355cdaf894fe80aa9ab7c9377071e85b",
  "label": "Flowify Notion Test",
  "description": "부모 페이지",
  "type": "page",
  "metadata": {
    "parentTitle": "개인 문서",
    "lastEditedTime": "2026-05-04T00:00:00Z"
  }
}
```

1차 목표:
- Slack: 기존 공개 채널 목록 선택
- Notion: 현재 integration이 접근 가능한 페이지 목록 선택

생성 지원 범위:
- Slack
  - 1차: 기존 채널 목록 선택만 지원
  - 2차 검토: 새 채널 생성
  - 비고: 새 채널 생성은 추가 scope(`channels:manage`)와 워크스페이스 정책 검토가 필요함
- Notion
  - 1차: 기존 부모 페이지 목록 선택만 지원
  - 2차 검토: 설정 패널에서 새 부모 페이지 생성
  - 비고: 현재 실행 단계에서는 선택한 부모 페이지 아래에 결과 페이지를 새로 만드는 것은 이미 가능하지만, 설정 단계에서 부모 페이지 자체를 새로 만드는 UX는 별도 작업이 필요함

주의 사항:
- Slack은 공개 채널 기준 1차 구현이 현실적이며, 비공개 채널/새 채널 생성은 scope와 워크스페이스 정책 검토가 필요함
- Notion은 integration이 접근 권한을 가진 페이지들만 목록에 보여야 함
- Notion `database` picker는 현재 저장 로직이 완전하지 않으므로 1차에서는 `page`만 우선 지원하는 것이 안전함

권장 위치:
- `src/main/java/org/github/flowify/catalog/service/picker/TargetOptionService.java`
- `src/main/java/org/github/flowify/catalog/controller/CatalogController.java`
- Slack / Notion provider 신규 클래스

### 2.5 FastAPI 프롬프트 구체화 요청

이번 메일 템플릿 3종은 단순히 템플릿 시드만 추가하는 것으로는 품질이 맞지 않습니다.

현재 FastAPI 런타임은 메일을 하나씩 개별 처리하기보다, 읽어온 메일 목록 전체를 한 번에 LLM으로 보내 단일 결과를 만드는 구조에 가깝습니다.
그래서 템플릿 설명과 실제 실행 결과를 최대한 맞추려면 FastAPI 쪽 프롬프트 구체화가 함께 필요합니다.

요청 방향:
- 메일 여러 개를 자유 요약하는 프롬프트가 아니라
- 각 메일별 정보를 빠짐없이 구조화하는 프롬프트로 강화
- 최종 결과는 Slack 메시지 1개 또는 Notion 페이지 1개로 유지
- 하지만 결과 내부에는 메일별 항목이 번호와 고정 포맷으로 구분되도록 유도

권장 출력 형식:
- 발신자
- 제목
- 핵심 내용
- 액션 필요 여부

예시 프롬프트 방향:
- 입력된 메일 목록의 모든 메일을 빠짐없이 포함한다
- 각 메일마다 번호를 붙인다
- 각 메일은 `발신자 / 제목 / 핵심 / 액션` 형식으로 정리한다
- 불필요한 서론과 결론 없이 바로 목록만 출력한다

예시 결과 형식:

```text
읽지 않은 메일 요약 5건

1. 발신자: Slack
- 제목: 인증 코드
- 핵심: 새 로그인 확인용 코드 안내
- 액션: 코드 확인 필요

2. 발신자: Postman
- 제목: 플랜 업데이트 예정
- 핵심: 요금제 변경 예정 안내
- 액션: 없음
```

관련 FastAPI 수정 대상:
- `app/core/nodes/llm_node.py`
- `app/services/llm_service.py`

### 2.6 Gmail fetch 개수 설정화

현재 Gmail source는 fetch 개수가 20으로 고정되어 있습니다.
하지만 템플릿 의도와 시드 설정은 최대 100개 처리를 가정하고 있습니다.

필요 작업:
- Gmail source가 `maxResults` 같은 config 값을 읽도록 변경
- 값이 없으면 기본 20 유지
- 메일 템플릿 시드에는 기본 `maxResults = 100` 반영

관련 FastAPI 수정 대상:
- `app/core/nodes/input_node.py`
- `app/services/integrations/gmail.py`

관련 Spring 수정 대상:
- `src/main/java/org/github/flowify/config/TemplateSeeder.java`

### 2.7 `메일별 구조화 -> 최종 결과 1개 집계` 구조를 목표로 한 런타임 개선

1차로는 프롬프트 구체화만으로 품질을 끌어올릴 수 있지만, 장기적으로는 아래 구조가 필요합니다.

`Gmail(EMAIL_LIST) -> Loop -> SINGLE_EMAIL * N -> LLM * N -> SUMMARY_LIST -> Aggregate -> Sink 1회`

즉 필요한 개선은 다음과 같습니다.

- Loop가 실제로 item 단위 fan-out 하도록 runtime 개선
- LLM이 `SINGLE_EMAIL` 기준 구조화 결과를 반환하도록 개선
- 구조화 결과를 모아 최종 결과 1개로 집계하는 aggregate 단계 추가
- Slack / Notion sink가 집계 결과를 한 번에 저장/전송하도록 개선

관련 FastAPI 수정 대상:
- `app/core/nodes/logic_node.py`
- `app/core/engine/executor.py`
- `app/core/nodes/llm_node.py`
- `app/core/nodes/output_node.py`

이 항목은 2차 구조 개선으로 봐도 되지만, 최종 목표 구조는 문서에 분명히 남겨두는 것이 좋습니다.

### 2.8 이 구조에 맞는 FE 공개 응답 계약 유지

중요한 점은, 백엔드 내부 런타임을 바꾸더라도 FE가 바로 못 알아듣는 공개 계약을 갑자기 만들면 안 된다는 점입니다.

권장 방향:
- 1차에서는 `메일별 구조화 -> 최종 결과 1개 집계`를 FastAPI 내부 runtime에서 처리
- 하지만 템플릿 상세 API와 가져오기 응답에서 보이는 public graph는 기존 FE가 이해할 수 있는 형태를 최대한 유지

즉 1차에서는 아래가 안전합니다.

- 공개 템플릿 graph: `gmail -> loop -> llm -> slack/notion`
- 내부 runtime: 필요 시 aggregate를 숨겨진 실행 단계로 처리

이유:
- FE는 아직 `aggregate` 같은 새 node type을 모름
- FE는 아직 `SUMMARY_LIST` 같은 새 data type을 모름
- 지금 단계에서 public contract까지 바꾸면 FE도 동시에 큰 수정이 필요해짐

만약 백엔드가 public response에 정말 `aggregate` node나 `SUMMARY_LIST` data type을 노출하려면, 그 경우 아래도 동시에 맞아야 합니다.

- node type metadata 확장
- mapping rules 확장
- schema preview value type 확장
- FE node presentation 확장

따라서 1차 요청에서는 아래를 권장합니다.

1. 내부 런타임만 구조 개선
2. FE 공개 응답은 최대한 기존 graph 호환 유지
3. 템플릿 이름/설명/config만 현실 동작에 맞게 조정

---

## 3. 권장 워크플로우 구조

### 3.1 읽지 않은 메일 목록 요약 후 Slack 공유
- Gmail 시작 노드
- Loop
- LLM
- Slack 도착 노드

### 3.2 중요 메일 목록 요약 후 Notion 저장
- Gmail 시작 노드
- Loop
- LLM
- Notion 도착 노드

### 3.3 중요 메일 목록에서 할 일 추출 후 Notion 저장
- Gmail 시작 노드
- Loop
- LLM
- Notion 도착 노드

---

## 4. FE가 기대하는 템플릿 응답 형태

### 4.1 템플릿 목록 응답

FE는 템플릿 목록에서 아래 필드를 사용합니다.

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

예시:

```json
{
  "id": "tpl-mail-summary-slack",
  "name": "읽지 않은 메일 목록 요약 후 Slack 공유",
  "description": "읽지 않은 메일 목록을 정해진 형식으로 요약해 Slack 채널에 공유합니다.",
  "category": "mail_summary_forward",
  "icon": "gmail",
  "requiredServices": ["gmail", "slack"],
  "isSystem": true,
  "authorId": null,
  "useCount": 0,
  "createdAt": "2026-05-03T10:00:00Z"
}
```

### 4.2 템플릿 상세 응답

상세에서는 목록 필드에 더해 아래가 필요합니다.

- `nodes`
- `edges`

예시:

```json
{
  "id": "tpl-mail-summary-slack",
  "name": "읽지 않은 메일 목록 요약 후 Slack 공유",
  "description": "읽지 않은 메일 목록을 정해진 형식으로 요약해 Slack 채널에 공유합니다.",
  "category": "mail_summary_forward",
  "icon": "gmail",
  "requiredServices": ["gmail", "slack"],
  "isSystem": true,
  "authorId": null,
  "useCount": 0,
  "createdAt": "2026-05-03T10:00:00Z",
  "nodes": [],
  "edges": []
}
```

---

## 5. FE가 기대하는 노드 정의 형태

### 5.1 Gmail 시작 노드 예시

```json
{
  "id": "node_gmail_start",
  "category": "service",
  "type": "gmail",
  "role": "start",
  "position": { "x": 80, "y": 180 },
  "config": {
    "isConfigured": true,
    "service": "gmail",
    "source_mode": "label_emails",
    "target": "UNREAD",
    "target_label": "읽지 않은 메일",
    "target_meta": {
      "systemLabel": true
    },
    "maxResults": 100
  },
  "dataType": null,
  "outputDataType": "EMAIL_LIST",
  "authWarning": false
}
```

### 5.2 Loop 노드 예시

```json
{
  "id": "node_loop",
  "category": "control",
  "type": "loop",
  "role": "middle",
  "position": { "x": 300, "y": 180 },
  "config": {
    "isConfigured": true,
    "targetField": "items",
    "maxIterations": 100,
    "timeout": 300
  },
  "dataType": "EMAIL_LIST",
  "outputDataType": "SINGLE_EMAIL",
  "authWarning": false
}
```

### 5.3 LLM 노드 예시

```json
{
  "id": "node_llm_summary",
  "category": "ai",
  "type": "llm",
  "role": "middle",
  "position": { "x": 520, "y": 180 },
  "config": {
    "isConfigured": true,
    "prompt": "입력된 메일 목록의 모든 메일을 빠짐없이 포함해, 각 메일을 발신자/제목/핵심/액션 형식으로 정리해줘.",
    "model": "gpt-4.1-mini",
    "outputFormat": "text",
    "temperature": 0.3,
    "summaryFormat": "mail_digest_v1",
    "resultMode": "single_aggregated"
  },
  "dataType": "SINGLE_EMAIL",
  "outputDataType": "TEXT",
  "authWarning": false
}
```

### 5.4 Slack 도착 노드 예시

```json
{
  "id": "node_slack_end",
  "category": "service",
  "type": "slack",
  "role": "end",
  "position": { "x": 740, "y": 180 },
  "config": {
    "isConfigured": false,
    "service": "slack",
    "channel": "",
    "message_format": "markdown",
    "header": "메일 요약"
  },
  "dataType": "TEXT",
  "outputDataType": null,
  "authWarning": false
}
```

### 5.5 Notion 도착 노드 예시

```json
{
  "id": "node_notion_end",
  "category": "service",
  "type": "notion",
  "role": "end",
  "position": { "x": 740, "y": 180 },
  "config": {
    "isConfigured": false,
    "service": "notion",
    "target_type": "page",
    "target_id": "",
    "title_template": "메일 요약 - {{date}}"
  },
  "dataType": "TEXT",
  "outputDataType": null,
  "authWarning": false
}
```

### 5.6 Edge 예시

```json
[
  { "source": "node_gmail_start", "target": "node_loop" },
  { "source": "node_loop", "target": "node_llm_summary" },
  { "source": "node_llm_summary", "target": "node_slack_end" }
]
```

---

## 6. 테스트 권장 사항

위치:
- `src/test/java/org/github/flowify/template/TemplateServiceTest.java`

확인 포인트:
- `mail_summary_forward` 카테고리 조회 가능 여부
- 새 시스템 템플릿 instantiate 가능 여부
- 기존 템플릿이 있어도 새 시스템 템플릿이 시드되는지
- Slack 채널 목록 API 응답 검증
- Notion 페이지 목록 API 응답 검증
- Gmail `maxResults` 반영 여부 검증
- 구조화 prompt 기준 결과 포맷 검증

가능하면 `TemplateSeeder` 증분 반영 로직에 대한 테스트도 함께 있으면 좋겠습니다.

---

## 7. 정리

이번 요청의 핵심은 다음 7가지입니다.

1. 메일 요약 템플릿 3종 시스템 시드 추가
2. 기존 upsert 기반 증분 시드 규칙에 맞춰 안정 반영
3. 현재 FE 계약에 맞는 노드 정의와 config shape로 시드 구성
4. Slack 채널 / Notion 페이지 picker용 백엔드 목록 API 추가
5. FastAPI 메일 요약 프롬프트를 구조화 결과 중심으로 구체화
6. Gmail fetch 개수를 config 기반으로 설정화
7. 장기적으로 `메일별 구조화 -> 최종 결과 1개 집계` 구조를 목표로 런타임 개선

필요하시면 FE 쪽에서 실제 화면 기준으로 어떤 식으로 보이는지도 추가로 정리드리겠습니다.
