# Flowify 프론트엔드 설계 문서

# Flowify 프론트엔드 설계 문서

> **작성일:** 2026-03-30
**목적:** 현행 프론트엔드 코드베이스를 분석하고, 백엔드 설계 문서와의 정합성을 검증하여, 향후 구현 방향을 제시한다.
>

---

## 1. 현행 분석 보고서

### 1.1 기술 스택

| 영역 | 라이브러리 | 버전 | 비고 |
| --- | --- | --- | --- |
| UI 프레임워크 | React | 19.2.0 |  |
| 빌드 도구 | Vite | 7.3.1 |  |
| 타입 시스템 | TypeScript | 5.9.3 |  |
| 라우팅 | react-router | 7.13.1 |  |
| 상태 관리 | Zustand + Immer | 5.0.12 / 11.1.4 |  |
| 서버 상태 | TanStack Query | 5.90.21 | **설정만 존재, 미사용** |
| 노드 에디터 | @xyflow/react | 12.10.1 |  |
| UI 컴포넌트 | Chakra UI | 3.34.0 | v3 (Ark UI 기반) |
| HTTP 클라이언트 | Axios | 1.13.6 |  |
| 아이콘 | react-icons | 5.6.0 | Material Design 계열 |

### 1.2 아키텍처 (FSD)

현재 Feature-Sliced Design을 따르고 있으며, 계층 구조는 아래와 같다.

```
app → pages → widgets → features → entities → shared
(상위)                                          (하위)
```

**Import 규칙:**

- 같은 FSD 계층 → 상대 경로 (`./`, `../`)
- 다른 FSD 계층 → 절대 경로 (`@/`)
- `import type`은 value import와 분리

### 1.3 구현 완료 현황

| 영역 | 상태 | 설명 |
| --- | --- | --- |
| 프로젝트 기반 (빌드, 린트, 포맷) | ✅ 완료 | Vite, ESLint, Prettier, Husky |
| 테마 시스템 | ✅ 완료 | 시맨틱 토큰 (색상, 레이아웃) |
| 라우팅 구조 | ✅ 완료 | 6개 페이지 + 2개 레이아웃 |
| 노드 타입 정의 | ✅ 완료 | 15개 NodeType, NODE_REGISTRY |
| 노드 커스텀 UI 컴포넌트 | ⚠️ 부분 | 16개 컴포넌트 존재, BaseNode 래핑 |
| Zustand 스토어 | ✅ 완료 | React Flow 핸들러 + 메타 관리 |
| 캔버스 (React Flow) | ✅ 완료 | Placeholder 렌더링, 리프 감지 |
| 에디터 툴바 | ✅ 완료 | 이름 편집, 실행/저장 버튼 |
| 노드 추가 (Drawer) | ✅ 완료 | NodeCategoryDrawer, useAddNode |
| 노드 설정 패널 | ⚠️ 부분 | PanelRenderer 존재, 실제 패널 미구현 |
| API 클라이언트 | ⚠️ 부분 | Axios 인스턴스 + 워크플로우 API만 |
| TanStack Query 연동 | ❌ 미구현 | QueryClient 설정만 존재 |
| 인증 (Google SSO) | ❌ 미구현 | LoginPage 스텁 |
| 워크플로우 목록 | ❌ 미구현 | WorkflowsPage 스텁 |
| 템플릿 | ❌ 미구현 | TemplatesPage, TemplateDetailPage 스텁 |
| 선택지 매핑 (동적 노드 설정) | ❌ 미구현 | 백엔드 mapping_rules.json 미연동 |
| 워크플로우 실행/모니터링 | ❌ 미구현 | 실행 상태 타입만 존재 |

### 1.4 핵심 문제점

1. **ApiResponse 타입 불일치** — 프론트엔드 `{ data, status, serverDateTime, errorCode, errorMessage }` ≠ 백엔드 `{ success, data, message, errorCode }`
2. **Workflow 엔티티 필드 누락** — `description`, `userId`, `sharedWith`, `isTemplate`, `templateId`, `trigger`, `isActive` 미정의
3. **DataType 불일치** — 프론트엔드 6개(kebab-case) vs 백엔드 8개(UPPER_SNAKE_CASE), `SCHEDULE_DATA`·`SINGLE_EMAIL` 누락
4. **노드 역할(role) 개념 부재** — 백엔드 `NodeDefinition.role: start|end|middle` 미반영
5. **서버 상태 관리 부재** — TanStack Query 훅 미구현, API 호출 패턴 미정립
6. **인증 흐름 부재** — Google SSO, OAuth 토큰 관리 전무

---

## 2. 디렉토리 구조 설계

### 2.1 현행 구조

