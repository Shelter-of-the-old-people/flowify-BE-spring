# LMS 강의자료 -> Google Drive 자동화 분석 보고서

> 분석 대상: `src/main/resources/lms_to_drive.py`
> 분석 일자: 2026-04-28
> 목적: 해당 자동화를 Flowify 서버 환경에서 실행할 수 있는지 가능성 분석

---

## 1. 원본 스크립트 동작 분석

### 1.1 전체 플로우

```
[Canvas LMS API] → 강의 목록 조회 → 학기/과목 선택(사용자 입력)
    → 파일 목록 조회 → 파일 다운로드
    → [Google Drive API] 폴더 생성 → 파일 업로드 (중복 스킵)
    → [Gmail SMTP] 백업 결과 이메일 발송
```

### 1.2 사용 외부 서비스 (4개)

| 서비스 | 역할 | 인증 방식 | 사용 라이브러리 |
|--------|------|-----------|----------------|
| Canvas LMS | 강의/파일 조회 (Source) | API Token (`CANVAS_TOKEN`) | `canvasapi` |
| Google Drive | 폴더 생성 + 파일 업로드 (Sink) | OAuth2 (`token.json`) | `googleapiclient` |
| Gmail | 결과 이메일 발송 (Sink) | SMTP App Password | `smtplib` |
| HTTP (requests) | Canvas 파일 다운로드 | Bearer Token | `requests` |

### 1.3 핵심 로직 단계별 분석

| 단계 | 코드 위치 | 동작 | 서버 전환 시 고려사항 |
|------|-----------|------|----------------------|
| 1 | `main():98` | Canvas API 연결 | REST API 호출로 대체 가능 |
| 2 | `main():106-112` | 전체 강의 목록 조회 + 학기별 분류 | API 호출로 대체 가능 |
| 3 | `main():115-128` | **사용자 대화형 입력 (input())** | **서버에서 불가 - API 파라미터로 변환 필요** |
| 4 | `main():134-161` | 파일 다운로드 → Drive 업로드 루프 | 서버 메모리에서 스트리밍 처리 필요 |
| 5 | `get_or_create_folder()` | Drive 폴더 존재 확인/생성 | Google Drive API 그대로 사용 |
| 6 | `main():143-147` | 중복 파일 스킵 (Drive 검색) | 동일 로직 적용 가능 |
| 7 | `send_summary_email_smtp()` | HTML 이메일 발송 | Gmail API 또는 SMTP 사용 |

---

## 2. Flowify 서버 환경과의 매핑 분석

### 2.1 아키텍처 매핑

```
lms_to_drive.py 플로우          →    Flowify 워크플로우 노드 구조
─────────────────────────        ─────────────────────────────
Canvas LMS 파일 조회 (Source)    →    Source 노드 (신규 서비스 필요)
파일 다운로드 + 변환 (Process)    →    중간 처리 노드 (LLM/패스스루)
Google Drive 업로드 (Sink)       →    Sink 노드: google_drive (이미 존재)
이메일 결과 발송 (Sink)           →    Sink 노드: gmail (이미 존재)
```

### 2.2 기존 카탈로그와의 호환성

| 스크립트 기능 | Flowify 카탈로그 | 상태 |
|--------------|-----------------|------|
| Google Drive 폴더 생성 | `google_drive` sink - `folder_id` config | **존재** |
| Google Drive 파일 업로드 | `google_drive` sink - `FILE_LIST` input | **존재** |
| Gmail 이메일 발송 | `gmail` sink - `send` action, `html` format | **존재** |
| Google Drive OAuth | `GoogleDriveConnector` | **방금 구현 완료** |
| Canvas LMS API 연동 | 카탈로그에 없음 | **신규 추가 필요** |

### 2.3 실행 흐름 대응

**Spring Boot 역할:**
- 워크플로우 정의 저장, 사용자 인증, OAuth 토큰 관리
- `ExecutionService.collectServiceTokens()`로 google_drive 토큰 수집
- `FastApiClient.execute()`로 FastAPI에 실행 위임

**FastAPI 역할 (실제 실행 엔진):**
- 워크플로우 노드를 순서대로 실행
- Canvas API 호출 → 파일 다운로드 → Drive 업로드 → 이메일 발송
- 완료 후 Spring Boot에 콜백

---

## 3. 실현 가능성 판정

### 3.1 가능한 부분 (즉시 구현 가능)

#### Google Drive 업로드 - 가능
- **근거**: `google_drive` sink가 카탈로그에 이미 정의됨
- `accepted_input_types`: `["TEXT", "SINGLE_FILE", "FILE_LIST", "SPREADSHEET_DATA"]`
- `folder_id`, `filename_template`, `file_format` 설정 지원
- OAuth 토큰: `GoogleDriveConnector`로 발급/저장 완료
- **결론: 파일을 Drive에 업로드하는 것은 기존 인프라로 완전히 가능**

#### Gmail 결과 발송 - 가능
- **근거**: `gmail` sink가 카탈로그에 이미 정의됨
- `to`, `subject`, `body_format(html)`, `action(send)` 설정 지원
- 원본 스크립트는 SMTP를 사용하지만, Flowify는 Gmail API를 사용
- **주의**: Gmail sink 사용 시 Google OAuth에 Gmail 스코프 추가 필요 (`https://www.googleapis.com/auth/gmail.send`)
- **결론: Gmail API 기반으로 동일한 이메일 발송 가능**

#### 중복 파일 스킵 - 가능
- 원본: `drive_service.files().list(q=query)` 로 기존 파일 확인
- Flowify: FastAPI 실행 엔진에서 Drive API로 동일 로직 구현 가능
- **결론: Drive API 호출이 가능하므로 중복 체크도 가능**

