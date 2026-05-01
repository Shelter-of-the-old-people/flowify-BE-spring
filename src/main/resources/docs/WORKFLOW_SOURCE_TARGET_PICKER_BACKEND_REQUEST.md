# Workflow Source Target Picker and Node IO Preview Backend Request

## 1. 요청 배경

현재 프론트는 노드 클릭 시 다음 API를 사용해 입출력 데이터 패널을 표시한다.

- `GET /api/workflows/{workflowId}/executions/latest/nodes/{nodeId}/data`
- `GET /api/workflows/{workflowId}/nodes/{nodeId}/schema-preview`

이 구조는 실행 이후의 `inputData`, `outputData`를 확인하기에는 동작하지만, 사용자가 워크플로우를 실행하기 전에는 실제 데이터 흐름을 충분히 이해하기 어렵다.

또한 시작 노드 생성 시 source target 입력 UX가 아직 낮다.

- Canvas LMS는 과목 ID나 학기명을 직접 입력해야 한다.
- Google Drive는 파일 ID나 폴더 ID를 직접 입력해야 한다.
- source catalog에는 `file_picker`, `folder_picker` 같은 target schema가 있지만, 실제 선택지 조회 API가 없어 프론트는 텍스트 입력으로 처리할 수밖에 없다.

따라서 아래 백엔드 기능 보강을 요청한다.

## 2. 현재 코드 기준 확인 사항

### 2.1 Spring

현재 Spring에는 다음 기반이 있다.

- `source_catalog.json`
  - Google Drive source mode는 `file_picker`, `folder_picker` target schema를 이미 가진다.
  - Canvas LMS source mode는 현재 `text_input` target schema를 가진다.
- `CatalogController`
  - `/api/editor-catalog/sources`
  - `/api/editor-catalog/sinks`
  - `/api/editor-catalog/mapping-rules`
- `WorkflowController`
  - `/api/workflows/{id}/nodes/{nodeId}/schema-preview`
- `ExecutionController`
  - `/api/workflows/{id}/executions/latest/nodes/{nodeId}/data`
- `OAuthTokenService`
  - `getDecryptedToken(userId, service)`로 사용자별 외부 서비스 토큰을 조회할 수 있다.

### 2.2 FastAPI

현재 FastAPI에는 다음 재사용 가능한 로직이 있다.

- `CanvasLmsService`
  - `get_active_courses(token)`
  - `get_course_files(token, course_id)`
  - `get_course_latest_file(token, course_id)`
- `GoogleDriveService`
  - `list_files(token, folder_id, max_results)`
  - `download_file(token, file_id)`
- `InputNodeStrategy`
  - Canvas LMS, Google Drive source mode를 실행 시점에 canonical payload로 변환한다.
- `WorkflowExecutor`
  - 노드별 `inputData`, `outputData`를 `nodeLogs`에 저장한다.

즉, 외부 서비스 데이터를 가져오는 내부 로직은 일부 존재하지만, 프론트가 source target 선택용으로 호출할 수 있는 API는 아직 없다.

## 3. 요구사항

## 3.1 실행 전 노드 입출력 예상 데이터 제공

사용자가 워크플로우를 실행하지 않아도 노드 연결과 설정만으로 현재 노드의 입력/출력 데이터 흐름을 이해할 수 있어야 한다.

현재 `schema-preview`는 노드의 `dataType`, `outputDataType` 기반 input/output schema를 반환한다. 이 응답을 확장하거나 신규 API를 추가해 실행 전 데이터 흐름 정보를 함께 내려주기를 요청한다.

### 요청 정보

입력 데이터:

- 이전 노드 ID
- 이전 노드 label
- 이전 노드 `outputDataType`
- 현재 노드가 받을 input schema

출력 데이터:

- 현재 노드 `outputDataType`
- 현재 노드 output schema

상태:

- configured
- executable
- missingFields