```
src/
├── app/                        # 앱 진입점
│   ├── providers/              # ApplicationProviders (Theme, Query)
│   └── routes/                 # Router.tsx
├── entities/                   # 도메인 엔티티
│   ├── connection/model/       # FlowEdgeData 타입
│   ├── node/                   # 노드 모델 + UI
│   │   ├── model/              # types, nodeRegistry, dataType
│   │   └── ui/                 # BaseNode, custom-nodes/ (16개)
│   └── workflow/model/         # Workflow, WorkflowSummary 타입
├── features/                   # 사용자 액션 단위
│   ├── add-node/               # AddNodeButton, NodeCategoryDrawer, useAddNode
│   └── configure-node/         # PanelRenderer (스텁)
├── pages/                      # 라우트별 페이지 (7개)
├── shared/                     # 공용 인프라
│   ├── api/                    # client.ts, workflow.api.ts
│   ├── constants/              # route-path.ts
│   ├── libs/                   # graph.ts, query-client.ts
│   ├── model/                  # workflowStore.ts
│   ├── styles/                 # 테마 토큰
│   ├── theme/                  # Chakra 테마 설정
│   └── types/                  # api.type.ts
└── widgets/                    # 조합형 UI 블록
    ├── canvas/                 # Canvas, CanvasEmptyState
    ├── editor-layout/          # EditorLayout (풀스크린)
    ├── editor-toolbar/         # EditorToolbar
    ├── input-panel/            # InputPanel
    ├── layout/                 # RootLayout, Header, Footer
    └── output-panel/           # OutputPanel
```

### 2.2 추가 필요 구조

아래는 백엔드 요구사항 대비 신규로 필요한 디렉토리/파일이다.

```
src/
├── entities/
│   ├── auth/                         # 🆕 인증 엔티티
│   │   └── model/
│   │       ├── types.ts              # User, AuthToken 타입
│   │       └── index.ts
│   ├── template/                     # 🆕 템플릿 엔티티
│   │   └── model/
│   │       ├── types.ts              # Template, TemplateSummary 타입
│   │       └── index.ts
│   └── oauth-token/                  # 🆕 OAuth 토큰 엔티티
│       └── model/
│           ├── types.ts              # OAuthToken 타입
│           └── index.ts
├── features/
│   ├── auth/                         # 🆕 인증 feature
│   │   ├── model/
│   │   │   └── useAuth.ts            # Google SSO 로그인 훅
│   │   └── ui/
│   │       └── GoogleLoginButton.tsx
│   ├── choice-mapping/               # 🆕 선택지 매핑 feature
│   │   ├── model/
│   │   │   ├── useChoices.ts         # 선택지 조회 훅
│   │   │   └── types.ts             # ChoiceAction, ProcessingMethod 타입
│   │   └── ui/
│   │       ├── ChoicePanel.tsx       # 선택지 패널
│   │       └── ProcessingMethodStep.tsx
│   ├── manage-workflow/              # 🆕 워크플로우 CRUD feature
│   │   ├── model/
│   │   │   ├── useWorkflows.ts       # TanStack Query 훅
│   │   │   └── useWorkflowMutation.ts
│   │   └── ui/
│   │       ├── WorkflowCard.tsx
│   │       └── CreateWorkflowDialog.tsx
│   ├── manage-template/              # 🆕 템플릿 관리 feature
│   │   └── model/
│   │       └── useTemplates.ts
│   ├── execute-workflow/             # 🆕 워크플로우 실행 feature
│   │   ├── model/
│   │   │   ├── useExecution.ts
│   │   │   └── useNodeLogs.ts
│   │   └── ui/
│   │       └── ExecutionStatusBar.tsx
│   └── oauth-connect/               # 🆕 외부 서비스 OAuth 연결
│       ├── model/
│       │   └── useOAuthTokens.ts
│       └── ui/
│           └── OAuthConnectButton.tsx
├── shared/
│   └── api/
│       ├── auth.api.ts               # 🆕
│       ├── template.api.ts           # 🆕
│       ├── oauth-token.api.ts        # 🆕
│       ├── choice.api.ts             # 🆕
│       └── execution.api.ts          # 🆕
└── widgets/
    ├── workflow-list/                # 🆕 워크플로우 목록 위젯
    ├── template-list/                # 🆕 템플릿 목록 위젯
    ├── template-detail/              # 🆕 템플릿 상세 위젯
    ├── debug-panel/                  # 🆕 실행 디버그 뷰 위젯
    └── chat-interface/               # 🆕 LLM 대화형 플로우 생성
```

---

## 3. 라우팅 설계

### 3.1 현행 라우트

| 경로 | 페이지 | 레이아웃 | 상태 |
| --- | --- | --- | --- |
| `/` | MainPage | RootLayout | 스텁 |
| `/login` | LoginPage | RootLayout | 스텁 |
| `/templates` | TemplatesPage | RootLayout | 스텁 |
| `/templates/:id` | TemplateDetailPage | RootLayout | 스텁 |
| `/workflows` | WorkflowsPage | RootLayout | 스텁 |
| `/workflows/:id` | WorkflowEditorPage | EditorLayout | 구현 중 |
| `*` | NotFoundPage | — | 완료 |

### 3.2 백엔드 요구사항 대비 라우트 매핑

