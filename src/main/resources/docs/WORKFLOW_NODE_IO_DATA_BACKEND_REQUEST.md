# 워크플로우 노드 입출력 데이터 패널 연동 백엔드 요청서

> 작성일: 2026-04-30  
> 작성 목적: 프론트엔드의 "들어오는 데이터 / 출력 데이터" 패널을 실제 실행 결과와 연결하기 위한 백엔드 API 및 응답 계약 요청

---

## 1. 요청 배경

프론트엔드에는 이미 워크플로우 에디터에서 선택한 노드 기준으로 다음 두 패널이 존재합니다.

- 들어오는 데이터 패널
- 출력 데이터 패널

현재 패널은 실제 실행 데이터가 아니라 워크플로우 그래프 메타데이터만 표시합니다.

현재 표시 가능한 정보:

- 이전 노드의 `outputDataType`
- 현재 노드의 `dataType`, `outputDataType`
- 노드 설정 완료 여부
- 실행 가능 여부
- middle node wizard에서 선택한 처리 방식 및 옵션
- "백엔드 연동 후 제공 예정" placeholder

프론트엔드의 다음 목표는 워크플로우 실행 후 사용자가 각 노드를 클릭하면서 실제 데이터 흐름을 확인할 수 있게 만드는 것입니다.

사용자가 확인해야 하는 정보:

- 이 노드가 실제로 어떤 데이터를 입력으로 받았는지
- 이 노드가 처리 후 어떤 데이터를 출력했는지
- 실패한 노드는 어떤 입력에서 어떤 에러가 발생했는지
- 스킵된 노드는 왜 데이터가 없는지
- 실행 전에는 예상 입출력 스키마가 무엇인지

즉, 현재 설정 중심 화면을 노드 단위 실행 디버깅 경험으로 확장하려는 목적입니다.

---

## 2. 확정된 정책

이번 요청 범위에서 다음 정책을 전제로 합니다.

### 2.1 실행 로그 조회 권한

실행 로그와 실행 데이터는 워크플로우 소유자만 확인할 수 있습니다.

- 소유자: 실행 목록, 실행 상세, 노드 입출력 데이터 조회 가능
- 공유 사용자: 실행 로그와 실행 데이터 조회 불가

공유 사용자가 워크플로우 에디터를 볼 수 있더라도 실행 결과 데이터는 내려주지 않는 정책으로 정리합니다.

### 2.2 실행 목록 API 응답 변경 허용

기존 `GET /api/workflows/{workflowId}/executions` endpoint의 응답 shape를 summary DTO로 변경해도 됩니다.

프론트엔드 타입은 변경된 백엔드 응답에 맞춰 같이 수정하겠습니다.

### 2.3 민감 데이터 마스킹

현재 단계에서는 어떤 데이터가 민감 데이터인지 프론트에서 확정하기 어렵습니다.

따라서 민감 데이터 마스킹, 필드별 비공개 처리, 크기 제한 정책은 이번 범위에서 제외하고 추후 별도 보안/프라이버시 이슈로 다룹니다.

단, 현재 FastAPI에서 수행 중인 기본 sanitize 정책은 유지해 주세요.

### 2.4 NodeLog id

`NodeLog`에 별도 `id` 필드는 필요하지 않습니다.

프론트엔드는 백엔드 계약에 맞춰 `ExecutionLog.id`를 제거하거나 optional로 조정하겠습니다.

---

## 3. 현재 백엔드 코드 기준 재사용 가능 지점

현재 백엔드에는 입출력 데이터 패널 구현에 필요한 기반이 이미 일부 존재합니다.

### 3.1 FastAPI 실행 엔진

FastAPI 실행 엔진은 노드 실행 시 `nodeLogs`에 다음 값을 저장합니다.

- `nodeId`
- `status`
- `inputData`
- `outputData`
- `snapshot`
- `error`
- `startedAt`
- `finishedAt`

따라서 실제 노드별 입출력 데이터의 원천은 이미 존재합니다.

