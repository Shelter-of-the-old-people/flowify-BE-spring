# GitHub Node Spring Design

> 작성일: 2026-05-25
> 대상: Spring backend
> 범위: GitHub 노드 개선을 위한 catalog, picker, config contract, validation 설계
> 관련 레포: `flowify-FE`, `flowify-BE`

---

## 1. 목적

이 문서는 GitHub 노드 개선 이슈에서 Spring backend가 책임져야 하는 catalog, picker, generation contract, validation 정책을 정의한다.

이번 이슈의 핵심은 GitHub source를 단순 1차 MVP가 아니라
실제 운영 가능한 source로 끌어올리는 것이다.

Spring은 아래 책임을 가진다.

- GitHub source catalog를 개선된 제품 정책에 맞게 갱신한다.
- GitHub 저장소 목록 picker API를 제공한다.
- GitHub source config 계약을 FE / FastAPI와 동일하게 정렬한다.
- 첫 실행 정책과 필터 정책을 workflow config 계약으로 고정한다.
- GitHub repo 입력 정규화 규칙을 Spring validation에도 반영한다.

즉 Spring의 목표는
"GitHub source 설정의 단일 계약(source of truth)을 제공하는 것"
이다.

---

## 2. 제품 정책

이번 이슈에서 Spring이 기준으로 삼아야 하는 제품 정책은 아래와 같다.

### 2.1 `new_pr` 의미

- `new_pr`는 **새로 생성된 PR을 polling 기반으로 감지하는 source**다.
- 현재 open 상태인 PR만 보는 source가 아니다.
- PR이 생성된 뒤 닫혔더라도, 아직 감지되지 않았다면 emit 대상이 될 수 있다.

즉 Spring catalog / helper text / generation contract도
"새로 생성된 PR 감지" 의미를 기준으로 설명해야 한다.

### 2.2 첫 실행 정책

첫 실행 정책은 source config에 명시적으로 들어간다.

허용 값:

- `0`: 지금부터 추적
- `1~20`: 첫 실행 시 최근 생성 PR 백필 개수

기본값:

- `5`

Spring은 이 값을 generation contract와 validation에서 동일하게 보장해야 한다.

### 2.3 저장소 입력 방식

GitHub source target은 두 경로를 지원한다.

- repo picker
- 수동 입력 fallback

수동 입력에서는 아래 형식을 모두 허용한다.

- `owner/repo`
- `https://github.com/owner/repo`
- `https://github.com/owner/repo/pulls`

하지만 내부 저장값은 항상 `owner/repo` canonical form이어야 한다.

### 2.4 필터 범위

이번 이슈에서 지원하는 GitHub source 필터는 아래와 같다.

- base branch
- label
- author
- draft 포함 여부

Spring은 이 필드들을 source config 계약, generation policy, validation에서 모두 공식 필드로 취급해야 한다.

---

## 3. 현재 상태와 문제

### 3.1 source catalog가 아직 text input 중심이다

현재 GitHub source catalog는 사실상 `owner/repo` 직접 입력만 전제한다.

문제:

- 저장소 목록 picker 계약이 없다.
- GitHub URL을 직접 입력할 때의 정규화 정책이 없다.
- FE와 FastAPI가 같은 정규화 규칙을 공유하지 못한다.

### 3.2 `new_pr` 의미가 현재 구현/문서와 어긋난다

기존 문서와 helper text는
"새 PR 감지"라고 서술하면서도,
세부 계약은 open PR polling MVP에 가깝다.

이번 이슈에서는 Spring도 아래 의미를 기준으로 다시 고정해야 한다.

- `new_pr = 새로 생성된 PR 감지`

### 3.3 GitHub target option provider가 없다

Spring에는 이미 source target option provider 구조가 있지만,
GitHub는 provider가 비어 있다.

즉 현재 FE는 Gmail / Drive / Sheets처럼 목록 선택형 UX를 GitHub에 붙일 수 없다.