| UIR | 요구 경로 | 현행 | 조치 |
| --- | --- | --- | --- |
| UIR-00 | `/login` | ✅ 존재 | Google SSO 버튼 구현 필요 |
| UIR-01 | `/templates` | ✅ 존재 | 목록 UI 구현 필요 |
| UIR-02 | `/templates/:id` | ✅ 존재 | 상세 UI 구현 필요 |
| UIR-03 | `/workflows` | ✅ 존재 | 목록 UI 구현 필요 |
| UIR-04 | `/workflows/new` | ❌ **삭제됨** | 아래 참조 |
| UIR-05 | `/workflows/:id` | ✅ 존재 | 에디터 고도화 필요 |

**UIR-04 `/workflows/new` 처리:**

- PR #47에서 해당 페이지와 create-workflow feature가 삭제됨
- 현재 설계 방향: `/workflows/:id` 에디터 내부에서 가이드형 노드 설정 흐름으로 통합
- 새 워크플로우 생성 시 빈 워크플로우를 API로 먼저 생성 → 받은 ID로 `/workflows/:id`로 리다이렉트 → 에디터 내부에서 시작/도착 설정 가이드 표시

### 3.3 인증 가드

현행: 없음. 모든 라우트가 비보호 상태.

**설계:**

```
app/routes/
├── Router.tsx
└── components/
    ├── ProtectedRoute.tsx    # 🆕 인증 가드 래퍼
    └── index.ts
```

```
<Routes>
  {/* 비보호 */}
  <Route element={<RootLayout />}>
    <Route path="/" element={<MainPage />} />
    <Route path="/login" element={<LoginPage />} />
  </Route>

  {/* 보호 (로그인 필수) */}
  <Route element={<ProtectedRoute />}>
    <Route element={<RootLayout />}>
      <Route path="/templates" element={<TemplatesPage />} />
      <Route path="/templates/:id" element={<TemplateDetailPage />} />
      <Route path="/workflows" element={<WorkflowsPage />} />
    </Route>
    <Route element={<EditorLayout />}>
      <Route path="/workflows/:id" element={<WorkflowEditorPage />} />
    </Route>
  </Route>

  <Route path="*" element={<NotFoundPage />} />
</Routes>
```

---

## 4. 상태 관리 설계

### 4.1 상태 분류 원칙

| 상태 종류 | 관리 도구 | 예시 |
| --- | --- | --- |
| **서버 상태** | TanStack Query | 워크플로우 목록, 템플릿, 사용자 정보 |
| **에디터 상태** | Zustand (workflowStore) | 노드/엣지, 활성 패널, 실행 상태 |
| **인증 상태** | Zustand (authStore) | 현재 사용자, 토큰 |
| **UI 로컬 상태** | React useState | 모달 열림, 인풋 값 |

### 4.2 현행 workflowStore 분석

**현재 State 필드:**

| 필드 | 타입 | 용도 |
| --- | --- | --- |
| `nodes` | `Node[]` | React Flow 노드 |
| `edges` | `Edge[]` | React Flow 엣지 |
| `activePanelNodeId` | `string \| null` | 설정 패널 대상 노드 |
| `workflowId` | `string` | 현재 워크플로우 ID |
| `workflowName` | `string` | 워크플로우 이름 |
| `executionStatus` | `ExecutionStatus` | 실행 상태 |

**현재 Actions:**

- `onNodesChange`, `onEdgesChange`, `onConnect` — React Flow 이벤트
- `addNode`, `removeNode`, `updateNodeConfig` — 노드 CRUD
- `openPanel`, `closePanel` — 설정 패널
- `setWorkflowMeta`, `setWorkflowName`, `setExecutionStatus` — 메타
- `resetEditor` — 초기화

**누락 필드 (이전 이슈에서 추가했으나 현재 main에 없는 것):**

> 주의: `startNodeId`, `endNodeId`, `creationMethod`, `activePlaceholder` 등은 feat 브랜치에서 작업 중이며 main에는 아직 머지되지 않았다.
> 

### 4.3 신규 스토어 설계

### authStore (🆕)

```tsx
// src/shared/model/authStore.ts
interface AuthState {
  user: User | null;
  accessToken: string | null;
  isAuthenticated: boolean;
}

interface AuthActions {
  setAuth: (user: User, token: string) => void;
  clearAuth: () => void;
}
```

- 토큰은 `localStorage`에도 저장 (현행 `client.ts` 인터셉터와 호환)
- `clearAuth` 호출 시 localStorage도 함께 정리

### workflowStore 확장 (에디터 가이드 흐름용)

```tsx
// 기존 State에 추가
interface WorkflowEditorState {
  // ... 기존 필드 ...

  /** 가이드 흐름: 시작 노드 ID */
  startNodeId: string | null;
  /** 가이드 흐름: 도착 노드 ID */
  endNodeId: string | null;
  /** 중간 과정 생성 방식 */
  creationMethod: "manual" | null;
  /** 현재 클릭된 플레이스홀더 (서비스 선택 패널 표시용) */
  activePlaceholder: string | null;
}
```

### 4.4 TanStack Query 도입 계획

현재 `QueryClient`와 `QueryProvider`가 설정되어 있으나 사용되지 않고 있다.