### 3.2 Spring WorkflowExecution / NodeLog

Spring 쪽 `WorkflowExecution` 엔티티에도 `nodeLogs`가 있고, `NodeLog`에는 다음 필드가 존재합니다.

- `nodeId`
- `status`
- `inputData`
- `outputData`
- `snapshot`
- `error`
- `startedAt`
- `finishedAt`

즉 MongoDB에 저장된 실행 문서를 Spring API로 읽어 프론트에 전달할 수 있는 구조입니다.

### 3.3 기존 실행 조회 API

현재 존재하는 API:

```http
GET /api/workflows/{workflowId}/executions
GET /api/workflows/{workflowId}/executions/{executionId}
```

이 API를 기반으로 summary/detail DTO를 분리하면 프론트 입출력 데이터 패널 연동에 필요한 대부분의 데이터 흐름을 만들 수 있습니다.

### 3.4 SchemaPreviewService

현재 `SchemaPreviewService`는 워크플로우 최종 출력 중심으로 스키마를 계산합니다.

다만 내부적으로는 `outputDataType`과 `CatalogService.getSchemaTypeDefinition()`을 사용하므로, 선택한 노드의 `dataType`, `outputDataType` 기준 input/output schema preview API로 확장하기 좋습니다.

---

## 4. 요청사항

## 4.1 실행 상태 값 정규화

### 문제

FastAPI 실행 엔진 내부 상태는 성공 시 `success`를 사용합니다.

하지만 Spring callback payload 생성 시 성공 상태가 `completed`로 변환되고, Spring `completeExecution()`은 callback의 `status`를 그대로 `state`에 저장할 수 있습니다.

프론트엔드는 현재 다음 상태값을 기준으로 동작합니다.

```text
pending
running
success
failed
rollback_available
stopped
```

따라서 `completed`가 API 응답으로 내려오면 프론트의 실행 완료 판단, polling 종료 판단, 성공 상태 UI가 어긋날 수 있습니다.

### 요청

Spring 저장 상태와 API 응답 상태를 다음 값으로 통일해 주세요.

```text
pending
running
success
failed
rollback_available
stopped
```

구체 요청:

- FastAPI callback에서 `status=completed`가 들어오면 Spring 저장 시 `success`로 정규화
- 실행 목록 API 응답에서도 `success`로 반환
- 실행 상세 API 응답에서도 `success`로 반환
- 가능하면 FastAPI callback payload 자체도 `success`로 맞추는 방향 검토

### 기대 효과

- 프론트 polling 종료 조건이 안정적으로 동작합니다.
- 실행 상태 badge가 정확하게 표시됩니다.
- 노드 입출력 데이터 표시 조건이 단순해집니다.

---

## 4.2 실행 목록 API summary DTO 전환

### 문제

현재 `GET /api/workflows/{workflowId}/executions`는 `WorkflowExecution` 엔티티를 그대로 반환하는 구조입니다.

`WorkflowExecution`에는 `nodeLogs`가 포함되고, `nodeLogs`에는 `inputData`, `outputData`, `snapshot`처럼 커질 수 있는 데이터가 들어갑니다.

프론트엔드는 실행 상태 확인과 최신 실행 탐색을 위해 실행 목록을 자주 조회할 수 있습니다. 이때 상세 로그가 매번 포함되면 응답이 불필요하게 무거워집니다.

### 요청

기존 API 응답을 summary DTO로 변경해 주세요.

```http
GET /api/workflows/{workflowId}/executions
```

응답 예시:

```json
[
  {
    "id": "exec_xxx",
    "workflowId": "workflow_xxx",
    "state": "success",
    "startedAt": "2026-04-30T10:00:00Z",
    "finishedAt": "2026-04-30T10:00:10Z",
    "durationMs": 10000,
    "errorMessage": null,
    "nodeCount": 5,
    "completedNodeCount": 5
  }
]
```

목록 응답에서 제외할 필드:

```text
nodeLogs
inputData
outputData
snapshot
stackTrace
```

