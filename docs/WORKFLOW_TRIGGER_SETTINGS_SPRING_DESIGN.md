# 워크플로우 트리거 설정 기능 Spring 설계 문서

> **작성일:** 2026-05-10
> **대상:** Spring backend
> **용도:** trigger settings 기능 구현 전 기준 설계 문서
> **관련 이슈:** 트리거 설정 기능
> **관련 저장소:** `flowify-FE`, `flowify-BE`

---

## 1. 설계 배경

이번 기능의 목표는 워크플로우 단위로 아래와 같은 실행 정책을 설정할 수 있게 만드는 것이다.

- 수동 실행
- 몇 시간마다 확인
- 매일 특정 시간 실행
- 매주 특정 요일/시간 실행

현재 Spring에는 `Workflow.trigger`, `ScheduleTriggerService`, `WebhookService`가 이미 일부 존재한다.  
하지만 FE 에디터에서 이 값을 저장하고 복원하는 흐름은 아직 완성되지 않았고, Spring 내부에서도 trigger validation, lifecycle, overlap policy가 기능 기준으로 정리되어 있지 않다.

이번 문서는 기존 코드를 부분 보수하는 수준이 아니라, **workflow-level trigger의 source of truth를 Spring으로 다시 고정하는 기준 문서**다.

---

## 2. 현재 상태 요약

### 2.1 이미 존재하는 것

- `Workflow` 엔티티에 `trigger: TriggerConfig` 필드가 있다.
- `TriggerConfig`는 `type + config(Map)` 구조다.
- `WorkflowTranslator`는 `workflow.trigger`를 FastAPI runtime payload에 포함한다.
- `ScheduleTriggerService`는 `cron`, `timezone`을 읽어 `TaskScheduler`에 등록한다.
- `ExecutionService.executeScheduled()`는 일반 실행과 같은 FastAPI execute 경로를 사용한다.
- `WebhookService`와 webhook trigger config도 이미 일부 구현돼 있다.

### 2.2 아직 부족한 것

#### A. create 시 schedule 등록이 즉시 일어나지 않는다

현재 `createWorkflow()`는 workflow를 저장하지만, schedule trigger 등록 이벤트를 발행하지 않는다.  
즉 생성 시점부터 schedule이어도 첫 update 전까지는 등록 누락이 발생할 수 있다.

#### B. trigger validation이 없다

현재 `WorkflowValidator`는 노드와 실행 가능성 중심으로만 검증한다.

- trigger type 허용 범위
- `schedule.cron` 존재 여부
- timezone 유효성
- schedule_mode별 보조 필드 유효성

위 항목은 아직 저장 단계에서 막히지 않는다.

#### C. `source_mode.trigger_kind`와 `workflow.trigger`의 역할이 겹쳐 보인다

현재 source mode의 `trigger_kind`는 source mode 성격 설명 메타데이터에 가깝다.  
반면 `workflow.trigger`는 워크플로우 전체 자동 실행 정책이다.  
이 둘을 분리 규칙 없이 두면 FE, Spring, FastAPI가 서로 다른 의미로 해석할 수 있다.

#### D. 중복 실행 방지 정책이 없다

현재 schedule fire 시 `executionService.executeScheduled(workflowId)`를 바로 호출한다.  
동일 workflow가 이미 `pending` 또는 `running` 상태일 때 skip할지, queue에 쌓을지 기준이 없다.

#### E. `active`의 의미가 기능 기준으로 고정되지 않았다

현재 `active`는 화면에 따라 실행 상태처럼 보일 수 있지만, trigger 기능 기준으로는 schedule 자동 실행 활성화 여부에 더 가깝다.  
이번 기능에서 이 의미를 명확히 고정해야 한다.

---

## 3. 설계 원칙

### 3.1 V1 범위

V1에서 정식 지원할 trigger 타입은 아래 두 가지다.

- `manual`
- `schedule`

`webhook`은 기존 기반을 유지하되, 이번 구현의 주 전달 범위에서는 제외한다.

### 3.2 authoritative source

워크플로우 실행 정책의 source of truth는 아래 조합이다.

- `Workflow.trigger`
- `Workflow.active`

