# Workflow Source Target Picker Backend Completion Report

> 작성일: 2026-05-02
> 대상: Frontend 연동
> 근거 문서: `WORKFLOW_SOURCE_TARGET_PICKER_BACKEND_REQUEST.md`

## 1. 완료 요약

프론트 요청 사항 중 Spring 백엔드 담당 범위를 구현 완료했다.

- Source target option 조회 API 추가
- Canvas LMS `course_picker`, `term_picker` 지원
- Google Drive `file_picker`, `folder_picker` 지원
- Canvas source catalog의 target schema 변경
- 실행 전 node IO preview 응답 확장
- 시작 노드 source summary 응답 포함
- `target_label`, `target_meta`는 기존 `NodeDefinition.config` 저장 구조로 지원

`§3.3 canonical payload 타입 계약`은 FastAPI 런타임 payload 책임이므로 이번 Spring 구현 범위에서는 제외했다.

## 2. Target Options API

### Endpoint

```http
GET /api/editor-catalog/sources/{serviceKey}/target-options
```

### Query Parameters

| 이름 | 필수 | 설명 |
|------|------|------|
| `mode` | O | source mode key |
| `parentId` | X | Google Drive folder 탐색 기준 parent id |
| `query` | X | 검색어 |
| `cursor` | X | Google Drive pagination cursor |

### 지원 서비스/모드

| serviceKey | mode | picker type | 반환 type |
|------------|------|-------------|-----------|
| `canvas_lms` | `course_files` | `course_picker` | `course` |
| `canvas_lms` | `course_new_file` | `course_picker` | `course` |
| `canvas_lms` | `term_all_files` | `term_picker` | `term` |
| `google_drive` | `single_file` | `file_picker` | `file` |
| `google_drive` | `file_changed` | `file_picker` | `file` |
| `google_drive` | `new_file` | `folder_picker` | `folder` |
| `google_drive` | `folder_new_file` | `folder_picker` | `folder` |
| `google_drive` | `folder_all_files` | `folder_picker` | `folder` |

### Response Shape

```json
{
  "items": [
    {
      "id": "resource-id",
      "label": "표시 이름",
      "description": "보조 설명",
      "type": "course",
      "metadata": {}
    }
  ],
  "nextCursor": null
}
```

Spring 공통 응답 wrapper 때문에 실제 응답은 `ApiResponse<TargetOptionResponse>` 형태다.

## 3. Canvas LMS Picker

### 호출 예시

```http
GET /api/editor-catalog/sources/canvas_lms/target-options?mode=course_files&query=bigdata
```

### Course Picker Response Example

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

### Term Picker Response Example

```json
{
  "items": [
    {
      "id": "2026-1학기",
      "label": "2026-1학기",
      "description": "4개 과목",
      "type": "term",
      "metadata": {
        "courseCount": 4
      }
    }
  ],
  "nextCursor": null
}
```

### Catalog 변경

`source_catalog.json`의 Canvas target schema를 다음처럼 변경했다.

- `course_files`: `text_input` -> `course_picker`
- `course_new_file`: `text_input` -> `course_picker`
- `term_all_files`: `text_input` -> `term_picker`

## 4. Google Drive Picker

### File Picker 호출 예시

```http
GET /api/editor-catalog/sources/google_drive/target-options?mode=single_file&query=report
```

### Folder Picker 호출 예시

```http
GET /api/editor-catalog/sources/google_drive/target-options?mode=folder_all_files&parentId=root&query=lecture
```

`parentId`를 생략하면 folder picker는 `root`를 기준으로 조회한다.

### Pagination

Google Drive API의 `nextPageToken`을 `nextCursor`로 반환한다. 다음 페이지 조회 시 `cursor={nextCursor}`를 전달하면 된다.

```http
GET /api/editor-catalog/sources/google_drive/target-options?mode=single_file&cursor=NEXT_PAGE_TOKEN
```

### File Response Example

```json
{
  "items": [
    {
      "id": "file_abc",
      "label": "report.pdf",
      "description": "application/pdf",
      "type": "file",
      "metadata": {
        "mimeType": "application/pdf",
        "modifiedTime": "2026-05-01T00:00:00Z",
        "size": "1024"
      }
    }
  ],
  "nextCursor": "next-page-token"
}
```

### Folder Response Example

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

## 5. 선택값 저장 계약

별도 백엔드 수정 없이 아래 형태를 기존 node `config`에 저장할 수 있다.

```json
{
  "target": "12345",
  "target_label": "빅데이터분석",
  "target_meta": {
    "term": "2026-1학기"
  }
}
```

실행 로직은 기존처럼 `target`을 사용한다. `target_label`, `target_meta`는 UI 표시용으로 저장된다.

## 6. Enhanced Node IO Preview

### Endpoint

기존 endpoint의 응답 타입을 확장했다.

```http
GET /api/workflows/{workflowId}/nodes/{nodeId}/schema-preview
```

### Response Shape

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
      "fields": [],
      "display_hints": {
        "preferred_view": "table"
      }
    }
  },
  "output": {
    "dataType": "TEXT",
    "label": "텍스트",
    "schema": {
      "schema_type": "TEXT",
      "is_list": false,
      "fields": [],
      "display_hints": {
        "preferred_view": "document"
      }
    }
  },
  "nodeStatus": {
    "configured": true,
    "executable": true,
    "missingFields": null
  }
}
```

### 시작 노드 Source Summary

시작 노드(`role=start`)일 때는 `source`가 추가된다.

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
  },
  "nodeStatus": {
    "configured": true,
    "executable": true,
    "missingFields": null
  }
}
```

`source.targetLabel`은 `config.target_label`이 있으면 그 값을 사용하고, 없으면 `config.target`을 fallback으로 사용한다.

## 7. 인증/보안 메모

- Canvas picker는 서버 설정의 `app.oauth.canvas-lms.token`을 사용한다.
- Google Drive picker는 로그인 사용자 기준 `OAuthTokenService.getDecryptedToken(userId, "google_drive")`로 토큰을 조회한다.
- picker 응답에는 파일 내용과 토큰을 포함하지 않는다.
- Google Drive metadata는 `mimeType`, `modifiedTime`, `size` 정도로 제한했다.

## 8. FE 연동 체크리스트

- `source_catalog.json`에서 Canvas target schema가 picker 타입으로 내려오는지 확인
- picker 타입별로 `target-options` API 호출 연결
- 선택 시 `config.target = item.id`
- 선택 시 `config.target_label = item.label`
- 선택 시 `config.target_meta = item.metadata`
- Drive pagination은 `nextCursor`가 있을 때 `cursor` query로 다음 페이지 요청
- node panel에서 기존 schema preview 응답 대신 확장 응답의 `input`, `output`, `source`, `nodeStatus` 사용

## 9. 검증 결과

```bash
./gradlew compileJava
./gradlew test
```

결과: 모두 통과.

## 10. 변경 파일 요약

주요 신규 파일:

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

주요 수정 파일:

- `catalog/controller/CatalogController.java`
- `catalog/service/SchemaPreviewService.java`
- `config/WebClientConfig.java`
- `execution/service/FastApiClient.java`
- `workflow/controller/WorkflowController.java`
- `catalog/source_catalog.json`

## Follow-up Fixes

- `term_all_files` term picker now includes both `active` and `completed` Canvas courses so past semesters appear in the term list.
- `course_new_file` course picker now starts from `active` Canvas courses and also filters out courses whose `course.end_at` or `term.end_at` is already in the past, because Canvas `active` alone can still include older semesters.
