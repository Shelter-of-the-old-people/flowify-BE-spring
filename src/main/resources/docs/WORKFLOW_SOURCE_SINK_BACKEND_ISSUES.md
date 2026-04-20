# Workflow Source/Sink 백엔드 선행 이슈 정리

> 작성일: 2026-04-20
> 대상 프로젝트:
> - `flowify-BE-spring`
> - `flowify-BE`
> 관련 문서:
> - `docs/WORKFLOW_SOURCE_SINK_FLOW_DESIGN.md`
> - `docs/WORKFLOW_SOURCE_SINK_BACKEND_REQUEST.md`

---

## 1. 목적

이 문서는 source/sink 기반 workflow editor를 위해
backend가 먼저 해결해야 하는 선행 과제를 **실행 가능한 이슈 단위**로 쪼갠 문서다.

핵심 원칙은 아래와 같다.

- FE는 아직 source/sink editor를 실제 구현 명세로 고정하지 않는다
- backend의 public contract와 runtime contract를 먼저 정리한다
- FE가 모르는 backend 내부 제약이 있을 수 있으므로, 각 이슈는 backend 검토 후 범위 조정이 가능하다

---

## 2. 전체 우선순위

1. Spring/FastAPI 정렬 방식 확정 및 translation layer 설계
2. source catalog API
3. sink catalog API
4. result schema preview + incomplete node lifecycle 계약

이 순서가 중요한 이유는,
1번이 정해지지 않으면 2~4번의 API shape가 흔들리기 때문이다.

---

## 3. 이슈 1. Spring/FastAPI 정렬 및 translation layer

### 제목
`backend: Spring editor model과 FastAPI runtime model 정렬 전략 확정`

### 배경

현재 Spring은 workflow를 느슨한 저장 모델로 보관하고,
FastAPI는 제한된 runtime node type 중심으로 실행한다.

즉 지금은:

- Spring 저장 모델
- FastAPI 실행 모델

이 서로 다른 언어를 쓰고 있다.

source/sink editor를 열려면 먼저
**어느 계층이 editor model을 소유하고, 어느 계층이 runtime model을 소유하는지**
정리해야 한다.

### 목표

- 정렬 방식 `B`를 backend 팀 기준으로 최종 확정한다
- Spring은 editor/public contract owner
- FastAPI는 runtime/execution contract owner
- 양자 사이 translation layer 책임 범위를 정의한다

### 범위

- Spring 저장 모델 검토
- FastAPI runtime model 검토
- translator 입력/출력 shape 정의
- preflight validation 위치 정의
- schema inference 책임 계층 초안 정의

### 기대 산출물

- 정렬 방식 결정 문서
- translator 책임 정의
- Spring -> FastAPI 변환 초안
- preflight validation 초안

### 완료 조건

- backend가 정렬 방식 `B`를 공식 결정한다
- Spring/FastAPI 역할 분리가 문서로 남는다
- translator가 어떤 데이터를 받아 어떤 runtime node로 바꾸는지 설명된다
- FE가 이 결정을 source/sink editor의 전제 조건으로 참조할 수 있다

### FE 영향

이 이슈가 끝나야 FE는
source mode, sink service, canonical type, result schema 같은 개념을
어느 API 계층에서 받아야 하는지 확정할 수 있다.

---

## 4. 이슈 2. source catalog API

### 제목
`backend: source service catalog public API 제공`

### 배경

시작 노드는 아래 흐름을 목표로 한다.

1. source service 선택
2. 인증
3. source mode 선택
4. target 선택
5. canonical input type 확정

하지만 현재 backend에는 이를 authoritative 하게 설명하는 catalog API가 없다.

### 목표

FE가 시작 노드를 동적으로 그릴 수 있도록
source catalog를 backend public contract로 제공한다.

### 최소 응답 정보

- `key`
- `label`
- `authRequired`
- `sourceModes[]`
- `canonicalInputType`
- `triggerKind`
- `targetSchema`

### 권장 API

`GET /api/editor-catalog/sources`

### 예시 shape