### 희망 응답 예시

```json
{
  "nodeId": "node_123",
  "input": {
    "dataType": "FILE_LIST",
    "label": "파일 목록",
    "sourceNodeId": "node_start",
    "sourceNodeLabel": "Canvas LMS",
    "schema": {
      "schema_type": "FILE_LIST",
      "is_list": true,
      "fields": []
    }
  },
  "output": {
    "dataType": "TEXT",
    "label": "텍스트",
    "schema": {
      "schema_type": "TEXT",
      "is_list": false,
      "fields": []
    }
  },
  "nodeStatus": {
    "configured": true,
    "executable": true,
    "missingFields": []
  }
}
```

### 기대 효과

- 실행 전에도 input panel과 output panel에서 예상 데이터 흐름을 보여줄 수 있다.
- 프론트가 그래프/타입 관계를 과하게 추론하지 않아도 된다.
- 실행 결과가 없다는 안내와 예상 데이터 구조를 분리해 표시할 수 있다.

## 3.2 시작 노드 source 설정 정보 표시 지원

시작 노드를 클릭했을 때 "시작점" 같은 추상 문구 대신 실제 source 설정을 보여주고 싶다.

현재 시작 노드 config 예시는 다음과 같다.

```json
{
  "service": "canvas_lms",
  "source_mode": "course_files",
  "target": "12345",
  "canonical_input_type": "FILE_LIST",
  "trigger_kind": "manual"
}
```

### 요청 정보

시작 노드 preview 응답에 다음 source summary를 포함해주기를 요청한다.

- service key
- service label
- source mode key
- source mode label
- target value
- target label
- canonical input type
- trigger kind

### 희망 응답 예시

```json
{
  "nodeId": "node_start",
  "source": {
    "service": "canvas_lms",
    "serviceLabel": "Canvas LMS",
    "mode": "course_files",
    "modeLabel": "특정 과목 강의자료 전체",
    "target": "12345",
    "targetLabel": "빅데이터분석",
    "canonicalInputType": "FILE_LIST",
    "triggerKind": "manual"
  },
  "output": {
    "dataType": "FILE_LIST",
    "label": "파일 목록",
    "schema": {
      "schema_type": "FILE_LIST",
      "is_list": true,
      "fields": []
    }
  }
}
```

### 기대 효과

- 시작 노드가 실제로 어떤 서비스에서 어떤 데이터를 가져오는지 사용자가 이해할 수 있다.
- ID만 표시되는 문제를 줄일 수 있다.
- 이후 source preview 기능과 자연스럽게 연결할 수 있다.

## 3.3 canonical payload 타입 계약 명시

프론트는 실행 후 `inputData`, `outputData`를 JSON 그대로 보여주지 않고 타입별 UI로 표시하려고 한다.

FastAPI canonical payload는 이미 다음 타입 구조를 사용한다.

- `TEXT`
- `SINGLE_FILE`
- `FILE_LIST`
- `SPREADSHEET_DATA`
- `SINGLE_EMAIL`
- `EMAIL_LIST`
- `SCHEDULE_DATA`
- `API_RESPONSE`

### 요청 사항

- `inputData`, `outputData`에는 가능하면 항상 `type` 필드를 포함한다.
- 타입별 필드가 비어도 기본 구조를 유지한다.
- 알 수 없는 타입이나 예외 상황에서도 최소한 `type`과 원본 데이터를 포함한다.

### 대표 payload 예시

```json
{
  "type": "TEXT",
  "content": "요약 결과"
}
```

```json
{
  "type": "SINGLE_FILE",
  "filename": "report.pdf",
  "content": null,
  "mime_type": "application/pdf",
  "url": "https://example.com/report.pdf"
}
```

```json
{
  "type": "FILE_LIST",
  "items": [
    {
      "filename": "week01.pdf",
      "mime_type": "application/pdf",
      "size": 1024,
      "url": "https://example.com/week01.pdf"
    }
  ]
}
```

