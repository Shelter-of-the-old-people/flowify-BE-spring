# Workflow Contextual Processing Node Options Spring Design

> 작성일: 2026-05-22
> 대상: Spring backend
> 범위: 이전 노드 데이터 문맥 기반 중간처리노드 종류 / 내부 선택지 / 도착노드 선택지 제한 설계
> 관련 레포: `flowify-FE`, `flowify-BE`

---

## 1. 목적

이 문서는 워크플로우 편집기에서 사용자가 보게 되는 선택지를
"현재 노드 앞에 어떤 데이터가 들어왔는지"에 따라 제한하는 Spring 기준 설계를 정리한다.

이번 이슈에서 Spring이 책임지는 범위는 아래 3가지다.

- 중간처리노드 종류 제한
- 중간처리노드 내부 follow-up / branch-config 선택지 제한
- 도착노드(service / action) 선택지 제한을 위한 authoritative rule 제공

별도 이슈로 진행하는 AI 노드 수동 프롬프트 입력 UI는 이 문서 범위에 포함하지 않는다.

---

## 2. 현재 상태

### 2.1 middle choice API는 이미 존재한다

현재 middle choice는 아래 API를 사용한다.

- `GET /api/workflows/{id}/choices/{prevNodeId}`
- `POST /api/workflows/{id}/choices/{prevNodeId}/select`

`WorkflowController`와 `WorkflowService`는 실제로 저장된 `prevNode.outputDataType`을 기준으로
`ChoiceMappingService`를 호출해 선택지를 만든다.

즉 선택지 authority는 이미 Spring 쪽에 있다.

### 2.2 하지만 query context가 얕다

현재 `GET /choices/{prevNodeId}`는 query param으로 아래 2개만 받는다.

- `service`
- `file_subtype`

즉 `fields`, 선택된 컬럼, 이전 노드가 남긴 구조적 정보 같은 문맥은
선택지 조회 시점에 충분히 반영되지 못한다.

### 2.3 rules에는 많은 액션이 있지만 실행 안정성은 균일하지 않다

`mapping_rules.json`에는 다양한 처리 옵션이 정의되어 있다.

예:

- `filter_condition`
- `filter_type`
- `filter_content`
- `condition_value`
- `classify_by_field`
- `classify`
- `ai_filter`
- `merge`

하지만 현재 FastAPI runtime 기준으로는 이 중 일부만 안정적으로 end-to-end 동작한다.
Spring이 이 차이를 모른 채 전부 노출하면 사용자는 "보이지만 실제로는 불안정한 선택지"를 만나게 된다.

### 2.4 도착노드 제한은 input type 수준에 머물러 있다

현재 sink 선택은 주로 catalog의 `accepted_input_types`에 의존한다.

즉 아래 수준의 제한은 가능하다.

- `TEXT`를 받는 sink만 노출
- `SPREADSHEET_DATA`를 받는 sink만 노출

반면 아래 수준의 제한은 아직 없다.

- GitHub API 응답일 때만 적합한 도착지
- 특정 필드가 있을 때만 자연스러운 도착지
- 파일 subtype이 이미지일 때만 적합한 후속 경로

---

## 3. 목표

이번 이슈의 목표는 아래처럼 고정한다.

1. `GET /choices/{prevNodeId}` 단계에서 이전 노드 데이터 문맥을 더 풍부하게 반영한다.
2. Spring이 runtime-safe 하지 않은 middle choice를 기본적으로 숨긴다.
3. follow-up / branch-config의 옵션 source도 같은 문맥 기준으로 줄인다.
4. sink catalog에도 같은 문맥 규칙을 부여해 FE가 도착노드 목록을 줄일 수 있게 한다.
5. FE는 Spring이 정의한 rule을 소비하고, 의미 해석 authority는 Spring이 가진다.

---

## 4. 설계 원칙

### 4.1 Spring authority 유지

선택지의 의미 결정은 Spring이 맡는다.

FE는 아래만 담당한다.

- 이전 노드 문맥 수집
- 선택지 표시
- 사용자 선택 저장

즉 "무엇을 보여줄지"의 기준은 Spring rule이 되어야 한다.

### 4.2 output data type은 기본 축으로 유지한다

