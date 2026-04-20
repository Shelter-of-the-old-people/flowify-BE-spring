# Workflow Source/Sink Flow Design

> **작성일** 2026-04-19
> **수정일** 2026-04-20
> **목적** 시작/도착 노드 흐름을 프론트 정적 node catalog 중심 구조에서 `source service -> canonical data type -> processing -> sink service` 구조로 재정의한다.
> **문서 성격**
> - 이 문서는 **지금 바로 구현 가능한 FE 상세 명세서가 아니다**
> - 이 문서는 **목표 상태 정의 + 백엔드 선행 계약 + FE 전환 규칙**을 정리하는 문서다
> **우선순위**
> - 시작/도착 노드의 목표 구조 정의는 이 문서가 우선한다
> - 단, 실제 lifecycle과 현재 구현 동작은 `NODE_SETUP_WIZARD_DESIGN.md`를 함께 참고해야 한다

---

## 1. 문제 정의

현재 시작/도착 노드 선택 UI는 아래 FE 정적 데이터에 강하게 의존한다.

- `NODE_REGISTRY`
- `CATEGORY_SERVICE_MAP`
- `requiresAuth`

즉 현재 UI는 사용자가 "무슨 노드를 놓을까?"를 고르는 구조에 가깝다.

반면 최근 검토 결과는 아래와 같다.

- backend는 start/end role별 허용 node type 규칙을 authoritative 하게 갖고 있지 않다
- backend는 source/sink service catalog를 별도 API로 내려주지 않는다
- backend는 sink 상세 설정 schema를 내려주지 않는다
- backend는 FE 설정 단계에서 사용할 result schema preview를 내려주지 않는다
- Spring 저장 모델과 FastAPI 실행 모델은 아직 서로 다른 노드 체계를 사용한다
- `mapping_rules.json`은 middle choice 규칙이지 source/sink catalog를 대체하지 않는다

즉 지금 문제는 단순히 FE UI를 바꾸는 문제가 아니라,
**source/sink editor 목표 상태와 현재 backend 실행 계약 사이의 간극**을 정리하는 문제다.

---

## 2. 이 문서의 위치

이 문서는 세 층으로 읽어야 한다.

### 2.1 목표 상태
우리가 최종적으로 가고 싶은 editor 구조를 정의한다.

### 2.2 백엔드 선행 계약
그 목표 상태가 성립하려면 backend가 무엇을 authoritative 하게 제공해야 하는지 정의한다.

### 2.3 FE 전환 규칙
backend 계약이 아직 없을 때 FE/BE가 임시로 무엇을 공유 기준으로 삼을지 정의한다.

즉 이 문서의 시나리오 기반 catalog와 매핑표는
**최종 authoritative contract가 아니라, backend 계약이 생기기 전까지 사용할 전환 규칙**이다.

### 2.4 rollout gate
아래 backend 선행 계약이 열리기 전까지는, 시작/도착 노드의 실제 제품 UI를 이 문서 목표 상태로 바로 치환하지 않는다.

- 기존 start/end UI는 유지한다
- FE는 source/sink editor를 실제 구현 명세로 고정하지 않는다
- 이 문서는 목표 상태와 backend 선행 과제를 정렬하는 기준 문서로 사용한다

---

## 3. 목표 상태

목표 상태는 아래 흐름이다.

`Source Service`
-> `Canonical Input Data Type`
-> `mapping_rules.json Processing`
-> `Processed Result Data`
-> `Sink Service`
-> `Final Sink Configuration`

예시:

1. Google Drive
2. `FILE_LIST`
3. `LOOP` -> `AI summarize`
4. `TEXT`
5. Slack
6. 채널 / 메시지 포맷 설정

핵심 원칙:

- 시작은 **source service 중심**
- 중간은 **data type 중심**
- 도착은 **sink service 중심**
- source/sink raw response는 그대로 노출하지 않고 presentation layer를 둔다

---

## 4. 목표 상태 상세

## 4.1 시작 노드

시작 노드는 **source service node**다.

역할:

- 외부 서비스에서 데이터를 가져오기
- 내부 canonical input data type으로 정규화하기

### 시작 노드 목표 흐름

1. source service 선택
2. 인증
3. source mode 선택
4. 데이터 대상 선택
5. canonical input data type 확정
6. source node 생성

중요한 점:

- 사용자는 `SINGLE_FILE`, `EMAIL_LIST` 같은 내부 타입 이름을 직접 고르지 않는다
- 사용자가 고르는 것은 `service + source mode + target`
- 시스템은 그 결과를 canonical type으로 매핑한다

