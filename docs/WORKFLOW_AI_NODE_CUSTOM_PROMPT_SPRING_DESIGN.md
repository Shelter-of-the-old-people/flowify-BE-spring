# 워크플로우 AI 노드 사용자 프롬프트 결합 Spring 설계

> 작성일: 2026-05-25  
> 대상: Spring backend  
> 범위: choice 기반 AI 노드에서 `config.prompt`를 기본 프롬프트에 추가 결합하는 실행 계약 변경  
> 관련 레포: `flowify-FE`, `flowify-BE`  
> 관련 문서: `docs/WORKFLOW_AI_NODE_CUSTOM_PROMPT_UI_DESIGN.md`, `src/main/resources/docs/ai_prompt_rules.json`

---

## 1. 목적

이 문서는 workflow editor의 AI 노드에서 사용자가 입력한 `config.prompt`를
기존 choice 기반 기본 프롬프트를 대체하는 값이 아니라,
**기본 프롬프트 뒤에 추가로 반영되는 사용자 지시사항**으로 처리하도록
Spring의 prompt 조립 규칙을 재정의한다.

이번 변경의 목표는 아래와 같다.

- choice wizard가 만든 기본 프롬프트의 안정성과 기대 출력 형식을 유지한다.
- 사용자가 입력한 프롬프트도 실행 결과에 명시적으로 반영된다.
- 기존 템플릿성 manual prompt 노드와의 호환을 깨지 않는다.
- FastAPI runtime 계약은 그대로 유지하고, Spring에서 최종 `prompt` 문자열만 조합한다.

---

## 2. 현재 상태

### 2.1 현재 Spring은 manual prompt를 choice prompt보다 우선한다

현재 `ChoicePromptResolver`는 아래 순서로 동작한다.

1. `config.prompt`를 읽는다.
2. 값이 있으면 `resolveManualPrompt()`로 바로 종료한다.
3. 값이 없을 때만 `base_prompt + data_type_prompt + action_prompt + modifiers`를 조합한다.

즉 현재 의미는 아래와 같다.

- `config.prompt` 있음 -> manual prompt 단독 사용
- `config.prompt` 없음 -> choice 기반 자동 프롬프트 사용

이 구조는 FE에서 처음 설계한 “개인 프롬프트가 자동 프롬프트를 덮어쓴다” 정책과는 맞지만,
제품 관점에서는 아래 문제가 있다.

- choice wizard가 보장하던 출력 형식/안전 레일이 약해진다.
- 사용자가 짧은 프롬프트만 적으면 기대 출력 품질이 흔들릴 수 있다.
- action/data type별 기본 지시사항이 사라져 일관성이 떨어질 수 있다.

### 2.2 FastAPI는 최종 prompt 문자열만 실행한다

FastAPI `LLMNodeStrategy`는 Spring runtime payload에 들어 있는 최종 `prompt`를 그대로 사용한다.

- Spring이 어떤 문자열을 넘기느냐가 실행 동작을 결정한다.
- FastAPI는 prompt 조립 정책을 다시 알 필요가 없다.

따라서 이번 변경의 핵심은 Spring의 `ChoicePromptResolver`와 그 테스트다.

### 2.3 모든 manual prompt를 choice prompt에 붙이면 안 된다

현재 시스템에는 FE의 개인 프롬프트 UI에서 들어오는 `config.prompt` 외에도,
기존 템플릿/seed/수동 작성 노드에서 들어오는 `prompt`가 존재한다.

예:

- template seeder가 직접 넣는 정적 prompt
- choice wizard를 거치지 않는 legacy/manual AI 노드

이 경우에는 기본 choice prompt가 존재하지 않거나,
존재하더라도 manual prompt 단독 사용이 더 자연스러운 케이스가 있다.

따라서 이번 설계는 “manual prompt가 있으면 무조건 append”가 아니라,
**choice 기반 AI 노드일 때만 append하고, 그 외는 기존 manual-only fallback을 유지**해야 한다.

---

## 3. 제품 결정

### 3.1 choice 기반 AI 노드의 `config.prompt`는 “추가 사용자 지시사항”으로 본다

이번 정책에서 FE의 `직접 프롬프트 작성`은 실행 의미상 아래처럼 해석한다.

- 기존 choice 기반 기본 프롬프트를 유지한다.
- 사용자가 입력한 `config.prompt`를 그 뒤에 추가 지시사항으로 붙인다.

즉 사용자가 AI 노드에서 직접 프롬프트를 작성해도,
choice wizard가 만든 기본 프롬프트는 사라지지 않는다.