FE는 이 값을 저장/수정하고, Spring은 이를 검증하고 schedule을 등록/해제한다.  
FastAPI는 스케줄 owner가 아니라 runtime executor다.

### 3.3 source mode의 `trigger_kind`와 workflow trigger를 분리한다

두 값의 역할은 아래처럼 고정한다.

- `source_mode.trigger_kind`: source mode의 성격 설명 메타데이터
- `workflow.trigger`: 실제 워크플로우 자동 실행 정책

호환 규칙:

- `workflow.trigger=manual`이면 어떤 source mode든 수동 실행 시점에만 동작한다.
- `workflow.trigger=schedule`이면 source mode의 `trigger_kind`와 무관하게 스케줄 시점마다 해당 source mode 기준으로 데이터를 읽는다.
- Spring은 `trigger_kind`를 보고 별도 스케줄러를 등록하지 않는다.

### 3.4 legacy null trigger는 manual로 정규화한다

기존 데이터와의 호환을 위해 `trigger == null`은 저장/응답/실행 경계에서 `manual`로 해석한다.

### 3.5 trigger 변경은 미래 실행에만 영향을 준다

workflow가 현재 실행 중일 때 trigger 설정을 바꾸더라도,

- 이미 시작된 실행은 그대로 계속 진행한다.
- 바뀐 trigger와 active는 다음 스케줄부터 적용된다.

### 3.6 운영 가정은 V1에서 단순하게 둔다

V1은 아래 전제를 둔다.

- 단일 Spring 인스턴스 기준 스케줄 등록
- 서버 다운타임 동안 missed run 보정 없음
- 중복 실행은 기본적으로 skip

---

## 4. Spring 기준 데이터 계약

### 4.1 TriggerConfig 구조

Spring 저장 기준 구조는 아래를 따른다.

```json
{
  "type": "schedule",
  "config": {
    "schedule_mode": "interval",
    "cron": "0 0 */4 * * *",
    "timezone": "Asia/Seoul",
    "interval_hours": 4,
    "skip_if_running": true
  }
}
```

### 4.2 V1 허용 타입

- `manual`
- `schedule`

### 4.3 V1 schedule config 필드

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `schedule_mode` | string | O | `interval`, `daily`, `weekly` |
| `cron` | string | O | 실제 스케줄 등록 기준인 내부 실행용 필드 |
| `timezone` | string | O | V1 기본값이자 기본 노출값은 `Asia/Seoul` |
| `interval_hours` | integer | 조건부 | `schedule_mode=interval`일 때 사용 |
| `time_of_day` | string | 조건부 | `HH:mm`, daily/weekly UI 복원용 |
| `weekdays` | string array | 조건부 | `MON`~`SUN`, weekly UI 복원용 |
| `skip_if_running` | boolean | 선택 | 기본값 `true` |

### 4.4 manual 기본값과 canonicalization

manual trigger는 아래 형태를 canonical form으로 본다.

```json
{
  "type": "manual",
  "config": {}
}
```

정규화 규칙:

- create/update 입력에서 `trigger == null`이면 내부적으로 manual로 해석한다.
- 가능하면 저장 전 `manual + {}` 형태로 canonicalize한다.
- 과거 데이터에 null trigger가 남아 있더라도 응답과 실행 경계에서는 manual처럼 동작해야 한다.
- V1에서 별도 값이 없으면 `timezone=Asia/Seoul`을 기본값으로 채운다.

---

## 5. `active` 해석 규칙

이번 설계에서 `active`의 의미는 아래로 고정한다.

- `trigger.type == schedule`일 때: 자동 실행 활성화 여부
- `trigger.type == manual`일 때: 사실상 항상 `true`

즉 manual workflow에 대해 `active=false`를 별도 상태로 유지하지 않는다.

권장 처리:

- FE는 manual일 때 `active=true`를 보낸다.
- Spring도 `manual + active=false` 입력이 들어오면 `true`로 정규화할 수 있다.

---

## 6. Schedule lifecycle 설계

### 6.1 create

워크플로우 생성 시 아래 조건이면 schedule을 즉시 등록한다.

- `trigger.type == schedule`
- `active == true`
- `config.cron` 존재

즉 `createWorkflow()`도 `updateWorkflow()`와 같은 schedule publish 경로를 타야 한다.