**훅 설계:**

```
features/
├── manage-workflow/model/
│   ├── useWorkflows.ts          # useQuery → workflowApi.getList()
│   ├── useWorkflow.ts           # useQuery → workflowApi.getById(id)
│   └── useWorkflowMutation.ts   # useMutation → create/update/delete
├── manage-template/model/
│   ├── useTemplates.ts          # useQuery → templateApi.getList()
│   └── useTemplate.ts           # useQuery → templateApi.getById(id)
├── choice-mapping/model/
│   └── useChoices.ts            # useQuery → choiceApi.getChoices(dataType)
└── execute-workflow/model/
    ├── useExecution.ts          # useMutation → workflowApi.execute(id)
    └── useNodeLogs.ts           # useQuery → executionApi.getNodeLogs(execId)
```

**Query Key 규칙:**

```tsx
const queryKeys = {
  workflows: {
    all: ["workflows"] as const,
    detail: (id: string) => ["workflows", id] as const,
  },
  templates: {
    all: ["templates"] as const,
    detail: (id: string) => ["templates", id] as const,
  },
  choices: {
    byDataType: (dataType: string) => ["choices", dataType] as const,
  },
  executions: {
    logs: (execId: string) => ["executions", execId, "logs"] as const,
  },
};
```

---

## 5. API 통신 계층 설계

### 5.1 현행 문제: ApiResponse 타입 불일치

**프론트엔드 현행:**

```tsx
type ApiResponse<T> = {
  data: T;
  status: string;
  serverDateTime: string;
  errorCode: string | null;
  errorMessage: string | null;
};
```

**백엔드 실제 (PK-C07 ApiResponse):**

```tsx
type ApiResponse<T> = {
  success: boolean;
  data: T;
  message: string | null;
  errorCode: string | null;
};
```

**백엔드 페이지네이션 (PageResponse):**

```tsx
type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};
```

**조치:** `src/shared/types/api.type.ts`를 백엔드 스펙에 맞게 교체.

### 5.2 API 엔드포인트 매핑

백엔드 설계 문서(design_classes.md, sequence_diagram.md)에서 도출한 전체 API 목록:

### 인증 (AuthController — PK-C01)

| 메서드 | 경로 | 용도 | 프론트 함수명 |
| --- | --- | --- | --- |
| POST | `/api/auth/google/login` | Google SSO 로그인 | `authApi.googleLogin(idToken)` |
| POST | `/api/auth/refresh` | 토큰 갱신 | `authApi.refresh(refreshToken)` |
| POST | `/api/auth/logout` | 로그아웃 | `authApi.logout()` |

### 사용자 (UserController — PK-C02)

| 메서드 | 경로 | 용도 | 프론트 함수명 |
| --- | --- | --- | --- |
| GET | `/api/users/me` | 내 정보 | `userApi.getMe()` |
| PUT | `/api/users/me` | 프로필 수정 | `userApi.updateMe(data)` |
| DELETE | `/api/users/me` | 회원 탈퇴 | `userApi.deleteMe()` |

### 워크플로우 (WorkflowController — PK-C03)

| 메서드 | 경로 | 용도 | 현행 | 프론트 함수명 |
| --- | --- | --- | --- | --- |
| GET | `/api/workflows` | 목록 조회 | ✅ | `workflowApi.getList()` |
| GET | `/api/workflows/:id` | 상세 조회 | ✅ | `workflowApi.getById(id)` |
| POST | `/api/workflows` | 생성 | ✅ | `workflowApi.create(body)` |
| PUT | `/api/workflows/:id` | 수정 | ✅ | `workflowApi.update(id, body)` |
| DELETE | `/api/workflows/:id` | 삭제 | ✅ | `workflowApi.delete(id)` |
| POST | `/api/workflows/:id/validate` | 유효성 검증 | ❌ | `workflowApi.validate(id)` |

### 워크플로우 실행 (ExecutionController — PK-C04)

| 메서드 | 경로 | 용도 | 현행 | 프론트 함수명 |
| --- | --- | --- | --- | --- |
| POST | `/api/workflows/:id/execute` | 실행 | ⚠️ 경로만 | `executionApi.execute(id)` |
| GET | `/api/executions/:id` | 실행 상태 조회 | ❌ | `executionApi.getStatus(id)` |
| GET | `/api/executions/:id/logs` | 노드별 로그 | ❌ | `executionApi.getLogs(id)` |
| GET | `/api/executions/:id/snapshots` | 노드 스냅샷 | ❌ | `executionApi.getSnapshots(id)` |
| POST | `/api/executions/:id/stop` | 실행 중단 | ❌ | `executionApi.stop(id)` |

### 템플릿 (TemplateController — PK-C05)

| 메서드 | 경로 | 용도 | 프론트 함수명 |
| --- | --- | --- | --- |
| GET | `/api/templates` | 목록 조회 | `templateApi.getList()` |
| GET | `/api/templates/:id` | 상세 조회 | `templateApi.getById(id)` |
| POST | `/api/templates/:id/instantiate` | 템플릿으로 워크플로우 생성 | `templateApi.instantiate(id)` |