```json
{
  "type": "SPREADSHEET_DATA",
  "headers": ["name", "score"],
  "rows": [["Alice", 95]]
}
```

### 기대 효과

- 프론트가 `type` 기준으로 파일 카드, 파일 목록, 텍스트 문서, 표, 메일 카드 등을 렌더링할 수 있다.
- 알 수 없는 구조만 JSON fallback으로 처리할 수 있다.

## 3.4 source target 선택지 조회 API 추가

시작 노드 생성 시 target을 직접 입력하지 않고 외부 서비스 리소스 목록에서 선택할 수 있어야 한다.

### 요청 API

```http
GET /api/editor-catalog/sources/{serviceKey}/target-options?mode={sourceMode}
```

선택 파라미터:

```http
GET /api/editor-catalog/sources/google_drive/target-options?mode=folder_all_files&parentId=root&query=report
GET /api/editor-catalog/sources/canvas_lms/target-options?mode=course_files&query=bigdata
```

### 공통 응답 형식

```json
{
  "items": [
    {
      "id": "12345",
      "label": "빅데이터분석",
      "description": "2026-1학기",
      "type": "course",
      "metadata": {
        "term": "2026-1학기"
      }
    }
  ],
  "nextCursor": null
}
```

### 필드 정의

| 필드 | 설명 |
| --- | --- |
| `id` | 실제 `config.target`에 저장할 값 |
| `label` | 사용자에게 표시할 이름 |
| `description` | 보조 설명 |
| `type` | `course`, `term`, `file`, `folder` 등 |
| `metadata` | 서비스별 추가 정보 |
| `nextCursor` | 페이지네이션 필요 시 사용 |

## 3.5 Canvas LMS picker 지원

Canvas LMS는 현재 과목 ID와 학기명을 사용자가 직접 입력한다.

### 요청 기능

`course_files`, `course_new_file`:

- 사용자의 활성 과목 목록 반환
- `id`: course id
- `label`: course name
- `description`: term name

`term_all_files`:

- 사용자의 활성 과목에서 학기 목록 추출
- `id`: term name
- `label`: term name
- `description`: 해당 학기 과목 수 등

### source catalog 변경 요청

현재:

```json
{
  "key": "course_files",
  "target_schema": {
    "type": "text_input",
    "placeholder": "과목 ID (예: 12345)"
  }
}
```

변경 요청:

```json
{
  "key": "course_files",
  "target_schema": {
    "type": "course_picker",
    "multiple": false
  }
}
```

```json
{
  "key": "course_new_file",
  "target_schema": {
    "type": "course_picker",
    "multiple": false
  }
}
```

```json
{
  "key": "term_all_files",
  "target_schema": {
    "type": "term_picker",
    "multiple": false
  }
}
```

## 3.6 Google Drive file/folder picker 지원

Google Drive는 source catalog에 이미 `file_picker`, `folder_picker`가 정의되어 있다.

하지만 선택지 API가 없으므로 프론트는 현재 텍스트 입력으로 처리한다.

### 요청 기능

`file_picker`:

- 사용자가 접근 가능한 파일 목록 반환
- 검색 지원
- 가능하면 pagination 또는 cursor 지원

`folder_picker`:

- 사용자가 접근 가능한 폴더 목록 반환
- `parentId` 기반 폴더 탐색 지원
- root 조회 지원
- 검색 지원

### Drive folder query 참고

Google Drive 폴더만 조회할 때는 다음 mime type 필터가 필요하다.

```text
mimeType = 'application/vnd.google-apps.folder'
```

### 응답 예시

```json
{
  "items": [
    {
      "id": "folder_abc",
      "label": "강의자료",
      "description": "Google Drive folder",
      "type": "folder",
      "metadata": {
        "mimeType": "application/vnd.google-apps.folder",
        "modifiedTime": "2026-05-01T00:00:00Z"
      }
    }
  ],
  "nextCursor": null
}
```

