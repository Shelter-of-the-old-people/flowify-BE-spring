# Workflow Source/Sink 백엔드 요청서

> 작성일: 2026-04-20
> 대상 프로젝트: `flowify-fe`
> 대상 백엔드: `flowify-BE-spring`, `flowify-BE`
> 관련 문서:
> - `docs/WORKFLOW_SOURCE_SINK_FLOW_DESIGN.md`
> - `docs/NODE_SETUP_WIZARD_DESIGN.md`

---

## 1. 목적

이 문서는 source/sink 기반 workflow editor 목표 상태를 기준으로,
**백엔드가 먼저 결정하거나 제공해야 하는 계약과 아키텍처 선택지**를 요청하는 문서다.

핵심 목적은 아래 두 가지다.

1. 프론트가 현재 이해하고 있는 backend 현실과 실제 backend 계획이 일치하는지 확인
2. source/sink editor 구현 전에 backend가 먼저 열어야 하는 public contract를 합의

즉 이 문서는 “프론트가 이렇게 구현하겠다”는 통보가 아니라,
**백엔드의 최종 판단과 정정을 요청하는 문서**다.

---

## 2. 중요한 전제

현재 이 요청서는 아래를 기준으로 작성되었다.

- 현재 FE 코드
- 현재 backend 실행 코드
- 지금까지 확인된 문서와 시나리오

하지만 FE가 모를 수 있는 backend 상황이 있을 수 있다.

예:

- 아직 반영되지 않은 backend 브랜치 계획
- 운영 환경 제약
- FastAPI 실행기 개편 계획
- Spring/FastAPI 사이 내부 translator 계획
- OAuth/provider별 제약
- trigger 모델 개편 계획
- 데이터 마이그레이션 또는 하위 호환성 요구
- 성능/보안/배포 구조상 제약

따라서 backend는 이 문서를 검토할 때,
**FE가 모르는 내부 사정이나 선행 계획이 있다면 먼저 정정해 주어야 한다.**

즉 본 문서의 내용은 backend 최종 판단 전의 가설이며,
backend가 더 정확한 사실을 알고 있다면 그 기준으로 덮어쓰는 것이 맞다.

---

## 3. FE가 현재 이해한 상태

현재 FE가 이해한 backend 현실은 아래와 같다.

### 3.1 현재 가능한 것

- Spring은 느슨한 workflow graph 저장이 가능하다
- 미완료 node 저장은 가능하다
- `mapping_rules.json` 기반 middle choice는 부분 지원된다
- OAuth 연결 상태 조회는 가능하다
- FastAPI는 실행 후 결과 payload 저장은 가능하다

### 3.2 현재 없는 것

- source service catalog API
- source mode 목록 API
- source target schema API
- source mode -> canonical type authoritative mapping
- sink service catalog API
- sink accepted input type 계약
- sink detailed config schema
- result schema preview API

### 3.3 현재 가장 큰 구조적 문제

- Spring 저장 모델과 FastAPI 실행 모델이 서로 다른 노드 체계를 사용한다
- `mapping_rules.json`은 middle choice 전용이지 source/sink editor 전체 규칙이 아니다

즉 FE가 현재 이해한 결론은:

**source/sink editor는 지금 바로 구현 가능한 단계가 아니라, backend 선행 계약과 모델 정렬이 먼저 필요한 단계**라는 것이다.

---

## 4. FE 목표 상태

프론트가 최종적으로 가고 싶은 editor 구조는 아래다.

`source service -> canonical input type -> middle processing -> sink service -> final sink mapping`

### 4.1 시작 노드

목표 흐름:

1. source service 선택
2. 인증
3. source mode 선택
4. 데이터 대상 선택
5. canonical input type 확정
6. source node 생성

### 4.2 중간 처리

- `mapping_rules.json` 기반 choice
- 현재 `outputDataType` 기준 후속 처리 선택

### 4.3 도착 노드

목표 흐름:

1. sink service 선택
2. 인증
3. 임시 end node 생성
4. 중간 처리 완료
5. 마지막 단계에서 sink 상세 설정

마지막 단계는 단순 옵션 입력이 아니라,
**현재 결과 schema를 sink schema에 매핑하는 단계**로 생각하고 있다.

---

## 5. 백엔드에 요청하는 핵심 판단

