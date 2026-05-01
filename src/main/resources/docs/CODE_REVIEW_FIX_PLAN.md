# 코드 리뷰 기반 수정 계획

> 최종 업데이트: 2026-05-02
> 근거: Choice Wizard 보강 및 실행 서비스 변경 이후 현 코드 재리뷰
> 범위: 런타임 안정성, 데이터 보존, 보안 검증, 유지보수성
> 원칙: 이미 반영된 수정은 완료로 분리하고, 남은 작업은 영향도 순으로 진행

---

## 현재 상태 요약

| 순서 | Work Unit | 상태 | 심각도 | 핵심 문제 |
|------|-----------|------|--------|-----------|
| 0 | Choice Wizard `applicable_when` POST 검증 | **완료** | MEDIUM | 숨겨진 action 수동 POST 가능성 제거 |
| 1 | 실행 완료 데이터 저장 정합성 | **완료** | HIGH | `error`, `output`, `durationMs` 저장 동작 테스트 고정 |
| 2 | FastApiClient 타임아웃 없음 | **완료** | HIGH | `.block()` 호출에 30초 timeout 적용 |
| 3 | 스케줄/웹훅 실행 검증 누락 | **완료** | HIGH | 자동 실행 경로도 `validateForExecution()` 수행 |
| 4 | Internal callback 토큰 비교 보안 | **완료** | MEDIUM | timing-safe 비교 적용 + 토큰 길이 로그 제거 |
| 5 | ChoiceMappingService 정리 | **완료** | MEDIUM | 데드 코드 제거 + processing method options null 가드 |
| 6 | WorkflowResponse 수동 빌더 | **완료** | LOW | `toBuilder()` 기반으로 상세 조회 응답 재구성 |

---

## 완료된 항목

### Work Unit 0: Choice Wizard POST `applicable_when` 검증

**상태**: 완료

`ChoiceMappingService.onUserSelect()`에서 action id를 찾은 뒤 `applicable_when` 조건을 다시 검증하도록 보강했다. 따라서 GET에서 숨겨진 action을 수동 POST하거나 stale UI가 전송해도 context와 조건이 맞지 않으면 `INVALID_REQUEST`로 거부된다.

반영 파일:
- `src/main/java/org/github/flowify/workflow/service/choice/ChoiceMappingService.java`
- `src/test/java/org/github/flowify/workflow/ChoiceMappingServiceTest.java`

검증:
- `./gradlew test --tests org.github.flowify.workflow.ChoiceMappingServiceTest`
- `./gradlew test`

---

## Work Unit 1: 실행 완료 데이터 저장 정합성 검증

### 현재 코드 상태

FastAPI 완료 콜백 DTO와 저장 경로는 현재 반영되어 있다.

- `ExecutionCompleteRequest`: `status`, `output`, `durationMs`, `error` 보유
- `InternalExecutionController.completeExecution()`: `output`, `durationMs`까지 서비스에 전달
- `WorkflowExecution`: `error`, `output`, `durationMs` 필드 보유
- `ExecutionService.completeExecution()`: `state`, `finishedAt`, `error`, `output`, `durationMs` 저장

### 남은 문제

현재 변경은 테스트로 고정되어 있지 않다. 특히 아래 동작은 회귀 가능성이 크다.

- FastAPI의 `completed` 상태가 내부 상태 `success`로 정규화되는지
- `error`, `output`, `durationMs`가 `MongoTemplate.updateFirst()`에 포함되는지
- 대상 execution id가 없을 때 `EXECUTION_NOT_FOUND`를 던지는지

### 수정 계획

**파일**: `src/test/java/org/github/flowify/execution/ExecutionServiceTest.java`

테스트 추가:

```java
@Test
@DisplayName("실행 완료 콜백은 상태와 결과 데이터를 저장한다")
void completeExecution_updatesResultFields() {
    // updateFirst()의 Query/Update를 캡처해서 state, error, output, durationMs 검증
}
```

추가 검증:
- `status="completed"` 입력 시 저장 state는 `"success"`
- `output` map 저장
- `durationMs` 저장
- `error` 저장

---

## Work Unit 2: FastApiClient 타임아웃 추가

### 문제

`FastApiClient`의 모든 `.block()` 호출에 타임아웃이 없다. FastAPI가 응답하지 않으면 Spring 요청 스레드가 무기한 차단될 수 있다.

현재 위치:
- `execute()` — `src/main/java/org/github/flowify/execution/service/FastApiClient.java`
- `generateWorkflow()`
- `stopExecution()`
- `rollback()`

### 수정 계획

**파일**: `src/main/java/org/github/flowify/execution/service/FastApiClient.java`

`Duration` import 후 모든 blocking 호출 전에 timeout 적용:

```java
.bodyToMono(Map.class)
.timeout(Duration.ofSeconds(30))
.block();
```

`Void` 응답도 동일하게 적용:

```java
.bodyToMono(Void.class)
.timeout(Duration.ofSeconds(30))
.block();
```

