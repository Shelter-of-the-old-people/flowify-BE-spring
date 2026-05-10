# Workflow Google Sheets Node Spring Design

> **작성일** 2026-05-11
> **대상** Spring backend
> **용도** Google Sheets 노드와 picker 생성 흐름 설계
> **관련 저장소** `flowify-FE`, `flowify-BE`

---

## 1. 목적

이 문서는 Spring이 Google Sheets 기능에서 맡아야 하는 책임을 정의한다.

핵심 목표는 아래와 같다.

- Google Sheets를 시작, 중간, 끝 노드로 모두 지원한다.
- FE가 쓰는 picker 목록과 생성 API를 제공한다.
- FastAPI 실행에 필요한 runtime payload를 정확히 만든다.
- `new_row`, `row_updated`를 위한 durable node state를 관리한다.

---

## 2. Spring 책임 범위

### 2.1 Spring이 하는 일

- source/sink/action catalog 제공
- Google Sheets picker 목록 조회
- 새 스프레드시트 생성
- 새 시트 생성
- workflow validation
- runtime translation
- durable node state 저장/조회
- FastAPI callback에서 `nodeStateUpdates` commit

### 2.2 Spring이 하지 않는 일

- 실제 시트 diff 계산
- 실제 시트 검색/lookup/쓰기 실행

이 부분은 FastAPI가 맡는다.

---

## 3. 사용자 기준 생성 흐름

### 3.1 스프레드시트 생성

사용자가 스프레드시트 목록 단계에서 원하는 파일을 찾지 못하면 Spring 생성 API를 호출한다.

기본 정책:

- 새 스프레드시트는 내 드라이브 루트에 만든다.
- 생성 성공 시 picker item을 즉시 반환한다.
- FE는 반환값으로 바로 해당 스프레드시트 경로에 진입한다.

### 3.2 시트 생성

사용자가 특정 스프레드시트 안에서 원하는 탭을 찾지 못하면 Spring 생성 API를 호출한다.

기본 정책:

- 새 시트는 현재 선택된 스프레드시트 안에 만든다.
- 생성 성공 시 sheet picker item을 즉시 반환한다.
- FE는 반환값으로 즉시 선택 상태를 갱신한다.

### 3.3 생성 책임

이번 범위에서 생성은 `설정 단계 생성`만 포함한다.

- picker에서 명시적으로 생성
- 저장 전에 대상 확정
- 런타임 자동 생성은 제외

---

## 4. Picker 설계

### 4.1 기본 조회 흐름

Google Sheets picker는 아래 2단계 흐름을 사용한다.

1. spreadsheet 목록 조회
2. spreadsheet 선택 후 sheet tab 목록 조회

### 4.2 source와 sink 공통 정책

- source picker와 sink picker는 같은 생성 규칙을 사용한다.
- source mode, sink type과 무관하게 Google Sheets 생성 엔드포인트는 공통으로 제공한다.
- FE는 source, middle, sink 화면에서 동일한 생성 경험을 사용한다.

### 4.3 picker item 규칙

spreadsheet item:

- `id = spreadsheet_id`
- `type = spreadsheet`
- `label = spreadsheet title`

sheet item:

- `id = spreadsheet_id`
- `type = sheet`
- `metadata.sheetName = 실제 시트 탭 이름`
- `metadata.sheetId = 실제 시트 탭 id`

---

## 5. API 설계

### 5.1 목록 조회

기존 API를 그대로 사용한다.

- `GET /api/editor-catalog/sources/{serviceKey}/target-options`
- `GET /api/editor-catalog/sinks/{serviceKey}/target-options`

Google Sheets에서는:

- `parentId` 없음: spreadsheet 목록
- `parentId=spreadsheet_id`: sheet tab 목록

### 5.2 생성 API

이번 범위에서 아래 API를 추가한다.

- `POST /api/editor-catalog/google-sheets/spreadsheets`
- `POST /api/editor-catalog/google-sheets/sheets`

요청 예시:

```json
{ "name": "Gmail Reports" }
```

```json
{ "spreadsheetId": "spreadsheet_123", "sheetName": "Summary" }
```

응답은 picker item 형식의 `TargetOptionItem`을 그대로 사용한다.

---

## 6. Provider 설계

### 6.1 spreadsheet 목록

- Google Drive files API로 spreadsheet 파일 목록 조회

### 6.2 sheet 목록

- Google Sheets API로 해당 spreadsheet의 sheet tab 목록 조회

### 6.3 spreadsheet 생성

권장 방식:

- Google Sheets API `spreadsheets.create`
- 제목은 요청 `name`
- 응답에서 `spreadsheetId`, `properties.title`, 기본 sheet 정보를 읽어 picker item 생성

### 6.4 sheet 생성

권장 방식:

- Google Sheets API `batchUpdate` + `addSheet`
- 요청 `spreadsheetId`, `sheetName`
- 생성 성공 시 해당 sheet를 picker item으로 반환

중복 정책:

- 동일한 sheet name이 이미 있으면 중복 생성 대신 기존 시트를 반환하는 방향을 우선 고려한다.

---

## 7. Runtime translation

### 7.1 시작 노드

Google Sheets 시작 노드는 `runtime_source.config/state`를 포함한다.

필수 정보:

- `spreadsheet_id`
- `sheet_name`
- `range_a1`
- `header_row`
- `data_start_row`
- `key_column`
- `initial_sync_mode`
- `state`

### 7.2 중간 노드

Google Sheets 중간 노드는 `integration` runtime type과 `runtime_action`을 사용한다.

지원 액션:

- `read_range`
- `search_text`
- `lookup_row_by_key`

### 7.3 끝 노드

Google Sheets 끝 노드는 `runtime_sink.config`에 아래를 담는다.

- `spreadsheet_id`
- `sheet_name`
- `range_a1`
- `write_mode`
- `key_column`

---

## 8. Validation

### 8.1 시작 노드

- `sheet_name` 필수
- `row_updated`는 `key_column` 필수

### 8.2 중간 노드

- `action` 필수
- `spreadsheet_id` 필수
- `sheet_name` 필수
- `lookup_row_by_key`는 `key_column` 필수

### 8.3 끝 노드

- `sheet_name` 필수
- `update_row_by_key`, `upsert_row_by_key`는 `key_column` 필수

### 8.4 생성 흐름

생성 기능은 validation과 별개지만, 생성 직후 picker item을 기존 선택 흐름과 같은 방식으로 저장할 수 있어야 한다.

---

## 9. Durable node state

### 9.1 저장소

`workflow_node_states`

### 9.2 키

- `workflowId`
- `nodeId`

### 9.3 값

- `service`
- `state`
- `updatedAt`

Google Sheets state 예시:

```json
{
  "spreadsheet_id": "sheet_123",
  "sheet_name": "Responses",
  "last_seen_row_index": 205,
  "row_snapshot": {
    "submission_1": "hash-a"
  }
}
```

### 9.4 commit 규칙

- preview는 commit하지 않는다.
- 실패 실행은 commit하지 않는다.
- workflow 전체 성공 시에만 commit한다.

---

## 10. 검증 계획

- spreadsheet 목록 조회
- sheet 목록 조회
- spreadsheet 생성 API
- sheet 생성 API
- source validation
- middle validation
- sink validation
- runtime translation
- callback `nodeStateUpdates` commit
- `row_updated` dotted key state 저장 회귀

---

## 11. V1 범위

이번 V1에 포함:

- Google Sheets picker 2단계 조회
- 새 스프레드시트 만들기
- 새 시트 만들기
- 시작 노드 `sheet_all`, `new_row`, `row_updated`
- 중간 노드 `read_range`, `search_text`, `lookup_row_by_key`
- 끝 노드 `append_rows`, `overwrite_range`, `update_row_by_key`, `upsert_row_by_key`
- durable node state
- callback `nodeStateUpdates`

이번 V1에서 제외:

- row deletion 감지
- regex / fuzzy search
- 런타임 자동 생성
- 다중 시트 batch orchestration

---

## 12. 구현 대상 파일

### 12.1 catalog / picker

- `src/main/java/org/github/flowify/catalog/controller/CatalogController.java`
- `src/main/java/org/github/flowify/catalog/service/picker/TargetOptionService.java`
- `src/main/java/org/github/flowify/catalog/service/picker/GoogleSheetsTargetOptionProvider.java`
- 생성 요청 DTO

### 12.2 workflow / translation

- `WorkflowTranslator`
- `NodeLifecycleService`
- 관련 테스트

### 12.3 state / execution

- `ExecutionCompleteRequest`
- `ExecutionService`
- `WorkflowNodeState` 계층

---

## 13. 결정 요약

- Spring은 Google Sheets의 설정 단계 owner다.
- picker 조회와 생성은 Spring이 제공한다.
- FastAPI는 생성이 아니라 실행만 맡는다.
- 사용자가 `없으면 새 파일 만들기`, `없으면 새 시트 만들기`를 자연스럽게 할 수 있도록 생성 API를 V1 범위에 포함한다.