```json
{
  "services": [
    {
      "key": "google_drive",
      "label": "Google Drive",
      "authRequired": true,
      "sourceModes": [
        {
          "key": "single_file",
          "label": "특정 파일 사용",
          "canonicalInputType": "SINGLE_FILE",
          "triggerKind": "manual",
          "targetSchema": {
            "type": "file_picker",
            "multiple": false
          }
        }
      ]
    }
  ]
}
```

### 범위

- source 서비스 목록 authoritative source 정리
- source mode 목록 정리
- source mode -> canonical type 매핑 정리
- target schema 정의
- Spring public API 노출

### 완료 조건

- FE가 source service / source mode / target UI를 backend 응답만으로 렌더링할 수 있다
- `canonicalInputType`이 FE 임시 규칙이 아니라 backend authority가 된다
- source mode와 triggerKind 해석 책임이 backend에 명시된다

### FE 영향

이 이슈가 끝나야 FE는
source mode -> canonical type 확정 UI를 임시 규칙이 아니라 실제 계약 기반으로 구현할 수 있다.

---

## 5. 이슈 3. sink catalog API

### 제목
`backend: sink service catalog 및 sink schema public API 제공`

### 배경

도착 노드는 먼저 service/auth만 고르고,
가장 마지막 단계에서 sink 상세 설정을 한다.

따라서 FE는 최소한 아래를 알아야 한다.

- 어떤 sink 서비스가 있는지
- 어떤 input type을 받을 수 있는지
- 상세 설정 schema가 무엇인지

### 목표

FE가 sink service picker와 마지막 sink 설정 화면을 그릴 수 있도록
sink catalog와 sink schema 계약을 backend public contract로 제공한다.

### 최소 응답 정보

- `key`
- `label`
- `authRequired`
- `acceptedInputTypes`
- `configSchema` 또는 `configSchemaRef`

### 권장 API

- `GET /api/editor-catalog/sinks`
- `GET /api/editor-catalog/sinks/{serviceKey}/schema?inputType=...`
  또는 동등한 schema endpoint

### 예시 shape

```json
{
  "services": [
    {
      "key": "slack",
      "label": "Slack",
      "authRequired": true,
      "acceptedInputTypes": ["TEXT"],
      "configSchemaRef": "/api/editor-catalog/sinks/slack/schema?inputType=TEXT"
    }
  ]
}
```

### 초기 범위 후보

- Slack
- Gmail
- Notion
- Google Drive
- Google Sheets
- Google Calendar

### 범위

- sink 서비스 목록 authoritative source 정리
- sink별 accepted input types 정의
- sink별 상세 설정 schema 정의
- Spring public API 노출
- FastAPI 실제 execution path와 연결 가능한지 검토

### 완료 조건

- FE가 sink 목록과 지원 가능한 input type을 backend 응답으로 표시할 수 있다
- sink 상세 설정 화면이 schema 기반으로 구성 가능하다
- sink schema 책임 계층이 명확해진다

### FE 영향

이 이슈가 끝나야 FE는
도착 노드의 dynamic sink picker와 마지막 상세 설정 UI를 계약 기반으로 설계할 수 있다.

---

## 6. 이슈 4. result schema preview 및 incomplete lifecycle 계약

### 제목
`backend: schema preview API와 incomplete node lifecycle 계약 제공`

### 배경

도착 마지막 단계는
“현재 결과 schema”와 “sink schema”를 매핑하는 단계다.

또한 현재는 미완료 node를 저장할 수는 있지만,
choice와 execute는 안전하게 이어지지 않는다.

즉 아래 두 가지를 같이 닫아야 한다.

1. 결과 schema preview
2. incomplete node lifecycle

### 목표

- FE가 설정 중인 workflow의 결과 schema를 preview로 받을 수 있게 한다
- 저장 가능 / choice 가능 / execute 가능 조건을 분리해 공식 계약으로 만든다
- `isConfigured` 또는 동등한 상태 모델을 도입할지 결정한다

### 권장 API

`POST /api/workflows/schema-preview`

### 권장 응답 shape