이번 이슈가 문맥 기반 제한이라고 해도,
선택지 해석의 1차 축은 여전히 `prevNode.outputDataType`이다.

문맥 필터는 이 기본 축 위에 추가로 얹는다.

### 4.3 실행 불안정 옵션은 기본적으로 숨긴다

이번 이슈의 목적은 "더 많은 선택지를 보여주는 것"이 아니라
"의도한 선택지만 보여주는 것"이다.

따라서 runtime support가 모호한 액션은 우선 노출하지 않는 방향이 맞다.

### 4.4 sink도 같은 문맥 언어를 쓴다

middle choice와 sink choice가 서로 다른 문맥 언어를 쓰면
FE가 동일한 사용자 흐름을 일관되게 만들기 어렵다.

따라서 `service`, `file_subtype`, `fields` 같은 핵심 키는
middle / sink 모두 공통 의미를 가져야 한다.

---

## 5. 문맥 모델

v1에서 Spring이 처리할 공통 문맥 키는 아래로 제한한다.

- `service`
  - 의미: 현재 데이터의 가장 가까운 origin service
  - 예: `github`, `google_drive`, `gmail`
- `file_subtype`
  - 의미: 파일 기반 데이터의 subtype
  - 예: `image`, `pdf`
- `fields`
  - 의미: 사용자가 실제로 선택했거나 현재 데이터에서 인지 가능한 필드/컬럼 목록
  - 예: `["title", "url", "created_at"]`

### 5.1 service 값은 display label이 아니라 stable key를 우선한다

현재 FE에는 service를 `GitHub`, `Google Calendar`, `쿠팡`처럼 표시용 문자열로 바꾸는 흔적이 있다.

이번 이슈부터 Spring 계약은 가능하면 display label이 아니라 stable service key를 기준으로 삼는 편이 안전하다.

권장 예:

- `github`
- `google_calendar`
- `google_drive`

다만 현재 `mapping_rules.service_fields`는 대부분 display label을 key로 쓰고 있고,
`ChoiceMappingService.getServiceFields()`도 그 구조를 그대로 읽는다.
GitHub만 예외적으로 service key를 special-case 처리하고 있다.

즉 service key 기준으로 정리하려면 아래가 함께 바뀌어야 한다.

- FE query context 값
- `mapping_rules.service_fields` key
- `ChoiceMappingService.getServiceFields()` 정규화 로직

필요하다면 Spring은 과도기적으로 legacy display label도 허용하되,
장기 rule 기준값은 service key 중심으로 정리한다.

### 5.2 fields는 query 단계에서도 받아야 한다

현재 `fields` 정보는 선택 확정 이후 config에는 남더라도,
최초 `GET /choices` 단계에는 거의 반영되지 못한다.

이번 이슈의 핵심은 "처음부터 의도한 선택지만 보여주기"이므로
`fields`는 `select`뿐 아니라 `query` 단계에서도 들어와야 한다.

---

## 6. middle choice API 설계

### 6.1 GET contract 확장

`GET /api/workflows/{id}/choices/{prevNodeId}`는 아래 문맥을 받을 수 있어야 한다.

- `service`
- `file_subtype`
- `fields`

`fields`는 반복 query param 또는 Spring이 자연스럽게 바인딩 가능한 배열 형식으로 받는다.

예시:

```text
GET /api/workflows/{id}/choices/{prevNodeId}?service=github&fields=title&fields=url&fields=created_at
```

### 6.2 controller / service 책임

`WorkflowController`

- query param을 명시적으로 수집한다.
- 비어 있지 않은 값만 context map에 담는다.

`WorkflowService`

- `prevNode.outputDataType` 조회
- request context와 저장된 노드 config를 결합할 수 있는지 판단
- `ChoiceMappingService`에 전달

`ChoiceMappingService`

- 데이터 타입별 rule 조회
- `applicable_when` 필터
- runtime capability 필터
- `options_source` 확장
- 정렬 / 응답 생성

추가로 현재 `getProcessingMethodChoices(String dataType)`는 context를 받지 않으므로,
processing method도 문맥 기반으로 제한하려면
메서드 시그니처와 호출 경로 자체를 `context` aware 하게 바꿔야 한다.

### 6.3 select API는 구조를 크게 바꾸지 않는다