### OAuth 토큰 (OAuthTokenController — PK-C06)

| 메서드 | 경로 | 용도 | 프론트 함수명 |
| --- | --- | --- | --- |
| GET | `/api/oauth-tokens` | 연결된 서비스 목록 | `oauthApi.getTokens()` |
| POST | `/api/oauth-tokens/:service/connect` | 서비스 연결 | `oauthApi.connect(service)` |
| DELETE | `/api/oauth-tokens/:service` | 서비스 연결 해제 | `oauthApi.disconnect(service)` |
| GET | `/api/oauth-tokens/:service/status` | 연결 상태 확인 | `oauthApi.getStatus(service)` |

### 선택지 매핑 (ChoiceMappingService — PK-C08)

| 메서드 | 경로 | 용도 | 프론트 함수명 |
| --- | --- | --- | --- |
| GET | `/api/workflows/{id}/choices/{prevNodeId}` | 이전 노드 outputDataType 기반 선택지 조회 | `choiceApi.getChoices(id, prevNodeId)` |
| POST | `/api/workflows/{id}/choices/{prevNodeId}/select` | 사용자 선택 전송 (후속 설정 반환) | `choiceApi.select(id, prevNodeId, data)` |

### LLM 기반 워크플로우 생성

| 메서드 | 경로 | 용도 | 프론트 함수명 |
| --- | --- | --- | --- |
| POST | `/api/workflows/generate` | 채팅형 자동 생성 | `workflowApi.generate(messages)` |

### 5.3 현행 API 파일 vs 필요 API 파일

| 파일 | 현행 | 필요 |
| --- | --- | --- |
| `client.ts` | ✅ | 리프레시 토큰 인터셉터 추가 |
| `workflow.api.ts` | ✅ | `validate`, `generate` 추가 |
| `auth.api.ts` | ❌ | 🆕 |
| `user.api.ts` | ❌ | 🆕 |
| `template.api.ts` | ❌ | 🆕 |
| `oauth-token.api.ts` | ❌ | 🆕 |
| `choice.api.ts` | ❌ | 🆕 |
| `execution.api.ts` | ❌ | 🆕 |

### 5.4 클라이언트 인터셉터 개선

현행 401 처리: 단순 localStorage 제거 + `/login` 리다이렉트.

**필요 개선:**

1. 리프레시 토큰으로 자동 갱신 시도
2. 갱신 실패 시에만 로그아웃
3. 동시 요청 시 갱신 요청 중복 방지 (큐잉)

---

## 6. 워크플로우 에디터 설계

### 6.1 에디터 모드

에디터는 3가지 탭/모드로 구성된다 (UC-E01, UIR-05 기반):

| 모드 | 설명 | 현행 |
| --- | --- | --- |
| **편집** (Edit) | 노드/엣지 배치, 노드 설정 | ⚠️ 구현 중 |
| **실행** (Run) | 워크플로우 실행 + 결과 확인 | ❌ |
| **디버그** (Debug) | 노드별 데이터 흐름 미리보기 | ❌ |

### 6.2 가이드형 노드 설정 흐름 (UC-W01-A~D)

빈 워크플로우 진입 시 아래 단계로 진행한다:

```
[단계 1] 시작 노드 설정 (UC-W01-A)
    빈 캔버스 → "시작" 플레이스홀더 + "도착" 플레이스홀더
    사용자가 "시작" 클릭 → 서비스 선택 패널 → 노드 생성
    → startNodeId 설정

[단계 2] 도착 노드 설정 (UC-W01-B)
    사용자가 "도착" 클릭 → 서비스 선택 패널 → 노드 생성
    → endNodeId 설정

[단계 3] 중간 과정 결정 (UC-W01-C)
    startNodeId && endNodeId 존재 시 CreationMethodNode 표시
    두 가지 선택지:
    ├─ "다음 노드 설정하기" → creationMethod = "manual"
    └─ "AI로 중간 과정 생성하기" → (향후 LLM 연동)

[단계 4] 직접 설정 상세 (UC-W01-D) — manual 선택 시
    리프 노드마다 "다음" 플레이스홀더 표시
    사용자 클릭 → 선택지 매핑 API 호출 (이전 노드의 outputDataType 기반)
    → ChoicePanel 표시 → 사용자 선택 → 노드 생성
```

### 6.3 Canvas useMemo 분기 로직

`nodesWithPlaceholders` useMemo는 3개 분기로 동작한다:

| 분기 | 조건 | 동작 |
| --- | --- | --- |
| **1** | `!startNodeId \|\| !endNodeId` | 시작/도착 플레이스홀더 표시 |
| **2** | `startNodeId && endNodeId && !creationMethod` | CreationMethodNode + 도착 노드 위치 조정 |
| **3** | `startNodeId && endNodeId && creationMethod === "manual"` | 리프 노드별 "다음" 플레이스홀더 + 도착 노드 위치 조정 |

### 6.4 선택지 매핑 연동 (UC-W01-D + UC-CM01)