## 3.7 선택값 저장 보조 정보

현재 runtime은 `config.target` 값을 사용한다. 이 값은 유지해도 된다.

다만 프론트 표시를 위해 target label도 저장할 수 있으면 좋다.

### 요청 저장 형태

```json
{
  "target": "12345",
  "target_label": "빅데이터분석",
  "target_meta": {
    "term": "2026-1학기"
  }
}
```

### 주의 사항

- 실행 로직은 기존처럼 `target`만 사용해도 된다.
- `target_label`, `target_meta`는 UI 표시용이다.
- 저장이 어렵다면 프론트가 target option API로 재조회하는 방식도 가능하다.

## 4. 권장 구현 방향

### 4.1 Spring proxy 방식

권장 구조:

1. 프론트가 Spring target option API 호출
2. Spring이 사용자 인증 확인
3. Spring이 `OAuthTokenService.getDecryptedToken(userId, serviceKey)`로 토큰 조회
4. Spring이 FastAPI target option API 호출 또는 Spring 내부 외부 API client 호출
5. Spring이 공통 `TargetOptionResponse` 형태로 반환

이 방식의 장점:

- 프론트는 Spring API만 호출하면 된다.
- OAuth 토큰 소유권 검증 위치가 Spring으로 유지된다.
- FastAPI의 기존 integration service를 재사용할 수 있다.

### 4.2 Spring 직접 호출 방식

Spring에서 Google Drive, Canvas API를 직접 호출해도 된다.

장점:

- 추가 FastAPI API가 필요 없다.
- picker API 응답 생성 책임이 Spring에 집중된다.

단점:

- FastAPI에 이미 존재하는 Canvas/Drive 호출 로직과 중복될 수 있다.

## 5. 프론트 연동 계획

백엔드 기능이 제공되면 프론트는 다음을 구현할 예정이다.

- `SourceTargetForm`을 picker 타입별 UI로 확장
- `course_picker`, `term_picker`, `file_picker`, `folder_picker` 지원
- target option query hook 추가
- 검색, 로딩, 빈 상태, 오류, 재시도 UI 추가
- 선택 시 `target`에는 `id`, `target_label`에는 `label`, `target_meta`에는 `metadata` 저장
- 노드 패널에서 실행 전 예상 input/output 표시
- 실행 후 canonical payload `type` 기준 타입별 데이터 렌더링

## 6. 우선순위

1. source target option API 추가
2. Canvas LMS `course_picker`, `term_picker` catalog 변경
3. Google Drive `file_picker`, `folder_picker` option API 연결
4. 선택 target label/meta 저장 또는 재조회 방식 확정
5. 실행 전 node IO preview 응답 보강
6. canonical payload 타입 계약 명시

## 7. 백엔드 확인 요청

다음 사항 확인을 요청한다.

1. Spring에서 OAuth 토큰을 확인하고 FastAPI로 picker 요청을 위임하는 구조가 가능한지
2. Spring에서 직접 외부 API를 호출하는 방향이 더 적합한지
3. `target_label`, `target_meta`를 `NodeDefinition.config`에 저장해도 되는지
4. Canvas `term_all_files`의 `target`은 현재처럼 term name으로 유지해도 되는지
5. Canvas API에서 term id를 안정적으로 사용할 수 있는지
6. Drive folder picker에서 root 탐색과 pagination을 어떤 방식으로 지원할지
7. picker API 응답에 민감 데이터가 섞이지 않도록 어떤 필드를 제한할지

## 8. Backend Implementation Result

> 업데이트 일시: 2026-05-02

### 8.1 적용 범위