`POST /select`는 기존처럼 사용자의 action 확정과 후속 설정 계산에 집중한다.

이번 이슈의 핵심은 `GET /choices` 단계에서의 노출 제한이므로,
select API는 context 소비 범위를 필요 최소한으로 유지하는 편이 좋다.

---

## 7. rule 설계

### 7.1 existing `applicable_when`를 적극 사용한다

현재 `mapping_rules.json`의 `applicable_when` 개념은 유지한다.

이번 이슈에서는 이 필드를 아래 수준까지 적극 사용한다.

- processing method 노출 여부
- action 노출 여부
- follow-up option 묶음 노출 여부
- branch-config option 묶음 노출 여부

예:

- `file_subtype=image`일 때만 `describe_image`
- `fields`에 특정 컬럼이 있을 때만 `filter_fields`
- `service=github`일 때만 GitHub-specific field option

다만 현재 구조상 `applicable_when`는 `Action`에만 있고
`ProcessingMethod.options`를 담는 `Option` DTO에는 같은 메타데이터가 없다.

즉 processing method까지 같은 방식으로 제한하려면 아래 중 하나가 필요하다.

- `Option` DTO에 `applicable_when` 추가
- processing method 전용 rule 구조 확장

### 7.2 runtime exposure 상태를 rule에 명시한다

Spring은 "문맥에는 맞지만 runtime이 아직 약한 옵션"을 숨길 수 있어야 한다.

이를 위해 action / processing method / branch option에 아래 같은 메타데이터를 두는 방향을 권장한다.

- `runtime_status: supported | planned | hidden`

기본 동작:

- `supported`: 기본 노출
- `planned`: 기본 비노출
- `hidden`: 내부용, 비노출

이 값은 FE fallback rule과도 공유 가능하다.

현재 `ChoiceResponse.options[]`의 `Option` DTO는 아래 정도만 내려준다.

- `id`
- `label`
- `type`
- `node_type`
- `output_data_type`
- `priority`
- `branch_config`

따라서 `runtime_status`나 UX 설명 문구를 실제 응답에 싣고 싶다면
Spring `Option` DTO와 FE `ChoiceOption` 타입을 함께 확장해야 한다.

### 7.3 v1에서 기본 노출할 middle choice

아래 범위는 기본적으로 노출 가능한 축으로 본다.

- `one_by_one`
- `branch_by_file_type`
- `classify_by_content`
- `filter_fields`
- `filter_fields_table`
- `filter_metadata`
- `filter_metadata_table`
- `choice rule prompt + runtime output contract`가 맞는 AI 계열 액션
  - 원칙: Spring `ChoicePromptResolver`가 `action=process`로 정규화할 수 있고, 결과 output type이 `TEXT` 또는 `SPREADSHEET_DATA`인 경우
  - 예: `summarize`, `extract_info`, `translate`, `describe_image`, `ocr`, `ai_summarize`, `ai_analyze`, `ai_generate`, `classify_intent`, `sentiment`, `urgency`, `extract_todos`, `draft_reply`, `ai_refine`

### 7.4 v1에서 기본 비노출할 middle choice

아래는 우선 숨기는 쪽이 안전하다.

- `filter_condition`
- `filter_type`
- `filter_content`
- `condition_value`
- `classify_by_field`
- `classify`
- `ai_filter`
- `merge`

이유:

- deterministic runtime이 아직 없거나
- output contract가 현재 기대와 다르거나
- FE 입력 UI가 아직 충분히 받쳐주지 않기 때문이다.

특히 `ai_filter`, `merge`는 현재 의미상 `API_RESPONSE`를 유지하는 것처럼 보이지만,
실제 FastAPI LLM 출력은 구조화된 `items` 대신 `content` 문자열로 수렴할 수 있어 별도 정리가 필요하다.

---

## 8. 도착노드 선택지 제한 설계

### 8.1 별도 sink choice API 대신 catalog rule 확장을 우선한다

v1에서는 sink 전용 choice API를 새로 만들기보다,
기존 sink catalog에 문맥 규칙을 추가하고 FE가 이를 필터링하는 방향이 현실적이다.

권장 필드:

- `accepted_input_types`
- `applicable_when`

예:

```json
{
  "service": "google_sheets",
  "accepted_input_types": ["SPREADSHEET_DATA", "API_RESPONSE"],
  "applicable_when": {
    "service": ["github", "google_drive"]
  }
}
```

현재 `sink_catalog.json`과 FE `SinkServiceResponse` 타입에는 `applicable_when`이 없다.
즉 이 설계를 쓰려면 catalog schema 자체를 확장해야 한다.

### 8.2 sink rule도 같은 문맥 모델을 사용한다

sink catalog의 `applicable_when`은 middle choice와 동일한 키 체계를 쓴다.

- `service`
- `file_subtype`
- `fields`

이렇게 해야 FE가 middle choice context helper를 sink panel에서도 재사용할 수 있다.

---

## 9. 구현 포인트

주요 수정 후보는 아래다.

- `workflow/controller/WorkflowController.java`
- `workflow/service/WorkflowService.java`
- `workflow/service/choice/ChoiceMappingService.java`
- `workflow/service/choice/dto/*`
- `execution/service/WorkflowTranslator.java`
- `src/main/resources/docs/mapping_rules.json`
- `src/main/resources/catalog/sink_catalog.json`

권장 보강 컴포넌트:

- `ChoiceExposurePolicy` 또는 유사한 helper
  - 책임: rule 후보 중 runtime-safe 한 액션만 남기는 정책 캡슐화

이 정책을 `ChoiceMappingService`에 직접 하드코딩하기보다 분리해 두면
FastAPI capability 변경 시 추적이 쉬워진다.

---

## 10. 테스트 설계

### 10.1 controller / service

- `fields` query param 바인딩
- context map 생성
- 빈 값 제거

### 10.2 choice mapping

- `service` 기준 action 제한
- `file_subtype` 기준 action 제한
- `fields` 기준 action 제한
- `runtime_status=planned` 액션 비노출

### 10.3 sink catalog

- `accepted_input_types`만으로 보이던 sink가 `applicable_when`으로 더 줄어드는지 확인

### 10.4 translator / runtime alignment

- 숨긴 액션이 runtime으로 전달되지 않는지 확인
- `classify_by_content`, `branch_by_file_type`, `one_by_one`은 기존 동작을 깨지 않는지 확인

---

## 11. 이번 이슈 완료 기준

이번 이슈에서 Spring은 아래 조건을 만족해야 완료로 본다.

- `GET /choices/{prevNodeId}`가 `service`, `file_subtype`, `fields` 문맥을 받을 수 있다.
- processing method도 문맥 기반으로 제한할 수 있는 구조가 마련된다.
- action 노출 시 runtime-safe 하지 않은 옵션은 기본적으로 숨겨진다.
- `mapping_rules`와 choice DTO가 이번 이슈에서 필요한 문맥 / 노출 메타데이터를 표현할 수 있다.
- sink catalog가 `accepted_input_types` 외 문맥 기반 제한을 담을 수 있는 구조를 가진다.
- 기존 stable 흐름
  - `one_by_one`
  - `branch_by_file_type`
  - `classify_by_content`
  는 회귀 없이 유지된다.

이번 이슈에서 끝내지 않는 것은 아래다.

- `filter_condition`, `filter_type`, `filter_content` runtime 구현
- `condition_value`, `classify_by_field`, `classify`의 일반화된 runtime 구현
- `ai_filter`, `merge`의 구조화 output 재설계
- AI 노드 수동 프롬프트 UI

---

## 12. 오픈 포인트

1. `service` 문맥 값을 display label에서 service key로 완전히 전환할지
2. `fields`를 query param으로 유지할지, 이후 복잡도가 커지면 별도 context DTO로 바꿀지
3. sink choice도 장기적으로는 별도 API로 승격할지

---

## 13. 결론

이번 이슈에서 Spring은 단순한 rule 조회기가 아니라
"사용자에게 지금 보여줘도 되는 선택지"를 결정하는 선택지 authority가 되어야 한다.

핵심은 아래 3가지다.

- `GET /choices` 문맥을 `service`, `file_subtype`, `fields`까지 확장한다.
- `mapping_rules.json`에 runtime exposure 개념을 넣어 unstable action을 숨긴다.
- sink catalog에도 같은 문맥 규칙을 부여해 middle / destination 선택 경험을 일관되게 만든다.