#### 폴더 자동 생성 - 가능
- 원본: `get_or_create_folder()` 로 학기/과목별 폴더 생성
- Flowify: `google_drive` sink의 `folder_id` 설정으로 대상 폴더 지정
- 단, 자동 폴더 생성(없으면 만들기)은 FastAPI 실행 엔진에서 추가 로직 필요
- **결론: 기본 업로드는 가능, 자동 폴더 생성은 FastAPI 쪽 구현 필요**

---

### 3.2 불가능하거나 추가 작업이 필요한 부분

#### Canvas LMS API 연동 - 신규 개발 필요

- **문제**: Flowify 카탈로그에 Canvas LMS 서비스가 없음
- Canvas API는 REST 기반이므로 기술적으로 호출 가능하지만:
  1. `source_catalog.json`에 `canvas_lms` 서비스 정의 추가 필요
  2. FastAPI 실행 엔진에 Canvas API 호출 로직 구현 필요
  3. Canvas API Token을 OAuth 토큰처럼 사용자별 저장/관리 필요
- Canvas는 표준 OAuth2가 아닌 **Personal Access Token** 방식 → `ExternalServiceConnector`의 직접 토큰 저장 방식(Notion/GitHub과 동일 패턴)으로 처리 가능

```
필요한 작업량:
├── Spring Boot: Canvas 토큰 커넥터 1개 + 카탈로그 엔트리 1개
├── FastAPI: Canvas API 실행 노드 구현
└── 예상 난이도: 중 (기존 패턴 재사용 가능)
```

#### 대화형 사용자 입력 (input()) - 구조 변환 필요

- **문제**: 원본 스크립트 115~128번 줄에서 `input()`으로 학기/과목 선택
- 서버 환경에서는 대화형 입력 불가
- **해결 방안**:
  - 워크플로우 노드의 `config`에서 사전에 설정 (학기명, 과목 ID 등)
  - 또는 "전체 과목 백업" 모드로 단순화
  - 프론트엔드에서 Canvas API를 먼저 조회 → 사용자가 UI로 선택 → config에 저장 → 실행

#### 파일 스트리밍 처리 - 메모리 관리 필요

- **문제**: 원본은 로컬에서 `io.BytesIO(res.content)`로 메모리에 파일 전체 로드
- 서버에서 대용량 파일을 다수 처리 시 메모리 부족 가능
- **해결 방안**: FastAPI에서 스트리밍 다운로드 + 청크 업로드 구현
- 예상 난이도: 낮음 (Python httpx + googleapiclient resumable upload 지원)

---

## 4. Flowify 워크플로우로 변환 시 노드 구성

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐     ┌────────────┐
│  Canvas LMS     │ ──→ │  PASSTHROUGH     │ ──→ │  Google Drive   │ ──→ │  Gmail     │
│  (Source)       │     │  (파일 변환/정리)  │     │  (Sink)        │     │  (Sink)    │
│                 │     │                  │     │                 │     │            │
│ type: canvas_lms│     │ type: PASSTHROUGH│     │ type:google_drive│    │ type: gmail│
│ mode: course_   │     │ 중복 체크 +      │     │ folder_id: xxx  │     │ to: user@  │
│   files         │     │ 파일명 정리      │     │ format: original│     │ format:html│
│ config:         │     │                  │     │                 │     │ action:send│
│   term: 2026-1  │     │                  │     │                 │     │            │
│   course_ids:   │     │                  │     │                 │     │            │
│     [123, 456]  │     │                  │     │                 │     │            │
└─────────────────┘     └──────────────────┘     └─────────────────┘     └────────────┘
```

---

## 5. 종합 판정

### 실행 가능성 요약

| 항목 | 판정 | 비고 |
|------|------|------|
| Google Drive 파일 업로드 | **즉시 가능** | 카탈로그 + OAuth 모두 준비됨 |
| Gmail 결과 이메일 발송 | **즉시 가능** | Gmail 스코프 추가 필요 |
| Canvas LMS 파일 조회 | **추가 개발 필요** | 카탈로그 + FastAPI 노드 구현 |
| 대화형 과목 선택 | **구조 변경 필요** | config 파라미터로 변환 |
| 중복 파일 스킵 | **가능** | FastAPI 실행 로직에서 처리 |
| 폴더 자동 생성 | **가능 (추가 로직)** | FastAPI에서 Drive API 호출 |
| 전체 자동화 플로우 | **가능 (조건부)** | Canvas 커넥터 개발 후 가능 |

### 최종 결론

> **Flowify 서버에서 이 자동화는 실행 가능하다. 단, Canvas LMS 커넥터를 추가 개발해야 한다.**
>
> Google Drive 업로드와 Gmail 발송은 이미 Flowify 카탈로그에 구현되어 있고, OAuth 토큰 관리도 완료된 상태다. 부족한 부분은 Canvas LMS를 Source 서비스로 추가하는 것이며, 이는 기존 GitHub/Notion 토큰 저장 패턴을 그대로 재사용할 수 있다.
>
> 원본 스크립트의 대화형 입력(`input()`)은 워크플로우 노드의 `config` 파라미터로 대체하면 서버 환경에서도 동일한 기능을 제공할 수 있다.

### 구현 우선순위

1. **(필수)** Canvas LMS `ExternalServiceConnector` 추가 (Spring Boot - 기존 패턴 재사용)
2. **(필수)** `source_catalog.json`에 `canvas_lms` 서비스 정의 추가
3. **(필수)** FastAPI 실행 엔진에 Canvas API 노드 구현
4. (권장) Gmail 스코프 추가 (`gmail.send`) - Google Cloud Console에서 설정
5. (선택) 폴더 자동 생성 로직 - FastAPI google_drive sink에 추가