### 5.1 가장 먼저 필요한 것: Spring/FastAPI 정렬 방식 확정

FE가 이해한 선택지는 두 가지다.

#### 옵션 A
- Spring 저장 모델도 FastAPI 실행 타입 중심으로 정렬

#### 옵션 B
- Spring은 service-centric editor/public contract 유지
- FastAPI는 runtime/execution-centric 모델 유지
- 실행 직전에 translation layer로 변환

현재 FE는 **옵션 B가 더 현실적**이라고 보고 있다.

이유:

- FE/editor 도메인을 service-centric하게 유지할 수 있다
- Spring이 source/sink catalog와 schema preview 같은 public contract owner가 되기 쉽다
- FastAPI는 실행용 모델에 집중할 수 있다

하지만 이 판단 역시 backend가 더 잘 아는 내부 제약이 있으면 달라질 수 있다.

따라서 backend는 아래를 먼저 답해주어야 한다.

1. 최종 권장안은 A인지 B인지
2. 그 이유가 무엇인지
3. 이 결정을 먼저 하지 않으면 이후 API shape가 흔들리는지
4. FE가 모르는 내부 계획 때문에 다른 선택이 필요한지

---

## 6. 백엔드에 요청하는 public contract

## 6.1 source catalog API

FE가 필요한 최소 정보:

- source 서비스 목록
- `key`
- `label`
- `authRequired`
- `sourceModes[]`
- `canonicalInputType`
- `triggerKind`
- `targetSchema`

권장 API 예시:

```json
GET /api/editor-catalog/sources
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

backend에 요청하는 판단:

- 이 API를 Spring public API로 여는 것이 맞는지
- source mode / canonicalInputType / targetSchema를 backend authority로 가져가는 것이 맞는지
- FE가 모르는 source provider 제약이 있는지

---

## 6.2 sink catalog API

FE가 필요한 최소 정보:

- sink 서비스 목록
- `key`
- `label`
- `authRequired`
- `acceptedInputTypes`
- `configSchema` 또는 `configSchemaRef`

권장 API 예시:

```json
GET /api/editor-catalog/sinks
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

1차 범위 후보:

- Slack
- Gmail
- Notion
- Google Drive
- Google Sheets
- Google Calendar

backend에 요청하는 판단:

- 이 API도 Spring public API로 여는 것이 맞는지
- sink schema는 Spring이 public contract를 주고 FastAPI가 실행만 맡는 구조가 맞는지
- 위 6개 sink를 1차 범위로 보는 것이 현실적인지
- FE가 모르는 provider별 제약이 있는지

---

## 6.3 result schema preview API

도착 노드 마지막 단계에서는 “현재 결과 schema”와 “sink schema”를 매핑해야 한다.

FE가 필요한 최소 정보:

- `schemaType`
- `isList`
- `fields[]`
- `displayHints`

권장 API 예시:

```json
POST /api/workflows/schema-preview
{
  "workflowId": "optional",
  "draft": {}
}
```

권장 응답 예시:

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

backend에 요청하는 판단:

1. 이 preview는 `workflow 전체`보다 `active node output` 또는 `end node 직전 output` 단위가 맞는지
2. public API는 Spring이 맡고, inference는 FastAPI 또는 공용 분석 계층이 맡는 구조가 맞는지
3. FE가 모르는 schema inference 제약이 있는지

---

## 6.4 incomplete node lifecycle 계약

현재 FE가 이해한 상태:

- 저장은 가능할 수 있다
- choice는 선행 조건이 충족되지 않으면 실패할 수 있다
- execute는 더 엄격히 차단되어야 한다

FE가 backend에 요청하는 계약:

- `save 가능 조건`
- `choice 가능 조건`
- `execute 가능 조건`
- `isConfigured` 같은 명시 상태 필요 여부
- preflight validation 책임 계층

중요:

이 부분 역시 FE가 모르는 backend 제약이 있을 수 있다.
예를 들면 저장은 허용하지만 특정 단계부터는 DB 구조상 허용이 어렵거나,
trigger와 source mode가 충돌할 수 있다.

따라서 backend는 **미완료 node를 어디까지 공식 지원할 수 있는지**를 명확히 판단해 주어야 한다.

---

## 7. FE가 지금 멈춰야 하는 영역