권한 정책:

- 워크플로우 소유자만 조회 가능
- 공유 사용자는 실행 목록 조회 불가

### 기대 효과

- 실행 상태 polling 및 최신 실행 탐색이 가벼워집니다.
- 상세 데이터는 필요한 시점에만 조회할 수 있습니다.

---

## 4.3 실행 상세 API nodeLogs 계약 명시

### 요청

실행 상세 API는 `nodeLogs`를 포함하는 상세 DTO를 반환해 주세요.

```http
GET /api/workflows/{workflowId}/executions/{executionId}
```

응답 예시:

```json
{
  "id": "exec_xxx",
  "workflowId": "workflow_xxx",
  "state": "success",
  "startedAt": "2026-04-30T10:00:00Z",
  "finishedAt": "2026-04-30T10:00:10Z",
  "durationMs": 10000,
  "errorMessage": null,
  "nodeLogs": [
    {
      "nodeId": "node_xxx",
      "status": "success",
      "inputData": {},
      "outputData": {},
      "snapshot": {},
      "error": null,
      "startedAt": "2026-04-30T10:00:00Z",
      "finishedAt": "2026-04-30T10:00:05Z"
    }
  ]
}
```

프론트에서 사용하는 필수 필드:

- `nodeId`
- `status`
- `inputData`
- `outputData`
- `error.message`
- `startedAt`
- `finishedAt`

`NodeLog.id`는 필요하지 않습니다.

권한 및 정합성 요청:

- 워크플로우 소유자만 조회 가능
- path의 `workflowId`와 execution 문서의 `workflowId`가 일치하는지 검증 필요
- 일치하지 않으면 `EXECUTION_NOT_FOUND` 또는 `WORKFLOW_ACCESS_DENIED` 계열 오류 반환

### 기대 효과

프론트는 실행 상세의 `nodeLogs`에서 선택 노드의 로그를 찾아 입출력 데이터를 표시할 수 있습니다.

```ts
const nodeLog = execution.nodeLogs.find((log) => log.nodeId === activeNodeId);
```

---

## 4.4 완료 콜백 데이터 저장 보장

### 문제

FastAPI callback payload에는 다음 값이 포함될 수 있습니다.

```json
{
  "status": "completed",
  "output": {},
  "durationMs": 1200,
  "error": null
}
```

Spring DTO에는 `output`, `durationMs`, `error` 필드가 존재하지만, 현재 완료 처리에서 저장되지 않거나 일부 무시될 수 있습니다.

### 요청

`WorkflowExecution`에 다음 top-level 필드를 저장해 주세요.

- `output`
- `durationMs`
- `errorMessage`

구체 요청:

- `ExecutionCompleteRequest.output` 저장
- `ExecutionCompleteRequest.durationMs` 저장
- `ExecutionCompleteRequest.error`는 API 응답에서는 `errorMessage`로 노출 권장
- `completeExecution()`에서 `status`, `finishedAt` 외에도 위 값 저장
- callback status가 `completed`면 저장 전 `success`로 정규화

### 기대 효과

- 실행 목록 summary에서 실행 시간과 실패 이유를 표시할 수 있습니다.
- 실행 상세 상단에서 워크플로우 최종 output 요약을 표시할 수 있습니다.
- 노드별 데이터와 워크플로우 최종 결과를 구분해 보여줄 수 있습니다.

---

## 4.5 최신 실행 조회 API 추가

### 요청

프론트는 노드 클릭 시 가장 최근 실행 결과를 기준으로 입출력 데이터를 보여주고 싶습니다.

아래 API를 추가해 주세요.

```http
GET /api/workflows/{workflowId}/executions/latest
```

응답 예시:

```json
{
  "id": "exec_xxx",
  "workflowId": "workflow_xxx",
  "state": "success",
  "startedAt": "2026-04-30T10:00:00Z",
  "finishedAt": "2026-04-30T10:00:10Z",
  "durationMs": 10000,
  "errorMessage": null,
  "nodeCount": 5,
  "completedNodeCount": 5
}
```