**현행:** ServiceSelectionPanel이 NODE_REGISTRY 전체를 정적으로 표시.

**목표:** 백엔드 `mapping_rules.json` 기반으로 이전 노드의 `outputDataType`에 따라 동적 선택지 제공.

**흐름:**

```
사용자가 "다음" 플레이스홀더 클릭
    ↓
이전 노드의 outputDataType 확인
    ↓
GET /api/workflows/{id}/choices/{prevNodeId}
    ↓
[requires_processing_method === true]
    → ProcessingMethodStep 표시 ("한 건씩" / "전체 사용")
    → 선택 후 actions 목록 표시
[requires_processing_method === false]
    → 바로 actions 목록 표시 (priority 순 정렬)
    ↓
사용자가 action 선택
    ↓
[follow_up 존재] → 후속 설정 표시
[branch_config 존재] → 분기 설정 표시
    ↓
POST /api/workflows/{id}/choices/{prevNodeId}/select { selectedOptionId, ... }
    ↓
응답의 node_type으로 내부 노드 생성
```

### 6.5 프론트엔드 NodeType vs 백엔드 NodeType 관계

**핵심 구분:** 프론트엔드의 15개 NodeType(service category)과 백엔드의 6개 내부 node_type은 서로 다른 개념이다.

| 개념 | 프론트엔드 | 백엔드 | 사용처 |
| --- | --- | --- | --- |
| 서비스 카테고리 | `NodeType` (15개) | — | 시작/도착 노드 선택 시 표시 |
| 내부 처리 타입 | — | `node_type` (6개) | 중간 노드 선택지 매핑 결과 |
- **시작/도착 노드:** 사용자가 서비스 카테고리(communication, storage 등)에서 선택
- **중간 노드:** 선택지 매핑 API 결과에 따라 내부 처리 타입(LOOP, CONDITION_BRANCH, AI 등)으로 자동 결정

프론트엔드는 중간 노드의 처리 타입을 직접 노출하지 않는다. 사용자에게는 자연어 선택지("하나씩 처리하기", "AI로 요약하기" 등)만 보여주고, 내부적으로 해당 `node_type`의 노드를 생성한다.

### 6.6 노드 데이터 모델 정합

**백엔드 NodeDefinition (PK-C03):**

```
NodeDefinition {
  id: String
  type: String         // 서비스 타입 (gmail, google-drive 등)
  label: String
  role: "start" | "end" | "middle"
  position: Position   // { x, y }
  config: Map<String, Object>
  dataType: String     // 입력 데이터 타입
  outputDataType: String  // 출력 데이터 타입
  authWarning: Boolean    // OAuth 미연결 경고
}
```

**프론트엔드 FlowNodeData 현행:**

```tsx
interface FlowNodeData {
  type: NodeType;
  label: string;
  config: NodeConfig;
  inputTypes: DataType[];    // 배열
  outputTypes: DataType[];   // 배열
  // 누락: role, dataType(단일), outputDataType(단일), authWarning
}
```

**정합 포인트:**

1. `role` 추가 — 시작/도착/중간 노드 구분
2. `dataType` / `outputDataType` — 배열이 아닌 **단일 문자열**로 변경 (백엔드는 1:1 매핑)
3. `authWarning` 추가 — OAuth 미연결 서비스 노드에 경고 표시
4. `inputTypes`/`outputTypes` 배열은 NODE_REGISTRY에만 남기고, FlowNodeData에서는 단일 값으로 교체

### 6.7 DataType 정합

| 프론트엔드 현행 | 백엔드 | 조치 |
| --- | --- | --- |
| `file-list` | `FILE_LIST` | 케이스 변환 유틸 또는 통일 |
| `single-file` | `SINGLE_FILE` | 〃 |
| `text` | `TEXT` | 〃 |
| `spreadsheet` | `SPREADSHEET_DATA` | 이름도 다름 → 통일 |
| `email-list` | `EMAIL_LIST` | 케이스 변환 |
| `api-response` | `API_RESPONSE` | 케이스 변환 |
| ❌ 없음 | `SINGLE_EMAIL` | 🆕 추가 |
| ❌ 없음 | `SCHEDULE_DATA` | 🆕 추가 |

**권장:** 프론트엔드도 `UPPER_SNAKE_CASE`로 통일하여 변환 로직 제거. NODE_REGISTRY와 기존 코드 전체 리팩토링 필요.

---

## 7. 주요 흐름별 컴포넌트 트리

### 7.1 Google SSO 로그인 (SD-U01)

```
LoginPage
└── GoogleLoginButton
    ├── Google OAuth Popup 호출
    ├── idToken 수신
    ├── POST /api/auth/google/login
    ├── authStore.setAuth(user, accessToken)
    └── navigate("/workflows")
```

### 7.2 워크플로우 목록 (UIR-03)