### 시작 노드 canonical input data type

- `FILE_LIST`
- `SINGLE_FILE`
- `EMAIL_LIST`
- `SINGLE_EMAIL`
- `SPREADSHEET_DATA`
- `API_RESPONSE`
- `SCHEDULE_DATA`
- `TEXT`

### 시작 노드 canonical type 해석 규칙

- 이벤트형 trigger는 기본적으로 `single`
- 직접 실행 / 전체 조회형 fetch는 기본적으로 `list`
- 시트/행 데이터는 `SPREADSHEET_DATA`
- 일정 범위 조회는 `SCHEDULE_DATA`
- 구조화된 외부 서비스 응답은 `API_RESPONSE`
- 문서/노트/대화 본문은 `TEXT`
- 비 API 웹 결과를 위한 새 canonical type은 추가하지 않고, 결과 형태에 따라 기존 canonical type으로 정규화한다

## 4.2 중간 처리

중간 처리는 **`mapping_rules.json`**을 source of truth로 둔다.

즉 middle은:

- 현재 `outputDataType`
- `mapping_rules.json`
- follow-up / branch / filter / condition

을 기준으로 동작한다.

중간 단계의 책임:

- processing method 선택
- action 선택
- output data type 변환
- follow-up / branch 설정

## 4.3 도착 노드

도착 노드는 **sink service node**다.

역할:

- 처리 결과를 외부 서비스로 전달하기
- 저장 / 전송 / 등록 / 알림 수행하기

### 도착 노드 목표 흐름

1. sink service 선택
2. 인증
3. 임시 end node 생성
4. 중간 처리 완료
5. 마지막 단계에서 sink 상세 설정

중요한 점:

- 도착은 처음에 service/auth만 고정한다
- end node는 service 선택 시점에 먼저 만든다
- 중간 처리 결과가 바뀌어도 end node 자체는 유지한다
- 단, 결과 schema가 바뀌어 기존 sink 상세 설정과 맞지 않으면 **상세 sink config만 invalid 처리**하고 마지막 단계에서 다시 설정하게 한다

### 도착 노드 마지막 단계

도착 마지막 단계는 단순 옵션 입력이 아니라
**현재 결과 schema를 sink schema에 매핑하는 단계**다.

즉 마지막 단계에서는:

- 현재 workflow 결과가 어떤 구조인지 보여준다
- sink 서비스가 어떤 필드를 요구하는지 보여준다
- 사용자가 결과 필드와 sink 필드를 연결한다
- 채널 / 포맷 / 액션 옵션을 최종 확정한다

---

## 5. 현재 backend 기준 현실 제약

현재 backend 실행 코드 기준으로는 아래 제약이 있다.

### 5.1 현재 가능한 것

- Spring은 느슨한 workflow graph 저장은 가능하다
- 미완료 node 저장은 가능하다
- `mapping_rules.json` 기반 middle choice는 부분 지원된다
- OAuth 연결 상태 조회는 가능하다
- FastAPI는 실행 후 결과 payload 저장은 한다

### 5.2 현재 없는 것

- source service catalog 없음
- source mode 목록 없음
- source target schema 없음
- source mode -> canonical type authoritative mapping 없음
- sink service catalog 없음
- sink accepted input type 계약 없음
- sink detailed config schema 없음
- result schema preview 없음
- FE가 직접 소비할 `mapping_rules.json` delivery API 없음

### 5.3 현재 구조적 충돌

- Spring 저장 모델과 FastAPI 실행 모델이 아직 다르다
- 서비스/OAuth taxonomy가 일관되지 않다
- trigger/source model이 아직 일반적인 source mode를 표현하지 못한다
- `mapping_rules.json`은 middle 전용 규칙이라 source/sink 전체를 설명하지 못한다

즉 현재 상태는:

- 일부 저장은 가능
- 일부 middle choice는 가능
- 하지만 source/sink editor 전체를 current backend contract 위에 바로 올릴 수 있는 상태는 아님

---

## 6. 백엔드 선행 계약

목표 상태를 실제 구현으로 옮기려면 backend가 아래 계약을 authoritative 하게 제공해야 한다.

## 6.1 source service catalog

최소 필요:

- source 서비스 목록
- 서비스 key / label
- auth 필요 여부
- source mode 목록
- source mode별 canonical input data type
- source mode별 target selection schema

예:

- file picker
- folder picker
- mail picker
- label picker
- channel picker

권장 public API:

- `GET /api/editor-catalog/sources`

권장 책임 계층:

- Spring: FE가 직접 소비하는 public contract 제공
- FastAPI 또는 공용 integration 계층: 실제 source capability와 runtime 지식 제공

