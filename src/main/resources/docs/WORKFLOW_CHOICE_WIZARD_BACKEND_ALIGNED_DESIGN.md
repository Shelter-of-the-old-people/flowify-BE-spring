# Workflow Choice Wizard Backend-Aligned Design

> 작성일: 2026-04-29  
> 목적: 현재 프론트의 middle-node choice wizard를 실제 백엔드 코드와 백엔드 설계 문서 기준으로 다시 정렬한다.  
> 범위: `GET /api/workflows/{id}/choices/{prevNodeId}` / `POST /api/workflows/{id}/choices/{prevNodeId}/select` 를 사용하는 middle-node 설정 흐름  
> 비범위: source/sink picker, OAuth, workflow execute, 템플릿 상세, 대시보드

---

## 1. 문제 정의

현재 워크플로우 생성 화면에서 middle placeholder를 클릭하고 처리 방식을 선택하면
`POST /api/workflows/{id}/choices/{prevNodeId}/select` 호출이 400을 반환한다.

하지만 이 문제는 단순 필드명 mismatch만의 문제가 아니다.

현재 프론트는 다음 흐름을 하나의 로컬 stateful wizard로 운영한다.

- processing method 선택
- action 선택
- follow-up / branch-config 입력

반면 백엔드는 항상 **실제로 저장된 이전 노드의 `outputDataType`** 을 기준으로 choice를 해석한다.

즉 현재 충돌은 아래 3층으로 나뉜다.

1. transport contract mismatch  
2. wizard state ownership mismatch  
3. 프론트의 로컬 전이 흐름과 백엔드의 authoritative flow mismatch

---

## 2. 현재 백엔드 동작

### 2.1 `GET /choices/{prevNodeId}`

백엔드는 다음 순서로 선택지를 만든다.

1. workflow 조회
2. `prevNodeId`에 해당하는 노드 조회
3. `prevNode.getOutputDataType()` 확인
4. `ChoiceMappingService.getOptionsForNode(outputDataType, context)` 호출

즉 choice 조회의 기준은 **프론트가 상상하는 현재 단계**가 아니라
**서버에 저장된 이전 노드의 현재 output type** 이다.

### 2.2 `POST /choices/{prevNodeId}/select`

백엔드는 다음 순서로 선택 결과를 계산한다.

1. workflow 조회
2. `prevNodeId` 노드 조회
3. `prevNode.getOutputDataType()` 확인
4. `ChoiceMappingService.onUserSelect(selectedOptionId, dataType)` 호출
5. `NodeSelectionResult` 반환

중요한 점:

- 현재 백엔드는 request body의 `dataType`을 authoritative source로 보지 않는다.
- 항상 `prevNode`의 실제 `outputDataType`을 다시 읽는다.

---

## 3. 현재 백엔드 설계 문서가 의도한 흐름

백엔드 sequence diagram 기준으로, 목록형 데이터(`FILE_LIST`)에서
`requires_processing_method = true` 인 경우 흐름은 아래와 같다.

1. `GET /choices/{prevNodeId}`
2. 사용자가 1차 처리 방식 선택  
   예: `one_by_one`
3. `POST /choices/{prevNodeId}/select`
4. 백엔드가 `nodeType=LOOP`, `outputDataType=SINGLE_FILE` 반환
5. 프론트가 **LOOP 노드를 즉시 저장**
6. 이후 단계는 **새로 저장된 LOOP 노드 id** 를 기준으로
   다시 `GET /choices/{loopNodeId}` 를 호출
7. 다음 action 선택 진행

즉 백엔드가 의도한 구조는:

> processing method 이후의 다음 단계가 같은 `prevNodeId` 위에서 이어지는 것이 아니라,  
> **새로 저장된 중간 노드를 기준으로 새 choice 단계가 다시 시작되는 흐름**

이다.

---

## 4. 현재 프론트 구현과의 차이

현재 프론트 `OutputPanel`은 다음 구조로 동작한다.