```json
{
  "schemaType": "TEXT",
  "isList": false,
  "fields": [
    {
      "key": "summary",
      "label": "요약",
      "valueType": "string",
      "required": false
    }
  ],
  "displayHints": {
    "preferredView": "document"
  }
}
```

### 계약으로 명시되어야 할 것

- save 가능 조건
- choice 가능 조건
- execute 가능 조건
- `isConfigured` 또는 동등한 상태
- preflight validation 책임 계층

### 범위

- schema inference 책임 계층 정리
- Spring public API gateway 여부 확정
- FastAPI 또는 공용 분석 계층 inference 여부 확정
- incomplete node 상태 전이 정의

### 완료 조건

- FE가 sink 마지막 단계에서 결과 schema preview를 받을 수 있다
- 미완료 node가 어디까지 저장 가능하고, 언제 실행 차단되는지 공식화된다
- FE와 backend가 같은 lifecycle 언어를 쓸 수 있다

### FE 영향

이 이슈가 끝나야 FE는
result schema preview 기반 sink mapping UI와 incomplete node 실행 가드를 실제 구현할 수 있다.

---

## 7. backend가 검토 시 먼저 봐야 할 것

각 이슈는 아래를 전제로 검토해야 한다.

- FE가 모를 수 있는 내부 backend 계획이 있을 수 있다
- 운영/보안/배포/성능 제약이 있을 수 있다
- 아직 반영되지 않은 리팩터링 브랜치가 있을 수 있다
- provider별 제약 때문에 범위가 달라질 수 있다

즉 backend는 이 이슈 목록을 그대로 수락하기보다,
**필요하면 먼저 정정하고 그 뒤에 범위를 확정하는 방식으로 검토**해야 한다.

---

## 8. 백엔드 구현 상태 (2026-04-20 업데이트)

### 이슈 1: Spring/FastAPI 정렬 — 구현 완료

- **결정: 옵션 B** (translation layer)
- Spring = editor/public contract owner
- FastAPI = runtime/execution owner
- `WorkflowTranslator` 서비스 구현: 실행 직전에 Spring editor 모델 → FastAPI runtime 모델 변환
- 위치: `execution/service/WorkflowTranslator.java`

### 이슈 2: source catalog API — 구현 완료

- `GET /api/editor-catalog/sources`
- 10개 source 서비스, 서비스별 source mode → canonical type 매핑 제공
- 정적 JSON (`catalog/source_catalog.json`)으로 관리, `@PostConstruct`에서 로드
- FE는 이 API 응답만으로 source service/mode/target UI를 렌더링 가능

### 이슈 3: sink catalog API — 구현 완료

- `GET /api/editor-catalog/sinks`
- `GET /api/editor-catalog/sinks/{serviceKey}/schema?inputType=...`
- 6개 sink 서비스, 서비스별 acceptedInputTypes + configSchema 제공
- 정적 JSON (`catalog/sink_catalog.json`)으로 관리

### 이슈 4: schema preview — 구현 완료

- `GET /api/workflows/{id}/schema-preview`
- 워크플로우 노드 체인을 순회하여 마지막 outputDataType 기반 스키마 반환
- 로컬 추론 (FastAPI 호출 없음)
- canonical type별 필드 정의: `catalog/schema_types.json`

### incomplete node lifecycle 계약

- **저장**: 미완성 노드 저장 가능 (기존 동작 유지)
- **choice**: `outputDataType` 등 선행 조건 충족 시에만 가능 (기존 `ChoiceMappingService` 동작)
- **실행**: `WorkflowValidator.validate()` 통과 + 모든 서비스 노드에 OAuth 토큰 존재 시에만 허용

---

## 9. 한 줄 요약

source/sink editor를 열기 위한 backend 선행 과제는
**1) 모델 정렬, 2) source catalog, 3) sink catalog, 4) schema preview + lifecycle 계약**
이 네 개 이슈로 나눠서 추진하는 것이 가장 자연스럽다.

**2026-04-20 기준 4개 이슈 모두 초기 구현 완료. FE는 catalog API를 기반으로 source/sink editor 구현을 시작할 수 있다.**