권장 최소 응답 shape:

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

중요:

- `source mode`
- `canonicalInputType`
- `targetSchema`

이 세 가지는 FE 임의 규칙이 아니라, backend authority로 승격하는 것을 기본 원칙으로 한다.

## 6.2 sink service catalog

최소 필요:

- sink 서비스 목록
- 서비스 key / label
- auth 필요 여부
- 수용 가능한 input data type
- sink 상세 설정 schema

권장 public API:

- `GET /api/editor-catalog/sinks`

권장 책임 계층:

- Spring: FE가 직접 소비하는 public contract 제공
- FastAPI: 실제 sink execution 수행

권장 최소 응답 shape:

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

초기 범위 후보:

- `Slack`
- `Gmail`
- `Notion`
- `Google Drive`
- `Google Sheets`
- `Google Calendar`

## 6.3 result schema delivery

최소 필요:

- 현재 workflow 결과의 normalized schema
- canonical data type별 기본 필드 구조
- follow-up / branch / transform 이후 반영된 결과 필드
- sink 상세 설정 화면에서 바로 쓸 수 있는 schema

권장 단위:

- workflow 전체보다는
- `active node output` 또는
- `end node 직전 output`

이 더 자연스럽다.

권장 public API:

- `POST /api/workflows/schema-preview`

권장 이유:

- editor는 saved graph뿐 아니라 unsaved draft도 다룰 가능성이 높다
- query parameter 기반 조회보다 preview request body를 받는 쪽이 덜 경직된다

권장 책임 계층:

- Spring: public API gateway
- FastAPI 또는 공용 분석 계층: schema inference 책임

권장 최소 응답 shape:

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

## 6.4 mapping rules delivery

최소 필요:

- `mapping_rules.json`과 동등한 규칙 데이터
- 또는 FE와 backend가 같은 버전을 공유하는 계약

## 6.5 incomplete node lifecycle 계약

최소 필요:

- 저장 가능 범위
- choice 가능 조건
- execute 차단 조건
- `isConfigured` 같은 명시 상태

권장 원칙:

- 저장 가능과 실행 가능을 분리해서 다룬다
- 미완료 node는 저장 가능할 수 있다
- choice는 `outputDataType` 등 필요한 선행 조건이 충족될 때만 가능하다
- execute는 `isConfigured == true` 또는 동등한 상태일 때만 허용한다
- Spring은 preflight validation을 통해 실행 가능 여부를 먼저 판정한다

---

## 7. Spring/FastAPI 정렬 원칙

가장 먼저 필요한 것은 **Spring 저장 모델과 FastAPI 실행 모델 정렬**이다.

현재 충돌:

- Spring은 자유로운 `category/type/config/dataType/outputDataType/role` 저장 모델
- FastAPI는 제한된 실행 타입 중심 모델

이 문서 기준 권장안은 **옵션 B**다.

### 옵션 A. Spring도 실행 타입 중심으로 정렬

- Spring 저장 모델을 FastAPI 실행 타입에 맞춘다

### 옵션 B. translation layer 도입

- Spring의 service-centric 저장 모델을 유지
- 실행 직전에 FastAPI 실행 타입으로 변환하는 translation layer 추가

권장 이유:

- FE/editor 도메인을 service-centric하게 유지할 수 있다
- source/sink catalog, canonical type, sink schema 같은 FE 계약을 Spring에서 안정적으로 열 수 있다
- FastAPI는 runtime execution 모델에 집중할 수 있다

역할 분리:

- Spring: editor/public contract owner
- FastAPI: runtime/execution contract owner
- translation layer: Spring model -> FastAPI runtime model 변환

주의:

- translation layer
- preflight validation
- schema inference

는 backend 선행 구현 범위에 포함된다

이 정렬 없이 source/sink editor를 먼저 FE에 얹으면,
저장은 되지만 실행 계약이 불안정한 상태가 된다.

---

## 8. FE 전환 규칙

backend authoritative contract가 아직 없는 현재 단계에서는 아래를 **임시 제품 규칙**으로 사용한다.

## 8.1 시나리오 기반 초기 source service catalog

`docs/scenarios`를 기준으로 초기 source service catalog를 정리한다.

현재 시나리오에서 확인된 시작 서비스:

- `Google Drive`
- `Gmail`
- `Google Sheets`
- `Google Calendar`
- `유튜브`
- `네이버 뉴스`
- `쿠팡`
- `GitHub`
- `Slack`
- `Notion`

### 서비스별 source mode -> canonical type 매핑표