1. `GET /choices/{rootParentNodeId}` 로 wizard 시작
2. processing method 선택 후 `POST /select`
3. 로컬 state에서 `currentDataTypeKey`를 전이  
   예: `FILE_LIST -> SINGLE_FILE`
4. 이후 action 단계도 **같은 `rootParentNodeId`** 를 기준으로 계속 진행

이 구조는 백엔드 설계와 다르다.

왜냐하면 백엔드는:

- `one_by_one` 선택 후
- LOOP 노드를 저장한 뒤
- 그 새 노드를 기준으로 다음 choice를 다시 조회

하길 기대하기 때문이다.

즉 현재 프론트는:

- **중간 노드를 저장하기 전에**
- 로컬 state만으로 다음 단계 의미를 이어가고 있다.

이 때문에 서버는 여전히 원래 parent node의 `outputDataType`
예: `FILE_LIST`
를 기준으로 판단하고, 프론트가 기대하는 `SINGLE_FILE` 문맥과 어긋난다.

---

## 5. 직접적인 400 원인

현재 프론트는 `POST /select` 에 아래 형태로 보낸다.

```json
{
  "selectedOptionId": "one_by_one",
  "dataType": "FILE_LIST",
  "context": {}
}
```

하지만 현재 백엔드 DTO는 JSON property `actionId`를 기대한다.

즉 현재 running backend 기준으로는:

- 프론트: `selectedOptionId`
- 백엔드: `actionId`

가 불일치하여 400이 발생할 수 있다.

이 mismatch는 반드시 해결해야 하지만, 이것만 고쳐서는 구조 문제가 끝나지 않는다.

---

## 6. 목표 상태

목표는 아래 한 문장으로 요약한다.

> **선택의 의미 결정은 백엔드가 하고, 프론트는 그 결과를 저장하고 다음 단계로 자연스럽게 연결하는 orchestration layer가 된다.**

즉 프론트는 더 이상 choice 의미를 로컬에서 계산하는 주체가 아니라:

- 백엔드가 준 choice를 보여주고
- 백엔드가 준 `NodeSelectionResult`를 저장하고
- 그 저장 결과를 기준으로 다음 단계 UI를 열어주는 역할

을 맡는다.

---

## 7. 설계 원칙

### 7.1 백엔드 authority 우선

아래 항목은 백엔드 응답을 authority로 본다.

- choice 단계 분기
- node type 결정
- output data type 결정
- follow-up 존재 여부
- branch-config 존재 여부

프론트는 정상 흐름에서 이를 재해석하거나 merge 우선권을 갖지 않는다.

### 7.2 중간 노드 저장 후 다음 단계 진행

processing method 또는 action 선택으로 node type이 결정되면,
프론트는 먼저 그 노드를 workflow에 저장/갱신해야 한다.

그 다음:

- follow-up 이 있으면 현재 노드 추가 설정 단계로 진입
- follow-up 이 없고 다음 노드 설정이 필요하면
  **방금 저장된 노드 id 기준으로 새 choice 단계 시작**

### 7.3 같은 `prevNodeId`로 다단계 wizard를 이어가지 않는다

`FILE_LIST -> one_by_one -> LOOP` 가 결정되면,
이후 단계의 authoritative parent는 root parent가 아니라
**새로 생성된 LOOP 노드** 다.

### 7.4 로컬 `mappingRules`는 authority가 아니다

정상 흐름에서는:

- `GET /choices` 응답
- `POST /select` 응답

만으로 wizard를 이어간다.

`mappingRules.ts` / `mappingRulesAdapter.ts` 는 아래 경우에만 사용한다.

1. 백엔드 unavailable 시 제한적 fallback
2. 개발 중 참고 데이터
3. 과도기적 호환층

또한 이 fallback 책임은 **`features/choice-panel/model`** 에 둔다.  
`widgets/output-panel` 이 직접 규칙 해석 authority를 가져서는 안 된다.

---

## 8. 레이어 책임 분리

코드 컨벤션 기준으로 이번 이슈의 책임은 아래처럼 나눈다.

### 8.1 `entities/workflow`

책임:

- choice API request/response type 정의
- transport contract adapter
- `actionId` / `selectedOptionId` 같은 백엔드 계약 정합 처리
- query/mutation hook 제공

포함 후보:

- `src/entities/workflow/api/types.ts`
- `src/entities/workflow/api/*`
- `src/entities/workflow/model/useWorkflowChoicesQuery.ts`
- `src/entities/workflow/model/useSelectWorkflowChoiceMutation.ts`

### 8.2 `features/choice-panel/model`

책임:

- choice flow helper
- fallback `mappingRules` adapter
- choice context shaping helper
- 백엔드 미지원 상황에서의 제한적 보조 규칙

즉 로컬 choice 규칙은 이 레이어에 남고,
widget이 직접 merge/계산을 들고 있지 않게 한다.

### 8.3 `pages/workflow-editor/model`

책임:

- editor-specific wizard state machine
- “선택 -> 저장 -> 다음 단계 시작” orchestration
- 현재 단계가 follow-up 인지, 다음 node choice 인지 판정
- 새 노드 id 기준 후속 query 시작

즉 이번 이슈의 핵심 orchestration은
**page-level model 책임** 으로 둔다.

### 8.4 `widgets/output-panel/ui`

책임:

- 현재 wizard state 렌더링
- step별 UI 표시
- handler 연결

즉 `OutputPanel`은 **orchestration의 최종 주체가 아니라 render shell** 에 가까워져야 한다.

---

## 9. 목표 UX 흐름

### 9.1 `requires_processing_method = true`

예: `FILE_LIST`

1. 사용자가 middle placeholder 클릭
2. 프론트가 `GET /choices/{prevNodeId}`
3. 백엔드가 processing method 선택지 반환
4. 사용자가 `one_by_one` 선택
5. 프론트가 `POST /choices/{prevNodeId}/select`
6. 백엔드가 `NodeSelectionResult(nodeType=LOOP, outputDataType=SINGLE_FILE)` 반환
7. 프론트가 LOOP 노드 저장
8. follow-up 이 없으면
   **LOOP 노드를 기준으로 `GET /choices/{loopNodeId}`**
9. 사용자는 다음 action 선택 단계로 자연스럽게 진입

### 9.2 `requires_processing_method = false`

예: `SINGLE_FILE`

1. 사용자가 middle placeholder 클릭
2. 프론트가 `GET /choices/{prevNodeId}`
3. 백엔드가 action 선택지 반환
4. 사용자가 action 선택
5. 프론트가 `POST /choices/{prevNodeId}/select`
6. 백엔드가 `NodeSelectionResult(nodeType, outputDataType, followUp?, branchConfig?)` 반환
7. 프론트가 현재 노드 저장/갱신
8. follow-up / branch-config 가 있으면 현재 노드 설정 단계로 진입
9. 없으면 다음 노드 설정으로 자동 연결 가능

### 9.3 follow-up / branch-config 존재 시

이 단계는 “다음 노드 설정”이 아니라
**방금 생성된 현재 노드의 추가 설정 단계** 로 취급한다.

즉 흐름은:

1. 선택 확정
2. 현재 노드 type 결정
3. 현재 노드 저장
4. follow-up UI 표시
5. follow-up 완료 후 현재 노드 config 저장
6. 그 다음에야 다음 노드 설정으로 이동

---

## 10. 상태 모델 구체화

프론트가 가져야 할 wizard 상태는 의미 계산용이 아니라
**UI orchestration용 상태** 여야 한다.

추천 상태 예시:

```ts
type ChoiceWizardMode =
  | "idle"
  | "loading-choices"
  | "select-processing-method"
  | "select-action"
  | "configure-follow-up"
  | "persisting-node"
  | "opening-next-node";

type ChoiceWizardContext = {
  workflowId: string;
  anchorNodeId: string;      // 현재 단계 기준 parent node
  selectedNodeId: string | null; // 방금 저장/갱신된 node
  currentChoice: ChoiceResponse | null;
  pendingFollowUp: ChoiceFollowUp | null;
  pendingBranchConfig: ChoiceBranchConfig | null;
};
```

