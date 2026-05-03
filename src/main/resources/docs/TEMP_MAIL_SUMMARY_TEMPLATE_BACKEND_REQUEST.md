# 메일 요약 후 전달 템플릿 Spring 수정 요청서

> **작성일:** 2026-05-03
> **대상:** Spring 백엔드 담당자
> **용도:** 임시 전달용 문서
> **관련 이슈:** `메일 요약 후 전달 템플릿 구체화 및 시스템 템플릿 구현`

---

## 1. 요청 배경

FE에는 템플릿 목록, 템플릿 상세, 템플릿 인스턴스화 화면이 이미 준비되어 있습니다.

다만 시스템 템플릿 데이터는 Spring `TemplateSeeder`가 생성하고 있기 때문에, 백엔드에 새 템플릿이 추가되지 않으면 FE 화면에는 메일 요약 템플릿이 노출되지 않습니다.

현재 백엔드 코드를 기준으로 확인한 문제는 아래와 같습니다.

- `TemplateSeeder`가 `templateRepository.count() > 0`이면 전체 seed를 건너뜁니다.
- 기존 개발 DB에 템플릿이 하나라도 있으면 새 시스템 템플릿이 자동 추가되지 않습니다.
- `TemplateRepository`에는 현재 이름 기준 조회 메서드가 없습니다.
- Gmail은 source/sink catalog에는 존재하지만, Spring OAuth connector 구현은 아직 없습니다.
- FE는 서비스 노드의 `type`을 `gmail`, `slack`, `notion` 같은 실제 서비스 키로 받을 때 가장 안정적으로 표시할 수 있습니다.

이번 요청은 단순히 템플릿 이름만 추가하는 작업이 아니라, 사용자가 템플릿을 선택해 실제 워크플로우로 만들 수 있는 수준의 시스템 템플릿 계약을 맞추는 작업입니다.

---

## 2. 요청 범위

이번 범위에 포함되는 작업은 아래입니다.

1. 메일 요약/전달 시스템 템플릿 3종 추가
2. 기존 DB에도 새 시스템 템플릿이 반영되도록 `TemplateSeeder` 개선
3. 템플릿 노드/엣지를 FE와 FastAPI 실행 계약에 맞게 구성
4. Gmail OAuth connector 추가
5. Gmail source mode와 Slack/Notion sink config의 실행 가능 상태를 명확히 처리
6. seed 및 instantiate 테스트 추가

이번 범위에서 제외해도 되는 작업은 아래입니다.

- Gmail 메일 본문 preview UI 개선
- Slack/Notion target picker FE 추가 구현
- 민감 데이터 마스킹 정책
- 템플릿 상세 화면의 디자인 변경

---

## 3. 시스템 템플릿 3종 추가 요청

위치:

- `src/main/java/org/github/flowify/config/TemplateSeeder.java`

추가할 템플릿은 아래 3종입니다.

| 템플릿명 | 설명 | requiredServices | 권장 Gmail source |
| --- | --- | --- | --- |
| 읽지 않은 메일 요약 후 Slack 공유 | 읽지 않은 메일 목록을 요약해 Slack 채널로 공유 | `gmail`, `slack` | `label_emails` + `target = UNREAD` |
| 중요 메일 요약 후 Notion 저장 | 중요 메일 목록을 요약해 Notion에 저장 | `gmail`, `notion` | `label_emails` + `target = IMPORTANT` |
| 중요 메일 할 일 추출 후 Notion 저장 | 중요 메일에서 해야 할 일을 추출해 Notion에 저장 | `gmail`, `notion` | `label_emails` + `target = IMPORTANT` |

공통 metadata 기준:

```json
{
  "category": "mail_summary_forward",
  "icon": "gmail",
  "isSystem": true,
  "authorId": null
}
```

주의:

- `requiredServices`는 화면 표시와 OAuth 연결 상태 판단에 사용됩니다.
- `requiredServices`에는 도메인명이나 추상 타입이 아니라 실제 서비스 키를 넣어야 합니다.
- 예: `gmail`, `slack`, `notion`

---

## 4. TemplateSeeder 개선 요청

### 4.1 현재 문제

현재 `TemplateSeeder`는 템플릿이 하나라도 있으면 전체 seed를 종료합니다.

