# Canvas LMS Manual Token Picker Spring Design

> 작성일: 2026-05-14
> 대상 브랜치: `fix#32-canvas-lms-manual-token-picker`

## 1. 목적

계정 페이지에서 사용자가 직접 저장한 `Canvas LMS manual token`이
`canvas_lms` source node의 target option 조회에도 동일하게 반영되도록
Spring 오케스트레이션을 정리한다.

이번 문서는 "토큰 저장은 성공했는데 노드에서 과목/학기 선택지를 불러오지 못하는"
불일치를 해소하는 데만 집중한다.

## 2. 현재 문제

### 2.1 현재 정상 동작하는 경로

- 계정 페이지의 manual token 저장은 `CanvasLmsManualTokenHandler`가 담당한다.
- 이 경로는 사용자가 입력한 `accessToken`으로 Canvas API를 직접 호출해 검증한다.
- 따라서 "토큰 저장/검증" 자체는 사용자 토큰 기준으로 정상 동작한다.

### 2.2 현재 잘못 연결된 경로

- `canvas_lms` source picker는 `TargetOptionService`에서만 `oauthTokenService` lookup을 건너뛴다.
- `CanvasLmsTargetOptionProvider`는 메서드 인자로 받은 `token`이 아니라
  서버 설정값 `app.oauth.canvas-lms.token`을 사용한다.
- 그 결과 계정 페이지에서 저장한 토큰과 picker가 사용하는 토큰의 authoritative source가 다르다.

### 2.3 사용자 증상

- 계정 페이지에서는 Canvas LMS 연결이 성공한 것처럼 보인다.
- 그러나 workflow editor에서 `canvas_lms` source node를 고르면
  과목 목록 또는 학기 목록을 불러오는 시점에 실패할 수 있다.
- FE에서는 이를 `외부 서비스에서 선택지를 불러오지 못했습니다.`로 표시한다.

## 3. 이번 이슈 범위

### 3.1 포함

- `canvas_lms` source picker가 사용자 저장 토큰을 사용하도록 Spring 내부 연결을 수정한다.
- Canvas picker 관련 Spring 테스트를 이번 정책에 맞게 갱신한다.
- `course_files`, `course_new_file`, `term_all_files` 3개 source mode의 token source를 통일한다.

### 3.2 제외

- FE 문구, UI 흐름, 도움말 링크 수정
- FastAPI runtime execution 로직 수정
- Notion, GitHub 등 다른 manual token 서비스의 동작 방식 변경
- Canvas LMS OAuth 방식 추가

## 4. 설계 결정

### 4.1 authoritative token source

`canvas_lms`의 authoritative token source는 서버 env가 아니라
계정 페이지에서 저장된 사용자별 encrypted token이다.

즉, 아래 두 경로가 같은 토큰 원천을 바라봐야 한다.

- manual token 저장/검증
- workflow editor의 source picker 조회

### 4.2 TargetOptionService 정책

`TargetOptionService`는 auth-required source service라면
서비스별 예외 없이 `oauthTokenService.getDecryptedToken(...)`을 호출한다.

이번 이슈에서는 `canvas_lms` 예외 분기를 제거한다.

이 결정의 의미는 다음과 같다.

- Canvas LMS도 다른 auth-required source와 같은 규칙을 따른다.
- picker 직전의 토큰 조회 책임은 `TargetOptionService`가 가진다.
- provider는 "토큰을 어디서 가져올지"가 아니라
  "전달받은 토큰으로 어떤 target option을 만들지"만 담당한다.

### 4.3 CanvasLmsTargetOptionProvider 정책

`CanvasLmsTargetOptionProvider`는 서버 설정값 토큰에 의존하지 않는다.

- `getOptions(...)`의 `token` 인자가 비어 있으면 `OAUTH_NOT_CONNECTED`를 반환한다.
- 실제 Canvas API 호출은 모두 전달받은 `token`으로 수행한다.
- 내부 `fetchCourses(...)`도 `token`을 명시적으로 받는 시그니처로 바꾼다.

이로써 provider는 stateless한 picker 컴포넌트에 가까워지고,
manual token / OAuth token / alias token 여부와 무관하게
상위 orchestration이 넘긴 토큰만 사용한다.

### 4.4 에러 처리 원칙

이번 이슈는 token source를 맞추는 작업이지,
Canvas LMS 전용 에러 분류 체계를 새로 도입하는 작업은 아니다.

따라서 아래 원칙을 유지한다.

- 토큰이 없으면 `OAUTH_NOT_CONNECTED`
- 외부 Canvas API 응답 실패는 기존처럼 `EXTERNAL_API_ERROR`

필요하면 후속 이슈에서 `401/403` 세분화는 별도로 다룬다.

## 5. 구현 포인트

### 5.1 수정 파일

- `src/main/java/org/github/flowify/catalog/service/picker/TargetOptionService.java`
- `src/main/java/org/github/flowify/catalog/service/picker/CanvasLmsTargetOptionProvider.java`
- `src/test/java/org/github/flowify/catalog/TargetOptionServiceTest.java`
- `src/test/java/org/github/flowify/catalog/service/picker/CanvasLmsTargetOptionProviderTest.java`

### 5.2 기대 코드 변화

`TargetOptionService`

- `canvas_lms` 예외 제거
- auth-required source 공통 규칙 유지

`CanvasLmsTargetOptionProvider`

- `@Value("${app.oauth.canvas-lms.token:}")` 제거
- `fetchCourses(boolean includeCompleted)`를
  `fetchCourses(String token, boolean includeCompleted)`로 변경
- Canvas API 요청의 bearer token을 전달 인자 기준으로 설정

## 6. 테스트 기대사항

### 6.1 TargetOptionService 테스트

기존의 "Canvas LMS는 oauth lookup을 건너뛴다"는 테스트는
이번 이슈 이후 잘못된 기대값이 된다.

새 기대값은 다음과 같다.

- `canvas_lms`도 `oauthTokenService.getDecryptedToken("user-1", "canvas_lms", List.of())`
  경로를 탄다.
- 조회된 토큰이 provider에 그대로 전달된다.

### 6.2 Provider 테스트

provider 단위에서는 적어도 아래를 보장한다.

- 전달받은 token이 비어 있으면 즉시 `OAUTH_NOT_CONNECTED`
- term/course filtering 로직은 기존과 동일하게 유지

## 7. 호환성 및 영향도

- DB schema 변경 없음
- API 스펙 변경 없음
- FE 요청 payload 변경 없음
- FastAPI runtime 계약 변경 없음

영향도는 `Canvas LMS source picker` 경로로 국한된다.

## 8. 결정 요약

- Canvas LMS picker도 사용자 저장 토큰을 authoritative source로 사용한다.
- Spring의 source picker orchestration에서 `canvas_lms`만 따로 빼는 예외를 제거한다.
- provider는 서버 env 토큰이 아니라 상위 계층이 전달한 토큰만 사용한다.
- 이번 이슈는 Canvas LMS manual token과 picker 간 불일치만 수정하고,
  다른 서비스나 화면은 건드리지 않는다.
