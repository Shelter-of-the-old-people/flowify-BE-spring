# Workflow List Auto-Run Toggle Spring Design

> **작성일:** 2026-05-10
> **대상:** Spring backend
> **용도:** workflow list auto-run toggle 기능의 Spring 설계 문서
> **관련 저장소:** `flowify-FE`, `flowify-BE`

---

## 1. 목적

이 문서는 워크플로우 목록에서 schedule workflow의 자동 실행을 바로 켜고 끌 수 있도록 할 때 Spring이 담당할 동작을 정리한다.

이번 기능의 핵심은 다음과 같다.

- 목록의 auto-run 토글은 새 API가 아니라 기존 workflow update 흐름을 사용한다.
- `run/stop execution`과 `enable/disable schedule`은 다른 액션으로 해석한다.
- `active=false`가 되면 해당 workflow의 schedule 등록을 즉시 해제한다.

---

## 2. 현재 상태 요약

이미 Spring에는 필요한 핵심 동작이 들어 있다.

- `PUT /api/workflows/{id}`로 `active`를 수정할 수 있다.
- schedule workflow 저장 시 register/unregister event를 발행한다.
- `ScheduleTriggerService`가 등록과 해제를 담당한다.
- manual workflow는 canonical rule상 `active=true`로 정규화된다.

즉 이번 기능에 새 scheduler 기능을 더하는 것이 아니라, 기존 기능을 목록 UI에서 안전하게 호출할 수 있게 만드는 작업에 가깝다.

---

## 3. 동작 규칙

### 3.1 schedule workflow

- `active=true` -> 자동 실행 켜짐
- `active=false` -> 자동 실행 꺼짐

목록에서 토글하면 Spring은 기존 update flow를 타고:

1. workflow 저장
2. trigger/active 정규화
3. register 또는 unregister event 발행

을 수행한다.

### 3.2 manual workflow

- 목록에서 auto-run 토글 대상이 아니다.
- Spring은 기존처럼 `manual + active=false` 입력을 받아도 `active=true`로 정규화한다.

즉 FE가 manual workflow에 toggle request를 보내지 않는 것이 맞고, Spring은 들어오더라도 canonicalize한다.

### 3.3 실행 중인 workflow와의 관계

목록에서 auto-run을 꺼도:

- 이미 시작된 execution은 계속 진행된다.
- 이후 schedule fire만 멈춘다.

이 규칙은 기존 trigger settings 설계와 동일하다.

---

## 4. API 설계

새 endpoint는 추가하지 않는다.

계속 아래 API를 사용한다.

```http
PUT /api/workflows/{id}
Content-Type: application/json

{
  "active": false
}
```

schedule workflow에서는 위 요청만으로 충분하다.

---

## 5. 권한 규칙

- owner만 auto-run 토글 가능
- shared user는 update API 자체가 기존 권한 규칙에 따라 수정 불가

즉 Spring에서 새 권한 모델을 추가할 필요는 없다. 기존 `updateWorkflow()` 접근 제어를 그대로 따른다.

---

## 6. 이번 범위의 구현 판단

이번 목록 auto-run 토글 자체만 놓고 보면 Spring 코드 변경은 필수는 아니다.

필요 조건은 이미 충족돼 있다.

- partial update로 `active` 변경 가능
- schedule register/unregister lifecycle 존재
- manual normalization 존재

다만 문서와 검증에서는 아래를 다시 확인한다.

- schedule `active=true -> false` 시 unregister
- schedule `active=false -> true` 시 register
- manual workflow는 여전히 `active=true` canonicalization 유지

---

## 7. 검증 항목

### 7.1 서비스 테스트

- schedule workflow를 `active=false`로 update하면 unregister event가 발행된다.
- schedule workflow를 다시 `active=true`로 update하면 register event가 발행된다.
- manual workflow를 `active=false`로 update해도 응답은 `active=true`로 정규화된다.

### 7.2 통합 테스트

- 목록 토글 후 실제 schedule fire가 멈추는지 확인한다.
- 다시 켠 뒤 다음 주기부터 fire가 재개되는지 확인한다.
- 이미 running 상태인 execution은 toggle과 무관하게 끝까지 진행되는지 확인한다.

---

## 8. 한 줄 요약

이번 Spring 관점의 핵심은 새 API나 새 scheduler를 만드는 것이 아니라, 기존 `workflow.active` 기반 schedule lifecycle을 목록 UI에서도 그대로 재사용하는 것이다.
