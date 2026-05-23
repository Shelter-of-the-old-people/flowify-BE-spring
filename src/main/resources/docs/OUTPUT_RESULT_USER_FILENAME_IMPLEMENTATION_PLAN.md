# 출력 결과 파일명 커스터마이징 구현 계획

> 작성일: 2026-05-23
> 기준 문서: `OUTPUT_RESULT_USER_FILENAME_DESIGN.md`
> 대상 브랜치: `feat#63-output-result-user-fix`
> 목적: 현재 FE/Spring/FastAPI 구현 상태를 기준으로, 사용자 출력 파일명 커스터마이징 요구사항을 실제 실행 결과까지 연결하는 구현 방향을 정리한다.

---

## 1. 결론

현재 상태에서 요구사항을 닫기 위한 핵심 작업은 **FastAPI Google Drive output runtime에서 `filename_template`을 실제 업로드 파일명 생성에 연결하는 것**이다.

FE와 Spring은 이미 상당 부분 준비되어 있다.

- FE는 sink schema field를 자동 렌더링한다.
- optional field가 비어 있으면 config에 저장하지 않는다.
- Spring catalog에는 Google Drive `filename_template`, `file_format`이 이미 있다.
- Spring translator는 end node config를 `runtime_sink.config`로 그대로 전달한다.

따라서 1차 구현은 아래 순서가 가장 안전하다.

1. Spring Boot는 현재 catalog 계약을 유지하되, translator/config 보존 회귀 테스트를 추가한다.
2. FastAPI는 Google Drive sink에서 `filename_template`을 authoritative key로 사용한다.
3. FastAPI는 기존 `config.filename`을 `SPREADSHEET_DATA` backward compatibility fallback으로만 유지한다.
4. `filename_template` 결과는 기존 Drive path resolver에 그대로 넣지 않고, 파일명으로 sanitize한 뒤 업로드한다.
5. FE는 제품 copy와 최소 validation을 보강한다.
6. Gmail 첨부파일명, PDF/DOCX 변환 품질, 중복 파일명 정책 UI는 후속으로 분리한다.

---

## 2. 구현 범위

### 2.1 1차 포함

- Google Drive sink `filename_template` runtime 적용
- Google Drive `TEXT`, `SINGLE_FILE`, `FILE_LIST`, `SPREADSHEET_DATA` 파일명 결정 경로 정렬
- `TEXT`, `SPREADSHEET_DATA`처럼 runtime이 직접 생성하는 결과물 중심의 확장자 보정
- `SINGLE_FILE`, `FILE_LIST`처럼 원본 bytes/mime을 그대로 업로드하는 경로의 원본 확장자 보존
- `filename_template` 미입력 시 기존 기본 파일명 유지
- path separator, `.`/`..`, 빈 결과 등 최소 sanitization
- Spring translator가 `filename_template`, `file_format`을 FastAPI로 전달한다는 테스트
- FE copy, trim 저장, obvious invalid character validation

### 2.2 1차 제외

- Gmail `text_delivery_mode=attachment`일 때 첨부파일명 커스터마이징
- Gmail `body_format=html` MIME 생성 고도화
- Google Drive PDF/DOCX 실제 변환 품질 개선
- FILE_LIST 항목별 고급 이름 규칙 UI
- 중복 파일명 처리 방식을 사용자가 선택하는 UI
- template 변수 자동완성 UI

---

## 3. 서비스별 판단

| 서비스 | 현재 상태 | 이번 작업 판단 |
| --- | --- | --- |
| Google Drive | catalog에는 `filename_template`, `file_format`이 있으나 FastAPI runtime 반영이 불완전 | 1차 핵심 구현 대상 |
| Notion | `title_template` catalog/runtime 반영됨 | copy/test 보강만 필요 |
| Gmail | `subject`, `text_delivery_mode` 일부 runtime 반영됨. `body_format`은 plain 중심 | 파일명 요구사항에서는 후속 |
| Discord | `message_template`, `username`, `avatar_url` runtime 반영됨 | 변경 불필요 |
| Google Sheets | `write_mode`, `range_a1`, `key_column` runtime 반영됨 | 파일명 요구와 직접 관련 없음 |