| source service | source mode | 의미 | canonical type |
| --- | --- | --- | --- |
| `Google Drive` | 특정 파일 사용 | 파일 1개 선택 | `SINGLE_FILE` |
| `Google Drive` | 특정 파일이 변경되었을 때 | 파일 1개 이벤트 | `SINGLE_FILE` |
| `Google Drive` | 새로운 파일이 들어올 때 | 새 파일 1개 이벤트 | `SINGLE_FILE` |
| `Google Drive` | 특정 폴더에 파일이 들어올 때 | 폴더에 새 파일 1개 이벤트 | `SINGLE_FILE` |
| `Google Drive` | 폴더 전체 파일 사용 | 폴더 전체 조회 | `FILE_LIST` |
| `Gmail` | 특정 메일 사용 | 메일 1건 선택 | `SINGLE_EMAIL` |
| `Gmail` | 새 메일이 도착했을 때 | 메일 1건 이벤트 | `SINGLE_EMAIL` |
| `Gmail` | 특정 보낸 사람의 메일이 올 때 | 메일 1건 이벤트 | `SINGLE_EMAIL` |
| `Gmail` | 별표(중요) 메일 사용 | 메일 1건 선택 또는 이벤트 | `SINGLE_EMAIL` |
| `Gmail` | 뉴스레터 라벨 메일들 사용 | 여러 메일 조회 | `EMAIL_LIST` |
| `Gmail` | 첨부파일이 있는 메일이 올 때 | 첨부파일 payload 사용 | `FILE_LIST` |
| `Google Sheets` | 시트 전체 사용 | 여러 행 조회 | `SPREADSHEET_DATA` |
| `Google Sheets` | 직접 실행 | 전체 데이터 조회 | `SPREADSHEET_DATA` |
| `Google Sheets` | 새로운 행이 추가될 때 | 단일 행 이벤트 | `SPREADSHEET_DATA` |
| `Google Sheets` | 데이터가 수정될 때 | 단일 행 또는 영역 변경 이벤트 | `SPREADSHEET_DATA` |
| `Google Calendar` | 매일 특정 시간에 일정 조회 | 오늘/일정 범위 조회 | `SCHEDULE_DATA` |
| `Google Calendar` | 매주 특정 요일에 일정 조회 | 주간 일정 범위 조회 | `SCHEDULE_DATA` |
| `유튜브` | 검색 결과 조회 | 영상 목록 조회 | `API_RESPONSE` |
| `유튜브` | 특정 채널의 새 영상 | 구조화된 영상 정보 이벤트 | `API_RESPONSE` |
| `유튜브` | 특정 영상 댓글 조회 | 댓글 목록 조회 | `API_RESPONSE` |
| `네이버 뉴스` | 키워드 검색 결과 조회 | 기사 목록 조회 | `API_RESPONSE` |
| `네이버 뉴스` | 정기 뉴스 수집 | 기사 목록 조회 | `API_RESPONSE` |
| `쿠팡` | 상품 URL 가격 조회 | 상품 구조화 데이터 | `API_RESPONSE` |
| `쿠팡` | 상품 리뷰 목록 조회 | 리뷰 구조화 데이터 | `API_RESPONSE` |
| `GitHub` | 새 PR / 리뷰 요청 | 코드 변경 정보 이벤트 | `API_RESPONSE` |
| `Slack` | 채널 대화 직접 가져오기 | 대화 로그 본문 | `TEXT` |
| `Notion` | 특정 페이지/노트 직접 사용 | 문서/본문 | `TEXT` |

## 8.2 시나리오 기반 초기 sink service catalog

현재 시나리오에서 확인된 도착 서비스:

- `Slack`
- `Gmail`
- `Notion`
- `Google Drive`
- `Google Sheets`
- `Google Calendar`

### sink service별 기본 역할

| sink service | 기본 역할 | 초기 단계에서 정하는 것 | 마지막 단계에서 정하는 것 |
| --- | --- | --- | --- |
| `Slack` | 메시지 전송 | 서비스 선택, 인증 | 채널, 메시지 포맷, 머릿말 |
| `Gmail` | 이메일 발송/초안 저장 | 서비스 선택, 인증 | 수신자, 제목, 본문 포맷 |
| `Notion` | 페이지/DB 항목 생성 | 서비스 선택, 인증 | 데이터베이스/페이지, 속성 매핑 |
| `Google Drive` | 파일 저장 | 서비스 선택, 인증 | 저장 폴더, 파일명 규칙 |
| `Google Sheets` | 행 추가/업데이트 | 서비스 선택, 인증 | 시트 선택, 열 매핑, 저장 방식 |
| `Google Calendar` | 일정 생성/등록 | 서비스 선택, 인증 | 캘린더 선택, 일정 필드 매핑, 등록 방식 |