backend 판단 전까지 FE는 아래를 실제 구현 명세로 확정하지 않는다.

- dynamic source/sink catalog UI
- source mode -> canonical type 확정 UI
- result schema preview 기반 sink mapping UI
- incomplete node execute 흐름
- source/sink editor 전체를 현재 제품 구조로 확정하는 일

이건 단순히 API가 아직 없어서가 아니라,
**backend가 아직 최종 아키텍처와 authority boundary를 확정하지 않았기 때문**이다.

---

## 8. backend에 요청하는 응답 방식

backend는 가능하면 아래 형식으로 답해주면 좋다.

### 8.1 먼저 결정해야 하는 아키텍처 선택

- 항목
- 추천안
- 이유
- 관련 파일/라인
- FE가 모를 수 있는 추가 제약

### 8.2 바로 필요한 새 API/계약

- 항목
- 최소 응답 shape
- 어느 계층(Spring/FastAPI/공용 분석기)이 맡는 것이 자연스러운지
- FE가 모를 수 있는 추가 제약

### 8.3 FE가 지금 멈춰야 하는 영역

- 항목
- 이유
- 선행 조건
- FE가 모를 수 있는 추가 제약

---

## 9. backend에 명시적으로 요청하는 점

이 문서는 FE가 현재까지 파악한 사실을 정리한 것이다.

하지만 backend는 아래를 우선적으로 확인해주어야 한다.

1. FE가 모르는 내부 제약이 있는지
2. 아직 반영되지 않은 backend 계획이 있는지
3. 운영/배포/보안/성능상 이유로 다른 방향이 필요한지
4. Spring/FastAPI 역할 분리에 이미 팀 내 합의가 있는지
5. FE의 source/sink editor 목표 상태와 충돌하는 숨은 제약이 있는지

즉 backend는 이 문서를 그대로 승인하기보다,
**필요하면 먼저 정정하고 그 뒤에 합의하는 방식으로 검토해주어야 한다.**

---

---

## 10. 백엔드 응답 (2026-04-20)

### 10.1 아키텍처 선택: 옵션 B 확정

- Spring = editor/public contract owner
- FastAPI = runtime/execution owner
- Translation layer: `WorkflowTranslator` 서비스로 구현
- 실행 직전에 Spring editor model → FastAPI runtime payload 변환

### 10.2 source catalog API 구현 완료

```
GET /api/editor-catalog/sources
```
- 10개 서비스, 서비스별 source mode + canonical type + trigger kind + target schema
- `catalog/source_catalog.json`에서 정적 관리
- `canonicalInputType`, `triggerKind`, `targetSchema` 모두 backend authority

### 10.3 sink catalog API 구현 완료

```
GET /api/editor-catalog/sinks
GET /api/editor-catalog/sinks/{serviceKey}/schema?inputType=...
```
- 6개 서비스, accepted input types + config schema
- `catalog/sink_catalog.json`에서 정적 관리

### 10.4 schema preview API 구현 완료

```
GET /api/workflows/{id}/schema-preview
```
- 로컬 추론 (노드 체인의 마지막 outputDataType 기반)
- `catalog/schema_types.json`에 canonical type별 필드 정의

### 10.5 lifecycle 계약

- 저장: 미완성 노드 저장 가능
- choice: outputDataType 선행 조건 충족 필요
- 실행: 모든 노드 validated + OAuth 토큰 필수

### 10.6 FE가 모를 수 있는 추가 사항

- sink configSchema는 inputType에 관계없이 현재 서비스별 단일 스키마. inputType별 분기가 필요하면 추후 확장 가능
- translation layer는 현재 최소 변환만 수행. FastAPI runtime 모델이 확정되면 변환 로직 고도화 필요
- schema preview는 선형 워크플로우만 지원. 분기 워크플로우는 "UNDETERMINED" 반환

---

## 11. 한 줄 요약

이 요청서는 source/sink 기반 workflow editor를 위해
**backend가 먼저 결정해야 할 아키텍처, public contract, lifecycle 계약**을 정리한 문서이며,
동시에 **FE가 아직 모르는 backend 상황이 있을 수 있다는 점을 전제로 정정과 판단을 요청하는 문서**다.

**2026-04-20 기준 backend 응답 완료. 4개 선행 이슈 모두 초기 구현됨.**