기존 `catch (Exception e)`가 `TimeoutException` 계열도 처리하므로 별도 예외 타입 분기는 필수는 아니다. 다만 로그 메시지에는 timeout 여부가 드러나도록 개선하는 것이 좋다.

---

## Work Unit 3: 스케줄/웹훅 실행 전 검증 추가

### 문제

`executeWorkflow()`는 실행 전 `workflowValidator.validateForExecution()`을 호출하지만, `executeScheduled()`와 `executeFromWebhook()`는 검증 없이 FastAPI 실행으로 넘어간다.

영향:
- 미완성 노드, 누락된 연결, 인증 요구 상태가 스케줄/웹훅 경로에서 반복 실행될 수 있음
- 직접 실행과 자동 실행의 백엔드 authority가 달라짐

### 수정 계획

**파일**: `src/main/java/org/github/flowify/execution/service/ExecutionService.java`

`executeScheduled()`:

```java
Workflow workflow = workflowService.findWorkflowOrThrow(workflowId);
String userId = workflow.getUserId();

workflowValidator.validateForExecution(workflow, nodeLifecycleService, catalogService, userId);
```

`executeFromWebhook()`도 동일하게 runtime model 생성 전에 검증한다.

테스트:
- `executeScheduled()`가 `validateForExecution()`을 호출하는지 검증
- `executeFromWebhook()`가 `validateForExecution()`을 호출하는지 검증

---

## Work Unit 4: Internal callback 토큰 비교 보안 강화

### 문제

`InternalExecutionController`가 내부 토큰을 `String.equals()`로 비교하고, 실패 로그에 expected/received length를 남긴다.

현재 코드:

```java
if (!internalToken.equals(token)) {
    log.warn("Internal token mismatch on callback for execId={}. expected length={}, received length={}",
            execId, internalToken.length(), token.length());
```

문제:
- 길이 정보가 로그에 노출됨
- 일반 문자열 비교는 timing-safe 비교가 아님
- `token`이 null로 들어오는 경우 처리도 명시적이지 않음

### 수정 계획

**파일**: `src/main/java/org/github/flowify/execution/controller/InternalExecutionController.java`

```java
private boolean isValidInternalToken(String token) {
    if (internalToken == null || token == null) {
        return false;
    }
    return MessageDigest.isEqual(
            internalToken.getBytes(StandardCharsets.UTF_8),
            token.getBytes(StandardCharsets.UTF_8));
}
```

호출부:

```java
if (!isValidInternalToken(token)) {
    log.warn("Internal token mismatch on callback for execId={}", execId);
    throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
}
```

테스트:
- 올바른 토큰은 통과
- 잘못된 토큰은 `AUTH_FORBIDDEN`
- 실패 로그에 토큰 길이 정보가 남지 않도록 코드 리뷰 기준 확인

---

## Work Unit 5: ChoiceMappingService 잔여 정리

### 이미 완료된 부분

`onUserSelect()` action 선택 시 `isApplicable(action, context)`를 호출해 `applicable_when` 불일치 action을 거부한다. GET 필터링도 같은 helper를 사용한다.

### 남은 문제 A: 데드 코드

`resolveOptionsSource(Action, Map)`는 public 메서드지만 현재 호출처가 없다. 내부 동적 옵션 resolve는 `resolveOptionsBySource(String, Map)`가 담당한다.

삭제 대상:
- `src/main/java/org/github/flowify/workflow/service/choice/ChoiceMappingService.java`
- `resolveOptionsSource(Action, Map)`

### 남은 문제 B: processing method options NPE

`getProcessingMethod()`는 null 체크하지만 `getOptions()`는 null 체크하지 않는다.

위험 위치:

```java
config.getProcessingMethod().getOptions().stream()
for (Option opt : config.getProcessingMethod().getOptions())
```

### 수정 계획

`getProcessingMethodChoices()`에서 명확한 예외로 방어:

```java
if (config.getProcessingMethod().getOptions() == null) {
    throw new BusinessException(ErrorCode.INVALID_REQUEST,
            "데이터 타입 '" + dataType + "'의 처리 방식에 옵션이 정의되지 않았습니다.");
}
```

`onUserSelect()`에서는 null이면 processing method 매칭을 건너뛰도록 방어:

```java
if (config.isRequiresProcessingMethod()
        && config.getProcessingMethod() != null
        && config.getProcessingMethod().getOptions() != null) {
    ...
}
```

테스트:
- `applicable_when` 테스트는 이미 존재
- processing method options 누락 케이스는 별도 fixture가 필요하면 리플렉션으로 `mappingRules`를 주입해 단위 테스트 구성

---

## Work Unit 6: WorkflowResponse 수동 빌더 제거

### 문제

`WorkflowController.getWorkflow()`에서 `WorkflowResponse`를 수동으로 재구성한다. 필드가 추가될 때 누락될 가능성이 있다.

현재 코드:

```java
return ApiResponse.ok(WorkflowResponse.builder()
        .id(response.getId())
        .name(response.getName())
        ...
        .nodeStatuses(statuses)
        .build());
```