주의:

- `currentDataTypeKey` 같은 값은 로컬 의미 추론용 state가 아니라
  서버 응답 결과를 표시하기 위한 보조 값이어야 한다.
- “현재 단계의 기준 node id”가
  root parent인지,
  새로 생성된 loop node인지
  명확하게 구분되어야 한다.

---

## 11. API 계약 구체화

### 11.1 단기 정렬

우선 프론트 request body는 running backend와 맞아야 한다.

예시:

```json
{
  "actionId": "one_by_one",
  "context": {}
}
```

또는 백엔드가 `selectedOptionId`를 최종 계약으로 채택한다면
FE/BE를 함께 바꿔 일치시켜야 한다.

핵심은:

- 페이지나 widget에서 필드명을 임시로 꼬지 않는다
- **`entities/workflow` adapter에서만 계약을 정렬한다**

### 11.2 `dataType` 필드

현재 백엔드는 authoritative source로 사용하지 않으므로,
최종 계약에서 아래 중 하나로 정리해야 한다.

1. 완전히 제거
2. 디버그/참고값으로만 유지

프론트는 백엔드 결정 흐름에 맞추는 쪽으로 설계한다.

### 11.3 `context`

최종적으로 정리해야 할 항목:

- 어떤 key를 허용하는지
- `fields_from_service`
- `fields_from_data`
- `applicable_when`
- file subtype / service field / branch option

이 문서는 FE가 context shape를 독자 설계하지 않고
백엔드와 계약을 확정해야 함을 전제로 한다.

---

## 12. 구현 단계 제안

### 단계 1. transport contract 정렬

목표:

- `POST /choices/{prevNodeId}/select` 400 제거
- FE/BE request field 정합

포함:

- request body 필드명 정렬
- `entities/workflow` 타입 수정
- transport adapter 추가 또는 제거

### 단계 2. processing-method 이후 흐름 재구성

목표:

- `processing method 선택 -> 노드 저장 -> 새 노드 기준 choice 조회`
  흐름으로 교체

포함:

- root parent 기반 로컬 연장 흐름 제거
- 새 노드 id 기준 재조회 연결

### 단계 3. action / follow-up 흐름 재구성

목표:

- action 선택 후 현재 노드 저장
- follow-up / branch-config 는 현재 노드 설정 단계로 처리
- 완료 후 다음 노드 설정으로 연결

### 단계 4. 로컬 authority 제거

목표:

- `mappingRules` merge 중심 흐름 제거
- 서버 응답 중심 wizard로 단순화

포함:

- `OutputPanel` 내부 로컬 의미 계산 축소
- fallback은 feature/model로만 제한

---

## 13. 백엔드와 반드시 정렬해야 하는 하드 계약

아래 항목은 프론트에서 추정하거나 임의 해석하면 안 된다.  
이번 이슈 구현 전에 **백엔드와 동일한 계약으로 확정**되어야 한다.

### 13.1 `POST /choices/{prevNodeId}/select` request body

반드시 맞아야 하는 항목:

- 선택지 id 필드 이름
  - `actionId`
  - `selectedOptionId`
- `dataType` 포함 여부
- `context` 포함 여부
- `context`의 nullable 정책

즉 프론트는 `entities/workflow` 계층에서
running backend의 request contract와 완전히 동일한 body를 만들어야 한다.

### 13.2 `GET /choices`와 `POST /select`의 책임 경계

반드시 맞아야 하는 항목:

- `GET /choices`는 현재 단계의 선택지만 주는지
- `POST /select`는 최종 `NodeSelectionResult`만 주는지
- follow-up / branch-config가 어느 시점 응답에 실리는지
- processing method와 action이 같은 `ChoiceOption` 구조를 쓰는지

즉 프론트는
어떤 단계에서 어떤 응답 shape를 기대해야 하는지를
백엔드 계약과 정확히 맞춰야 한다.

### 13.3 authoritative data type source

반드시 맞아야 하는 항목:

- 백엔드가 계속 `prevNode.outputDataType`만 authoritative source로 쓰는지
- 아니면 request body의 `dataType`을 의미 있는 입력으로 받을 계획인지
- processing method 이후 다음 단계의 기준 node가
  - 기존 parent인지
  - 새로 저장된 중간 node인지

이 항목은 wizard 전체 흐름을 결정하므로,
프론트에서 임의로 우회하면 안 된다.

### 13.4 중간 노드 저장 타이밍

반드시 맞아야 하는 항목:

- `NodeSelectionResult` 수신 후 프론트가 즉시 node를 저장해야 하는지
- 저장 전에 follow-up을 먼저 받아도 되는지
- processing method 결과가 `LOOP`, `CONDITION_BRANCH`일 때
  그 노드를 먼저 workflow에 반영해야 하는지

현재 설계는 **먼저 저장 -> 그 다음 단계 진행**을 기준으로 하지만,
이 타이밍은 백엔드 의도와 완전히 일치해야 한다.

### 13.5 `context` 공식 shape

반드시 맞아야 하는 항목:

- 허용 key 집합
  - `service`
  - `fields`
  - `file_subtype`
  - `branch option source`
  - 기타
- key별 value type
- `fields_from_service`, `fields_from_data`, `applicable_when`에서
  각 값이 실제로 어떻게 소비되는지

즉 `context`는 프론트가 “대충 object”로 보내는 영역이 아니라,
명시적인 계약 문서가 필요한 구조다.

---

## 14. 구현 협의가 필요한 항목

아래 항목은 중요하지만, 위 13장처럼 wizard 구조를 바로 깨뜨리는
하드 계약과는 구분해서 본다.

### 14.1 error semantics

협의가 필요한 항목:

- invalid option 선택 시 status code
- 잘못된 단계 전이 시 status code
- choice unavailable 시 status code
- 응답 body 에러 shape

프론트는 이 값을 기준으로
- step 유지
- fallback 메시지
- wizard 종료

를 나누게 되지만, 정상 플로우 정렬 이후에 다듬어도 된다.

### 14.2 node add/update payload의 최소 요구사항

협의가 필요한 항목:

- `NodeSelectionResult`를 받은 뒤 프론트가 node add/update 시
  어떤 필드를 반드시 포함해야 하는지
  - `type`
  - `dataType`
  - `outputDataType`
  - `config`
  - `role`
- follow-up 이전의 중간 node가 불완전 config 상태로 저장돼도 되는지
- 저장 직후 `GET /choices/{newNodeId}`가 가능하려면
  어떤 최소 상태가 보장되어야 하는지

이 항목은 중요하지만,
우선은 “선택 -> 저장 -> 새 node 기준 다음 단계”가 맞물리는지부터 맞춘 뒤
세부 payload 최소조건을 정리해도 된다.

---

## 15. 오픈 이슈

백엔드와 함께 확인은 필요하지만,
위 13장/14장보다 우선순위가 한 단계 낮은 UX/표현 계열 항목:

1. `GET /choices` 응답에 UX 보조용 설명 필드를 얼마나 풍부하게 줄지
2. follow-up 문구/description을 백엔드에서 모두 책임질지
3. branch-config UI 힌트(placeholder, helper text)까지 내려줄지
4. 백엔드 unavailable 시 허용할 fallback UX 범위

---

## 16. 최종 결론

현재 choice wizard 문제는 단순 프론트 버그 하나가 아니다.

실제 백엔드가 의도한 구조는:

1. 사용자가 선택
2. 서버가 node 의미 결정
3. 프론트가 노드 저장
4. 새로 저장된 노드를 기준으로 다음 단계 진행

이다.

따라서 프론트는
로컬 wizard state로 의미를 계속 끌고 가는 구조에서 벗어나,
**선택마다 백엔드 결정을 저장하고 그 저장 결과를 기준으로 다음 단계를 다시 시작하는 구조**
로 바뀌어야 한다.

이 방향이:

- 현재 백엔드 코드
- 백엔드 sequence diagram
- `mapping_rules.json` 기반 구조

와 가장 잘 맞는다.
