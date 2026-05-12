# Workflow Google Sheets Node Spring Design

> 작성일: 2026-05-11
> 대상: Spring backend
> 용도: Google Sheets 노드와 picker 생성 흐름 설계
> 관련 저장소: `flowify-FE`, `flowify-BE`

---

## 1. 목적

이 문서는 Google Sheets 기능에서 Spring이 맡아야 하는 책임과 오케스트레이션 규칙을 정의한다.

이번 설계의 목표는 아래와 같다.

- Google Sheets를 시작, 중간, 끝 노드로 모두 지원한다.
- FE가 사용할 picker 목록과 생성 API를 제공한다.
- FastAPI 실행에 필요한 runtime payload를 정확히 만든다.
- `new_row`, `row_updated`를 위한 durable node state를 관리한다.

---

## 2. Spring 책임 범위

### 2.1 Spring이 담당하는 것

- source, action, sink catalog 제공
- Google Sheets picker 목록 조회
- 새 스프레드시트 생성
- 새 시트 생성
- workflow 저장 시 검증
- runtime translation
- durable node state 저장과 조회
- FastAPI callback에서 `nodeStateUpdates` commit

### 2.2 Spring이 담당하지 않는 것

- 실제 시트 diff 계산
- 실제 시트 검색과 lookup 실행
- 실제 Google Sheets 읽기/쓰기 로직

위 항목은 FastAPI가 담당한다.

---

## 3. 생성 흐름

### 3.1 스프레드시트 생성

사용자가 스프레드시트 목록에서 원하는 파일을 찾지 못하면 Spring 생성 API를 호출한다.

기본 정책:

- 새 스프레드시트는 사용자 Drive 루트에 만든다.
- 생성 성공 후 picker item을 즉시 반환한다.
- FE는 반환값으로 바로 해당 파일을 선택 상태로 만든다.
- 같은 이름의 다른 스프레드시트가 이미 있어도 이름 중복 생성은 허용한다.
- authoritative id는 항상 새로 생성된 `spreadsheetId`다.

### 3.2 시트 생성

사용자가 특정 스프레드시트 안에서 원하는 시트 탭을 찾지 못하면 Spring 생성 API를 호출한다.

기본 정책:

- 같은 이름의 시트가 이미 있으면 새로 만들지 않고 기존 시트를 반환한다.
- 없으면 새로 만들고 picker item을 즉시 반환한다.
- FE는 반환값으로 바로 해당 탭을 선택한다.

---

## 4. Picker 오케스트레이션

### 4.1 스프레드시트 목록

Spring은 사용자가 접근 가능한 스프레드시트 파일 목록을 제공한다.

각 item은 최소 아래 정보를 가져야 한다.

- `id = spreadsheetId`
- `label = spreadsheet title`
- `service = google_sheets`
- `metadata`
  - `spreadsheetId`

### 4.2 시트 탭 목록

Spring은 특정 스프레드시트 안의 시트 탭 목록을 제공한다.

각 item은 최소 아래 정보를 가져야 한다.

- UI 선택용 고유 `id`
- `label = sheet title`
- `service = google_sheets`
- `metadata`
  - `spreadsheetId`
  - `sheetName`

중요 규칙:

- 시트 탭 item의 `id`는 단순 `spreadsheetId`이면 안 된다.
- 같은 파일 안 여러 탭을 구분할 수 있어야 한다.

현재 규칙:

- `sheet option id = spreadsheetId::sheet::sheetName`

이 값은 UI 선택용이며, 실제 저장의 authoritative 값은 아니다.

---

## 5. 저장과 실행 간 authoritative 값

Spring은 FE에서 온 Google Sheets 선택값을 안정적인 저장 구조로 번역해야 한다.

### 5.1 시작 노드

저장 기준:

- `target = spreadsheet_id`
- `target_label = spreadsheet title`
- `sheet_name`
- `header_row`
- `data_start_row`
- `initial_sync_mode`
- 필요 시 `key_column`

### 5.2 중간 노드

중간 노드는 `runtime_action`에 필요한 구조화된 config를 저장한다.