정책 요청:

- 워크플로우 소유자만 조회 가능
- 정렬 기준은 `startedAt desc`
- 실행 이력이 없으면 `data: null` 형태를 권장
- 404를 사용해야 한다면 프론트에서 별도 처리할 수 있도록 오류 코드 명시 필요

### 기대 효과

- 프론트에서 실행 목록 전체를 받은 뒤 최신 실행을 고르는 로직을 줄일 수 있습니다.
- 노드 패널 진입 시 최신 실행 상태를 빠르게 확인할 수 있습니다.

---

## 4.6 특정 실행의 특정 노드 입출력 데이터 조회 API 추가

### 요청

실행 상세 전체를 내려받지 않고, 특정 노드에 필요한 입출력 데이터만 조회할 수 있는 API를 추가해 주세요.

```http
GET /api/workflows/{workflowId}/executions/{executionId}/nodes/{nodeId}/data
```

응답 예시:

```json
{
  "executionId": "exec_xxx",
  "workflowId": "workflow_xxx",
  "nodeId": "node_xxx",
  "status": "success",
  "inputData": {},
  "outputData": {},
  "snapshot": {},
  "error": null,
  "startedAt": "2026-04-30T10:00:00Z",
  "finishedAt": "2026-04-30T10:00:05Z",
  "available": true,
  "reason": null
}
```

데이터가 없는 경우 예시:

```json
{
  "executionId": "exec_xxx",
  "workflowId": "workflow_xxx",
  "nodeId": "node_xxx",
  "status": "skipped",
  "inputData": null,
  "outputData": null,
  "snapshot": null,
  "error": null,
  "startedAt": null,
  "finishedAt": null,
  "available": false,
  "reason": "NODE_SKIPPED"
}
```

권장 reason 값:

```text
NO_EXECUTION
EXECUTION_RUNNING
NODE_NOT_EXECUTED
NODE_SKIPPED
NODE_FAILED
DATA_EMPTY
```

권한 및 정합성 요청:

- 워크플로우 소유자만 조회 가능
- path의 `workflowId`와 execution 문서의 `workflowId` 일치 검증
- path의 `nodeId`가 해당 workflow에 존재하는지 검증 권장

### 기대 효과

- 노드 클릭 시 필요한 데이터만 조회할 수 있습니다.
- 실행 상세 전체의 큰 `nodeLogs` payload를 반복해서 내려받지 않아도 됩니다.

---

## 4.7 최신 실행 기준 노드 입출력 데이터 조회 API 추가

### 요청

사용자 기본 경험은 "가장 최근 실행 결과 기준으로 선택 노드의 입출력 데이터 확인"입니다.

따라서 아래 API를 추가하면 프론트 구현이 가장 단순해집니다.

```http
GET /api/workflows/{workflowId}/executions/latest/nodes/{nodeId}/data
```

응답은 `4.6 특정 실행의 특정 노드 입출력 데이터 조회 API`와 동일한 구조를 사용하면 됩니다.

실행 이력이 없는 경우:

```json
{
  "executionId": null,
  "workflowId": "workflow_xxx",
  "nodeId": "node_xxx",
  "status": null,
  "inputData": null,
  "outputData": null,
  "snapshot": null,
  "error": null,
  "startedAt": null,
  "finishedAt": null,
  "available": false,
  "reason": "NO_EXECUTION"
}
```

### 기대 효과

- 프론트에서 최신 실행 조회와 노드 데이터 조회를 분리하지 않아도 됩니다.
- 패널 진입 시 API 호출 흐름이 단순해집니다.

---

## 4.8 노드 단위 스키마 프리뷰 API 추가

### 문제

현재 schema preview는 워크플로우 최종 출력 중심입니다.

입출력 데이터 패널에서는 선택한 노드 기준으로 다음 정보가 필요합니다.

- 이 노드가 받는 데이터 스키마
- 이 노드가 내보내는 데이터 스키마