---

## 4. 최종 범위

## 4.1 GitHub source catalog 개선

Spring은 `github:new_pr` source를 유지하되,
catalog 설명과 target schema를 개선된 정책에 맞게 바꾼다.

유지되는 것:

- service key: `github`
- source mode: `new_pr`
- canonical input type: `API_RESPONSE`
- trigger kind: `event`

바뀌는 것:

- target schema를 picker 친화적으로 확장
- helper text를 새 정책 기준으로 수정
- 첫 실행 정책 / 필터 config를 공식 field로 정의

## 4.2 GitHub repo picker 추가

Spring은 GitHub target option provider를 추가한다.

이 provider는 사용자 토큰으로 GitHub API를 호출해
사용자가 접근 가능한 저장소 목록을 반환한다.

반환 item 권장 형태:

- `id`: `owner/repo`
- `label`: `owner/repo`
- `description`: `private/public`, owner type, default branch
- `metadata`:
  - `owner`
  - `repo`
  - `visibility`
  - `defaultBranch`
  - `ownerType`

### 4.2.1 검색 / 페이징

기존 target option API 계약을 그대로 사용한다.

- `query`: repo 검색
- `cursor`: 다음 페이지

즉 GitHub도 기존 remote picker 인프라에 자연스럽게 편입된다.

## 4.3 수동 입력 정규화

picker를 쓰지 않는 경우에도 수동 입력을 허용한다.

Spring validation 정책:

- `owner/repo` 허용
- GitHub URL 허용
- 최종적으로 `owner/repo`로 정규화
- 정규화 실패 시 invalid

즉 Spring은 FE처럼 입력을 넓게 받고,
런타임 계약은 canonical form으로 좁히는 역할을 해야 한다.

## 4.4 첫 실행 정책 계약 추가

source config에 아래 필드를 공식 추가한다.

- `backfill_count`

허용 값:

- `0`
- `5`
- `10`

기본값:

- `5`

이 필드는 FastAPI runtime이 첫 실행 bootstrap/backfill 정책을 정할 때 직접 사용한다.

## 4.5 필터 계약 추가

source config에 아래 필드를 공식 추가한다.

- `base_branch`
- `labels`
- `authors`
- `include_drafts`

권장 타입:

- `base_branch`: string
- `labels`: string[]
- `authors`: string[]
- `include_drafts`: boolean

Spring은 generation / validation 단계에서 이 필드들이 schema 밖으로 밀려나지 않도록 보장해야 한다.

---

## 5. Catalog 방향

### 5.1 source mode 문구

`new_pr`의 label / helper text는 아래 의미를 반영해야 한다.

- `새로 생성된 PR을 감지합니다`
- `첫 실행 시 최근 PR을 일부 함께 처리할 수 있습니다`
- `필요한 저장소는 목록에서 고르거나 직접 입력할 수 있습니다`

즉 catalog 문구는 open 상태 polling source처럼 읽히면 안 된다.

### 5.2 target schema 방향

권장 target schema 방향:

- schema type: picker 친화 타입 사용
- 또는 기존 text input을 유지하되, `picker_supported`와 `targetValuePolicy`를 함께 명시

중요한 점은 FE가 GitHub를 remote picker 대상으로 인식할 수 있어야 한다는 것이다.

즉 Spring은 아래 둘 중 하나를 고정해야 한다.

1. 새 schema type 추가
   - 예: `repo_picker`
2. 기존 schema type + meta flag
   - 예: `text_input` + `picker_supported=true` + `targetValuePolicy=github_repo`

이번 이슈에서는 기존 target option 인프라 재사용을 위해
**기존 schema 계약을 크게 깨지 않는 방향**이 더 안전하다.

### 5.3 helper text 방향

권장 helper text 의미:

- `내 저장소 목록에서 선택하거나 owner/repo 형식으로 직접 입력할 수 있습니다. GitHub URL을 붙여넣어도 자동 정규화됩니다.`

즉 사용자가 picker와 manual fallback 둘 다 이해할 수 있어야 한다.

---

## 6. Picker 계약

### 6.1 API

기존 endpoint를 그대로 사용한다.

- `GET /catalog/sources/{serviceKey}/target-options?mode=new_pr&query=...&cursor=...`

즉 GitHub 전용 새 endpoint를 만들 필요는 없다.

### 6.2 provider 선택

Spring은 `TargetOptionProvider` 구현으로 `github`를 추가한다.

책임:

- 사용자 GitHub token 복호화
- GitHub 저장소 목록 조회
- query 기반 필터링
- pagination cursor 처리
- FE picker가 바로 쓸 수 있는 item 변환

### 6.3 토큰 전제

GitHub는 manual token 서비스이므로,
provider는 OAuthTokenService에서 `github` 토큰을 가져와 API 호출에 사용한다.

토큰이 없거나 유효하지 않으면
FE가 source를 설정하는 단계에서 적절한 오류를 받을 수 있어야 한다.

---

## 7. Workflow Generation 계약

### 7.1 source config policy

Workflow generation policy는 GitHub source에 대해 아래를 명시해야 한다.

- target value policy: `github_repo`
- backfill count 허용 값
- 허용 filter field

즉 AI workflow generation도 FE 수동 설정과 같은 config contract를 따라야 한다.

### 7.2 AI generation 입력 정규화

LLM이 GitHub repo를 아래처럼 만들 수 있다.

- `openai/openai-python`
- `https://github.com/openai/openai-python`

Spring generation 경로도 결국 canonical target은 `owner/repo`로 맞춰야 한다.

즉 수동 설정, AI generation, runtime execution이 모두 같은 canonical value를 보게 만들어야 한다.

---

## 8. Validation 방향

### 8.1 target 검증

Spring validator와 lifecycle configured 판정은
기존의 strict `owner/repo only`에서 한 단계 확장된다.

검증 정책:

- 비어 있으면 invalid
- `owner/repo`면 valid
- GitHub URL이면 valid after normalization
- 그 외 값은 invalid

### 8.2 first-run / filter 필드 검증

검증 정책:

- `backfill_count`는 `0` 또는 `1~20`만 허용
- `include_drafts`는 boolean만 허용
- `labels`, `authors`는 문자열 배열만 허용
- `base_branch`는 비어 있지 않은 문자열일 때만 의미 있는 값으로 취급

### 8.3 configured 판정

GitHub source는 아래를 만족해야 configured다.

- 토큰 연결됨
- target 정규화 가능
- source mode 유효
- 필터 필드가 허용 형식

즉 picker로 선택했든 수동 입력했든
configured 판정 기준은 동일해야 한다.

---

## 9. FE / FastAPI와의 정합성

### 9.1 FE와의 정합성

FE는 Spring catalog와 picker 계약을 그대로 사용한다.

따라서 Spring이 아래를 안정적으로 제공해야 한다.

- GitHub source schema
- GitHub target option API
- helper text
- backfill / filter field schema

### 9.2 FastAPI와의 정합성

FastAPI는 최종적으로 `owner/repo` canonical target과
공식 filter/backfill field를 사용한다.

즉 Spring이 아래를 고정해야 한다.

- `target`
- `backfill_count`
- `base_branch`
- `labels`
- `authors`
- `include_drafts`

이 값들이 execution translation에서 그대로 runtime config로 넘어갈 수 있어야 한다.

---

## 10. 테스트 기준

### 10.1 Catalog / provider 테스트

- GitHub source catalog가 개선된 schema를 제공한다.
- GitHub target option provider가 repo 목록을 반환한다.
- query / cursor가 정상 작동한다.

### 10.2 Validation 테스트