### 5.3 끝 노드

저장 기준:

- `spreadsheet_id`
- `sheet_name`
- `write_mode`
- 필요 시 `range_a1`
- 필요 시 `key_column`

---

## 6. Runtime Translation

Spring은 workflow 저장 구조를 FastAPI가 이해할 수 있는 runtime payload로 번역해야 한다.

### 6.1 Start node translation

Google Sheets 시작 노드는 `RuntimeSource`로 번역한다.

필수 전달값:

- `service`
- `mode`
- `target = spreadsheet_id`
- `config.sheet_name`
- 필요 시 `range_a1`
- `header_row`
- `data_start_row`
- 필요 시 `initial_sync_mode`
- 필요 시 `key_column`
- 저장된 node state

### 6.2 Middle node translation

Google Sheets 중간 노드는 `runtime_action`으로 번역한다.

지원 액션:

- `read_range`
- `search_text`
- `lookup_row_by_key`

### 6.3 End node translation

Google Sheets 끝 노드는 sink config로 번역한다.

지원 저장 방식:

- `append_rows`
- `overwrite_range`
- `update_row_by_key`
- `upsert_row_by_key`

---

## 7. Node State 관리

### 7.1 저장 위치

Google Sheets 상태는 workflow와 node 기준으로 durable 하게 저장한다.

식별 키:

- `workflowId`
- `nodeId`

### 7.2 사용 목적

상태는 아래 두 시작 모드에 필요하다.

- `new_row`
- `row_updated`

### 7.3 Commit 규칙

- preview 실행에서는 상태를 commit하지 않는다.
- 실제 실행이 성공하고 callback이 정상 완료된 경우에만 상태를 commit한다.
- 실패 실행에서는 상태를 갱신하면 안 된다.

### 7.4 Mongo-safe map key 규칙

`row_updated`의 row snapshot은 이메일처럼 `.`가 포함된 key를 가질 수 있다.

Mongo map key 제약 때문에 Spring은 아래를 보장해야 한다.

- 저장 전 key escape
- 읽을 때 key 복원

---

## 8. 저장 시점 검증

Spring은 workflow 저장 시 Google Sheets 관련 설정을 검증해야 한다.

### 8.1 시작 노드 검증

- spreadsheet 선택 여부
- sheet 선택 여부
- `row_updated`일 때 `key_column` 존재 여부
- `header_row`, `data_start_row` 최소 형식

### 8.2 중간 노드 검증

- `read_range`에 필요한 범위 정보
- `search_text`의 검색값 소스 존재 여부
- `lookup_row_by_key`의 `key_column` 및 lookup 값 소스 존재 여부

### 8.3 끝 노드 검증

- spreadsheet 선택 여부
- sheet 선택 여부
- `overwrite_range`의 `range_a1`
- `update_row_by_key`, `upsert_row_by_key`의 `key_column`

중요한 UX 규칙:

- 잘못된 설정은 가능하면 저장 직후 `nodeStatuses`에 드러나야 한다.
- 사용자가 실행 버튼을 누른 뒤에야 처음 알게 되는 구조는 피해야 한다.

---

## 9. 사용자 경험 관점 정책

추가 규칙:

- `range_a1`가 `A1`, `A1:B10`처럼 시트 이름 없는 값이면 선택한 `sheet_name` 기준 범위로 해석된다는 점이 FE/BE 문서와 일관돼야 한다.

Spring이 직접 UI를 가지지는 않지만, API 설계는 사용자 경험을 크게 좌우한다.

이번 이슈에서 API가 보장해야 할 UX 기준:

- 목록에 원하는 스프레드시트가 없으면 바로 만들 수 있어야 한다.
- 파일을 선택한 뒤 원하는 시트가 없으면 바로 만들 수 있어야 한다.
- 생성된 대상은 즉시 picker item으로 돌아와야 한다.
- 같은 스프레드시트 안 여러 시트는 UI에서 구분 가능해야 한다.
- 저장 응답은 잘못된 검색 설정 같은 문제를 바로 보여줄 수 있어야 한다.

---

## 10. 실사용 시나리오 대응 범위