이 구조에서는 운영/개발 DB에 기존 템플릿이 존재하는 순간 새 시스템 템플릿이 영구적으로 추가되지 않습니다.

### 4.2 요청 방향

전체 skip 방식 대신 시스템 템플릿 단위 upsert로 변경해 주세요.

권장 repository 메서드:

```java
Optional<Template> findByNameAndIsSystem(String name, boolean isSystem);
```

가능하면 `findByName`보다 `findByNameAndIsSystem`을 권장합니다. 사용자 생성 템플릿과 시스템 템플릿 이름이 우연히 겹치는 경우를 피하기 위함입니다.

권장 처리:

```java
private void upsertSystemTemplate(Template seedTemplate) {
    templateRepository
        .findByNameAndIsSystem(seedTemplate.getName(), true)
        .ifPresentOrElse(existing -> {
            seedTemplate.setId(existing.getId());
            seedTemplate.setUseCount(existing.getUseCount());
            seedTemplate.setCreatedAt(existing.getCreatedAt());
            templateRepository.save(seedTemplate);
        }, () -> templateRepository.save(seedTemplate));
}
```

정책:

- 시스템 템플릿 정의 변경은 seed 재실행 시 반영되어도 됩니다.
- 단, 기존 `id`, `createdAt`, `useCount`는 유지하는 편이 좋습니다.
- 사용자 생성 템플릿은 seed 과정에서 덮어쓰지 않습니다.

---

## 5. 템플릿 노드 계약

### 5.1 공통 노드 규칙

FE와 백엔드 계약 기준으로 새 템플릿 노드는 아래 규칙을 따라야 합니다.

| 노드 종류 | category | type | role |
| --- | --- | --- | --- |
| Gmail 시작 노드 | `service` | `gmail` | `start` |
| Slack 도착 노드 | `service` | `slack` | `end` |
| Notion 도착 노드 | `service` | `notion` | `end` |
| Loop 노드 | `control` | `loop` | `middle` |
| LLM 노드 | `ai` | `llm` | `middle` |

중요:

- 시작/도착 서비스 노드의 `type`은 반드시 실제 서비스 키여야 합니다.
- 기존 템플릿 일부처럼 `category = communication`, `category = storage` 형태로 넣으면 백엔드의 `requiredServices` 추출 기준과 어긋날 수 있습니다.
- 모든 노드는 `position`을 가져야 합니다. `position`이 없으면 React Flow preview에서 오류가 날 수 있습니다.
- edge에는 `source`, `target`이 정확히 들어가야 합니다. `id`는 선택값이어도 되지만, 가능하면 안정적인 id를 넣어 주세요.

### 5.2 데이터 타입 흐름

메일 목록을 하나씩 처리하는 템플릿의 기본 흐름은 아래가 맞습니다.

```text
Gmail Start
  outputDataType: EMAIL_LIST

Loop
  dataType: EMAIL_LIST
  outputDataType: SINGLE_EMAIL

LLM
  dataType: SINGLE_EMAIL
  outputDataType: TEXT

Slack/Notion
  dataType: TEXT
```

주의:

- Loop의 `outputDataType`은 `TEXT`가 아니라 `SINGLE_EMAIL`이어야 합니다.
- LLM의 `dataType`은 `TEXT`가 아니라 `SINGLE_EMAIL`이어야 합니다.
- 이 흐름은 현재 `mapping_rules.json`의 `EMAIL_LIST -> SINGLE_EMAIL -> TEXT` 처리 방식과 맞습니다.

---

## 6. Gmail source mode 계약

현재 Spring source catalog에는 Gmail source가 존재합니다.

확인된 Gmail source mode:

| source_mode | output type | 설명 |
| --- | --- | --- |
| `single_email` | `SINGLE_EMAIL` | 특정 메일 1건 |
| `new_email` | `SINGLE_EMAIL` | 새 메일 이벤트 |
| `sender_email` | `SINGLE_EMAIL` | 특정 발신자 메일 |
| `starred_email` | `SINGLE_EMAIL` | 별표 메일 |
| `label_emails` | `EMAIL_LIST` | 특정 라벨의 메일 목록 |
| `attachment_email` | `FILE_LIST` | 첨부파일 있는 메일 |

