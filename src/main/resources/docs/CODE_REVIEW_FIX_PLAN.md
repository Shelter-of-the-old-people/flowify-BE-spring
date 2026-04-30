# 코드 점검 기반 구조적 수정 계획

> 작성일: 2026-04-30
> 근거: Choice Wizard 백엔드 정합 작업 이후 전체 코드 점검
> 범위: 런타임 버그, 데이터 유실, 보안 취약점, 유지보수 문제 (총 7건)
> 원칙: 테스트 최소화, 구조적으로 중요한 문제만 수정

---

## 이슈 요약

| 순서 | Work Unit | 심각도 | 핵심 문제 |
|------|-----------|--------|-----------|
| 1 | 실행 완료 데이터 유실 | **HIGH** | FastAPI 콜백의 `error`, `output`, `durationMs`가 저장되지 않음 |
| 2 | FastApiClient 타임아웃 없음 | **HIGH** | `.block()` 무기한 대기 → 스레드 풀 고갈 가능 |
| 3 | ChoiceMappingService 데드코드 + NPE | **MEDIUM** | 중복 메서드 + `getOptions()` null 가드 누락 |
| 4 | WorkflowResponse 수동 빌더 | **LOW** | 16개 필드 수동 복사 → 필드 추가 시 누락 위험 |
| 5 | 보안/검증 보강 | **MEDIUM** | 스케줄 실행 검증 누락 + 토큰 비교 timing attack 취약 |

---

## Work Unit 1: 실행 완료 데이터 유실 수정

### 문제

FastAPI가 실행 완료 콜백으로 `error`, `output`, `durationMs`를 전송하지만, 백엔드에서 전부 무시된다.

현재 흐름:
```
FastAPI → POST /api/internal/executions/{execId}/complete
         Body: { status, error, output, durationMs }
                          ↓
InternalExecutionController.completeExecution()
         → executionService.completeExecution(execId, status, error)
                          ↓
ExecutionService.completeExecution()
         → mongoTemplate.updateFirst(): state, finishedAt만 저장
         → error 파라미터 무시, output/durationMs 아예 전달 안 됨
```

### 수정 계획

#### 1-1. WorkflowExecution 엔티티에 필드 추가

**파일**: `src/main/java/org/github/flowify/execution/entity/WorkflowExecution.java`

```java
// 기존 필드 (finishedAt) 이후에 추가
private String error;
private Map<String, Object> output;
private Long durationMs;
```

#### 1-2. completeExecution() 시그니처 확장 + 저장

**파일**: `src/main/java/org/github/flowify/execution/service/ExecutionService.java` (line 130)

변경 전:
```java
public void completeExecution(String execId, String status, String error) {
    Update update = new Update()
            .set("state", status)
            .set("finishedAt", Instant.now());
```

변경 후:
```java
public void completeExecution(String execId, String status, String error,
                               Map<String, Object> output, Long durationMs) {
    Update update = new Update()
            .set("state", status)
            .set("finishedAt", Instant.now())
            .set("error", error)
            .set("output", output)
            .set("durationMs", durationMs);
```

#### 1-3. InternalExecutionController 호출부 수정

**파일**: `src/main/java/org/github/flowify/execution/controller/InternalExecutionController.java` (line 45)

변경 전:
```java
executionService.completeExecution(execId, request.getStatus(), request.getError());
```

변경 후:
```java
executionService.completeExecution(execId, request.getStatus(), request.getError(),
        request.getOutput(), request.getDurationMs());
```

---

## Work Unit 2: FastApiClient 타임아웃 추가

### 문제

`FastApiClient`의 모든 `.block()` 호출에 타임아웃이 없다. FastAPI가 응답하지 않으면 Spring 스레드가 영구 차단된다.

현재 코드:
```java
.bodyToMono(Map.class)
.block();  // 무기한 대기
```

### 수정 계획

**파일**: `src/main/java/org/github/flowify/execution/service/FastApiClient.java`