### 6.2 update

아래 변경은 모두 같은 lifecycle path로 처리한다.

- manual -> schedule
- schedule -> manual
- schedule cron 변경
- timezone 변경
- active true/false 변경

### 6.3 delete

workflow 삭제 시 기존 workflow가 schedule이었다면 unregister event를 발행한다.

### 6.4 app startup

서버 시작 시 DB에서 아래 조건의 workflow를 읽어 재등록한다.

- `trigger.type == schedule`
- `active == true`

### 6.5 현재 실행 중인 workflow와의 관계

trigger나 active를 바꾸는 시점에 workflow가 이미 실행 중이라면:

- 현재 실행은 중단하지 않는다.
- 다음 스케줄부터 새 정책을 적용한다.
- `schedule -> manual` 변경은 이후 fire만 막고, 이미 시작된 execution rollback과는 연결하지 않는다.

---

## 7. Schedule registration 정책

### 7.1 등록 기준

실제 schedule 등록에는 아래 두 값만 사용한다.

- `config.cron`
- `config.timezone`

나머지 필드는 UI 복원 및 validation 보조 정보다.

### 7.2 schedule owner

실제 스케줄 owner는 Spring이다.

- FE -> Spring: trigger 저장
- Spring: 검증, 등록, 해제, 재등록
- Spring -> FastAPI: 실행 요청

FastAPI의 `/api/v1/triggers`는 이번 기능의 공식 경로가 아니다.

### 7.3 단일 인스턴스 전제

현재 구조는 메모리 내 `TaskScheduler` registry를 사용하므로, V1은 단일 Spring 인스턴스를 전제로 한다.

- 다중 인스턴스에서 같은 schedule이 중복 등록될 수 있다.
- 분산락이나 외부 scheduler는 이번 범위에 포함하지 않는다.

### 7.4 다운타임 중 missed run 정책

서버가 꺼져 있던 동안 발생했어야 할 실행은 V1에서 보정하지 않는다.

- 재시작 시 활성 schedule을 다시 등록한다.
- 다음 cron 시점부터 다시 실행된다.
- catch-up execution은 후속 범위다.

---

### 7.5 interval 기준 시각 보완점

- 현재 V1 구현에서 `schedule_mode=interval`은 내부 `cron`으로 변환되어 시계 기준 슬롯에 맞춰 등록된다.
- 예를 들어 `4시간마다`는 `0, 4, 8, 12, 16, 20시` 슬롯으로 등록된다.
- 따라서 사용자가 오후 `1:17`에 자동 실행을 다시 켜면 다음 실행은 `5:17`이 아니라 다음 cron 슬롯인 `16:00`이 된다.
- 이 동작은 기술적으로 단순하지만, 사용자가 이해하는 `N시간마다`와는 어긋날 수 있다.
- 향후 목표 동작은 `interval`을 사용자가 자동 실행을 켠 시점 또는 마지막 기준 시각부터 계산하는 방식이다.
- 즉 `4시간마다`를 `13:17`에 켜면 `17:17`, `21:17`처럼 앵커 시각 기준으로 반복되어야 한다.
- 이 보완을 위해서는 `enabled_at` 또는 `interval_anchor_at` 같은 기준 시각을 workflow trigger metadata에 함께 저장하고, Spring scheduler가 그 시각을 기준으로 다음 실행 시점을 계산하도록 확장할 필요가 있다.
- `daily`, `weekly`는 계속 시계 기준 스케줄로 유지하고, 이 보완은 `interval`에만 적용하는 것이 자연스럽다.

## 8. 중복 실행 정책

V1 기본 정책은 **skip if running**이다.

### 8.1 기본값

- `skip_if_running = true`

### 8.2 동작

schedule fire 시점에 같은 workflow의 최신 실행이 아래 상태면 새 실행을 시작하지 않는다.

- `pending`
- `running`

### 8.3 이유

- source 중복 수집을 줄인다.
- sink 중복 전송을 줄인다.
- queueing, parallel execution, replay 정책을 V1에서 함께 풀지 않는다.

### 8.4 구현 시사점

권장 구현:

- `ExecutionRepository`에 running 여부 조회 메서드 추가
- `ScheduleTriggerService` 또는 `ExecutionService.executeScheduled()` 진입부에서 guard 수행
- skip 시 info 또는 warn 로그 남김

---

## 9. Trigger validation 설계

### 9.1 validation 시점

아래 두 경계에서 trigger validation이 필요하다.

- workflow create/update 저장 전
- schedule 등록 직전

### 9.2 validation 규칙

#### manual

- `config`는 비어 있어도 된다.

#### schedule 공통

- `cron` 필수
- `timezone` 필수
- `schedule_mode`는 허용 enum 값이어야 한다
- `cron`은 Spring `CronTrigger`로 파싱 가능해야 한다
- `timezone`은 `ZoneId.of()`로 파싱 가능해야 한다

#### interval

- `interval_hours` 필수
- 1 이상 정수
- V1에서는 24 이하로 제한한다
- 기본값은 `4`
- FE 권장 프리셋은 `1`, `2`, `4`, `6`, `12`, `24`

#### daily

- `time_of_day` 필수

#### weekly

- `time_of_day` 필수
- `weekdays` 최소 1개 이상

#### 내부 cron

- `cron`만 authoritative
- 사용자가 직접 입력하지는 않지만, Spring은 최종적으로 이 값을 기준으로 스케줄을 등록한다
- 보조 복원 필드는 없어도 된다

### 9.3 구현 위치

권장 방향:

- `WorkflowValidator`에 trigger validation 메서드 추가
- raw `Map<String, Object>` 직접 접근을 줄이기 위한 parsing helper 도입

예시 이름:

- `WorkflowTriggerSupport`
- `ScheduleTriggerConfigResolver`

---

## 10. FastAPI 전달 계약

### 10.1 기본 원칙

Spring은 FastAPI에 `workflow.trigger`를 그대로 전달한다.

이 값은 FastAPI가 스케줄링하기 위한 값이 아니라,
이번 실행이 어떤 trigger context에서 시작됐는지 보존하기 위한 metadata다.

### 10.2 V1에서 반드시 맞춰야 하는 것

- `manual`과 `schedule` 모두 같은 execute endpoint로 들어간다.
- FastAPI는 `trigger.type == schedule`이어도 별도 대기하지 않는다.
- Spring이 schedule owner라는 전제를 문서와 코드에서 유지한다.

---

## 11. 검증 전략

이번 기능은 구현 후 검증을 크게 가져가야 한다.  
단위 테스트만으로 끝내지 않고, lifecycle, restart, overlap, 계약 테스트까지 묶어서 확인한다.

### 11.1 단위 테스트

- trigger parsing helper가 null/manual/schedule payload를 올바르게 정규화한다.
- `WorkflowValidator`가 invalid cron, invalid timezone, missing interval/weekdays를 막는다.
- `interval_hours=0`, `25`, 소수, 문자열 입력을 모두 validation에서 막는다.
- `publishScheduleEvent()`가 create/update/manual 전환에서 올바른 register/unregister 결정을 내린다.

### 11.2 서비스 테스트

- 새 workflow를 `schedule + active=true`로 생성하면 즉시 register 이벤트가 발생한다.
- `schedule -> manual` 전환 시 unregister 된다.
- `manual -> schedule` 전환 시 register 된다.
- `schedule active=true -> false` 전환 시 unregister 된다.
- `cron` 또는 `timezone` 변경 시 기존 스케줄이 교체된다.

### 11.3 재시작/운영 테스트

- app startup 시 DB의 schedule workflow가 재등록된다.
- 서버 다운타임 후 missed run이 catch-up되지 않고 다음 주기부터만 동작한다.
- 실행 중인 workflow가 있을 때 다음 주기가 skip된다.
- `timezone` 미입력 저장 시 `Asia/Seoul`로 정규화되는지 확인한다.

### 11.4 계약 테스트

- FastAPI runtime payload에 `workflow.trigger`가 그대로 포함된다.
- manual/schedule 모두 동일한 execute path를 사용한다.
- source mode의 `trigger_kind` 변경이 schedule 등록 로직을 직접 바꾸지 않는다.

### 11.5 수동 회귀 테스트