이번 템플릿은 여러 메일을 처리해야 하므로 `label_emails` 사용을 권장합니다.

권장 config:

```json
{
  "service": "gmail",
  "source_mode": "label_emails",
  "target": "UNREAD",
  "target_label": "읽지 않은 메일",
  "target_meta": {
    "systemLabel": true
  }
}
```

중요 메일의 경우:

```json
{
  "service": "gmail",
  "source_mode": "label_emails",
  "target": "IMPORTANT",
  "target_label": "중요 메일",
  "target_meta": {
    "systemLabel": true
  }
}
```

백엔드 확인 필요:

- FastAPI Gmail handler가 `label_emails`의 `target = UNREAD`, `target = IMPORTANT`를 Gmail system label로 처리할 수 있는지 확인이 필요합니다.
- 현재 실행부가 `IMPORTANT`를 지원하지 않는다면, 백엔드/FastAPI 쪽에서 `label_emails` target으로 system label을 허용해 주세요.
- 만약 `UNREAD`, `IMPORTANT`를 `label_emails`로 처리하지 않을 정책이라면, `unread_email`, `important_email` source mode를 catalog와 실행부에 명시적으로 추가해 주세요.

---

## 7. Slack/Notion 도착 노드 설정 상태

Slack/Notion sink catalog 기준으로 도착 노드는 필수 설정값이 있습니다.

Slack 필수 config:

- `channel`

Notion 필수 config:

- `target_type`
- `target_id`

따라서 시스템 템플릿 seed 단계에서 채널/페이지를 특정할 수 없다면 도착 노드는 `isConfigured = false`가 맞습니다.

권장 정책:

```text
Gmail 시작 노드
- 템플릿에서 source_mode/target을 확정할 수 있으므로 isConfigured = true 가능

Loop/LLM 노드
- 템플릿에서 prompt/처리 방식을 확정할 수 있으므로 isConfigured = true 가능

Slack/Notion 도착 노드
- 사용자별 채널/페이지 선택이 필요하므로 isConfigured = false 권장
```

주의:

- `channel = null`인데 `isConfigured = true`이면 FE에는 설정 완료처럼 보이지만 실행 시 실패할 수 있습니다.
- `target_id = null`인데 `isConfigured = true`인 Notion 노드도 동일하게 위험합니다.

---

## 8. 노드 예시

### 8.1 Gmail 시작 노드

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
    }
  },
  "dataType": null,
  "outputDataType": "EMAIL_LIST",
  "authWarning": false
}
```

### 8.2 Loop 노드

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

### 8.3 LLM 요약 노드

```json
{
  "id": "node_llm_summary",
  "category": "ai",
  "type": "llm",
  "role": "middle",
  "position": { "x": 520, "y": 180 },
  "config": {
    "isConfigured": true,
    "prompt": "아래 메일의 핵심 내용을 3줄로 요약해줘. 발신자, 제목, 주요 내용을 포함해줘.",
    "model": "gpt-4.1-mini",
    "outputFormat": "text",
    "temperature": 0.3
  },
  "dataType": "SINGLE_EMAIL",
  "outputDataType": "TEXT",
  "authWarning": false
}
```

### 8.4 LLM 할 일 추출 노드

```json
{
  "id": "node_llm_todos",
  "category": "ai",
  "type": "llm",
  "role": "middle",
  "position": { "x": 520, "y": 180 },
  "config": {
    "isConfigured": true,
    "prompt": "아래 메일에서 사용자가 해야 할 일을 추출해줘. 각 항목은 할 일, 마감일, 관련 발신자를 포함해줘.",
    "model": "gpt-4.1-mini",
    "outputFormat": "text",
    "temperature": 0.2
  },
  "dataType": "SINGLE_EMAIL",
  "outputDataType": "TEXT",
  "authWarning": false
}
```

### 8.5 Slack 도착 노드

```json
{
  "id": "node_slack_end",
  "category": "service",
  "type": "slack",
  "role": "end",
  "position": { "x": 760, "y": 180 },
  "config": {
    "isConfigured": false,
    "service": "slack",
    "channel": null,
    "message_format": "markdown",
    "header": "메일 요약"
  },
  "dataType": "TEXT",
  "outputDataType": null,
  "authWarning": false
}
```

### 8.6 Notion 도착 노드

```json
{
  "id": "node_notion_end",
  "category": "service",
  "type": "notion",
  "role": "end",
  "position": { "x": 760, "y": 180 },
  "config": {
    "isConfigured": false,
    "service": "notion",
    "target_type": null,
    "target_id": null,
    "title_template": "메일 요약 - {{date}}"
  },
  "dataType": "TEXT",
  "outputDataType": null,
  "authWarning": false
}
```

### 8.7 Edge 예시

```json
[
  {
    "id": "edge_gmail_to_loop",
    "source": "node_gmail_start",
    "target": "node_loop"
  },
  {
    "id": "edge_loop_to_llm",
    "source": "node_loop",
    "target": "node_llm_summary"
  },
  {
    "id": "edge_llm_to_slack",
    "source": "node_llm_summary",
    "target": "node_slack_end"
  }
]
```

---

## 9. 템플릿별 권장 그래프

### 9.1 읽지 않은 메일 요약 후 Slack 공유

```text
Gmail(label_emails, UNREAD)
  -> Loop(EMAIL_LIST -> SINGLE_EMAIL)
  -> LLM summarize(SINGLE_EMAIL -> TEXT)
  -> Slack(TEXT)