### 수정 계획

선호안: `WorkflowResponse`의 기존 정적 팩토리 메서드를 활용하도록 서비스/컨트롤러 경계를 정리한다.

작은 수정안:

**파일**: `src/main/java/org/github/flowify/workflow/dto/WorkflowResponse.java`

```java
@Builder(toBuilder = true)
```

**파일**: `src/main/java/org/github/flowify/workflow/controller/WorkflowController.java`

```java
return ApiResponse.ok(response.toBuilder().nodeStatuses(statuses).build());
```

주의:
- `WorkflowResponse.from(workflow, warnings, nodeStatuses)`가 이미 있으므로, 장기적으로는 node status 조립 위치를 서비스 계층으로 옮기는 편이 더 일관적이다.

---

## 권장 실행 순서

1. **Work Unit 2**: FastApiClient timeout 추가
2. **Work Unit 3**: 스케줄/웹훅 실행 전 검증 추가
3. **Work Unit 1**: 완료 콜백 저장 동작 테스트 고정
4. **Work Unit 4**: internal token 비교 보안 강화
5. **Work Unit 5**: ChoiceMappingService 잔여 정리
6. **Work Unit 6**: WorkflowResponse 수동 빌더 제거

---

## 수정 대상 파일 요약

| 파일 | Work Unit | 변경 내용 |
|------|-----------|-----------|
| `execution/service/FastApiClient.java` | 2 | `.timeout(Duration.ofSeconds(30))` 추가 |
| `execution/service/ExecutionService.java` | 3 | 스케줄/웹훅 실행 전 `validateForExecution()` 추가 |
| `execution/controller/InternalExecutionController.java` | 4 | timing-safe 토큰 비교 + 길이 로그 제거 |
| `workflow/service/choice/ChoiceMappingService.java` | 5 | 데드 코드 제거 + processing method options null 가드 |
| `workflow/dto/WorkflowResponse.java` | 6 | `@Builder(toBuilder = true)` 또는 구조 정리 |
| `workflow/controller/WorkflowController.java` | 6 | 수동 빌더 제거 |
| `execution/ExecutionServiceTest.java` | 1, 3 | 완료 콜백 저장/자동 실행 검증 테스트 추가 |

---

## 검증 방법

기본 검증:

```bash
./gradlew test
```

선택 검증:

```bash
./gradlew test --tests org.github.flowify.execution.ExecutionServiceTest
./gradlew test --tests org.github.flowify.workflow.ChoiceMappingServiceTest
```

수정 후 확인할 주요 시나리오:
- FastAPI 지연/무응답 시 Spring 요청이 30초 내 실패하는지
- 직접 실행, 스케줄 실행, 웹훅 실행이 같은 검증 규칙을 타는지
- 완료 콜백이 `output`, `durationMs`, `error`를 저장하는지
- `applicable_when` action이 context 불일치 시 POST에서도 거부되는지

# Update Result

> 업데이트 일시: 2026-05-02

## 적용 완료

- Work Unit 1: `ExecutionService.completeExecution()`의 저장 동작을 `ExecutionServiceTest`로 고정했다.
  - `completed` → `success` 상태 정규화 검증
  - `error`, `output`, `durationMs`, `finishedAt` 저장 검증
  - 대상 execution 미존재 시 `EXECUTION_NOT_FOUND` 검증
- Work Unit 2: `FastApiClient`의 4개 blocking 호출에 `Duration.ofSeconds(30)` timeout을 추가했다.
- Work Unit 3: `executeScheduled()`와 `executeFromWebhook()`에 `workflowValidator.validateForExecution()`을 추가했다.
- Work Unit 4: `InternalExecutionController`의 내부 토큰 비교를 `MessageDigest.isEqual()` 기반으로 변경하고 토큰 길이 로그를 제거했다.
- Work Unit 5: `ChoiceMappingService.resolveOptionsSource()` 데드 코드를 제거하고 processing method option null 가드를 추가했다.
- Work Unit 6: `WorkflowResponse`에 `toBuilder`를 활성화하고 `WorkflowController.getWorkflow()`의 수동 필드 복사를 제거했다.

## 변경 파일

- `src/main/java/org/github/flowify/execution/service/FastApiClient.java`
- `src/main/java/org/github/flowify/execution/service/ExecutionService.java`
- `src/main/java/org/github/flowify/execution/controller/InternalExecutionController.java`
- `src/main/java/org/github/flowify/workflow/service/choice/ChoiceMappingService.java`
- `src/main/java/org/github/flowify/workflow/dto/WorkflowResponse.java`
- `src/main/java/org/github/flowify/workflow/controller/WorkflowController.java`
- `src/test/java/org/github/flowify/execution/ExecutionServiceTest.java`
- `src/main/resources/docs/CODE_REVIEW_FIX_PLAN.md`

## 검증 결과

```bash
./gradlew test --tests org.github.flowify.execution.ExecutionServiceTest --tests org.github.flowify.workflow.ChoiceMappingServiceTest
./gradlew test
```

결과: 모두 통과.