---

## 4. FastAPI 구현 설계

### 4.1 파일명 결정 우선순위

Google Drive sink의 최종 업로드 파일명은 아래 우선순위로 결정한다.

1. `config.filename_template`이 있으면 template 결과 사용
2. 없으면 input payload의 `filename` 사용
3. 없으면 기존 기본값 사용

`SPREADSHEET_DATA`에서는 기존 `config.filename`을 fallback으로 유지한다.

```python
template = config.get("filename_template")
legacy_filename = config.get("filename")
```

권장 fallback:

- `TEXT`: `output.{file_format}`
- `SINGLE_FILE`: input `filename` 또는 `output`
- `FILE_LIST`: item `filename` 또는 `file_{index}`
- `SPREADSHEET_DATA`: `config.filename`, 없으면 `{sheet_name}.csv`

### 4.2 Template 변수

1차에서는 Notion `title_template`에서 쓰는 변수 집합과 맞춘다.

- `{{date}}`
- `{{filename}}`
- `{{subject}}`
- `{{mime_type}}`
- `{{sheet_name}}`
- `{{source_url}}`

`FILE_LIST`에는 `{{index}}`도 지원하는 것이 좋다. 같은 template 결과가 반복될 수 있기 때문이다.

### 4.3 확장자 정책

FastAPI가 최종 확장자를 보정한다.

- `TEXT`
  - `file_format` 기본값은 `txt`
  - template에 확장자가 없으면 `.{file_format}`을 붙인다.
  - `file_format=original`은 `txt` fallback으로 처리한다.
  - 단, 현재 runtime은 PDF/DOCX 변환을 수행하지 않으므로 `pdf`, `docx`는 변환 완료 기능처럼 다루지 않는다.
- `SINGLE_FILE`
  - `file_format=original` 또는 미입력이면 원본 확장자를 유지한다.
  - template은 원칙적으로 base name을 바꾸는 용도로 사용하고, 실제 변환이 없으면 원본 mime/content와 확장자를 보존한다.
  - `file_format`이 `txt/pdf/docx`라도 실제 변환이 구현되지 않았다면 단순 확장자 변경으로 처리하지 않는다.
- `FILE_LIST`
  - 각 item별 원본 확장자를 기본 유지한다.
  - template이 있으면 item별 base name을 만들고, 확장자는 원본 파일의 확장자를 유지한다.
  - 같은 실행 안에서 template 결과가 중복되면 `_2`, `_3` 같은 suffix를 붙이는 backend 기본 정책을 둔다.
- `SPREADSHEET_DATA`
  - 1차는 CSV 생성이므로 `.csv`를 보장한다.

주의:

- 현재 FastAPI는 `TEXT`에서 `file_format`을 파일명에만 반영하고 실제 PDF/DOCX 변환은 보장하지 않는다.
- 따라서 1차에서 `file_format=pdf/docx`를 완성 기능처럼 표현하지 않고, 변환이 필요한 경우 별도 작업으로 분리한다.

### 4.4 Sanitization

`filename_template` 결과는 사용자 입력이므로 FastAPI에서 반드시 sanitize한다.

최소 정책:

- `/`, `\`는 path separator로 해석하지 않고 `_`로 치환하거나 validation error 처리
- null/control character 제거
- trim 후 빈 문자열이면 fallback 사용
- `.` 또는 `..` 단독 파일명 금지
- 지나치게 긴 파일명은 제한 길이로 자르기

권장 구현은 “사용자 workflow 실패를 줄이기 위해 위험 문자를 `_`로 치환하고, 결과가 비면 fallback”이다.

### 4.5 Google Drive runtime 변경 지점

대상 파일:

- `flowify-BE/app/core/nodes/output_node.py`

현재 Google Drive 경로:

- `SINGLE_FILE`: input `filename` 사용
- `TEXT`: `output.{file_format}` 고정
- `FILE_LIST`: item `filename` 사용
- `SPREADSHEET_DATA`: `config.filename` 또는 `{sheet_name}.csv`

변경 방향:

- `_send_google_drive()`에서 각 data type별로 공통 filename resolver를 사용한다.
- 기존 input filename의 `/`, `\` 기반 하위 폴더 생성 동작은 source path 보존 용도로 유지할 수 있다.
- `filename_template` 결과는 path로 해석하지 않고 sanitize된 파일명으로만 사용한다.
- `_render_filename_template(config, input_data, fallback_filename, index=None)` 추가
- `_sanitize_output_filename(filename)` 추가
- `_ensure_extension(filename, extension)` 추가
- `_deduplicate_output_filename(filename, seen_names)` 추가
- `SPREADSHEET_DATA`는 `filename_template` 우선, `filename` fallback 유지

예상 helper:

```python
def _resolve_output_filename(
    self,
    config: dict,
    input_data: dict,
    fallback_filename: str,
    *,
    index: int | None = None,
    extension: str | None = None,
) -> str:
    template = str(config.get("filename_template") or "").strip()
    if template:
        filename = self._render_output_template(template, input_data, fallback_filename, index)
    else:
        filename = fallback_filename
    filename = self._sanitize_output_filename(filename)
    if extension:
        filename = self._ensure_extension(filename, extension)
    return filename