Spring 설계는 아래 시나리오를 염두에 둔다.

- Gmail 메일을 시트에 적재
- 정책표 lookup 후 다른 서비스로 전달
- 시트 전체를 읽어 요약/리포트 생성
- 특정 키워드를 검색해 다른 탭에 저장
- 새 행 감지 기반 자동화
- 수정 행 감지 기반 자동화
- 없는 스프레드시트나 시트를 바로 만들고 저장

---

## 11. 테스트 기대사항

아래가 충족되면 Spring 동작이 올바르다고 본다.

- 새 스프레드시트 생성 API가 정상 동작한다.
- 새 시트 생성 API가 정상 동작한다.
- 같은 이름의 시트가 있으면 재사용한다.
- 시트 탭 picker item id가 충돌하지 않는다.
- FE가 선택한 시트 탭이 다른 탭으로 잘못 복원되지 않는다.
- runtime translation이 올바른 `spreadsheet_id`와 `sheet_name`을 FastAPI로 전달한다.
- callback 이후 node state가 정상 commit된다.
- `row_updated` 상태가 Mongo-safe key 규칙을 지킨다.

---

## 12.1 공통 표 가공 경로

Google Sheets 저장 경험은 `Gmail -> Sheets` 한 가지 흐름만으로 설명되면 안 된다.
Spring은 시작 타입이 무엇이든 표형 payload가 Google Sheets 저장 흐름으로 자연스럽게 이어질 수 있도록 catalog, 저장 검증, runtime translation을 맞춰야 한다.

대표 표 가공 액션:

- `filter_fields_table`
- `filter_metadata_table`

Spring 관점 책임:

- FE가 `표로 정리해서 저장` 같은 공통 액션을 노출할 수 있도록 mapping 규칙과 설명 자료를 유지한다.
- 저장 시점 검증 결과를 `nodeStatuses`로 함께 반환해, 표 가공 설정 누락이 실행 전에 드러나게 한다.
- `SPREADSHEET_DATA`가 만들어진 뒤에는 시작 타입과 무관하게 동일한 Google Sheets sink 경로를 탈 수 있도록 runtime payload를 일관되게 유지한다.

대표 실사용 시나리오:

- Gmail 메일 로그를 표로 정리해 시트에 적재
- 파일 메타데이터를 자산 시트로 적재
- 기존 시트 데이터를 다시 골라 다른 시트 보고서 탭으로 저장

문서 일관성 원칙:

- FE에서 새로 노출하는 공통 표 가공 액션은 Spring 문서와 설명 자료에도 반영한다.
- Google Sheets는 메일·파일·기존 시트 데이터를 다루는 공통 표 자동화 서비스로 설명한다.

## 12.2 향후 보완점

Spring은 Google Sheets 중간 노드의 저장 검증과 runtime translation을 계속 지원한다.

대상 기능:

- `read_range`
- `search_text`
- `lookup_row_by_key`

다만 현재 FE 에디터의 `다음 단계 -> 중간 처리 추가` 흐름은 공통 `data-process` 노드만 생성하므로, 위 기능은 현재 사용자 경로에서는 직접 생성하거나 설정할 수 없다.

따라서 이번 이슈에서는 Spring 쪽 지원 코드는 유지하되, 실제 노출은 보류 상태로 두고 문서에만 향후 보완점으로 남긴다.

향후 FE 에디터가 중간 노드 타입 확장을 지원하게 되면, Spring의 현재 저장 검증과 runtime translation 경로를 그대로 연결해 노출 범위를 다시 열어야 한다.

## 12. 결정 요약

Spring은 Google Sheets의 조정자 역할을 맡는다.

Spring이 반드시 책임져야 하는 일:

- picker 목록 제공
- 스프레드시트 생성
- 시트 생성
- 저장 구조 안정화
- runtime payload 번역
- durable node state commit

Spring이 직접 계산하지 않는 일:

- 시트 diff 계산
- 검색과 lookup 실행
- 실제 Google Sheets API 읽기/쓰기 세부 동작

이 영역은 FastAPI가 담당한다.
