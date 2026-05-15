# Gmail Sender Display Name Spring Design

> 작성일: 2026-05-15
> 대상: Spring backend
> 용도: Gmail 발신자 표시명 보장 기능을 위한 Spring 책임 설계
> 관련 저장소: `flowify-BE`, `flowify-FE`

---

## 1. 목적

이 문서는 Gmail sink 발송 시 `From` 헤더의 표시명을 안정적으로 보장하기 위해
Spring backend가 어떤 사용자 식별 정보를 FastAPI 실행 계약에 실어야 하는지 정의한다.

이번 이슈의 목표는 아래와 같다.

- Gmail 발송 시 계정 설정에만 의존하지 않고 사용자 이름을 안정적으로 전달한다.
- FastAPI가 `이름 + 이메일` 형식의 `From` 헤더를 만들 수 있도록 실행 계약을 확장한다.
- 기존 Gmail source/sink 동작과 다른 서비스 실행 계약을 깨뜨리지 않는다.
- 사용자 이름을 찾지 못하는 경우에는 기존 fallback 동작을 유지한다.

---

## 2. 현재 문제

이전 이슈에서 `flowify-BE`는 아래 1차 방어를 이미 넣었다.

- Gmail `sendAs.displayName`을 우선 사용
- 값이 없거나 조회가 실패하면 bare email로 fallback
- Gmail 수신 시 `Subject`, `From`, `To` 헤더 MIME decode

이 조합으로 깨진 문자열 문제는 막을 수 있었지만,
실환경에서는 여전히 `김민호 <mhtiger362@gmail.com>` 대신 `mhtiger362@gmail.com`으로 fallback 되는 경우가 관찰되었다.

즉 지금 구조는 아래 특성을 가진다.

- 깨진 문자열은 제거할 수 있다.
- 그러나 표시명은 Gmail 계정의 `sendAs` 설정에 의존한다.
- Spring은 로그인 시점에 이미 Google profile의 `name`을 알고 있지만, FastAPI 실행 계약으로는 보내지 않는다.

---

## 3. 현재 구조 분석

### 3.1 Spring이 이미 알고 있는 정보

`AuthService`는 Google 로그인 처리 시 아래 사용자 정보를 받고 `User`에 저장한다.

- `email`
- `name`
- `picture`

즉 Spring은 Gmail OAuth 토큰과 별개로,
애플리케이션 기준의 신뢰 가능한 사용자 이름 소스를 이미 보유하고 있다.

### 3.2 FastAPI 실행 계약의 현재 한계

현재 `FastApiClient`는 FastAPI 호출 시 아래만 전달한다.

- request body:
  - `workflow`
  - `service_tokens`
- header:
  - `X-User-ID`
  - `X-Internal-Token`

여기에는 발신자 표시명 후보가 없다.

따라서 FastAPI는 Gmail API의 `sendAs.displayName`에만 의존하게 되고,
그 값이 비어 있거나 기대와 다르면 bare email fallback 외에 선택지가 없다.

---

## 4. 설계 목표

이번 기능에서 Spring이 달성해야 하는 목표는 아래와 같다.

1. FastAPI 실행 요청에 사용자 표시명 후보를 포함한다.
2. 기존 header 계약을 깨지 않도록 body 기반 확장으로 처리한다.
3. 표시명 정보가 없는 사용자도 기존과 동일하게 실행 가능해야 한다.
4. Gmail 이외의 서비스는 이 정보를 무시해도 되도록 호환성을 유지한다.

---

## 5. 설계 원칙

### 5.1 표시명의 authoritative source

1순위 authoritative source는 Spring `User.name`이다.

이유:

- Google login 시점에 이미 검증된 profile name이다.
- Gmail `sendAs` 설정과 분리되어 있다.
- 애플리케이션 사용자 기준으로 일관성이 있다.

### 5.2 전송 방식

표시명은 FastAPI request body의 별도 runtime context로 전달한다.

이유:

- 기존 `X-User-ID` header 의미를 오염시키지 않는다.
- 추후 사용자 locale, timezone, profile metadata 같은 문맥 값도 같은 컨테이너에 실을 수 있다.
- Gmail 이외의 노드가 필요 시 선택적으로 읽을 수 있다.

### 5.3 backward compatibility

- 기존 FastAPI는 새 필드를 무시해도 실행 가능해야 한다.
- Spring이 표시명을 찾지 못하면 빈 값 또는 미포함으로 보내고,
  FastAPI는 기존 `sendAs.displayName -> bare email` fallback을 유지한다.

---

## 6. 제안 계약

### 6.1 FastAPI execute/preview body 확장

Spring은 기존 request body에 아래 runtime context를 추가한다.

```json
{
  "workflow": { "...": "..." },
  "service_tokens": {
    "gmail": "ya29..."
  },
  "runtime_context": {
    "user_profile": {
      "user_id": "665f...",
      "email": "mhtiger362@gmail.com",
      "display_name": "김민호"
    }
  }
}
```

