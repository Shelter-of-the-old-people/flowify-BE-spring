# 蹂묓빀 蹂닿퀬?? feat/file-upload-auto-share-backend ??main

> **?묒꽦??** 2026-05-04
> **PR:** #4 ???뚯씪 ?낅줈???먮룞 怨듭쑀 ?쒖뒪???쒗뵆由?諛?sink picker 吏??異붽?
> **蹂묓빀 諛⑹떇:** 濡쒖뺄 異⑸룎 ?닿껐 ??main??吏곸젒 push
> **蹂묓빀 ?쒖꽌:** PR #5 ??PR #6 ??**PR #4** (留덉?留?

---

## 1. 蹂묓빀 ?붿빟

PR #4(`feat/file-upload-auto-share-backend`)??紐⑤뱺 肄붾뱶媛 main??諛섏쁺?섏뿀?쇰ŉ, PR #5(硫붿씪 ?붿빟 ?꾨떖)? PR #6(?대뜑 臾몄꽌 ?붿빟)??蹂寃쎌궗??씠 ?④퍡 蹂묓빀?섎㈃???꾨옒? 媛숈? 李⑥씠媛 諛쒖깮?⑸땲??

---

## 2. PR #4 肄붾뱶媛 main??100% 諛섏쁺???뚯씪

PR #4?먯꽌 ?덈줈 異붽????뚯씪 以?main???숈씪?섍쾶 議댁옱?섎뒗 ?뚯씪?낅땲??