```

---

## 5. Spring Boot 구현 설계

Spring Boot는 큰 기능 구현이 필요하지 않다. 다만 계약을 테스트로 고정한다.

대상:

- `src/main/resources/catalog/sink_catalog.json`
- `src/main/java/org/github/flowify/execution/service/WorkflowTranslator.java`
- `src/test/java/org/github/flowify/catalog/CatalogServiceTest.java`
- `src/test/java/org/github/flowify/execution/WorkflowTranslatorTest.java`

해야 할 일:

- Google Drive sink catalog가 `filename_template`, `file_format`을 계속 내려주는지 테스트
- `filename_template`, `file_format`이 end node config에 있으면 `runtime_sink.config`에 그대로 보존되는지 테스트
- optional field이므로 `NodeLifecycleService`에서 required field로 취급하지 않는지 테스트
- `WorkflowGenerationResultService`에는 생성 결과 config 보존 테스트가 일부 있으므로, 새 테스트는 catalog/translator/lifecycle 계약의 빈틈을 메우는 수준으로 제한한다.

Spring은 파일명 template을 해석하지 않는다.

---

## 6. FE 구현 설계

FE는 schema 기반 렌더링이 이미 되므로, 1차는 UX copy와 가벼운 입력 정리 중심이다.

대상:

- `flowify-FE/src/features/configure-node/model/sink-field-presentation.ts`
- `flowify-FE/src/features/configure-node/model/sink-node-draft.ts`
- `flowify-FE/src/features/configure-node/ui/panels/SinkNodePanel.tsx`
- `flowify-FE/src/entities/workflow/lib/node-status.ts`

해야 할 일:

- `filename_template`
  - label: `출력 파일명`
  - placeholder: `예: {{filename}}_{{date}}`
  - helpText: `비워두면 기본 파일명을 사용합니다. 확장자는 파일 형식에 맞춰 자동 보정될 수 있습니다.`
- `file_format`
  - label: `파일 형식`
  - helpText: `저장할 파일 형식입니다. original은 원본 형식을 유지합니다.`
- `title_template`
  - label: `출력 제목`
  - placeholder: `예: {{filename}} 결과`
  - helpText: `비워두면 기본 제목을 사용합니다.`
- `filename_template`, `title_template`은 저장 전 trim
- `/`, `\`, null character, 단독 `.`/`..` 정도는 FE에서도 막기
- 빈 값이면 config에 저장하지 않는 기존 정책 유지
- `SinkNodePanel.tsx`에는 model helper와 중복된 legacy normalize/validate 로직이 있으므로, model helper만 고치지 말고 실제 저장 경로도 함께 고친다.
- `node-status.ts`에는 이미 `filename_template`, `file_format`, `title_template` label이 있으므로, copy를 바꿀 때만 수정한다.

FE는 최종 파일명 계산과 확장자 보정을 하지 않는다.

---

## 7. 테스트 계획

### 7.1 Spring Boot

- `CatalogServiceTest`
  - Google Drive sink schema에 `filename_template`, `file_format` 존재 확인
  - 두 필드가 optional임을 확인
- `WorkflowTranslatorTest`
  - Google Drive end node config의 `filename_template`, `file_format`이 `runtime_sink.config`에 보존되는지 확인
- `NodeLifecycleServiceTest`
  - Google Drive sink는 `folder_id`만 필수이고 `filename_template` 누락으로 미설정 처리되지 않는지 확인

### 7.2 FastAPI

- Google Drive `TEXT`
  - template 없음: 기존 `output.txt`
  - template 있음: template 기반 파일명
  - 확장자 없음: `.txt` 보정
- Google Drive `SINGLE_FILE`
  - template 없음: 원본 filename 유지
  - template 있음: template 결과 사용
  - `file_format=original`: 원본 확장자 유지
  - template에 path separator가 있어도 하위 폴더를 만들지 않음
- Google Drive `FILE_LIST`
  - item별 template 적용
  - `{{index}}` 적용
  - 같은 실행 안의 동일 이름 충돌 시 suffix 정책 확인
  - template 없음: 기존 원본 filename의 하위 폴더 생성 동작 유지
- Google Drive `SPREADSHEET_DATA`
  - `filename_template` 우선
  - legacy `filename` fallback
  - 기본 `{sheet_name}.csv`
- invalid filename
  - `/`, `\`, `..`, 빈 결과 처리

### 7.3 FE

- `sink-field-presentation.test.ts`
  - `filename_template`, `file_format`, `title_template` copy 확인
- `sink-node-draft.test.ts`
  - optional empty field 미저장
  - `filename_template` trim 저장
  - obvious invalid filename validation

---

## 8. 구현 순서

1. Spring Boot 테스트 보강
   - 이미 catalog와 translator 경로가 있으므로 회귀 테스트만 먼저 추가한다.
2. FastAPI Google Drive runtime 구현
   - helper를 만들고 `_send_google_drive()`의 네 data type 경로에 적용한다.
3. FastAPI 단위 테스트 추가
   - runtime upload service를 mock/stub해서 업로드 filename을 검증한다.
4. FE copy/validation 보강
   - schema renderer 구조는 유지한다.
5. 통합 테스트
   - FE에서 Google Drive sink에 `filename_template`을 입력하고 저장
   - Spring workflow 저장 payload 확인
   - FastAPI runtime upload filename 확인

---

## 9. 완료 기준

1차 완료 기준:

- 사용자가 Google Drive sink에서 `filename_template`을 입력하면 실제 Google Drive 업로드 파일명에 반영된다.
- 사용자가 입력하지 않으면 기존 기본 파일명 동작이 유지된다.
- `SPREADSHEET_DATA`도 `filename_template`을 우선 사용한다.
- 기존 `config.filename` workflow는 깨지지 않는다.
- `/`, `\`, `..` 등 위험 파일명 입력이 runtime에서 안전하게 처리된다.
- Spring translator는 config를 손실 없이 FastAPI로 넘긴다.
- FE는 사용자가 이해할 수 있는 copy와 최소 validation을 제공한다.

---

## 10. 최종 권장안

이번 요구사항은 Spring catalog field 추가 문제가 아니라, 이미 준비된 `filename_template` 계약을 **FastAPI 실행 결과까지 닫는 작업**으로 보는 것이 맞다.

가장 작은 안전한 구현은 다음과 같다.

- `filename_template`을 Google Drive 파일명 생성의 authoritative key로 확정한다.
- `config.filename`은 `SPREADSHEET_DATA` backward compatibility fallback으로만 유지한다.
- FastAPI가 template render, sanitization, extension handling을 책임진다.
- `filename_template`은 파일명 규칙이지 폴더 경로 규칙이 아니므로 path separator를 폴더 생성으로 해석하지 않는다.
- `file_format`은 변환 기능이 아니므로, 실제 변환 없는 확장자 변경은 최소화한다.
- FE는 표시 copy와 최소 validation만 담당한다.
- Spring은 schema와 runtime config 전달을 테스트로 고정한다.

이렇게 진행하면 기존 workflow 호환성을 유지하면서도 사용자가 기대하는 “결과 파일명 지정”이 실제 저장 결과까지 연결된다.