### 3.2 사용자 프롬프트는 기본 프롬프트 뒤에 붙는다

최종 prompt는 아래 순서로 조립한다.

1. `base_prompt`
2. `data_type_prompt`
3. `action_prompt`
4. `choiceSelections` 기반 modifier
5. `config.prompt` 기반 사용자 추가 지시사항

권장 문구 형식:

```text
사용자 추가 프롬프트:
{config.prompt}
```

또는

```text
추가 사용자 요청:
{config.prompt}
```

중요한 점은 “그냥 문자열을 이어 붙이는 것”보다
**LLM이 이 블록을 추가 지시사항으로 이해할 수 있게 명시적인 prefix를 두는 것**이다.

### 3.3 append 정책은 choice 기반 AI 노드에서만 적용한다

아래 조건을 모두 만족할 때만 append 정책을 적용한다.

- node type이 prompt 대상 노드 (`AI`, `AI_FILTER`)
- `choiceActionId`가 존재한다
- `dataType`이 resolve 가능하다
- 해당 `dataType + choiceActionId`에 대한 action prompt rule이 존재한다
- `config.prompt`가 비어 있지 않다

이 조건을 만족하면:

- `prompt_source = choice_rule_augmented`
- `prompt = choice prompt + manual prompt suffix`

조건을 만족하지 않으면:

- 기존처럼 `manual` fallback 유지
- `prompt_source = manual`

### 3.4 기존 `choiceSelections`의 custom 입력은 그대로 유지한다

wizard follow-up의 `text_input`은 현재도 `choiceSelections` 안에서 modifier로 붙는다.

이번 정책에서도 이 동작은 바꾸지 않는다.

즉 append 순서는 아래다.

1. choice base/dataType/action prompt
2. follow-up modifier
3. FE 개인 프롬프트(`config.prompt`)

따라서 제품 의미는 아래처럼 정리된다.

- wizard 직접 입력: 기존 선택 흐름 안의 보조 지시
- 개인 프롬프트: 노드 전체에 대한 추가 사용자 지시

### 3.5 manual-only legacy 노드는 그대로 유지한다

아래 같은 경우에는 `config.prompt`를 기존처럼 단독 사용한다.

- `choiceActionId`가 없음
- `dataType`이 없음
- 해당 조합의 prompt rule이 없음
- choice 기반이 아닌 legacy/manual AI 노드

이 규칙은 아래를 보호한다.

- 기존 template seeder prompt
- choice wizard를 거치지 않는 수동 AI 노드
- 특정 계약에 의존하는 legacy runtime

---

## 4. Spring 실행 계약

### 4.1 `ChoicePromptResolver.resolve()` 동작 규칙

새 동작은 아래 우선순위를 따른다.

1. prompt 대상 노드가 아니면 `Map.of()`
2. `choiceActionId` 없음
   - `config.prompt` 있으면 `manual`
   - 없으면 기존 예외/빈값 처리
3. `choiceActionId` 있음 + choice prompt rule resolve 가능
   - `config.prompt` 없음 -> `choice_rule`
   - `config.prompt` 있음 -> `choice_rule_augmented`
4. choice rule resolve 불가
   - `config.prompt` 있으면 `manual`
   - 없으면 기존 예외

### 4.2 `prompt_source` 값

이번 변경으로 `prompt_source`는 아래 셋을 공식 지원한다.

- `choice_rule`
- `choice_rule_augmented`
- `manual`

의미:

- `choice_rule`: choice 기반 자동 프롬프트만 사용
- `choice_rule_augmented`: choice 기반 자동 프롬프트 + 사용자 프롬프트 추가
- `manual`: legacy/manual prompt 단독 사용

### 4.3 runtime payload 예시

#### A. 추천 설정 사용

```json
{
  "action": "process",
  "prompt": "base...\n\ndataType...\n\naction...\n\nmodifier...",
  "prompt_source": "choice_rule"
}
```

#### B. 개인 프롬프트 설정됨

```json
{
  "action": "process",
  "prompt": "base...\n\ndataType...\n\naction...\n\nmodifier...\n\n추가 사용자 요청:\n보고용 문체로 정리하고, 마지막에 한 줄 결론을 추가해줘.",
  "prompt_source": "choice_rule_augmented"
}
```

#### C. legacy manual-only node

```json
{
  "action": "process",
  "prompt": "직접 작성한 템플릿 프롬프트",
  "prompt_source": "manual"
}
```

---

## 5. 구현 방향

### 5.1 `ChoicePromptResolver`

핵심 변경점:

- `resolveManualPrompt()`를 unconditional fast-path로 두지 않는다.
- 먼저 choice rule 조합 가능 여부를 판단한다.
- 조합 가능하면 `buildChoicePromptParts(...)`를 만든 뒤
  `config.prompt`가 있을 때 suffix를 append한다.

권장 helper 분리:

- `resolveChoicePromptParts(...)`
- `appendManualPromptSuffix(...)`
- `canBuildChoicePrompt(...)`
- `resolveManualOnlyPrompt(...)`

### 5.2 `WorkflowTranslator`

translator는 현재도 `ChoicePromptResolver.resolve(...)` 결과를 runtime config에 merge한다.

이번 변경에서는 translator 자체 계약은 바꾸지 않아도 된다.

단, 테스트에서 아래를 새로 보장해야 한다.

- `prompt_source=choice_rule_augmented`가 runtime에 그대로 반영된다.
- `prompt` 값에 base/action/modifier + 사용자 prompt suffix가 포함된다.

### 5.3 FastAPI

FastAPI 코드 변경은 이번 범위에서 필요 없다.

이유:

- 최종 `prompt` 문자열은 Spring이 만든다.
- FastAPI는 그 문자열을 그대로 `LLMService`에 넘긴다.

---

## 6. FE 전달 메타데이터

### 6.1 왜 FE 추론만으로는 부족한가

현재 FE는 `choiceActionId`, 이전 노드 문맥, 데이터 타입을 보고 "기본 AI 지시 요약"을 추정해서 보여준다.
이 방식은 빠르게 구현하기에는 적합하지만, 아래 한계가 있다.

- 실제 Spring prompt 규칙이 바뀌어도 FE 요약 문구가 자동으로 따라가지 않는다.
- FE가 보여주는 설명과 실제 런타임 prompt 구성이 미세하게 어긋날 수 있다.
- 사용자가 프롬프트 엔지니어링을 하려면 "지금 기본으로 어떤 지시가 이미 들어가는지"를 더 정확히 알아야 한다.

따라서 이번 정책 이후에는 FE가 단순 추론만 하지 않고, Spring이 계산한 **기본 AI 지시 메타데이터**를 함께 받아서 보여주는 구조를 목표로 한다.

### 6.2 메타데이터는 node schema preview 응답으로 전달한다

이번 범위에서 가장 자연스러운 전달 경로는 `GET /workflows/{id}/nodes/{nodeId}/schema-preview` 계열 응답이다.

이유는 아래와 같다.

- FE는 이미 AI 노드 상세 패널 주변에서 node schema preview를 사용하고 있다.
- source preview endpoint는 실행/preview 성격이 강하고 source node 중심이라 AI instruction summary 전달에 적합하지 않다.
- schema preview는 "현재 노드 설정을 설명하는 lightweight metadata"를 싣기에 더 자연스럽다.

따라서 `EnhancedNodePreviewResponse`에 AI prompt metadata 블록을 추가하는 방향으로 설계한다.

권장 필드명:

```json
{
  "aiPrompt": {
    "available": true,
    "mode": "recommended",
    "promptSource": "choice_rule",
    "customPromptMode": "append",
    "choiceActionId": "summarize",
    "basePromptSummary": "이메일 본문을 요약하고 중요 일정과 후속 액션을 정리합니다.",
    "includedInstructions": [
      "핵심 내용 요약",
      "중요 일정 추출",
      "후속 액션 bullet 정리"
    ]
  }
}
```

### 6.3 메타데이터 의미

- `available`
  - 현재 노드에서 서버가 AI prompt guidance를 계산할 수 있는지 여부
- `mode`
  - FE 저장 상태 기준 표현
  - `recommended` 또는 `custom`
- `promptSource`
  - 실행 계약 기준 source
  - `choice_rule`, `choice_rule_augmented`, `manual`
- `customPromptMode`
  - 현재 제품 정책 고정값
  - `append`
- `choiceActionId`
  - FE가 현재 어떤 AI action인지 더 정확히 표시할 수 있게 돕는 보조 정보
- `basePromptSummary`
  - 사용자가 이해할 수 있는 1~2문장 요약
  - 원문 prompt 전체가 아니라 "기본으로 어떤 명령이 들어가는지"를 설명하는 문장
- `includedInstructions`
  - base prompt에 포함된 대표 instruction 목록
  - 너무 세세한 내부 prompt 원문을 노출하지 않고도 프롬프트 엔지니어링 방향을 잡을 수 있게 한다

### 6.4 서버 계산 기준

`basePromptSummary`와 `includedInstructions`는 아래 입력을 기반으로 계산한다.

1. `base_prompt`
2. `data_type_prompt`
3. `action_prompt`
4. `choiceSelections` 기반 modifier

