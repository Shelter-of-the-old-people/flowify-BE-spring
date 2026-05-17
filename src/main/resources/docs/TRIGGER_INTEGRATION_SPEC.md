# Trigger 연동 통합 명세 (TRIGGER_INTEGRATION_SPEC)

> **대상 독자:** Spring Boot 팀 · FastAPI 팀 · Frontend 팀
> **최종 수정:** 2026-04-26
> **관련 문서:** [FASTAPI_CONTRACT_SPEC.md](FASTAPI_CONTRACT_SPEC.md) · [FASTAPI_IMPLEMENTATION_GUIDE.md](FASTAPI_IMPLEMENTATION_GUIDE.md)

---

## 목차

1. [Trigger 연동 현황 요약](#1-trigger-연동-현황-요약)
2. [Trigger 타입별 상세 스펙](#2-trigger-타입별-상세-스펙)
3. [현재 Wire Contract (Spring → FastAPI)](#3-현재-wire-contract-spring--fastapi)
4. [Phase 2 구현 계획 — 팀별 역할 분리](#4-phase-2-구현-계획--팀별-역할-분리)
5. [Schedule Trigger 구현 가이드](#5-schedule-trigger-구현-가이드)
6. [Webhook Trigger 구현 가이드](#6-webhook-trigger-구현-가이드)
7. [팀별 TODO 체크리스트](#7-팀별-todo-체크리스트)
8. [변경 이력](#8-변경-이력)

---

## 1. Trigger 연동 현황 요약

### 1.1 구현 상태 표

| 항목 | 상태 | 담당 |
|------|------|------|
| `TriggerConfig` 엔티티 저장/조회 | ✅ 완료 | Spring |
| `WorkflowTranslator`에서 trigger 필드 FastAPI payload에 포함 | ✅ 완료 | Spring |
| Source catalog `trigger_kind` 정의 | ✅ 완료 | Spring |
| Manual trigger (실행 버튼 → FastAPI) | ✅ 완료 | Spring + FastAPI |
| FastAPI execute endpoint `trigger.type` 분기 처리 | ⚠️ 부분 완료 | FastAPI |
| Schedule trigger — cron 기반 자동 실행 | ❌ 미구현 | Spring (주도) |
| Webhook trigger — 외부 이벤트 수신 및 실행 | ❌ 미구현 | Spring (주도) |
| FastAPI → Spring 완료 콜백 (callback) | ❌ 미구현 (폴링만) | Spring + FastAPI |

### 1.2 Phase 1 완료 흐름 (Manual Trigger)

```
[Frontend]
  └─ POST /api/workflows/{id}/execute
       │
[Spring Boot - ExecutionService.executeWorkflow()]
  ├─ WorkflowValidator.validateForExecution()
  ├─ collectServiceTokens()         ← OAuth 토큰 수집
  ├─ WorkflowTranslator.toRuntimeModel()  ← trigger 필드 포함 (line 47-52)
  └─ FastApiClient.execute()
       │
       ▼  POST /api/v1/workflows/{id}/execute  (X-User-ID: {userId})
[FastAPI]
  └─ { "execution_id": "exec_xxx" }
       │
[Spring Boot]
  └─ executionId 클라이언트에 반환
       │
[Frontend]
  └─ GET /api/workflows/{id}/executions/{execId}  (폴링으로 상태 확인)
```

### 1.3 Phase 2 미구현 상세

#### Schedule Trigger 미구현 이유
- `TriggerConfig` 에 `type: "schedule"`, `config.cron` 은 저장되지만,
  Spring에 이를 읽어 `TaskScheduler`에 등록하는 코드가 없음.
- 서버 재시작 시 기존 스케줄도 사라짐 (DB 재등록 로직 없음).

#### Webhook Trigger 미구현 이유
- 외부 서비스(GitHub, Slack 등)에서 콜백을 받을 엔드포인트(`/api/webhooks/{webhookId}`)가 없음.
- webhookId 발급 및 HMAC 서명 검증 로직이 없음.
- FastAPI로 event_payload 전달 계약이 미정.

#### FastAPI 콜백 미구현 이유
- 현재는 클라이언트가 폴링(`GET .../executions/{execId}`)으로 상태를 확인.
- FastAPI → Spring `POST /api/internal/executions/{execId}/complete` 엔드포인트가 없음.

---

## 2. Trigger 타입별 상세 스펙

### 2.1 Manual Trigger

```json
{
  "type": "manual",
  "config": {}
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `type` | string | ✅ | 항상 `"manual"` |
| `config` | object | ✅ | 빈 객체 `{}` |

**동작:** Frontend에서 실행 버튼 클릭 → `POST /api/workflows/{id}/execute`.

---

### 2.2 Schedule Trigger

```json
{
  "type": "schedule",
  "config": {
    "cron": "0 9 * * *",
    "timezone": "Asia/Seoul"
  }
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `type` | string | ✅ | 항상 `"schedule"` |
| `config.cron` | string | ✅ | Unix cron 표현식 (5자리: `분 시 일 월 요일`) |
| `config.timezone` | string | ✅ | IANA 타임존 식별자 (예: `Asia/Seoul`, `UTC`) |

**cron 표현식 예시:**

| cron | 의미 |
|------|------|
| `0 9 * * *` | 매일 오전 9시 |
| `0 9 * * 1` | 매주 월요일 오전 9시 |
| `0 */6 * * *` | 6시간마다 |
| `30 8 1 * *` | 매월 1일 오전 8시 30분 |

---

### 2.3 Webhook Trigger

```json
{
  "type": "webhook",
  "config": {
    "webhookId": "wh_a1b2c3d4e5f6",
    "secret": "$2a$10$hashed_secret_here",
    "endpoint": "/api/webhooks/wh_a1b2c3d4e5f6",
    "source_service": "github"
  }
}
```

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `type` | string | ✅ | 항상 `"webhook"` |
| `config.webhookId` | string | ✅ | 서버가 발급한 고유 ID (`wh_` 접두사) |
| `config.secret` | string | ✅ | HMAC 서명 검증용 BCrypt 해시 (평문 저장 금지) |
| `config.endpoint` | string | 읽기전용 | 발급된 엔드포인트 URL (Frontend 표시용) |
| `config.source_service` | string | 선택 | 어느 외부 서비스 webhook인지 식별 (예: `github`, `slack`) |

---

## 3. 현재 Wire Contract (Spring → FastAPI)

### 3.1 현재 Payload 구조 (실제 코드 기반)

**코드 경로:**
- `FastApiClient.java:24` — requestBody 조립
- `WorkflowTranslator.java:47-52` — trigger 필드 삽입
- `ExecutionService.java:48-49` — runtimeModel + serviceTokens 전달

**현재 전송 payload (Manual, 최소 예시):**

```json
POST /api/v1/workflows/{workflowId}/execute
X-User-ID: {userId}

{
  "workflow": {
    "id": "wf_123",
    "name": "내 워크플로우",
    "userId": "user_456",
    "trigger": {
      "type": "manual",
      "config": {}
    },
    "nodes": [
      {
        "id": "node_1",
        "category": "SOURCE",
        "type": "GMAIL",
        "label": "Gmail 읽기",
        "config": { "source_mode": "read", "target": "inbox" },
        "dataType": "email",
        "outputDataType": "email",
        "role": "start",
        "runtime_type": "input",
        "runtime_source": {
          "service": "GMAIL",
          "canonical_input_type": "email",
          "mode": "read",
          "target": "inbox"
        }
      },
      {
        "id": "node_2",
        "category": "AI",
        "type": "AI",
        "label": "요약",
        "config": { "action": "summarize" },
        "dataType": "email",
        "outputDataType": "text",
        "role": "middle",
        "runtime_type": "llm",
        "runtime_config": {
          "node_type": "AI",
          "output_data_type": "text",
          "action": "summarize"
        }
      },
      {
        "id": "node_3",
        "category": "SINK",
        "type": "SLACK",
        "label": "Slack 전송",
        "config": { "channel": "#general" },
        "dataType": "text",
        "outputDataType": "text",
        "role": "end",
        "runtime_type": "output",
        "runtime_sink": {
          "service": "SLACK",
          "config": { "channel": "#general" }
        }
      }
    ],
    "edges": [
      { "id": "edge_1", "source": "node_1", "target": "node_2" },
      { "id": "edge_2", "source": "node_2", "target": "node_3" }
    ]
  },
  "service_tokens": {
    "GMAIL": "ya29.a0AfH6SMC...",
  }
}
```

**FastAPI 응답:**
```json
{ "execution_id": "exec_abc123" }
```

### 3.2 Phase 2 — Schedule/Webhook 포함 payload 예시

**Schedule Trigger payload:**
```json
{
  "workflow": {
    "trigger": {
      "type": "schedule",
      "config": {
        "cron": "0 9 * * *",
        "timezone": "Asia/Seoul"
      }
    },
    "nodes": [...],
    "edges": [...]
  },
  "service_tokens": { "GMAIL": "ya29..." }
}
```

**Webhook Trigger payload (Spring이 webhook 수신 후 FastAPI 호출 시):**
```json
{
  "workflow": {
    "trigger": {
      "type": "webhook",
      "config": {
        "webhookId": "wh_a1b2c3",
        "source_service": "github",
        "event_payload": {
          "action": "push",
          "repository": "my-repo",
          "ref": "refs/heads/main"
        }
      }
    },
    "nodes": [...],
    "edges": [...]
  },
  "service_tokens": {}
}
```

> **FastAPI 팀 임시 처리 권장안:**
> Phase 1 동안 trigger.type이 `"schedule"` 또는 `"webhook"`이어도 `"manual"` 과 동일하게 처리.
> trigger 분기 처리 코드를 추가하되, 미지원 type은 `200 OK`로 무시하거나 경고 로그만 출력.

---

## 4. Phase 2 구현 계획 — 팀별 역할 분리

### 4.1 역할 매트릭스

| 기능 | Spring | FastAPI | Frontend |
|------|--------|---------|----------|
| Schedule cron 등록/해제 API | **주도** | 수신 | 설정 UI |
| 서버 재시작 시 스케줄 DB 재등록 | **주도** | - | - |
| Schedule → FastAPI 자동 실행 호출 | **주도** | 수신 | - |
| Webhook 엔드포인트 발급 (`POST /api/workflows/{id}/webhook`) | **주도** | - | 호출 |
| Webhook 수신 엔드포인트 (`POST /api/webhooks/{webhookId}`) | **주도** | - | - |
| HMAC 서명 검증 | **주도** | - | - |
| Webhook 이벤트 → FastAPI execute 호출 | **주도** | 수신 | - |
| `trigger.type` 분기 처리 | 계약 제공 | **주도** | - |
| `trigger.config.event_payload` → InputNodeStrategy | - | **주도** | - |
| 실행 완료 콜백 수신 (`POST /api/internal/executions/{id}/complete`) | **주도** | 호출 | - |
| Schedule trigger 설정 UI | - | - | **주도** |
| Webhook URL 표시 및 복사 UI | - | - | **주도** |
| Trigger 타입 선택 드롭다운 | - | - | **주도** |

### 4.2 서비스 간 연동 흐름 (Phase 2)

**Schedule Trigger 흐름:**
```
[Spring ScheduleTriggerService - cron 발화]
  └─ ExecutionService.executeScheduled(workflowId)
       └─ FastApiClient.execute(...)
            └─ [FastAPI] 실행
                 └─ (완료 시) POST /api/internal/executions/{execId}/complete
                      └─ [Spring InternalExecutionController] 상태 업데이트
```

**Webhook Trigger 흐름:**
```
[외부 서비스 - GitHub/Slack/etc.]
  └─ POST /api/webhooks/{webhookId}  (서명 포함)
       │
[Spring WebhookReceiveController] ← 인증 제외 (permitAll)
  ├─ WebhookService.verifySignature()  ← HMAC-SHA256 검증
  ├─ workflowId 조회 (webhookId → workflow)
  ├─ event_payload 추출
  └─ ExecutionService.executeFromWebhook(workflowId, eventPayload)
       └─ FastApiClient.execute(...)  ← trigger.config.event_payload 포함
            └─ [FastAPI] InputNodeStrategy에서 event_payload 활용
```

---

## 5. Schedule Trigger 구현 가이드

### 5.1 Spring 구현

#### 5.1.1 SchedulingConfig.java (신규)

```java
@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("schedule-trigger-");
        scheduler.initialize();
        return scheduler;
    }
}
```

#### 5.1.2 ScheduleTriggerService.java (신규)

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleTriggerService {

    private final TaskScheduler taskScheduler;
    private final WorkflowRepository workflowRepository;
    private final ExecutionService executionService;

    // workflowId → ScheduledFuture 매핑 (메모리 레지스트리)
    private final Map<String, ScheduledFuture<?>> registry = new ConcurrentHashMap<>();

    @PostConstruct
    public void reloadSchedulesFromDb() {
        // 서버 재시작 시 DB의 활성 schedule workflow 전부 재등록
        List<Workflow> workflows = workflowRepository
            .findByTriggerTypeAndIsActive("schedule", true);
        workflows.forEach(wf -> {
            try {
                registerSchedule(wf.getId(),
                    (String) wf.getTrigger().getConfig().get("cron"),
                    (String) wf.getTrigger().getConfig().get("timezone"));
            } catch (Exception e) {
                log.warn("스케줄 재등록 실패 workflowId={}", wf.getId(), e);
            }
        });
        log.info("서버 재시작 시 {} 개 스케줄 재등록 완료", workflows.size());
    }

    public void registerSchedule(String workflowId, String cron, String timezone) {
        unregisterSchedule(workflowId); // 기존 스케줄 제거 후 재등록
        ZoneId zone = ZoneId.of(timezone != null ? timezone : "UTC");
        CronTrigger trigger = new CronTrigger(cron, zone);

        ScheduledFuture<?> future = taskScheduler.schedule(
            () -> executionService.executeScheduled(workflowId), trigger);
        registry.put(workflowId, future);
        log.info("스케줄 등록: workflowId={}, cron={}, tz={}", workflowId, cron, timezone);
    }

    public void unregisterSchedule(String workflowId) {
        ScheduledFuture<?> existing = registry.remove(workflowId);
        if (existing != null) {
            existing.cancel(false);
            log.info("스케줄 해제: workflowId={}", workflowId);
        }
    }
}
```

#### 5.1.3 WorkflowService 연동 포인트

- `activateWorkflow(id)` 또는 `updateWorkflow(id, req)` 내부에서:
  - trigger.type == "schedule" AND isActive == true → `scheduleTriggerService.registerSchedule()`
  - isActive == false 또는 workflow 삭제 → `scheduleTriggerService.unregisterSchedule()`

#### 5.1.4 ExecutionService 신규 메서드

```java
// 스케줄/웹훅에서 호출되는 내부 실행 (userId 없이 시스템 실행)
public String executeScheduled(String workflowId) {
    Workflow workflow = workflowService.findWorkflowOrThrow(workflowId);
    String systemUserId = workflow.getUserId(); // 소유자 대신 실행
    Map<String, String> serviceTokens = collectServiceTokens(systemUserId, workflow.getNodes());
    Map<String, Object> runtimeModel = workflowTranslator.toRuntimeModel(workflow);
    return fastApiClient.execute(workflowId, systemUserId, runtimeModel, serviceTokens);
}
```

### 5.2 FastAPI 구현 (선택적)

FastAPI에서 APScheduler를 사용해 직접 스케줄을 관리하는 것도 가능하지만,
**Spring이 스케줄을 발화하고 FastAPI는 execute 요청을 수신하는 방식**을 권장.

APScheduler가 필요한 경우:
```python
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger
import pytz

scheduler = AsyncIOScheduler()

def add_schedule(workflow_id: str, cron: str, timezone: str):
    zone = pytz.timezone(timezone)
    scheduler.add_job(
        execute_workflow,
        CronTrigger.from_crontab(cron, timezone=zone),
        args=[workflow_id],
        id=workflow_id,
        replace_existing=True
    )
```

---

## 6. Webhook Trigger 구현 가이드

### 6.1 Spring 구현

#### 6.1.1 WebhookController.java (신규) — Webhook 발급/무효화

```
POST   /api/workflows/{id}/webhook    → webhookId 발급, TriggerConfig에 저장
DELETE /api/workflows/{id}/webhook    → webhookId 무효화, TriggerConfig 초기화
GET    /api/workflows/{id}/webhook    → 현재 webhook 정보 조회
```

**발급 응답 예시:**
```json
{
  "webhookId": "wh_a1b2c3d4e5f6",
  "endpoint": "https://api.flowify.io/api/webhooks/wh_a1b2c3d4e5f6",
  "secret": "plain_secret_for_display_once",
  "note": "이 secret은 다시 표시되지 않습니다. 안전한 곳에 보관하세요."
}
```

> secret은 발급 시 1회만 평문 반환, DB에는 BCrypt 해시로 저장.

#### 6.1.2 WebhookReceiveController.java (신규) — 외부 이벤트 수신

```
POST /api/webhooks/{webhookId}
```

- **인증 제외:** `SecurityConfig`에서 `/api/webhooks/**` → `permitAll()`
- HMAC 서명 헤더: `X-Hub-Signature-256` (GitHub 호환) 또는 `X-Flowify-Signature`

#### 6.1.3 WebhookService.java (신규) — 서명 검증 + 실행 트리거

```java
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WorkflowRepository workflowRepository;
    private final ExecutionService executionService;

    public String processWebhook(String webhookId, String signature,
                                  String rawBody, Map<String, Object> payload) {
        Workflow workflow = workflowRepository
            .findByTriggerConfigWebhookId(webhookId)
            .orElseThrow(() -> new BusinessException(ErrorCode.WEBHOOK_NOT_FOUND));

        verifySignature(webhookId, signature, rawBody, workflow);

        return executionService.executeFromWebhook(workflow.getId(), payload);
    }

    private void verifySignature(String webhookId, String signature,
                                  String rawBody, Workflow workflow) {
        String storedHash = (String) workflow.getTrigger().getConfig().get("secret");
        String expected = computeHmacSha256(rawBody, storedHash);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }
    }
}
```

#### 6.1.4 SecurityConfig 수정

```java
.requestMatchers("/api/webhooks/**").permitAll()   // 추가
.requestMatchers("/api/internal/**").hasRole("INTERNAL") // 내부 콜백 보호
```

#### 6.1.5 InternalExecutionController.java (신규) — FastAPI 완료 콜백 수신

```
POST /api/internal/executions/{execId}/complete
```

**요청 body:**
```json
{
  "status": "completed",
  "output": { ... },
  "duration_ms": 1250
}
```

**처리:** MongoDB `workflow_executions` 컬렉션의 해당 execution 상태 업데이트.

### 6.2 외부 서비스별 Webhook 등록 가이드

| 서비스 | 등록 방법 | 서명 헤더 |
|--------|-----------|-----------|
| GitHub | Settings → Webhooks → Add webhook | `X-Hub-Signature-256` |
| Slack | Slack App → Event Subscriptions → Request URL | `X-Slack-Signature` |
| Notion | Notion API → Webhooks (베타) | 서비스별 상이 |
| 직접 연동 | — | `X-Flowify-Signature` |

**GitHub Webhook 등록 예시:**
```
Payload URL: https://api.flowify.io/api/webhooks/wh_a1b2c3
Content type: application/json
Secret: [발급 시 표시된 plain secret]
Events: push, pull_request (선택)
```

**서명 검증 알고리즘 (HMAC-SHA256):**
```
signature = "sha256=" + HMAC-SHA256(rawBody, secret)
비교: 수신된 X-Hub-Signature-256 헤더와 timing-safe 비교
```

### 6.3 FastAPI — event_payload 처리

Webhook trigger로 실행된 경우 `trigger.config.event_payload`에 외부 이벤트 데이터가 포함됨.

```python
def execute_workflow(workflow: dict, service_tokens: dict):
    trigger = workflow.get("trigger", {})
    trigger_type = trigger.get("type", "manual")

    if trigger_type == "webhook":
        event_payload = trigger.get("config", {}).get("event_payload", {})
        # InputNodeStrategy에 event_payload를 초기 데이터로 주입
        initial_data = event_payload
    elif trigger_type == "schedule":
        initial_data = {}  # 스케줄은 초기 데이터 없음, source node가 직접 수집
    else:  # manual
        initial_data = {}

    # 노드 실행 시 input node에 initial_data 전달
```

---

## 7. 팀별 TODO 체크리스트

### 7.1 Spring 팀

**Phase 1 완료 항목:**
- [x] `TriggerConfig` 엔티티 (type + config Map)
- [x] Workflow 저장 시 TriggerConfig 저장
- [x] `WorkflowTranslator.toRuntimeModel()` — trigger 필드 FastAPI payload에 포함
- [x] `ExecutionService.executeWorkflow()` — Manual trigger 실행
- [x] Source catalog에 trigger_kind 정의

**Phase 2 미완료 항목:**
- [ ] `SchedulingConfig.java` — `@EnableScheduling` + `TaskScheduler` 빈 등록
- [ ] `ScheduleTriggerService.java` — `@PostConstruct` DB 재등록 + 등록/해제 메서드
- [ ] `WorkflowService` 수정 — isActive 변경/삭제 시 스케줄 연동
- [ ] `ExecutionService.executeScheduled()` — 시스템 실행 메서드
- [ ] `WebhookController.java` — webhook 발급/무효화/조회 API
- [ ] `WebhookReceiveController.java` — `POST /api/webhooks/{webhookId}` 수신
- [ ] `WebhookService.java` — 서명 검증 + 실행 트리거
- [ ] `WorkflowRepository` — webhookId로 workflow 조회 메서드
- [ ] `InternalExecutionController.java` — FastAPI 완료 콜백 수신
- [ ] `SecurityConfig` 수정 — `/api/webhooks/**` permitAll, `/api/internal/**` 보호
- [ ] `ErrorCode` 추가 — `WEBHOOK_NOT_FOUND`, `WEBHOOK_SIGNATURE_INVALID`

---

### 7.2 FastAPI 팀

**Phase 1 완료 항목:**
- [x] `POST /api/v1/workflows/{id}/execute` 수신 및 처리
- [x] runtime_type 기반 노드 전략 분기 (input/llm/output)
- [x] `service_tokens` 처리

**Phase 2 미완료 항목:**
- [ ] execute endpoint에서 `trigger.type` 분기 처리
  - `"manual"` → 현행 유지
  - `"schedule"` → initial_data 없이 source node 실행
  - `"webhook"` → `trigger.config.event_payload` → InputNodeStrategy 전달
- [ ] 실행 완료 시 Spring 콜백 호출
  - `POST {SPRING_BASE_URL}/api/internal/executions/{execId}/complete`
  - 요청 body: `{ "status": "completed", "output": {...}, "duration_ms": 1250 }`
- [ ] 실행 실패 시 Spring 콜백 호출
  - `POST {SPRING_BASE_URL}/api/internal/executions/{execId}/complete`
  - 요청 body: `{ "status": "failed", "error": "..." }`

---

### 7.3 Frontend 팀

**Phase 1 완료 항목:**
- [x] 실행 버튼 (Manual trigger) 구현
- [x] 실행 상태 폴링 (`GET /api/workflows/{id}/executions/{execId}`)

**Phase 2 미완료 항목:**
- [ ] Trigger 타입 선택 드롭다운 (manual / schedule / webhook)
- [ ] Schedule trigger 설정 UI
  - cron 표현식 입력 (또는 GUI 빌더)
  - 타임존 선택 (드롭다운)
  - 다음 실행 시간 미리보기
- [ ] Webhook trigger 설정 UI
  - `POST /api/workflows/{id}/webhook` 호출하여 webhook 발급
  - 발급된 endpoint URL 표시 + 복사 버튼
  - secret 1회 표시 (마스킹 처리)
  - 외부 서비스 등록 안내 (GitHub, Slack 등)
- [ ] Trigger 상태 표시 (활성/비활성)
- [ ] Schedule trigger 활성화/비활성화 토글

---

## 8. 변경 이력

| 날짜 | 내용 |
|------|------|
| 2026-04-26 | 최초 작성 — Phase 1 현황 정리, Phase 2 팀별 역할 분리, 구현 가이드 포함 |