실행 전 또는 실행 결과가 없는 상태에서는 실제 데이터 대신 이 스키마를 보여줄 예정입니다.

### 요청

아래 API를 추가해 주세요.

```http
GET /api/workflows/{workflowId}/nodes/{nodeId}/schema-preview
```

응답 예시:

```json
{
  "nodeId": "node_xxx",
  "input": {
    "schemaType": "SINGLE_FILE",
    "isList": false,
    "fields": [],
    "displayHints": {}
  },
  "output": {
    "schemaType": "TEXT",
    "isList": false,
    "fields": [],
    "displayHints": {}
  }
}
```

재사용 가능 지점:

- `NodeDefinition.dataType`
- `NodeDefinition.outputDataType`
- `CatalogService.getSchemaTypeDefinition()`
- 기존 `SchemaPreviewService`

권한 정책:

- 워크플로우를 볼 수 있는 사용자는 스키마 프리뷰 조회 가능
- 단, 실행 데이터가 아니라 타입/스키마 정보만 반환

### 기대 효과

- 실행 전에도 입출력 패널이 의미 있는 정보를 보여줄 수 있습니다.
- 실제 데이터가 없을 때도 빈 placeholder 대신 예상 데이터 구조를 보여줄 수 있습니다.

---

## 5. 프론트 연결 계획

백엔드 기능이 준비되면 프론트는 다음 순서로 연결할 예정입니다.

1. 사용자가 노드를 클릭한다.
2. active node id와 workflow id를 확인한다.
3. 최신 실행 기준 노드 데이터 API를 호출한다.
4. 실행 데이터가 있으면 `inputData`, `outputData`, `status`, `error`를 표시한다.
5. 실행 데이터가 없으면 노드 단위 schema preview API를 호출한다.
6. schema preview도 없으면 기존 타입/상태 기반 placeholder를 표시한다.

상태별 UI:

| 상태 | 들어오는 데이터 패널 | 출력 데이터 패널 |
|------|----------------------|------------------|
| 실행 전 | input schema 또는 이전 노드 output type | output schema 또는 output type |
| 실행 중 | 실행 완료 후 표시 안내 | 실행 완료 후 표시 안내 |
| 성공 | `inputData` 표시 | `outputData` 표시 |
| 실패 | `inputData` + error 표시 | 실패 상태 표시 |
| 스킵 | 스킵 안내 | 스킵 안내 |
| 데이터 없음 | 데이터 없음 안내 | 데이터 없음 안내 |

---

## 6. 우선순위 제안

### 1순위: 프론트 안정성 선행

1. 실행 상태 값 정규화
2. 실행 목록 API summary DTO 전환
3. 실행 상세 API `nodeLogs` 계약 명시
4. 완료 콜백 데이터 저장 보장

### 2순위: 노드 입출력 패널 직접 연동

5. 최신 실행 조회 API
6. 특정 실행의 특정 노드 입출력 데이터 조회 API
7. 최신 실행 기준 노드 입출력 데이터 조회 API

### 3순위: 실행 전 데이터 구조 표시

8. 노드 단위 스키마 프리뷰 API

---

## 7. 요청 요약

백엔드에 요청하는 핵심은 다음입니다.

1. `completed` 상태가 프론트로 내려오지 않도록 `success`로 정규화
2. 실행 목록은 summary DTO로 경량화
3. 실행 상세은 `nodeLogs.inputData/outputData/error/snapshot` 포함
4. 실행 로그 조회는 소유자만 가능
5. `NodeLog.id`는 요구하지 않음
6. 완료 callback의 `output`, `durationMs`, `error` 저장
7. 최신 실행 조회 API 추가
8. 노드 입출력 데이터 조회 API 추가
9. 최신 실행 기준 노드 입출력 데이터 조회 API 추가
10. 노드 단위 input/output schema preview API 추가

위 기능이 준비되면 프론트는 기존 입출력 패널을 실제 실행 데이터 기반 패널로 연결할 수 있습니다.
