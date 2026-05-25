# 워크플로우 Google Calendar 지원 제거 Spring 설계

> 작성일: 2026-05-25  
> 대상 레포: `flowify-BE-spring`  
> 범위: editor catalog, workflow generation, choice mapping, preview 계약에서 `google_calendar` 지원 제거  
> 관련 레포: `flowify-FE`, `flowify-BE`  
> 참고 문서: `docs/WORKFLOW_TRIGGER_SETTINGS_SPRING_DESIGN.md`, `docs/WORKFLOW_CONTEXTUAL_PROCESSING_NODE_OPTIONS_SPRING_DESIGN.md`, `src/main/resources/catalog/source_catalog.json`, `src/main/resources/catalog/sink_catalog.json`

---

## 1. 목적

이 문서는 Flowify 프로젝트에서 Google Calendar를 지원 대상에서 제외하기 위해 Spring 레이어에서 어떤 계약을 제거해야 하는지 정의한다.

이번 이슈의 목적은 단순히 UI에서 Google Calendar를 숨기는 것이 아니다. Spring이 editor catalog와 generation 규칙의 기준점 역할을 하므로, Spring에서 `google_calendar`를 제거해야만 FE와 FastAPI가 같은 기준으로 정리된다.

이번 변경으로 Spring이 달성해야 할 목표는 아래와 같다.

- 새 워크플로우 생성 흐름에서 Google Calendar source/sink가 더 이상 catalog에 노출되지 않는다.
- AI/choice mapping에서 Google Calendar가 추천 대상 서비스로 더 이상 등장하지 않는다.
- workflow generation이 `calendar_picker`, `calendar_id` 같은 Google Calendar 전용 field를 더 이상 생성하지 않는다.
- FE preview/설명 패널이 의존하는 source/sink catalog 계약에서 Google Calendar 항목이 제거된다.
- 레거시 `google_calendar` 워크플로우는 "지원 제외" 상태로 취급할 수 있는 기준을 마련한다.

---

## 2. 현재 상태

### 2.1 source catalog에 Google Calendar source가 남아 있다

현재 source catalog는 `google_calendar` source를 아래 두 mode와 함께 공개하고 있다.

- `daily_schedule`
- `weekly_schedule`

근거:

- `src/main/resources/catalog/source_catalog.json`

이 계약이 살아 있는 한 FE add-node/source picker는 Google Calendar source를 계속 노출한다.

### 2.2 sink catalog에 Google Calendar sink가 남아 있다

현재 sink catalog는 `google_calendar` sink를 아래 input type과 config schema로 공개하고 있다.

- accepted input types: `TEXT`, `SCHEDULE_DATA`
- config fields:
  - `calendar_id`
  - `event_title_template`
  - `duration_minutes`
  - `action`

근거:

- `src/main/resources/catalog/sink_catalog.json`

이 계약이 남아 있으면 FE sink panel과 workflow editor는 Google Calendar sink를 계속 지원 가능한 서비스로 해석한다.

### 2.3 generation policy에 calendar 전용 schema key가 남아 있다

현재 generation policy는 아래 Google Calendar 전용 field type / field key를 알고 있다.

- `calendar_picker`
- `calendar_id`

근거:

- `src/main/java/org/github/flowify/workflow/service/generation/WorkflowGenerationConfigPolicy.java`

이 상태에서는 source/sink catalog만 제거해도 generation 쪽에 calendar 전용 규칙이 남아 있게 된다.

### 2.4 choice mapping과 generation support에 서비스 명칭이 남아 있다

현재 Spring은 서비스 라벨과 generation 지원 서비스 목록에 Google Calendar를 포함한다.

근거:

- `src/main/java/org/github/flowify/workflow/service/choice/ChoiceMappingService.java`
- `src/main/java/org/github/flowify/workflow/service/generation/WorkflowGenerationSupport.java`
- `src/main/resources/docs/mapping_rules.json`

즉 catalog만 제거해도, choice wizard나 AI generation 문맥에서는 여전히 Google Calendar를 후보로 인식할 수 있다.

### 2.5 현재 Spring은 Google Calendar를 완전 구현된 provider로 취급하지 않는다

기존 문서와 테스트 흔적으로 보면 Google Calendar는 picker/provider가 완성되지 않았고, 일부 항목은 `picker_supported=false` 상태다.

즉 Google Calendar는 현재도 완성형 서비스라기보다 "카탈로그에 노출되지만 실제 구현은 제한적인 서비스"에 가깝다. 이번 제거는 기능 축소가 아니라 제품 지원 범위를 명확히 정리하는 작업으로 보는 편이 맞다.

---

## 3. 제품 결정

### 3.1 Google Calendar는 신규 생성/수정 대상에서 완전히 제외한다

이번 이슈 이후 Spring의 공식 계약에서는 Google Calendar를 지원 서비스로 더 이상 간주하지 않는다.

적용 범위:

- source catalog
- sink catalog
- choice mapping
- generation config policy
- generation support service list

즉 FE가 Spring 계약을 기준으로 화면을 구성하는 모든 경로에서 Google Calendar가 사라져야 한다.

### 3.2 레거시 Google Calendar 워크플로우는 마이그레이션 대상이 아니라 unsupported legacy로 본다

이번 이슈에서는 기존 DB의 `google_calendar` 노드를 다른 서비스로 자동 변환하지 않는다.

정책:

- 새로 만들 수 없다.
- catalog에서 찾을 수 없다.
- generation 대상에서 제외된다.
- 기존 워크플로우는 후속 레거시 정리 정책 전까지 unsupported legacy로 취급한다.

즉 Spring의 1차 목표는 "노출 제거"와 "신규 생성 차단"이다. 기존 DB 레코드의 자동 치환은 이번 범위에 포함하지 않는다.