필드 의미:

- `runtime_context.user_profile.user_id`
  - Spring authenticated user id
- `runtime_context.user_profile.email`
  - Spring user email
- `runtime_context.user_profile.display_name`
  - Gmail `From` 표시명 후보

### 6.2 preview/execute 공통 적용

아래 FastAPI 진입점 모두 동일한 `runtime_context` 구조를 사용한다.

- workflow execute
- node preview

이유:

- preview 단계에서도 Gmail sink 또는 향후 사용자 문맥이 필요한 노드가 같은 입력 구조를 사용하도록 맞춘다.

---

## 7. Spring 구현 책임

### 7.1 FastApiClient

`FastApiClient`는 request body 생성 시 `runtime_context`를 포함해야 한다.

포함 대상:

- `execute`
- `previewNode`

### 7.2 사용자 정보 조회

`FastApiClient`가 직접 repository를 가지는 대신,
호출 상위 서비스에서 `User` 정보를 조합해 전달하는 형태가 더 안전하다.

권장 방향:

- `ExecutionService`
- `WorkflowPreviewService`

에서 현재 사용자 정보를 조회하고,
FastApiClient에는 이미 정규화된 `runtime_context`를 넘긴다.

이유:

- FastApiClient를 transport layer 역할에 가깝게 유지할 수 있다.
- 실행과 preview에서 같은 사용자 컨텍스트 생성 로직을 재사용할 수 있다.

### 7.3 user profile 정규화 규칙

- `display_name`: `User.name` trim 후 사용
- 비어 있으면 `null` 또는 미포함
- `email`: `User.email`
- `user_id`: authenticated principal 기준 user id

---

## 8. FastAPI 기대 동작

Spring 문서 기준으로 FastAPI는 아래 우선순위를 사용해야 한다.

1. `runtime_context.user_profile.display_name`
2. Gmail `sendAs.displayName`
3. bare email

즉 Spring이 이름을 전달하면,
Gmail 계정 설정에 display name이 없어도 `김민호 <mhtiger362@gmail.com>`를 안정적으로 만들 수 있어야 한다.

---

## 9. 영향 범위

### 9.1 직접 영향

- `FastApiClient`
- `ExecutionService`
- `WorkflowPreviewService`
- 필요 시 사용자 정보 조회 공통 헬퍼

### 9.2 간접 영향

- FastAPI request schema 문서
- Gmail sink 실환경 테스트

### 9.3 영향 없음

- OAuth token 저장 구조 자체 변경은 이번 범위에서 필수 아님
- FE 설정 UI 변경 없음
- Gmail source target option provider 변경 없음

---

## 10. 예외 및 fallback 정책

### 10.1 사용자 이름 없음

아래 경우는 허용한다.

- `User.name`이 비어 있음
- legacy user라 이름이 없음

이 경우 Spring은 display name을 비우고,
FastAPI가 기존 fallback을 수행한다.

### 10.2 Gmail이 아닌 sink

`runtime_context.user_profile`은 다른 sink가 무시해도 된다.

즉 이 확장은 Gmail 전용 최적화이지만,
계약 자체는 일반적인 사용자 문맥 컨테이너로 유지한다.

---

## 11. 테스트 계획

### 11.1 단위 테스트

- `ExecutionService`가 user profile을 만들어 FastApiClient에 넘기는지
- `WorkflowPreviewService`가 같은 규칙을 쓰는지
- name이 없을 때 display_name이 비어도 예외가 나지 않는지

### 11.2 계약 테스트

- FastApiClient request body에 `runtime_context.user_profile.display_name`이 포함되는지
- 기존 body shape를 기대하는 호출이 깨지지 않는지

### 11.3 회귀 테스트

- Gmail이 아닌 workflow execute/preview도 그대로 통과하는지
- 기존 `X-User-ID` 기반 권한/인증 흐름이 유지되는지

---

## 12. 범위 제외

이번 설계 범위에서 제외한다.

- FE에서 발신자 표시명 직접 입력 UI 제공
- 사용자별 custom `from_name` 설정 화면
- Gmail alias 선택 UI
- OAuth token 문서에 display name 메타데이터를 별도 저장하는 구조

이 항목들은 현재 요구사항보다 범위가 크므로 후속 기능으로 분리한다.

---

## 13. 결론

Gmail 발신자 표시명을 안정적으로 보장하려면,
Spring이 이미 알고 있는 사용자 이름을 FastAPI 실행 계약으로 전달하는 것이 가장 단순하고 신뢰도 높은 방법이다.

이번 이슈에서 Spring은 다음만 책임지면 된다.

- 현재 로그인 사용자 `name`, `email`, `id`를 user profile로 정규화
- execute/preview request body에 `runtime_context.user_profile` 추가
- 기존 Gmail/non-Gmail 흐름을 깨지 않는 fallback 호환성 유지

이 설계를 기준으로 FastAPI는 Gmail `From` 헤더를
`display_name + email` 형태로 안정적으로 구성할 수 있다.