- `owner/repo` valid
- GitHub URL valid after normalization
- invalid string reject
- `backfill_count=0` valid
- `backfill_count=7` valid
- `backfill_count=21` invalid
- 그 외 값 reject
- labels/authors/include_drafts 타입 검증

### 10.3 Generation 테스트

- AI generation이 GitHub URL target을 넣어도 canonical target으로 정리된다.
- GitHub source config에 backfill/filter field가 유지된다.

---

## 11. 완료 기준

- GitHub source가 picker 기반 target option API를 가진다.
- 수동 입력은 `owner/repo`와 GitHub URL 모두 허용된다.
- Spring validation은 최종적으로 `owner/repo` canonical target을 보장한다.
- source config에 `backfill_count`, `base_branch`, `labels`, `authors`, `include_drafts`가 공식 포함된다.
- FE와 FastAPI가 같은 계약을 사용한다.

---

## 12. 비범위

이번 문서 범위에 포함하지 않는 것은 아래와 같다.

- GitHub sink/action
- GitHub event type 추가
  - merged
  - review requested
  - issue
  - release
- GitHub webhook lifecycle

즉 이번 Spring 범위는
"GitHub source 개선을 위한 picker/계약/validation 정비"
에 집중한다.

---

## 13. 상세 계약 부록

### 13.1 source config canonical shape

Spring이 최종적으로 보장해야 하는 GitHub source config shape 예시는 아래와 같다.

```json
{
  "service": "github",
  "source_mode": "new_pr",
  "target": "openai/openai-python",
  "target_meta": {
    "selectionSource": "picker",
    "owner": "openai",
    "repo": "openai-python",
    "visibility": "public",
    "defaultBranch": "main",
    "ownerType": "org"
  },
  "backfill_count": 5,
  "base_branch": "main",
  "labels": ["release", "hotfix"],
  "authors": ["alice", "bob"],
  "include_drafts": false
}
```

중요한 점:

- `target`은 항상 canonical `owner/repo`
- `target_meta`는 display/helper 용도
- runtime 필수 판단은 `target` 기준

### 13.2 picker API 응답 예시

권장 target option response 예:

```json
{
  "items": [
    {
      "id": "openai/openai-python",
      "label": "openai/openai-python",
      "description": "public · org · default: main",
      "type": "repository",
      "metadata": {
        "owner": "openai",
        "repo": "openai-python",
        "visibility": "public",
        "defaultBranch": "main",
        "ownerType": "org"
      }
    }
  ],
  "nextCursor": null
}
```

### 13.3 validation 세부 규칙

#### target

- `owner/repo`면 valid
- GitHub URL이면 valid after normalization
- 그 외 문자열은 invalid

#### backfill_count

- `0`, `5`, `10`만 valid
- 누락 시 default `5`

#### filters

- `base_branch`: 비어 있지 않은 문자열만 의미 있는 값으로 인정
- `labels`: 비어 있지 않은 문자열 배열
- `authors`: 비어 있지 않은 문자열 배열
- `include_drafts`: boolean

### 13.4 generation policy가 지켜야 하는 것

AI workflow generation도 수동 설정과 같은 계약을 따라야 한다.

즉 GitHub 관련 generation policy는 아래를 만족해야 한다.

- GitHub URL을 넣으면 canonical `owner/repo`로 정규화
- `backfill_count`는 허용 값만 사용
- `labels`, `authors`는 배열로 유지
- `include_drafts`는 boolean만 허용

### 13.5 호환성 정책

기존 `owner/repo` 기반 GitHub source workflow는 그대로 유효해야 한다.

즉 이번 이슈는 아래 성격이어야 한다.

- 기존 workflow: 계속 실행 가능
- 새 workflow: picker + manual fallback 사용 가능
- 기존 수동 입력: URL까지 허용 범위 확대

이번 변경은 breaking removal이 아니라
GitHub source contract 확장으로 보는 편이 맞다.
