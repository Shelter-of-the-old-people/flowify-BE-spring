# Workflow Event Initial Sync Policy Spring Design

> 작성일: 2026-05-26
> 대상: Spring backend
> 범위: 이벤트형 source의 최초 실행 정책 catalog/contract/validation 설계
> 관련 레포: `flowify-FE`, `flowify-BE`

---

## 1. 목적

이 문서는 이벤트형 source의 **최초 실행 정책**을 Spring catalog/contract 관점에서 정의한다.

이번 단계의 최종 정책은 아래와 같다.

- 최초 실행 시 최근 항목 **1건만** 가져온다.
- 이후 실행부터는 **새 항목만** 추적한다.

Spring의 책임은 아래 세 가지다.

- 어떤 source mode가 이 공통 정책 대상인지 catalog의 source-of-truth를 제공
- FE가 실행 전 안내를 그릴 수 있도록 메타데이터를 제공
- FastAPI runtime과 어긋나지 않도록 contract와 validation 방향을 고정

이번 단계는 **사용자 선택형 정책 추가**가 아니라 **고정 정책을 catalog에 명시하는 단계**다.

---

## 2. 범위

## 2.1 직접 대상 source mode

이번 공통 정책의 직접 대상은 아래 mode다.

- `google_drive.folder_new_file`
- `gmail.new_email`
- `canvas_lms.course_new_file`
- `canvas_lms.course_new_announcement`
- `naver_news.new_articles`
- `web_news.seboard_new_posts`
- `web_news.website_feed`

## 2.2 제외 대상

아래 mode는 이미 별도 최초 실행 정책이 있으므로 이번 공통 정책에 포함하지 않는다.

- `github.new_pr`
  - 기존 `backfill_count` 유지
- `google_sheets.new_row`
- `google_sheets.row_updated`
  - 기존 `initial_sync_mode` 유지

## 2.3 pseudo-event mode

아래 mode는 catalog상 event처럼 보이지만, 현재 runtime 모델상 진짜 delta event로 보기 어렵다.
이번 단계에서는 최초 실행 정책 대상에 넣지 않는다.

- `google_drive.file_changed`
- `gmail.sender_email`
- `gmail.attachment_email`

이 셋은 별도 이슈에서 source 모델을 다시 정리해야 한다.

---

## 3. Spring 최종 결정

## 3.1 사용자는 이번 정책을 고르지 않는다

이번 단계에서 Spring contract는 아래를 전제로 한다.

- workflow config에 새로운 `initial_sync_mode` 입력값을 추가하지 않는다.
- source mode가 공통 정책 대상인지 여부만 catalog metadata로 노출한다.
- FE는 그 metadata를 보고 실행 전 안내만 보여준다.
- 실제 동작은 FastAPI runtime이 service/mode 기준으로 고정 처리한다.

즉 GitHub와 Google Sheets처럼 별도 입력 필드를 늘리는 방향이 아니다.

## 3.2 FE의 주 안내 위치는 실행 바 상단이다

이번 단계에서 Spring이 지원해야 하는 UX는 **실행 바 상단 안내**다.

즉 FE는 아래 상황에서 실행 직전 안내를 띄운다.

- 이벤트형 source 포함
- 아직 첫 실행 전
- 실행 blocker 없음

이때 보여줄 문구는 공통 문구다.

- `최초 실행 시 최근 항목 1건만 가져옵니다.`
- `이후 실행부터는 새 항목만 추적합니다.`

Spring은 이 UX를 위해 source mode가 공통 정책 대상이라는 사실을 catalog에 명시해야 한다.

---

## 4. Catalog 계약

## 4.1 공통 메타데이터

이번 단계에서 직접 대상 source mode는 `target_schema`에 아래 메타데이터를 추가한다.

```json
{
  "initial_sync_policy": "emit_latest_one"
}
```

위 값의 의미는 아래와 같다.

- 이 source mode는 최초 실행 시 최근 항목 1건만 가져온다.
- 이후 실행부터는 새 항목만 추적한다.
- FE는 실행 전 안내 대상임을 판정할 수 있다.

이번 단계에서는 이 값 하나로 충분하다.

## 4.2 위치