- Phase 1: Target Options API 기반 구조 구현
- Phase 2: Canvas LMS `course_picker`, `term_picker` 구현
- Phase 3: Google Drive `file_picker`, `folder_picker` 구현
- Phase 4: `target_label`, `target_meta` 저장은 기존 `NodeDefinition.config`로 지원되므로 코드 변경 없음
- Phase 5: 실행 전 Node IO Preview 응답 보강
- Phase 6: 시작 노드 Source Summary 응답 통합

### 8.2 추가/변경 API

#### Source target option 조회

```http
GET /api/editor-catalog/sources/{serviceKey}/target-options?mode={sourceMode}&parentId={parentId}&query={query}&cursor={cursor}
```

- `serviceKey`: `canvas_lms`, `google_drive`
- `mode`: source mode key
- `parentId`: Drive folder 탐색 시 사용. folder picker는 미지정 시 `root` 기준
- `query`: label/name 검색 필터
- `cursor`: Google Drive `nextPageToken` 기반 pagination

응답:

```json
{
  "items": [
    {
      "id": "resource-id",
      "label": "표시 이름",
      "description": "보조 설명",
      "type": "course|term|file|folder",
      "metadata": {}
    }
  ],
  "nextCursor": null
}
```

#### Node schema preview

기존 endpoint를 확장 응답으로 교체했다.

```http
GET /api/workflows/{workflowId}/nodes/{nodeId}/schema-preview
```

응답은 다음 정보를 포함한다.

- `input`: 이전 노드 id/label, 입력 data type, input schema
- `output`: 현재 노드 output data type, output schema
- `source`: 시작 노드일 때 source service/mode/target summary
- `nodeStatus`: configured, executable, missingFields

### 8.3 구현 파일

신규:

- `catalog/dto/picker/TargetOptionItem.java`
- `catalog/dto/picker/TargetOptionResponse.java`
- `catalog/service/picker/TargetOptionProvider.java`
- `catalog/service/picker/TargetOptionService.java`
- `catalog/service/picker/CanvasLmsTargetOptionProvider.java`
- `catalog/service/picker/GoogleDriveTargetOptionProvider.java`
- `catalog/dto/EnhancedNodePreviewResponse.java`
- `catalog/dto/NodeInputPreview.java`
- `catalog/dto/NodeOutputPreview.java`
- `catalog/dto/NodeStatusSummary.java`
- `catalog/dto/SourceConfigSummary.java`

수정:

- `catalog/controller/CatalogController.java`
- `config/WebClientConfig.java`
- `execution/service/FastApiClient.java`
- `workflow/controller/WorkflowController.java`
- `catalog/service/SchemaPreviewService.java`
- `catalog/source_catalog.json`

### 8.4 백엔드 확인 요청 응답

| # | 질문 | 응답 |
|---|------|------|
| 1 | Spring → FastAPI proxy 가능? | 가능하지만 이번 구현은 Spring 직접 호출 방식으로 처리했다. |
| 2 | Spring 직접 외부 API 호출? | 채택했다. Canvas/Drive picker 모두 Spring `WebClient`로 직접 호출한다. |
| 3 | `target_label`, `target_meta` 저장? | 가능하다. `NodeDefinition.config`가 `Map<String,Object>`라 별도 코드 변경 없이 저장된다. |
| 4 | Canvas `term_all_files` target은 term name 유지? | 유지했다. term picker의 `id`와 `label` 모두 term name이다. |
| 5 | Canvas API term id 안정성? | 구현은 term id에 의존하지 않고 term name 기준으로 그룹핑한다. |
| 6 | Drive root/pagination? | folder picker는 `parentId` 미지정 시 `root`, pagination은 `cursor`/`nextCursor`로 지원한다. |
| 7 | 민감 데이터 제한? | 파일 내용/토큰은 반환하지 않는다. Drive는 id/name/mimeType/modifiedTime/size, Canvas는 course/term 식별 정보만 반환한다. |

### 8.5 검증 결과

```bash
./gradlew compileJava
./gradlew test
```

결과: 모두 통과.