4개 `.block()` 호출 전에 `.timeout()` 추가:

```java
.bodyToMono(Map.class)
.timeout(Duration.ofSeconds(30))
.block();
```

적용 위치:
- `execute()` — line 35
- `generateWorkflow()` — line 63
- `stopExecution()` — line 80
- `rollback()` — line 103

기존 `catch (Exception e)` 블록이 `TimeoutException`도 처리하므로 추가 예외 처리 불필요.

---

## Work Unit 3: ChoiceMappingService 정리

### 문제 A: 데드 코드

이번 Choice Wizard 수정에서 `resolveOptionsBySource()` (private)를 신규 추가했지만,
기존 `resolveOptionsSource()` (public)가 그대로 남아있다. 두 메서드는 거의 동일한 로직.

| 메서드 | 접근자 | 파라미터 | 호출처 |
|--------|--------|----------|--------|
| `resolveOptionsSource(Action, Map)` | public | Action 객체 | **없음 (데드 코드)** |
| `resolveOptionsBySource(String, Map)` | private | optionsSource 문자열 | `resolveFollowUp()`, `resolveBranchConfig()` |

### 문제 B: NPE 위험

```java
// line 122 — getOptions()가 null이면 NPE
for (Option opt : config.getProcessingMethod().getOptions()) {
```

`getProcessingMethod()`는 null 체크하지만 `getOptions()`는 체크하지 않는다.

### 수정 계획

**파일**: `src/main/java/org/github/flowify/workflow/service/choice/ChoiceMappingService.java`

#### 3-1. 데드 코드 제거
- `resolveOptionsSource(Action, Map)` 메서드 삭제 (lines 258-298)
- 프로젝트 전체 grep 확인 완료: 호출처 없음

#### 3-2. getOptions() null 가드 추가

line 121-122 (onUserSelect):
```java
// 변경 전
if (config.isRequiresProcessingMethod() && config.getProcessingMethod() != null) {
    for (Option opt : config.getProcessingMethod().getOptions()) {

// 변경 후
if (config.isRequiresProcessingMethod() && config.getProcessingMethod() != null
        && config.getProcessingMethod().getOptions() != null) {
    for (Option opt : config.getProcessingMethod().getOptions()) {
```

line 95 (getProcessingMethodChoices):
```java
// 변경 전
List<Option> options = config.getProcessingMethod().getOptions().stream()

// 변경 후 — getOptions()가 null이면 명확한 예외
if (config.getProcessingMethod().getOptions() == null) {
    throw new BusinessException(ErrorCode.INVALID_REQUEST,
            "데이터 타입 '" + dataType + "'의 처리 방식에 옵션이 정의되지 않았습니다.");
}
List<Option> options = config.getProcessingMethod().getOptions().stream()
```

---

## Work Unit 4: WorkflowResponse 수동 빌더 제거

### 문제

`WorkflowController.getWorkflow()`에서 `WorkflowResponse`를 16개 필드를 수동으로 복사해서 재구성한다.
필드가 추가되면 이 코드가 누락 없이 업데이트될 보장이 없다.

현재 코드 (lines 76-92):
```java
return ApiResponse.ok(WorkflowResponse.builder()
        .id(response.getId())
        .name(response.getName())
        // ... 14개 필드 더 ...
        .nodeStatuses(statuses)
        .build());
```

### 수정 계획

#### 4-1. WorkflowResponse에 `toBuilder` 활성화

**파일**: `src/main/java/org/github/flowify/workflow/dto/WorkflowResponse.java` (line 17)

```java
// 변경 전
@Builder

// 변경 후
@Builder(toBuilder = true)
```

#### 4-2. WorkflowController.getWorkflow() 간소화

**파일**: `src/main/java/org/github/flowify/workflow/controller/WorkflowController.java` (lines 76-92)