```
WorkflowsPage
├── Header (RootLayout에서 제공)
├── WorkflowListWidget
│   ├── CreateWorkflowButton
│   │   ├── POST /api/workflows { name: "새 워크플로우" }
│   │   └── navigate(`/workflows/${id}`)
│   ├── WorkflowCard × N
│   │   ├── 이름, 상태, 수정일
│   │   ├── onClick → navigate(`/workflows/${id}`)
│   │   └── 삭제 버튼 → DELETE /api/workflows/:id
│   └── EmptyState (목록 비어있을 때)
└── Footer
```

### 7.3 워크플로우 에디터 — 편집 모드 (UIR-05)

```
WorkflowEditorPage
└── ReactFlowProvider
    └── WorkflowEditorInner
        ├── EditorToolbar
        │   ├── WorkflowNameEditor (인라인 편집)
        │   ├── 실행 버튼
        │   └── 저장 버튼 → PUT /api/workflows/:id
        └── Box (relative, 캔버스 영역)
            ├── Canvas
            │   ├── ReactFlow
            │   │   ├── [커스텀 노드 × N]
            │   │   ├── [PlaceholderNode × N] (useMemo에서 계산)
            │   │   └── [CreationMethodNode] (조건부)
            │   ├── Background
            │   ├── Controls
            │   └── MiniMap
            ├── CanvasEmptyState (nodes.length === 0)
            ├── ChoicePanel (activePlaceholder !== null)  ← 🆕 ServiceSelectionPanel 대체
            │   ├── ProcessingMethodStep (조건부)
            │   └── ChoiceActionList
            │       └── ChoiceActionItem × N
            ├── InputPanel (activePanelNodeId !== null)
            ├── OutputPanel (activePanelNodeId !== null)
            └── AddNodeButton (좌하단)
```

### 7.4 템플릿 기반 워크플로우 생성 (SD-W02)

```
TemplatesPage
├── TemplateListWidget
│   ├── TemplateCard × N
│   │   ├── 이름, 설명, 카테고리
│   │   └── onClick → navigate(`/templates/${id}`)
│   └── 검색/필터
└── ...

TemplateDetailPage
├── 템플릿 미리보기 (읽기 전용 React Flow)
├── 설명, 노드 구성
└── "이 템플릿으로 시작" 버튼
    ├── POST /api/templates/:id/instantiate
    └── navigate(`/workflows/${newId}`)
```

### 7.5 워크플로우 실행 (SD-E01)

```
WorkflowEditorPage (실행 모드 탭)
├── EditorToolbar
│   └── 실행 버튼 클릭
│       ├── POST /api/workflows/:id/execute
│       └── executionId 수신
├── Canvas (읽기 전용, 노드 상태 표시)
│   └── 각 노드에 실행 상태 오버레이 (대기/실행중/성공/실패)
└── DebugPanel (하단 또는 우측)
    ├── 전체 실행 진행률
    ├── NodeLogList
    │   └── NodeLogItem × N (입력/출력 데이터 미리보기)
    └── 에러 상세 (실패 시)
```

---

## 8. 백엔드 정합 체크리스트

### 8.1 타입 정합

| # | 항목 | 현행 | 필요 조치 | 우선순위 |
| --- | --- | --- | --- | --- |
| T-1 | `ApiResponse` 타입 | `{ data, status, serverDateTime }` | 백엔드 스펙 `{ success, data, message, errorCode }`로 교체 | 🔴 높음 |
| T-2 | `Workflow` 엔티티 | 6개 필드 | `description`, `userId`, `isTemplate`, `templateId`, `trigger`, `isActive` 추가 | 🔴 높음 |
| T-3 | `DataType` 열거형 | 6개, kebab-case | 8개, UPPER_SNAKE_CASE로 통일 | 🔴 높음 |
| T-4 | `FlowNodeData` | `inputTypes[]`, `outputTypes[]` | `role`, `dataType`, `outputDataType` (단일), `authWarning` 추가 | 🟡 중간 |
| T-5 | `NodeDefinition` 직렬화 | React Flow `Node<FlowNodeData>` 직접 전송 | 백엔드 `NodeDefinition` 형식으로 변환 유틸 필요 | 🟡 중간 |
| T-6 | `ExecutionStatus` | 프론트 4종 | 백엔드 실행 상태와 매핑 확인 | 🟢 낮음 |

### 8.2 API 연동

| # | 항목 | 현행 | 필요 조치 | 우선순위 |
| --- | --- | --- | --- | --- |
| A-1 | 인증 API | 없음 | `auth.api.ts` 신규 생성 | 🔴 높음 |
| A-2 | 리프레시 토큰 인터셉터 | 없음 | `client.ts`에 자동 갱신 로직 추가 | 🔴 높음 |
| A-3 | 선택지 매핑 API | 없음 | `choice.api.ts` 신규 생성 | 🔴 높음 |
| A-4 | 실행 API | execute만 존재 | `execution.api.ts`로 분리, 상태/로그/스냅샷 추가 | 🟡 중간 |
| A-5 | 템플릿 API | 없음 | `template.api.ts` 신규 생성 | 🟡 중간 |
| A-6 | OAuth 토큰 API | 없음 | `oauth-token.api.ts` 신규 생성 | 🟡 중간 |
| A-7 | 사용자 API | 없음 | `user.api.ts` 신규 생성 | 🟢 낮음 |
| A-8 | LLM 생성 API | 없음 | `workflowApi.generate` 추가 | 🟢 낮음 |
| A-9 | 유효성 검증 API | 없음 | `workflowApi.validate` 추가 | 🟢 낮음 |

