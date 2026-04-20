# Source/Sink Catalog 구현 계획

## Context

### 프론트엔드가 source/sink 기반 워크플로우 에디터를 구축하기 위해 백엔드에 4가지 선행 과제를 요청함:
1. Spring/FastAPI 아키텍처 정렬 (옵션 B: translation layer)
2. Source catalog API
3. Sink catalog API
4. Schema preview + incomplete node lifecycle 계약

   현재 mapping_rules.json은 중간 노드 choice만 다루고 있고, source/sink에 대한 authoritative
    contract가 없는 상태.

   아키텍처 결정

   - Spring = editor/public contract owner (catalog, schema, validation)
   - FastAPI = runtime/execution owner
   - Translation layer = 실행 직전에 Spring editor 모델 → FastAPI runtime 모델 변환
   - 카탈로그 = JSON 파일로 정적 관리 (mapping_rules.json과 동일 패턴)

   구현 단계

   Phase 1: JSON 카탈로그 + DTO (새 파일 9개)

   JSON 데이터 파일 (3개)

   src/main/resources/catalog/source_catalog.json
   - 10개 source 서비스 (Google Drive, Gmail, Sheets, Calendar, YouTube, 네이버뉴스, 쿠팡,
   GitHub, Slack, Notion)
   - 서비스별 source mode → canonical type 매핑 (FLOW_DESIGN.md 8.1절 매핑표 기반)
   - 구조: { services: [{ key, label, authRequired, sourceModes: [{ key, label,
   canonicalInputType, triggerKind, targetSchema }] }] }

   src/main/resources/catalog/sink_catalog.json
   - 6개 sink 서비스 (Slack, Gmail, Notion, Google Drive, Sheets, Calendar)
   - 서비스별 acceptedInputTypes + configSchema
   - 구조: { services: [{ key, label, authRequired, acceptedInputTypes, configSchema: {
   fields: [...] } }] }

   src/main/resources/catalog/schema_types.json
   - 8개 canonical type별 출력 스키마 정의 (TEXT, SINGLE_FILE, FILE_LIST, ...)
   - schema preview에서 사용
   - 구조: { "TEXT": { schemaType, isList, fields: [{ key, label, valueType, required }],
   displayHints } }

   DTO 클래스 (6개) — org.github.flowify.catalog.dto 패키지

   ┌────────────────────────────┬────────────────────────────────────────────────────────────
   ┐
   │            파일            │                            설명
   │
   ├────────────────────────────┼────────────────────────────────────────────────────────────
   ┤
   │ SourceCatalog.java         │ 최상위 source 카탈로그 (meta + services 리스트)
   │
   ├────────────────────────────┼────────────────────────────────────────────────────────────
   ┤
   │ SourceService.java         │ source 서비스 (key, label, authRequired, sourceModes)
   │
   ├────────────────────────────┼────────────────────────────────────────────────────────────
   ┤
   │ SourceMode.java            │ source mode (key, label, canonicalInputType, triggerKind,
   │
   │                            │ targetSchema)
   │
   ├────────────────────────────┼────────────────────────────────────────────────────────────
   ┤
   │ SinkCatalog.java           │ 최상위 sink 카탈로그 (meta + services 리스트)
   │
   ├────────────────────────────┼────────────────────────────────────────────────────────────
   ┤
   │ SinkService.java           │ sink 서비스 (key, label, authRequired, acceptedInputTypes,
   │
   │                            │  configSchema)
   │
   ├────────────────────────────┼────────────────────────────────────────────────────────────
   ┤
   │ SchemaPreviewResponse.java │ schema preview 응답 (schemaType, isList, fields,
   │
   │                            │ displayHints)
   │
   └────────────────────────────┴────────────────────────────────────────────────────────────
   ┘

   Phase 2: CatalogService (새 파일 1개)

   src/main/java/org/github/flowify/catalog/service/CatalogService.java
   - @PostConstruct에서 3개 JSON 파일 로드 (ChoiceMappingService와 동일 패턴)
   - getSourceCatalog() → 전체 source 카탈로그
   - getSinkCatalog() → 전체 sink 카탈로그
   - findSourceService(key) → 특정 source 서비스 조회
   - findSinkService(key) → 특정 sink 서비스 조회
   - getSinkSchema(serviceKey, inputType) → inputType별 sink 설정 스키마
   - getSchemaTypeDefinition(canonicalType) → canonical type의 출력 스키마

   Phase 3: CatalogController (새 파일 1개)

   src/main/java/org/github/flowify/catalog/controller/CatalogController.java
   - GET /api/editor-catalog/sources → source 카탈로그 전체
   - GET /api/editor-catalog/sinks → sink 카탈로그 전체
   - GET /api/editor-catalog/sinks/{serviceKey}/schema?inputType=... → sink 상세 스키마
   - 인증 필요 (기존 SecurityConfig의 anyRequest().authenticated() 적용)

   Phase 4: Schema Preview (새 파일 1개 + 기존 수정 1개)

   src/main/java/org/github/flowify/catalog/service/SchemaPreviewService.java
   - 워크플로우 노드 체인을 순회하여 마지막 outputDataType 결정
   - CatalogService.getSchemaTypeDefinition()으로 스키마 조회
   - 로컬 추론만 (FastAPI 호출 없음) — 분기 워크플로우는 "cannot determine" 반환

   WorkflowController.java 수정
   - POST /api/workflows/{id}/schema-preview 엔드포인트 추가

   Phase 5: Translation Layer (새 파일 1개 + 기존 수정 1개)

   src/main/java/org/github/flowify/execution/service/WorkflowTranslator.java
   - Spring editor 모델 → FastAPI runtime 모델 변환
   - source 노드: service key + mode → runtime source type
   - middle 노드: 기존 그대로 (mapping_rules의 node_type)
   - sink 노드: service key + config → runtime sink type

   ExecutionService.java 수정
   - fastApiClient.execute() 호출 전에 workflowTranslator.toRuntimeModel() 삽입

   Phase 6: Preflight Validation + ErrorCode (기존 수정 2개)

   WorkflowValidator.java 수정
   - validateForExecution() 메서드 추가:
     - 기존 validate() 경고를 에러로 승격
     - source/sink 노드 config 완성도 검증
     - authRequired 서비스의 OAuth 토큰 존재 확인

   ErrorCode.java 수정
   - CATALOG_SERVICE_NOT_FOUND — 카탈로그에서 서비스를 찾을 수 없음
   - CATALOG_INVALID_INPUT_TYPE — 지원하지 않는 입력 타입
   - PREFLIGHT_VALIDATION_FAILED — 실행 전 검증 실패

   Phase 7: 문서 최신화 (기존 수정 3개)

   - WORKFLOW_SOURCE_SINK_FLOW_DESIGN.md — 백엔드 선행 계약 상태 업데이트 (구현 완료 표시)
   - WORKFLOW_SOURCE_SINK_BACKEND_REQUEST.md — 백엔드 응답 섹션 추가
   - WORKFLOW_SOURCE_SINK_BACKEND_ISSUES.md — 이슈별 완료 상태 반영
   - FASTAPI_SPRINGBOOT_API_SPEC.md — translation layer 명세 추가
   - BACKEND_ARCHITECTURE.md — catalog 관련 흐름 추가

   파일 변경 요약

   ┌─────────────────────────────────────────────┬──────────────────────────────────┐
   │                    파일                     │               작업               │
   ├─────────────────────────────────────────────┼──────────────────────────────────┤
   │ resources/catalog/source_catalog.json       │ 생성                             │
   ├─────────────────────────────────────────────┼──────────────────────────────────┤
   │ resources/catalog/sink_catalog.json         │ 생성                             │
   ├─────────────────────────────────────────────┼──────────────────────────────────┤
   │ resources/catalog/schema_types.json         │ 생성                             │
   ├─────────────────────────────────────────────┼──────────────────────────────────┤
   │ catalog/dto/SourceCatalog.java              │ 생성                             │
   ├─────────────────────────────────────────────┼──────────────────────────────────┤
   │ catalog/dto/SourceService.java              │ 생성                             │
   ├─────────────────────────────────────────────┼──────────────────────────────────┤
   │ catalog/dto/SourceMode.java                 │ 생성                             │
   ├─────────────────────────────────────────────┼──────────────────────────────────┤
   │ catalog/dto/SinkCatalog.java                │ 생성                             │
   ├─────────────────────────────────────────────┼──────────────────────────────────┤
   │ catalog/dto/SinkService.java                │ 생성                             │
   ├─────────────────────────────────────────────┼──────────────────────────────────┤
   │ catalog/dto/SchemaPreviewResponse.java      │ 생성                             │
   ├─────────────────────────────────────────────┼──────────────────────────────────┤
   │ catalog/service/CatalogService.java         │ 생성                             │
   ├─────────────────────────────────────────────┼──────────────────────────────────┤
   │ catalog/service/SchemaPreviewService.java   │ 생성                             │
   ├─────────────────────────────────────────────┼──────────────────────────────────┤
   │ catalog/controller/CatalogController.java   │ 생성                             │
   ├─────────────────────────────────────────────┼──────────────────────────────────┤
   │ execution/service/WorkflowTranslator.java   │ 생성                             │
   ├─────────────────────────────────────────────┼──────────────────────────────────┤
   │ workflow/controller/WorkflowController.java │ 수정 — schema-preview 엔드포인트 │
   ├─────────────────────────────────────────────┼──────────────────────────────────┤
   │ workflow/service/WorkflowValidator.java     │ 수정 — validateForExecution      │
   ├─────────────────────────────────────────────┼──────────────────────────────────┤
   │ execution/service/ExecutionService.java     │ 수정 — translator 삽입           │
   ├─────────────────────────────────────────────┼──────────────────────────────────┤
   │ common/exception/ErrorCode.java             │ 수정 — 에러코드 3개 추가         │
   ├─────────────────────────────────────────────┼──────────────────────────────────┤
   │ resources/application.yml                   │ 수정 — catalog 경로 설정         │
   ├─────────────────────────────────────────────┼──────────────────────────────────┤
   │ 문서 5개                                    │ 수정 — 최신화                    │
   └─────────────────────────────────────────────┴──────────────────────────────────┘

   검증 방법

   1. ./gradlew clean build -x test 빌드 확인
   2. 서버 실행 후 Swagger에서 catalog 엔드포인트 확인:
     - GET /api/editor-catalog/sources → 10개 서비스 + source mode 확인
     - GET /api/editor-catalog/sinks → 6개 서비스 확인
     - GET /api/editor-catalog/sinks/slack/schema?inputType=TEXT → config schema 확인
   3. POST /api/workflows/{id}/schema-preview → 워크플로우 결과 스키마 확인
   4. 워크플로우 실행 시 WorkflowTranslator 동작 확인 (로그)
   5. 미완성 워크플로우 실행 시도 → PREFLIGHT_VALIDATION_FAILED 에러 확인