- FE에서 trigger 저장 후 새로고침 시 같은 값으로 복원된다.
- workflow list 등 다른 화면에서 active 의미가 깨지지 않는지 확인한다.
- schedule workflow를 삭제하면 더 이상 fire되지 않는다.

### 11.6 다중 workflow 시나리오 테스트

- 서로 다른 cron을 가진 workflow 여러 개가 동시에 등록되어도 각각 한 번씩만 fire된다.
- 같은 시각에 시작되는 schedule workflow 여러 개가 있어도 서로의 registry를 덮어쓰지 않는다.
- manual workflow와 schedule workflow가 섞여 있어도 schedule 등록 대상은 schedule만 유지된다.
- 일부 workflow만 active=false로 바꿔도 나머지 schedule workflow 등록 상태는 보존된다.
- 하나의 workflow가 실패하거나 skip되어도 다른 workflow의 다음 실행에는 영향이 없다.
- `1`, `2`, `4`, `6`, `12`, `24`시간 interval workflow가 함께 있어도 각 cron 계산과 등록이 의도대로 유지된다.

### 11.7 2026-05-10 로컬 실검증 메모

- 로컬 도커 스택 기준:
  - `flowify-spring-canvas-drive-test` → `localhost:8081`
  - `flowify-fastapi-canvas-drive-test-v2` → `localhost:8002`
  - `flowify-mongodb-canvas-drive-test` → `localhost:27018`
- `daily` trigger를 다음 1분 시점으로 저장한 뒤, Spring schedule 등록과 실제 발화를 확인했다.
- 빈 workflow로 1회 발화만 확인한 것이 아니라, 아래 시나리오를 추가로 검증했다.
- `google_drive` 시작 노드 1개 workflow:
  - 수동 실행 `success`
  - schedule 실행 `success`
  - 두 실행 모두 node log 1건 확인
- `google_drive -> passthrough -> google_drive` 다중 노드 workflow:
  - 수동 실행 `success`
  - 다음 분 schedule 실행 `success`
  - 두 실행 모두 node log 3건 확인
  - schedule 실행으로 대상 Drive 폴더에 생성된 파일 2개를 즉시 확인했고, 검증 직후 모두 삭제했다.
- Spring 로그에서는 `Registered schedule trigger`, `Schedule trigger fired`, `Unregistered schedule trigger`를 workflow id 기준으로 확인했다.
- FastAPI 로그에서는 동일 workflow에 대한 `/api/v1/workflows/{id}/execute` 호출이 수동 실행 1회, schedule 실행 1회로 총 2회 기록되는 것을 확인했다.
- `다음 주기에도 다시 발화되는지`는 사용자 노출 주기(`interval >= 1시간`, `daily`, `weekly`)만으로 즉시 보기 어렵기 때문에, 로컬에서 1초 cron을 사용하는 임시 검증으로 `executeScheduled()`가 2회 이상 호출되는지 확인했다.
- 이 반복 발화 검증은 저장소에 테스트 파일로 남기지 않고, 로컬 임시 검증 후 정리했다.

### 11.8 실행 커맨드 기준

- `./gradlew test --no-daemon --console=plain`
- 필요 시 trigger 관련 패키지 단위 테스트를 별도 실행한다.

---

## 12. 구현 대상 파일

### 12.1 주요 수정 파일

- `src/main/java/org/github/flowify/workflow/service/WorkflowService.java`
- `src/main/java/org/github/flowify/workflow/service/WorkflowValidator.java`
- `src/main/java/org/github/flowify/execution/service/ScheduleTriggerService.java`
- `src/main/java/org/github/flowify/execution/service/ExecutionService.java`
- `src/main/java/org/github/flowify/execution/repository/ExecutionRepository.java`

### 12.2 추가 가능 파일

- `src/main/java/org/github/flowify/workflow/service/WorkflowTriggerSupport.java`
- `src/main/java/org/github/flowify/workflow/service/ScheduleTriggerConfigResolver.java`
- `src/test/java/org/github/flowify/workflow/WorkflowTrigger*Test.java`

---

## 13. 한 줄 요약

이번 Spring 작업의 핵심은 **workflow 문서에 저장된 trigger를 authoritative source로 삼고, create/update/delete/startup 전 구간에서 같은 규칙으로 schedule lifecycle과 validation을 완성하는 것**이다.