## 8.3 FE 임시 규칙의 한계

이 규칙들은:

- backend authoritative contract가 아니다
- 현재 product 가설과 scenario를 정렬하기 위한 임시 기준이다
- backend contract가 생기면 대체될 수 있다

---

## 9. FE가 지금 당장 할 수 있는 것

backend 선행 계약이 생기기 전까지 FE가 당장 할 수 있는 것은 제한적이다.

### 가능한 것

- middle choice 흐름을 `mapping_rules.json` 기준으로 더 정리하기
- source/sink editor 목표 상태를 문서로 확정하기
- 임시 source/sink catalog를 제품 규칙으로 유지하기

### 보류해야 할 것

- backend authoritative source/sink editor를 실제 구현 명세로 고정하는 일
- result schema preview를 전제로 한 sink 마지막 단계 구현
- source mode/target schema를 동적 API로 소비하는 구현
- dynamic source/sink catalog UI 구현
- source mode -> canonical type 확정 UI를 FE 규칙으로 먼저 박는 일
- incomplete node execute 흐름 구현

---

## 10. 현재 구현과의 차이

현재 구현:

- 시작/도착 모두 `NODE_REGISTRY` 기반 node picker
- 서비스 목록도 FE 정적 map
- OAuth 필요 여부도 FE 정적 값
- source / processing / sink 역할이 한 화면에 섞여 있음

목표 상태:

- 시작 = source service picker
- 중간 = `mapping_rules.json` 기반 data-type processing
- 도착 = sink service picker
- 도착 상세 설정은 마지막 단계

즉 "무슨 노드를 놓을까?" 중심 UI에서
"어느 서비스에서 어떤 데이터를 가져와 어떻게 처리하고 어디로 보낼까?" 중심 구조로 바꾼다.

---

## 11. 백엔드 선행 후 FE 구현 순서

1. Spring/FastAPI 정렬 방식 `B` 확정
2. translation layer 및 preflight validation 설계
3. source catalog API
4. sink catalog API
5. result schema preview API
6. incomplete node lifecycle 계약 (`isConfigured` 포함)
7. 그 이후 시작/도착 source/sink editor 실제 구현

즉 FE 구현 우선순위는 backend 계약 선행 이후에 열린다.

---

## 12. 완료 조건 (문서 기준)

- 목표 상태와 현재 backend 제약이 분리되어 설명된다
- backend 선행 계약 목록이 명확히 정리된다
- FE 임시 전환 규칙과 최종 authoritative contract가 구분된다
- `mapping_rules.json`의 범위가 middle choice에 한정된다는 점이 명확하다
- Spring/FastAPI 모델 정렬이 1순위 선행 과제로 명시된다

---

## 13. 백엔드 구현 상태 (2026-04-20 업데이트)

### rollout gate 변경

아래 backend 선행 계약이 초기 구현 완료되었으므로, FE는 catalog API를 기반으로 source/sink editor 실제 구현을 시작할 수 있다.

| 선행 계약 | 상태 | API |
|-----------|------|-----|
| Spring/FastAPI 정렬 (옵션 B) | 완료 | `WorkflowTranslator` |
| source catalog | 완료 | `GET /api/editor-catalog/sources` |
| sink catalog | 완료 | `GET /api/editor-catalog/sinks` |
| sink schema | 완료 | `GET /api/editor-catalog/sinks/{key}/schema?inputType=...` |
| schema preview | 완료 | `GET /api/workflows/{id}/schema-preview` |
| lifecycle 계약 | 완료 | 저장 가능/choice 조건/실행 조건 분리 |

### FE 전환 규칙 업데이트

- 8.1절 매핑표 → `catalog/source_catalog.json`으로 authoritative contract 승격
- 8.2절 sink 목록 → `catalog/sink_catalog.json`으로 authoritative contract 승격
- FE 임시 규칙은 더 이상 불필요. catalog API 응답이 권위 있는 기준

---

## 14. 한 줄 요약

이 문서는 source/sink 기반 editor의 **목표 상태**를 정의하되, 현재 backend 실행 코드 기준으로는 아직 바로 구현 가능한 단계가 아니므로, **백엔드 선행 계약과 FE 전환 규칙을 함께 정리하는 문서**다.

**2026-04-20 기준 backend 선행 계약 초기 구현 완료. FE는 catalog API 기반으로 source/sink editor 구현 시작 가능.**