즉 `config.prompt`가 비어 있는 `recommended` 상태에서도,
Spring은 "현재 기본 AI 지시"를 설명하는 메타데이터를 만들 수 있어야 한다.

`config.prompt`가 존재하는 경우에는:

- 최종 `prompt_source`는 `choice_rule_augmented`
- `basePromptSummary`는 여전히 **기본 AI 지시 기준**으로 설명한다
- FE는 별도 문구로 "사용자 프롬프트가 이 지시 뒤에 추가 반영됩니다"를 보여준다

### 6.5 노출하지 않을 것

이번 메타데이터는 FE 가이던스를 위한 것이지, 내부 prompt 원문 전체를 공개하기 위한 것이 아니다.

따라서 아래는 직접 내려주지 않는다.

- `base_prompt` 원문 전체
- `action_prompt` 원문 전체
- modifier 원문 전체를 그대로 이어붙인 텍스트
- 최종 실행 prompt 전문

이유는 아래와 같다.

- 내부 rule 변경 자유도를 유지한다
- 사용자에게 지나치게 길고 구현 디테일이 섞인 텍스트를 보여주지 않는다
- "무슨 식의 명령이 들어가는지"만 알면 프롬프트 보강에는 충분하다

### 6.6 FastAPI는 여전히 변경하지 않는다

이 메타데이터는 FE 안내용이다.

- Spring은 `runtime_config.prompt`를 만들고
- 추가로 schema preview 응답에 `aiPrompt` metadata를 담아준다
- FastAPI는 여전히 최종 `prompt` 문자열만 받아 실행한다

즉 이번 보강에서도 FastAPI 책임은 바뀌지 않는다.

---

## 6. FE 문서와의 정합성

이번 Spring 변경이 들어가면 FE 문서도 아래 정책으로 맞춰야 한다.

- `직접 프롬프트 작성`은 “기본 프롬프트를 덮어쓴다”가 아니다.
- “기본 추천 프롬프트에 추가로 반영된다”가 맞다.
- 상태 문구:
  - `개인 프롬프트 설정됨`
  - `작성한 프롬프트가 기본 AI 지시사항 뒤에 추가로 반영됩니다.`
- helper text:
  - `직접 작성한 프롬프트는 기존 AI 선택 기반 추천 설정 뒤에 추가로 반영됩니다.`
- 기본 안내:
  - `현재 기본 AI 지시가 어떤 방향으로 생성되는지`를 사용자가 이해할 수 있어야 한다.
  - 다만 내부 prompt 원문 전체를 그대로 노출할 필요는 없다.
  - `이메일 핵심 내용과 할 일을 구조화해 정리합니다`
  - `강의자료의 핵심 개념과 학습 포인트를 정리합니다`
    같은 `기본 AI 지시 요약` 수준의 설명이면 충분하다.

즉 FE는 저장 구조를 그대로 유지해도 되지만,
사용자에게 설명하는 실행 의미는 이번 Spring 정책과 일치해야 한다.

---

## 7. 테스트 기준

### 7.1 Spring 단위 테스트

`ChoicePromptResolverTest`

- choice 기반 prompt 생성 시 `choice_rule`
- choice 기반 + `config.prompt` 시 `choice_rule_augmented`
- 최종 prompt에 `사용자 추가 요청:` suffix 포함
- legacy manual-only node는 여전히 `manual`

`WorkflowTranslatorTest`

- augmented prompt_source가 runtime config에 실린다
- prompt 문자열이 조합된 형태로 전달된다

### 7.2 회귀 기준

깨지면 안 되는 것:

- 기존 choice-only AI 노드 실행
- 기존 template/manual AI 노드 실행
- `choiceSelections`의 custom follow-up modifier 반영
- GitHub default prompt fallback

---

## 8. 비범위

이번 설계 범위에 포함하지 않는 것:

- FE의 저장 방식 변경
- FastAPI의 prompt 조립 로직 추가
- multi-input AI 노드 문맥 우선순위
- prompt_source를 이용한 신규 런타임 분기

---

## 9. 결론

이번 변경의 핵심은 FE 개인 프롬프트를 “manual override”가 아니라
**choice 기반 기본 프롬프트에 대한 추가 사용자 지시**로 재정의하는 것이다.

구현 책임은 Spring에 있다.

- FE는 `config.prompt`를 저장한다.
- Spring은 이를 choice prompt 뒤에 안전하게 결합한다.
- FastAPI는 최종 prompt를 그대로 실행한다.

따라서 이번 이슈의 backend 작업 범위는 Spring만 추가로 파면 충분하다.