### 3.3 문서와 코드의 기준점을 Spring catalog로 통일한다

이번 제거 작업의 기준점은 Spring catalog다.

정리 순서는 아래가 맞다.

1. Spring source/sink catalog에서 제거
2. Spring generation/choice mapping에서 제거
3. FE가 catalog 기반 노출을 잃도록 정리
4. FastAPI runtime에서 실제 sink/source 코드를 제거

이 순서를 따르면 레포 간 계약 충돌이 줄어든다.

---

## 4. Spring 변경 범위

### 4.1 source catalog에서 `google_calendar` 제거

대상 파일:

- `src/main/resources/catalog/source_catalog.json`

변경 내용:

- `google_calendar` service entry 전체 제거
- `daily_schedule`, `weekly_schedule` mode 정의 제거

기대 효과:

- FE source picker와 editor preview가 Google Calendar source를 더 이상 렌더링하지 않는다.

### 4.2 sink catalog에서 `google_calendar` 제거

대상 파일:

- `src/main/resources/catalog/sink_catalog.json`

변경 내용:

- `google_calendar` sink entry 전체 제거
- `calendar_id`, `event_title_template`, `duration_minutes`, `action` field schema 제거

기대 효과:

- FE sink picker와 sink config panel에서 Google Calendar가 더 이상 서비스 후보로 나타나지 않는다.

### 4.3 generation config policy에서 calendar 전용 field 제거

대상 파일:

- `src/main/java/org/github/flowify/workflow/service/generation/WorkflowGenerationConfigPolicy.java`

변경 내용:

- 지원 field type 목록에서 `calendar_picker` 제거
- 지원 field key 목록에서 `calendar_id` 제거

기대 효과:

- AI generation이 더 이상 Google Calendar 전용 config를 만들지 않는다.

### 4.4 generation support service 목록에서 제거

대상 파일:

- `src/main/java/org/github/flowify/workflow/service/generation/WorkflowGenerationSupport.java`

변경 내용:

- 서비스 허용 목록에서 `google_calendar` 제거

기대 효과:

- generation prompt/결정 로직이 Google Calendar를 지원 서비스로 보지 않는다.

### 4.5 choice mapping에서 제거

대상 파일:

- `src/main/java/org/github/flowify/workflow/service/choice/ChoiceMappingService.java`
- `src/main/resources/docs/mapping_rules.json`

변경 내용:

- `"google_calendar" -> "Google Calendar"` 서비스 라벨 매핑 제거
- Google Calendar 관련 choice option 제거

기대 효과:

- choice wizard/AI 추천 흐름에서 Google Calendar가 더 이상 downstream 서비스로 등장하지 않는다.

---

## 5. FE / FastAPI와의 정합성

### 5.1 FE

FE는 Spring catalog를 source of truth로 삼는 부분이 많다. 따라서 Spring에서 `google_calendar`가 빠지면 FE는 아래 방향으로 정리할 수 있다.

- node registry / icon registry / dashboard badge / list badge 제거
- add-node rollout 제거
- choice panel의 `Google Calendar` 매핑 제거

즉 FE는 Spring catalog 제거 이후 "노출 제거" 중심으로 맞추는 것이 안전하다.

### 5.2 FastAPI

FastAPI는 실제 sink runtime에 Google Calendar 실행 코드가 남아 있다.

즉 Spring에서 catalog를 제거하더라도 FastAPI에서 아래는 별도로 제거해야 한다.

- `GoogleCalendarService`
- `OutputNodeStrategy._send_google_calendar()`
- 관련 테스트와 fixture token

따라서 Spring 제거만으로는 완결되지 않는다. 다만 Spring 제거가 먼저 되어야 FE와 runtime의 기준이 분명해진다.

---

## 6. 테스트 전략

### 6.1 단위 테스트

수정 대상:

- `src/test/java/org/github/flowify/catalog/CatalogServiceTest.java`
- `src/test/java/org/github/flowify/workflow/ChoiceMappingServiceTest.java`

확인 항목:

- source catalog 응답에서 `google_calendar`가 사라졌는가
- sink catalog 응답에서 `google_calendar`가 사라졌는가
- choice mapping 결과에 `Google Calendar`가 남지 않는가

### 6.2 수동/계약 검증

확인 항목:

- FE add-node/service picker에서 Google Calendar가 더 이상 보이지 않는가
- FE sink 설정 후보에 Google Calendar가 더 이상 없는가
- generation path가 `calendar_id` 같은 field를 더 이상 만들지 않는가

### 6.3 회귀 검증

특히 아래 서비스가 영향 없이 그대로 남아야 한다.

- `google_drive`
- `google_sheets`
- `gmail`
- `notion`
- `github`
- `canvas_lms`

즉 "Google 계열 서비스 중 Calendar만 제거"가 정확히 되어야 한다.

---

## 7. 구현 순서

1. Spring catalog에서 `google_calendar` source/sink 제거
2. Spring generation config policy와 support list 정리
3. Spring choice mapping / mapping rules 정리
4. Spring 테스트 갱신
5. FE / FastAPI 제거 작업 진행
6. 기존 `flowify-fe-*` 컨테이너 기준 전체 테스트 및 클릭 검증

---

## 8. 완료 기준

- Spring source catalog에 `google_calendar`가 없다.
- Spring sink catalog에 `google_calendar`가 없다.
- Spring generation policy가 `calendar_picker`, `calendar_id`를 더 이상 다루지 않는다.
- Spring choice mapping과 rules에서 Google Calendar가 제거된다.
- 관련 테스트가 통과한다.
- FE와 FastAPI companion 이슈가 같은 제거 정책으로 맞춰진다.