| ?뚯씪 | ?ㅻ챸 | ?곹깭 |
|------|------|------|
| `catalog/service/picker/SinkTargetOptionProvider.java` | Sink picker ?명꽣?섏씠??| ?숈씪 (PR #5? ?숈씪 肄붾뱶) |
| `catalog/service/picker/SinkTargetOptionService.java` | Sink picker ?쇱슦???쒕퉬??| ?숈씪 |
| `catalog/service/picker/NotionSinkTargetOptionProvider.java` | Notion ?섏씠吏 紐⑸줉 議고쉶 | ?숈씪 |
| `config/WebClientConfig.java` | notionWebClient 鍮?異붽? | ?숈씪 |
| `test/.../SinkTargetOptionServiceTest.java` | Sink picker ?⑥쐞 ?뚯뒪??| ?숈씪 |
| `docs/FILE_UPLOAD_AUTO_SHARE_TEMPLATE_BACKEND_REQUEST.md` | FE ?붿껌??臾몄꽌 | ?숈씪 |

---

## 3. main??PR #4? ?ㅻⅨ 遺遺?(PR #5, #6?먯꽌 異붽?)

### 3.1 TemplateSeeder.java ???쒕뜑 濡쒖쭅 援ъ“ 蹂寃?

**PR #4 ?먮낯:**
```java
// 湲곗〈 loop 諛⑹떇 ?좎?
List<Template> templates = List.of(
    buildStudyNoteTemplate(),
    ...
    buildDriveUploadGmailTemplate(),
    buildDriveUploadNotionTemplate()
);

for (Template seedTemplate : templates) {
    var existing = templateRepository.findByNameAndIsSystem(seedTemplate.getName(), true);
    if (existing.isPresent()) { ... updated++; }
    else { ... created++; }
}
```

**?꾩옱 main:**
```java
// upsert 硫붿꽌??+ legacyNames 吏??諛⑹떇 (PR #5?먯꽌 ?꾩엯)
if (upsertTemplate(buildStudyNoteTemplate())) { updated++; } else { created++; }
if (upsertTemplate(buildMeetingMinutesTemplate())) { updated++; } else { created++; }
...
// 硫붿씪 ?쒗뵆由우? legacy name 吏??
if (upsertTemplate(buildUnreadMailSlackTemplate(),
        "?쎌? ?딆? 硫붿씪 ?붿빟 ??Slack 怨듭쑀")) { ... }
...
// PR #4???낅줈???쒗뵆由용룄 ?숈씪 ?⑦꽩?쇰줈 ?깅줉
if (upsertTemplate(buildDriveUploadGmailTemplate())) { updated++; } else { created++; }
if (upsertTemplate(buildDriveUploadNotionTemplate())) { updated++; } else { created++; }
```

**李⑥씠??**
- `upsertTemplate(Template, String... legacyNames)` 硫붿꽌???좉퇋 異붽?
- `findExistingSystemTemplate(String name, String... legacyNames)` 硫붿꽌???좉퇋 異붽?
- ?덇굅???대쫫?쇰줈??湲곗〈 DB 臾몄꽌瑜?李얠븘 id/useCount/createdAt 蹂댁〈 媛??
- PR #4???낅줈???쒗뵆由?3醫낆? legacy name???놁뼱 `upsertTemplate(template)` ?뺥깭濡??몄텧

**?곹뼢:** PR #4???쒗뵆由??깅줉 濡쒖쭅? ?숈씪?섍쾶 ?묐룞?⑸땲?? ?대쫫 湲곗? upsert?대ŉ 湲곗〈 ?곗씠?곕? 蹂댁〈?⑸땲?? 異붽???寃껋? 硫붿씪 ?쒗뵆由우쓽 ?대쫫 蹂寃????덇굅???대쫫 留덉씠洹몃젅?댁뀡 吏?먮퓧?낅땲??

---

### 3.2 TemplateSeeder.java ??硫붿씪 ?쒗뵆由?3醫??곗씠??紐⑤뜽 蹂寃?(PR #5)

PR #4 釉뚮옖移섏뿉??湲곗〈 硫붿씪 ?쒗뵆由우씠 ?먮낯 洹몃?濡쒖씤 諛섎㈃, main?먯꽌??PR #5媛 ?ㅼ쓬??蹂寃쏀뻽?듬땲??

| ??ぉ | PR #4 釉뚮옖移?(蹂寃??놁쓬) | main (PR #5 諛섏쁺) |
|------|------------------------|-------------------|
| ?쒗뵆由??대쫫 | `?쎌? ?딆? 硫붿씪 ?붿빟 ??Slack 怨듭쑀` | `?쎌? ?딆? 硫붿씪 紐⑸줉 ?붿빟 ??Slack 怨듭쑀` |
| ?쒗뵆由??대쫫 | `以묒슂 硫붿씪 ?붿빟 ??Notion ??? | `以묒슂 硫붿씪 紐⑸줉 ?붿빟 ??Notion ??? |
| ?쒗뵆由??대쫫 | `以묒슂 硫붿씪 ????異붿텧 ??Notion ??? | `以묒슂 硫붿씪 紐⑸줉?먯꽌 ????異붿텧 ??Notion ??? |
| Loop outputDataType | `SINGLE_EMAIL` | `EMAIL_LIST` |
| LLM dataType | `SINGLE_EMAIL` | `EMAIL_LIST` |
| LLM prompt | 媛쒕퀎 硫붿씪 ?붿빟 | 硫붿씪 **紐⑸줉** ?쇨큵 ?붿빟 |
| LLM config 異붽? | ??| `summaryFormat`, `resultMode: "single_aggregated"` |
| Gmail config 異붽? | ??| `maxResults: 100` |
| Slack config 異붽? | ??| `channel: ""` |
| Notion config 異붽? | ??| `target_type: "page"`, `target_id: ""` |
| ?ㅻ챸 臾멸뎄 | "?섎굹???붿빟" | "紐⑸줉???뺥빐吏??뺤떇?쇰줈 ?붿빟" |

**?듭떖 蹂寃?** 硫붿씪 泥섎━ 諛⑹떇??"硫붿씪 ?섎굹???쒖감 ?붿빟" ??"硫붿씪 紐⑸줉 ?쇨큵 吏묎퀎 ?붿빟"?쇰줈 蹂寃쎈맖.

---

### 3.3 TemplateSeeder.java ???대뜑 臾몄꽌 ?붿빟 ?쒗뵆由?3醫?異붽? (PR #6)

PR #4?먮뒗 ?녾퀬 main?먮쭔 議댁옱?섎뒗 ?좉퇋 ?쒗뵆由?

| 硫붿꽌??| ?쒗뵆由??대쫫 | 援ъ“ |
|--------|-----------|------|
| `buildFolderDocumentSlackTemplate()` | ?좉퇋 臾몄꽌 ?붿빟 ??Slack 怨듭쑀 | google_drive ??llm ??slack |
| `buildFolderDocumentGmailTemplate()` | ?좉퇋 臾몄꽌 ?붿빟 ??Gmail ?꾨떖 | google_drive ??llm ??gmail |
| `buildFolderDocumentSheetsTemplate()` | 臾몄꽌 ?붿빟 寃곌낵瑜?Google Sheets?????| google_drive ??llm ??google_sheets |
| `buildFolderDocumentSourceNode()` | (怨듯넻 ?ы띁) source_mode=folder_new_file | ??|

移댄뀒怨좊━: `folder_document_summary`, dataType: `SINGLE_FILE`

---

### 3.4 OAuthTokenService.java ??Token Alias ?꾨왂 異붽? (PR #6)

PR #4?먮뒗 ?녾퀬 main?먮쭔 議댁옱:

```java
private static final Map<String, String> TOKEN_SERVICE_ALIASES = Map.of(
    "google_sheets", "google_drive"
);

private String resolveTokenLookupService(String service) {
    return TOKEN_SERVICE_ALIASES.getOrDefault(service, service);
}
```

`getDecryptedToken()` ?몄텧 ??`google_sheets` ?쒕퉬???ㅻ줈 ?붿껌?섎㈃ `google_drive` ?좏겙???먮룞?쇰줈 議고쉶?⑸땲?? Google Sheets ?쒗뵆由우씠 蹂꾨룄 OAuth ?놁씠 Google Drive ?좏겙???ъ궗?⑺븷 ???덇쾶 ?⑸땲??

---

### 3.5 application.yml ??Google Drive OAuth scope ?뺤옣 (PR #6)

| ??ぉ | PR #4 釉뚮옖移?| main |
|------|-------------|------|
| google-drive scopes | `drive.readonly` `drive.file` | `drive.readonly` `drive.file` **`spreadsheets`** |
| gmail scopes | `gmail.readonly` `gmail.send` | `gmail.readonly` `gmail.send` |

李⑥씠: `https://www.googleapis.com/auth/spreadsheets` scope媛 main??異붽??? Google Sheets ?쒗뵆由우쓽 ?곌린 沅뚰븳???꾩슂.

---

### 3.6 CatalogController.java ??Swagger ?ㅻ챸 誘몄꽭 蹂寃?

| ?꾩튂 | PR #4 釉뚮옖移?| main |
|------|-------------|------|
| getSinkCatalog() description | "?섏슜 媛?ν븳 input type" | "?덉슜 媛?ν븳 input type" |

湲곕뒫 李⑥씠 ?놁쓬, 臾멸뎄留?蹂寃?

---

### 3.7 臾몄꽌 ?뚯씪 異붽? (PR #5, #6)

main?먮쭔 議댁옱?섎뒗 臾몄꽌:

| ?뚯씪 | 異쒖쿂 |
|------|------|
| `docs/TEMP_MAIL_SUMMARY_TEMPLATE_BACKEND_REQUEST.md` | PR #5 |
| `docs/FOLDER_DOCUMENT_SUMMARY_TEMPLATE_BACKEND_REQUEST.md` | PR #6 |
| `docs/SPRING_IMPLEMENTATION_STATUS.md` | main 吏곸젒 ?묒꽦 |

---

## 4. 李⑥씠 ?붿빟 留ㅽ듃由?뒪

| ?뚯씪 | PR #4 肄붾뱶 諛섏쁺 | PR #5/6 異붽? 蹂寃?| ?숈옉 ?곹뼢 |
|------|:-:|:-:|:-:|
| TemplateSeeder ???낅줈???쒗뵆由?3醫?| **?숈씪** | ??| ?놁쓬 |
| TemplateSeeder ??run() 援ъ“ | 蹂?섎맖 | upsert + legacyNames ?⑦꽩 | ?숈옉 ?숈씪, 援ъ“留??ㅻ쫫 |
| TemplateSeeder ??硫붿씪 ?쒗뵆由?3醫?| ?먮낯 ?좎? | ?대쫫/?곗씠?곕え??蹂寃?| **?곗씠???먮쫫 蹂寃?* |
| TemplateSeeder ???대뜑 ?쒗뵆由?3醫?| ?놁쓬 | ?좉퇋 異붽? | ?좉퇋 湲곕뒫 |
| OAuthTokenService | ?놁쓬 | token alias 異붽? | google_sheets ?좏겙 議고쉶 ?곹뼢 |
| application.yml | gmail scope留?| spreadsheets scope 異붽? | OAuth ?ъ뿰寃??꾩슂 媛??|
| CatalogController | ?숈씪 | Swagger 臾멸뎄留?| ?놁쓬 |
| Sink picker ?명봽??(4?뚯씪) | **?숈씪** | ??| ?놁쓬 |
| WebClientConfig | **?숈씪** | ??| ?놁쓬 |
| ?뚯뒪??| **?숈씪** | ??| ?놁쓬 |

---

## 5. 寃곕줎

PR #4???듭떖 湲곗뿬(?뚯씪 ?낅줈???쒗뵆由?3醫?+ sink picker ?명봽????**肄붾뱶 蹂寃??놁씠 洹몃?濡?main??諛섏쁺**?섏뿀?듬땲??

main?먯꽌 異붽?濡??щ씪吏?遺遺꾩? 紐⑤몢 PR #5, #6?먯꽌 ??寃껋씠硫?
1. **TemplateSeeder 援ъ“** ??loop ??upsert ?⑦꽩 ?꾪솚 (?숈옉 ?숈씪)
2. **硫붿씪 ?쒗뵆由??곗씠??紐⑤뜽** ??媛쒕퀎 泥섎━ ???쇨큵 吏묎퀎 (PR #5)
3. **?대뜑 臾몄꽌 ?쒗뵆由?3醫?* ???좉퇋 異붽? (PR #6)
4. **Token alias + spreadsheets scope** ??Google Sheets ?곕룞 吏??(PR #6)