메타데이터 위치는 현재 구조와의 호환성을 위해 `target_schema` 내부를 권장한다.

예:

```json
{
  "key": "folder_new_file",
  "trigger_kind": "event",
  "target_schema": {
    "type": "folder_picker",
    "multiple": false,
    "picker_supported": true,
    "initial_sync_policy": "emit_latest_one"
  }
}
```

이 방식을 쓰면 아래가 간단해진다.

- source catalog JSON 수정 범위가 작다
- FE가 기존 target schema 파싱 흐름 안에서 같이 읽기 쉽다
- source mode별 정책 부착이 직관적이다

## 4.3 이번 단계에서 추가하지 않는 것

이번 단계에서는 아래 필드는 넣지 않는다.

- 사용자 편집용 `initial_sync_mode`
- `emit_existing`
- `skip_existing`
- `emit_latest_count`

이유:

- 이번 단계의 정책은 고정값이다
- 입력 옵션까지 열면 GitHub/Sheets와 나머지 source의 의미 차이가 커져 복잡도가 급증한다

---

## 5. mode별 catalog 반영 대상

## 5.1 메타데이터 추가 대상

아래 mode에는 `initial_sync_policy = emit_latest_one`을 넣는다.

- `google_drive.folder_new_file`
- `gmail.new_email`
- `canvas_lms.course_new_file`
- `canvas_lms.course_new_announcement`
- `naver_news.new_articles`
- `web_news.seboard_new_posts`
- `web_news.website_feed`

## 5.2 메타데이터 추가 제외 대상

아래는 제외한다.

- `github.new_pr`
- `google_sheets.new_row`
- `google_sheets.row_updated`
- `google_drive.file_changed`
- `gmail.sender_email`
- `gmail.attachment_email`

즉 FE가 실행 바 안내를 띄울 때도 위 목록을 기준으로만 판단해야 한다.

---

## 6. validation/generation 원칙

## 6.1 validation

이번 단계에서 Spring validation은 아래 방향이면 충분하다.

- `initial_sync_policy` 값은 catalog 상수로만 관리
- workflow 저장 시 사용자 입력값 검증 대상은 아님
- source mode가 공통 정책 대상인지 여부를 generation 단계에서 안정적으로 유지

즉 validation의 핵심은 “사용자가 잘못 입력하지 못하게 막는 것”이 아니라 “catalog와 runtime 계약이 흔들리지 않게 하는 것”이다.

## 6.2 generation

Node lifecycle / source generation 단계에서는 아래가 보장되어야 한다.

- FE가 읽은 source mode와 backend runtime의 service/mode가 동일하다
- 공통 정책 대상 mode는 항상 같은 metadata를 가진다
- template/workflow 생성 시 별도 초기 정책 config를 억지로 넣지 않는다

이번 단계의 정책은 runtime source config가 아니라 **source mode 계약**이다.

---

## 7. FE와의 연결 규칙

FE는 아래 순서로 최초 실행 안내 여부를 판단한다.

1. source mode metadata에서 `initial_sync_policy` 읽기
2. 해당 워크플로우에 대상 source node가 있는지 계산
3. 실행 이력이 없는지 확인
4. 실행 blocker가 없으면 실행 바 상단에 안내 노출

따라서 Spring은 아래를 보장해야 한다.

- catalog API가 `initial_sync_policy`를 FE에 내려준다
- source mode별 정책이 브랜치/배포마다 흔들리지 않는다

---

## 8. 테스트 기준

Spring 관점에서 확인해야 할 것은 아래다.

1. 대상 mode에만 `initial_sync_policy = emit_latest_one`이 들어가는지
2. 제외 대상 mode에는 이 메타데이터가 없는지
3. FE가 catalog 응답에서 이 값을 안정적으로 읽을 수 있는지
4. workflow 저장/수정 시 이 정책이 사용자 입력값처럼 섞이지 않는지

---

## 9. 이번 단계의 결론

Spring의 최종 설계는 아래 한 문장으로 요약된다.

`이번 최초 실행 정책은 사용자 선택 옵션이 아니라 source mode 계약이며, Spring은 그 사실을 target_schema metadata로 FE와 runtime에 일관되게 전달한다.`