```java
// 변경 전: 16줄 수동 빌더
return ApiResponse.ok(WorkflowResponse.builder()
        .id(response.getId())
        .name(response.getName())
        // ...
        .build());

// 변경 후: 1줄
return ApiResponse.ok(response.toBuilder().nodeStatuses(statuses).build());
```

---

## Work Unit 5: 보안/검증 보강

### 문제 A: executeScheduled() 검증 누락

`executeWorkflow()`는 `workflowValidator.validateForExecution()`을 호출하지만,
`executeScheduled()`는 검증 없이 바로 실행한다.

잘못된 워크플로우(미완성 노드, 누락된 연결 등)가 스케줄에 의해 반복 실행될 수 있다.

### 문제 B: Internal 토큰 비교 보안

```java
// 현재: timing attack에 취약한 String.equals()
if (!internalToken.equals(token)) {
    log.warn("... expected length={}, received length={}",
            internalToken.length(), token.length());  // 토큰 길이 정보 유출
```

### 수정 계획

#### 5-1. executeScheduled()에 validateForExecution() 추가

**파일**: `src/main/java/org/github/flowify/execution/service/ExecutionService.java` (line 107)

```java
public String executeScheduled(String workflowId) {
    Workflow workflow = workflowService.findWorkflowOrThrow(workflowId);
    String userId = workflow.getUserId();

    // 추가: 스케줄 실행 전 검증
    workflowValidator.validateForExecution(workflow, nodeLifecycleService, catalogService, userId);

    Map<String, String> tokens = collectServiceTokens(userId, workflow.getNodes());
    Map<String, Object> runtimeModel = workflowTranslator.toRuntimeModel(workflow);
    return fastApiClient.execute(workflowId, userId, runtimeModel, tokens);
}
```

#### 5-2. InternalExecutionController 토큰 비교 보안 강화

**파일**: `src/main/java/org/github/flowify/execution/controller/InternalExecutionController.java` (lines 39-41)

```java
// 변경 전
if (!internalToken.equals(token)) {
    log.warn("Internal token mismatch on callback for execId={}. expected length={}, received length={}",
            execId, internalToken.length(), token.length());

// 변경 후
if (!MessageDigest.isEqual(
        internalToken.getBytes(StandardCharsets.UTF_8),
        token.getBytes(StandardCharsets.UTF_8))) {
    log.warn("Internal token mismatch on callback for execId={}", execId);
```

---

## 수정 대상 파일 요약

| 파일 | Work Unit | 변경 내용 |
|------|-----------|-----------|
| `execution/entity/WorkflowExecution.java` | 1 | error, output, durationMs 필드 추가 |
| `execution/service/ExecutionService.java` | 1, 5 | completeExecution 확장 + executeScheduled 검증 추가 |
| `execution/controller/InternalExecutionController.java` | 1, 5 | 콜백 파라미터 확장 + timing-safe 토큰 비교 |
| `execution/service/FastApiClient.java` | 2 | .timeout(30s) 추가 |
| `workflow/service/choice/ChoiceMappingService.java` | 3 | 데드 코드 제거 + NPE 가드 |
| `workflow/dto/WorkflowResponse.java` | 4 | @Builder(toBuilder=true) |
| `workflow/controller/WorkflowController.java` | 4 | 수동 빌더 → toBuilder 1줄 |

**총 7개 파일 수정, 신규 파일 없음**

---

## 실행 순서 (권장)

1. **Work Unit 1** (데이터 유실) — 가장 높은 영향도, 현재 실행 결과가 영구 손실 중
2. **Work Unit 2** (타임아웃) — 프로덕션 안정성
3. **Work Unit 3** (ChoiceMappingService) — 최근 Choice Wizard 변경과 직접 관련
4. **Work Unit 5** (보안/검증) — 보안 강화
5. **Work Unit 4** (WorkflowResponse) — 유지보수성, 가장 낮은 긴급도

---

## 검증 방법

- `./gradlew compileJava` — 빌드 성공 확인
- `./gradlew test` — 전체 테스트 통과 확인