### 8.3 기능 구현

| # | UC 식별자 | 기능 | 현행 | 필요 조치 | 우선순위 |
| --- | --- | --- | --- | --- | --- |
| F-1 | UC-U01 | Google SSO 로그인 | 스텁 | LoginPage + GoogleLoginButton 구현 | 🔴 높음 |
| F-2 | UC-W01-A~D | 가이드형 노드 설정 | ⚠️ 부분 | Canvas useMemo 분기 완성, ChoicePanel 연동 | 🔴 높음 |
| F-3 | UC-CM01 | 동적 선택지 제공 | 없음 | choice-mapping feature 전체 구현 | 🔴 높음 |
| F-4 | UC-W01 (목록) | 워크플로우 목록 | 스텁 | WorkflowsPage + WorkflowListWidget 구현 | 🟡 중간 |
| F-5 | UC-W03 | 템플릿 기반 생성 | 스텁 | TemplatesPage, TemplateDetailPage 구현 | 🟡 중간 |
| F-6 | UC-U02 | 서비스별 OAuth 인증 | 없음 | oauth-connect feature 구현 | 🟡 중간 |
| F-7 | UC-E01 | 워크플로우 실행 | 상태 타입만 | execute-workflow feature + DebugPanel | 🟡 중간 |
| F-8 | UC-E02 | 노드별 데이터 흐름 미리보기 | 없음 | debug-panel widget 구현 | 🟡 중간 |
| F-9 | UC-S01~S05 | 서비스 노드 설정 패널 | PanelRenderer 스텁 | 노드별 설정 패널 UI 구현 | 🟡 중간 |
| F-10 | UC-P01~P09 | 프로세싱 노드 설정 패널 | 없음 | 노드별 설정 패널 UI 구현 | 🟡 중간 |
| F-11 | UC-A01 | AI 처리 설정 | 없음 | LLM 노드 설정 패널 구현 | 🟡 중간 |
| F-12 | UC-W02 | LLM 기반 자동 생성 | 없음 | chat-interface widget + API 연동 | 🟢 낮음 |

### 8.4 권장 구현 순서

```
Phase 1 — 기반 정합 (타입/API 계층)
├── T-1: ApiResponse 타입 교체
├── T-2: Workflow 엔티티 확장
├── T-3: DataType 통일
├── A-1: auth.api.ts
├── A-2: 리프레시 토큰 인터셉터
└── TanStack Query 훅 기본 패턴 수립

Phase 2 — 핵심 흐름
├── F-1: Google SSO 로그인
├── F-4: 워크플로우 목록
├── F-2: 가이드형 노드 설정 완성
├── A-3 + F-3: 선택지 매핑 API + ChoicePanel
└── T-4: FlowNodeData 확장

Phase 3 — 서비스 연동
├── F-6: OAuth 연결
├── F-9~F-11: 노드 설정 패널들
└── T-5: NodeDefinition 직렬화

Phase 4 — 실행 및 고급 기능
├── F-7: 워크플로우 실행
├── F-8: 디버그 뷰
├── F-5: 템플릿
└── F-12: LLM 기반 자동 생성
```

---

## 부록

### A. 코드 컨벤션 요약

| 규칙 | 설명 |
| --- | --- |
| Import 순서 | 외부 → `@/` 절대 → `../` `./` 상대 (Prettier 플러그인 자동 정렬) |
| `import type` | value import와 반드시 분리 |
| FSD 계층 간 import | 상위→하위만 허용, 같은 계층은 상대 경로 |
| 배럴 인덱스 | 상위 `index.ts`는 `export *`, 하위는 named export |
| 스타일 | Chakra UI props만 사용, inline style 금지 |
| 컴포넌트 | 함수 선언문 또는 const + 화살표 함수 |
| 상태 관리 | Zustand slice 패턴, immer middleware |

### B. 용어 대응표

| 프론트엔드 용어 | 백엔드 용어 | 설명 |
| --- | --- | --- |
| `Node` (React Flow) | `NodeDefinition` | 캔버스의 노드 |
| `Edge` (React Flow) | `EdgeDefinition` | 노드 간 연결선 |
| `NodeType` (15종) | — | 서비스 카테고리 (domain/processing/ai) |
| — | `node_type` (6종) | 내부 처리 타입 (LOOP, AI 등) |
| `FlowNodeData` | `NodeDefinition.config` 등 | 노드 데이터 |
| `DataType` | `data_types` (mapping_rules.json) | 노드 간 데이터 흐름 타입 |
| `workflowStore` | — | 프론트 전용 에디터 상태 |
| `activePanelNodeId` | — | 설정 패널 대상 노드 |
| `activePlaceholder` | — | 서비스 선택 패널 대상 플레이스홀더 |