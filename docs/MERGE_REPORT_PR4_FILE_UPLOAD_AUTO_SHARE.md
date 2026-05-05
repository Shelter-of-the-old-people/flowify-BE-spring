# 병합 보고서: feat/file-upload-auto-share-backend → main

> **작성일:** 2026-05-04
> **PR:** #4 — 파일 업로드 자동 공유 시스템 템플릿 및 sink picker 지원 추가
> **병합 방식:** 로컬 충돌 해결 후 main에 직접 push
> **병합 순서:** PR #5 → PR #6 → **PR #4** (마지막)

---

## 1. 병합 요약

PR #4(`feat/file-upload-auto-share-backend`)의 모든 코드가 main에 반영되었으며, PR #5(메일 요약 전달)와 PR #6(폴더 문서 요약)의 변경사항이 함께 병합되면서 아래와 같은 차이가 발생합니다.

---

## 2. PR #4 코드가 main에 100% 반영된 파일

PR #4에서 새로 추가한 파일 중 main에 동일하게 존재하는 파일입니다.

| 파일 | 설명 | 상태 |
|------|------|------|
| `catalog/service/picker/SinkTargetOptionProvider.java` | Sink picker 인터페이스 | 동일 (PR #5와 동일 코드) |
| `catalog/service/picker/SinkTargetOptionService.java` | Sink picker 라우팅 서비스 | 동일 |
| `catalog/service/picker/SlackSinkTargetOptionProvider.java` | Slack 채널 목록 조회 | 동일 |
| `catalog/service/picker/NotionSinkTargetOptionProvider.java` | Notion 페이지 목록 조회 | 동일 |
| `config/WebClientConfig.java` | slackWebClient, notionWebClient 빈 추가 | 동일 |
| `test/.../SinkTargetOptionServiceTest.java` | Sink picker 단위 테스트 | 동일 |
| `docs/FILE_UPLOAD_AUTO_SHARE_TEMPLATE_BACKEND_REQUEST.md` | FE 요청서 문서 | 동일 |
| **TemplateSeeder — 업로드 템플릿 3종 메서드** | `buildDriveUploadSlackTemplate()`, `buildDriveUploadGmailTemplate()`, `buildDriveUploadNotionTemplate()` | **동일** (내부 코드 변경 없음) |

---

## 3. main이 PR #4와 다른 부분 (PR #5, #6에서 추가)

### 3.1 TemplateSeeder.java — 시더 로직 구조 변경

**PR #4 원본:**
```java
// 기존 loop 방식 유지
List<Template> templates = List.of(
    buildStudyNoteTemplate(),
    ...
    buildDriveUploadSlackTemplate(),
    buildDriveUploadGmailTemplate(),
    buildDriveUploadNotionTemplate()
);

for (Template seedTemplate : templates) {
    var existing = templateRepository.findByNameAndIsSystem(seedTemplate.getName(), true);
    if (existing.isPresent()) { ... updated++; }
    else { ... created++; }
}
```

**현재 main:**
```java
// upsert 메서드 + legacyNames 지원 방식 (PR #5에서 도입)
if (upsertTemplate(buildStudyNoteTemplate())) { updated++; } else { created++; }
if (upsertTemplate(buildMeetingMinutesTemplate())) { updated++; } else { created++; }
...
// 메일 템플릿은 legacy name 지원
if (upsertTemplate(buildUnreadMailSlackTemplate(),
        "읽지 않은 메일 요약 후 Slack 공유")) { ... }
...
// PR #4의 업로드 템플릿도 동일 패턴으로 등록
if (upsertTemplate(buildDriveUploadSlackTemplate())) { updated++; } else { created++; }
if (upsertTemplate(buildDriveUploadGmailTemplate())) { updated++; } else { created++; }
if (upsertTemplate(buildDriveUploadNotionTemplate())) { updated++; } else { created++; }
```

**차이점:**
- `upsertTemplate(Template, String... legacyNames)` 메서드 신규 추가
- `findExistingSystemTemplate(String name, String... legacyNames)` 메서드 신규 추가
- 레거시 이름으로도 기존 DB 문서를 찾아 id/useCount/createdAt 보존 가능
- PR #4의 업로드 템플릿 3종은 legacy name이 없어 `upsertTemplate(template)` 형태로 호출

**영향:** PR #4의 템플릿 등록 로직은 동일하게 작동합니다. 이름 기준 upsert이며 기존 데이터를 보존합니다. 추가된 것은 메일 템플릿의 이름 변경 시 레거시 이름 마이그레이션 지원뿐입니다.

---

### 3.2 TemplateSeeder.java — 메일 템플릿 3종 데이터 모델 변경 (PR #5)

PR #4 브랜치에는 기존 메일 템플릿이 원본 그대로인 반면, main에서는 PR #5가 다음을 변경했습니다:

| 항목 | PR #4 브랜치 (변경 없음) | main (PR #5 반영) |
|------|------------------------|-------------------|
| 템플릿 이름 | `읽지 않은 메일 요약 후 Slack 공유` | `읽지 않은 메일 목록 요약 후 Slack 공유` |
| 템플릿 이름 | `중요 메일 요약 후 Notion 저장` | `중요 메일 목록 요약 후 Notion 저장` |
| 템플릿 이름 | `중요 메일 할 일 추출 후 Notion 저장` | `중요 메일 목록에서 할 일 추출 후 Notion 저장` |
| Loop outputDataType | `SINGLE_EMAIL` | `EMAIL_LIST` |
| LLM dataType | `SINGLE_EMAIL` | `EMAIL_LIST` |
| LLM prompt | 개별 메일 요약 | 메일 **목록** 일괄 요약 |
| LLM config 추가 | — | `summaryFormat`, `resultMode: "single_aggregated"` |
| Gmail config 추가 | — | `maxResults: 100` |
| Slack config 추가 | — | `channel: ""` |
| Notion config 추가 | — | `target_type: "page"`, `target_id: ""` |
| 설명 문구 | "하나씩 요약" | "목록을 정해진 형식으로 요약" |

**핵심 변경:** 메일 처리 방식이 "메일 하나씩 순차 요약" → "메일 목록 일괄 집계 요약"으로 변경됨.

---

### 3.3 TemplateSeeder.java — 폴더 문서 요약 템플릿 3종 추가 (PR #6)

PR #4에는 없고 main에만 존재하는 신규 템플릿:

| 메서드 | 템플릿 이름 | 구조 |
|--------|-----------|------|
| `buildFolderDocumentSlackTemplate()` | 신규 문서 요약 후 Slack 공유 | google_drive → llm → slack |
| `buildFolderDocumentGmailTemplate()` | 신규 문서 요약 후 Gmail 전달 | google_drive → llm → gmail |
| `buildFolderDocumentSheetsTemplate()` | 문서 요약 결과를 Google Sheets에 저장 | google_drive → llm → google_sheets |
| `buildFolderDocumentSourceNode()` | (공통 헬퍼) source_mode=folder_new_file | — |

카테고리: `folder_document_summary`, dataType: `SINGLE_FILE`

---

### 3.4 OAuthTokenService.java — Token Alias 전략 추가 (PR #6)

PR #4에는 없고 main에만 존재:

```java
private static final Map<String, String> TOKEN_SERVICE_ALIASES = Map.of(
    "google_sheets", "google_drive"
);

private String resolveTokenLookupService(String service) {
    return TOKEN_SERVICE_ALIASES.getOrDefault(service, service);
}
```

`getDecryptedToken()` 호출 시 `google_sheets` 서비스 키로 요청하면 `google_drive` 토큰을 자동으로 조회합니다. Google Sheets 템플릿이 별도 OAuth 없이 Google Drive 토큰을 재사용할 수 있게 합니다.

---

### 3.5 application.yml — Google Drive OAuth scope 확장 (PR #6)

| 항목 | PR #4 브랜치 | main |
|------|-------------|------|
| google-drive scopes | `drive.readonly` `drive.file` | `drive.readonly` `drive.file` **`spreadsheets`** |
| gmail scopes | `gmail.readonly` `gmail.send` | `gmail.readonly` `gmail.send` |

차이: `https://www.googleapis.com/auth/spreadsheets` scope가 main에 추가됨. Google Sheets 템플릿의 쓰기 권한에 필요.

---

### 3.6 CatalogController.java — Swagger 설명 미세 변경

| 위치 | PR #4 브랜치 | main |
|------|-------------|------|
| getSinkCatalog() description | "수용 가능한 input type" | "허용 가능한 input type" |

기능 차이 없음, 문구만 변경.

---

### 3.7 문서 파일 추가 (PR #5, #6)

main에만 존재하는 문서:

| 파일 | 출처 |
|------|------|
| `docs/TEMP_MAIL_SUMMARY_TEMPLATE_BACKEND_REQUEST.md` | PR #5 |
| `docs/FOLDER_DOCUMENT_SUMMARY_TEMPLATE_BACKEND_REQUEST.md` | PR #6 |
| `docs/SPRING_IMPLEMENTATION_STATUS.md` | main 직접 작성 |

---

## 4. 차이 요약 매트릭스

| 파일 | PR #4 코드 반영 | PR #5/6 추가 변경 | 동작 영향 |
|------|:-:|:-:|:-:|
| TemplateSeeder — 업로드 템플릿 3종 | **동일** | — | 없음 |
| TemplateSeeder — run() 구조 | 변환됨 | upsert + legacyNames 패턴 | 동작 동일, 구조만 다름 |
| TemplateSeeder — 메일 템플릿 3종 | 원본 유지 | 이름/데이터모델 변경 | **데이터 흐름 변경** |
| TemplateSeeder — 폴더 템플릿 3종 | 없음 | 신규 추가 | 신규 기능 |
| OAuthTokenService | 없음 | token alias 추가 | google_sheets 토큰 조회 영향 |
| application.yml | gmail scope만 | spreadsheets scope 추가 | OAuth 재연결 필요 가능 |
| CatalogController | 동일 | Swagger 문구만 | 없음 |
| Sink picker 인프라 (4파일) | **동일** | — | 없음 |
| WebClientConfig | **동일** | — | 없음 |
| 테스트 | **동일** | — | 없음 |

---

## 5. 결론

PR #4의 핵심 기여(파일 업로드 템플릿 3종 + sink picker 인프라)는 **코드 변경 없이 그대로 main에 반영**되었습니다.

main에서 추가로 달라진 부분은 모두 PR #5, #6에서 온 것이며:
1. **TemplateSeeder 구조** — loop → upsert 패턴 전환 (동작 동일)
2. **메일 템플릿 데이터 모델** — 개별 처리 → 일괄 집계 (PR #5)
3. **폴더 문서 템플릿 3종** — 신규 추가 (PR #6)
4. **Token alias + spreadsheets scope** — Google Sheets 연동 지원 (PR #6)