```

metadata:

```json
{
  "name": "읽지 않은 메일 요약 후 Slack 공유",
  "description": "읽지 않은 메일을 하나씩 요약해 Slack 채널로 공유합니다.",
  "category": "mail_summary_forward",
  "icon": "gmail",
  "requiredServices": ["gmail", "slack"],
  "isSystem": true
}
```

### 9.2 중요 메일 요약 후 Notion 저장

```text
Gmail(label_emails, IMPORTANT)
  -> Loop(EMAIL_LIST -> SINGLE_EMAIL)
  -> LLM summarize(SINGLE_EMAIL -> TEXT)
  -> Notion(TEXT)
```

metadata:

```json
{
  "name": "중요 메일 요약 후 Notion 저장",
  "description": "중요 메일을 하나씩 요약해 Notion 페이지 또는 데이터베이스에 저장합니다.",
  "category": "mail_summary_forward",
  "icon": "gmail",
  "requiredServices": ["gmail", "notion"],
  "isSystem": true
}
```

### 9.3 중요 메일 할 일 추출 후 Notion 저장

```text
Gmail(label_emails, IMPORTANT)
  -> Loop(EMAIL_LIST -> SINGLE_EMAIL)
  -> LLM extract_todos(SINGLE_EMAIL -> TEXT)
  -> Notion(TEXT)
```

metadata:

```json
{
  "name": "중요 메일 할 일 추출 후 Notion 저장",
  "description": "중요 메일에서 해야 할 일을 추출해 Notion에 정리합니다.",
  "category": "mail_summary_forward",
  "icon": "gmail",
  "requiredServices": ["gmail", "notion"],
  "isSystem": true
}
```

---

## 10. Gmail OAuth 연동 요청

현재 상태:

- FastAPI 문서와 catalog에는 Gmail source/sink가 있습니다.
- FE도 Gmail 서비스를 노드 서비스로 인식합니다.
- Spring OAuth connector 목록에는 Gmail 전용 connector가 없습니다.

필요 작업:

- Gmail용 `ExternalServiceConnector` 구현
- Gmail OAuth authorize URL 생성
- Gmail OAuth callback 처리
- Gmail access token 저장
- Gmail refresh token 저장 및 갱신
- `OAuthTokenService`에서 `gmail` service key 처리
- auth status API에서 `gmail` 연결 상태 반환

권장 위치:

- `src/main/java/org/github/flowify/oauth/service/GmailConnector.java`
- `src/main/java/org/github/flowify/oauth/service/OAuthTokenService.java`
- `src/main/resources/application.yml`

권장 설정:

```yaml
app:
  oauth:
    gmail:
      client-id: ${GMAIL_CLIENT_ID}
      client-secret: ${GMAIL_CLIENT_SECRET}
      redirect-uri: ${GMAIL_REDIRECT_URI}
      scopes:
        - https://www.googleapis.com/auth/gmail.readonly
```

추가 고려:

- Gmail sink까지 실제 사용하려면 `gmail.send` scope도 필요합니다.
- 이번 3개 템플릿은 Gmail source만 사용하므로 최소 scope는 `gmail.readonly`입니다.
- refresh token 발급을 위해 Google OAuth 요청에 `access_type=offline`, `prompt=consent` 처리가 필요합니다.

---

## 11. FE 영향

백엔드 Gmail OAuth가 추가되면 FE에서도 아래 작업을 후속으로 진행합니다.

- OAuth 연결 지원 목록에 `gmail` 추가
- Gmail 연결 상태 표시 확인
- 템플릿 상세에서 `requiredServices: ["gmail", ...]` 연결 상태 확인
- Gmail source node의 `target_label` 표시 확인
- Slack/Notion 도착 노드가 `isConfigured = false`일 때 설정 필요 상태로 보이는지 확인

즉, 백엔드에서 Gmail OAuth connector와 system template seed가 들어오면 FE는 바로 연결 목록과 템플릿 생성 흐름을 이어서 맞출 수 있습니다.

---

## 12. 테스트 요청

### 12.1 Seeder 테스트

확인 항목:

- DB에 기존 템플릿이 있어도 메일 템플릿 3종이 추가된다.
- 같은 seed를 여러 번 실행해도 중복 템플릿이 생기지 않는다.
- 기존 시스템 템플릿의 `useCount`, `createdAt`이 불필요하게 초기화되지 않는다.
- 사용자 생성 템플릿이 seed 과정에서 덮어써지지 않는다.

### 12.2 Template 조회 테스트

확인 항목:

- `GET /api/templates?category=mail_summary_forward`로 3종 조회 가능
- 목록 응답에 `requiredServices`, `icon`, `isSystem`, `useCount`, `createdAt` 포함
- 상세 응답에 `nodes`, `edges` 포함
- 모든 노드에 `position` 존재

### 12.3 Instantiate 테스트

확인 항목:

- 각 템플릿이 workflow로 instantiate 된다.
- 생성된 workflow의 시작 노드 `type = gmail`
- 생성된 workflow의 도착 노드 `type = slack` 또는 `notion`
- `requiredServices`가 실제 서비스 키 기준으로 추출된다.
- Slack/Notion 도착 노드는 target 미선택 상태에서 `isConfigured = false`로 유지된다.

### 12.4 Gmail OAuth 테스트

확인 항목:

- `gmail` 연결 URL 생성 가능
- callback 후 token 저장 가능
- auth status API에서 `gmail` 연결 상태 확인 가능
- refresh token이 있을 경우 access token 갱신 가능

---

## 13. 완료 기준

이번 작업은 아래 조건을 만족하면 완료로 봅니다.

1. 새 시스템 템플릿 3종이 기존 DB에도 중복 없이 seed된다.
2. 템플릿 목록에서 `mail_summary_forward` 카테고리로 조회된다.
3. 템플릿 상세의 모든 노드에 `position`이 존재한다.
4. 서비스 노드는 `category = service`, `type = gmail/slack/notion` 규칙을 따른다.
5. 메일 목록 처리 흐름은 `EMAIL_LIST -> SINGLE_EMAIL -> TEXT`로 구성된다.
6. Slack/Notion target이 비어 있으면 도착 노드는 `isConfigured = false`다.
7. Gmail OAuth connector가 추가되어 사용자가 Gmail을 연동할 수 있다.
8. Gmail 연결 후 템플릿을 workflow로 생성할 수 있다.

---

## 14. 정리

핵심 요청은 아래입니다.

1. 메일 요약/전달 시스템 템플릿 3종 추가
2. `TemplateSeeder`를 이름 기준 system template upsert 방식으로 개선
3. 노드 계약을 `service/gmail`, `service/slack`, `service/notion` 기준으로 정리
4. Gmail source는 `label_emails` + system label target 방식으로 구성
5. Loop/LLM 데이터 타입을 `EMAIL_LIST -> SINGLE_EMAIL -> TEXT`로 수정
6. Slack/Notion 도착 노드는 사용자 target 선택 전까지 `isConfigured = false` 유지
7. Gmail OAuth connector 추가
