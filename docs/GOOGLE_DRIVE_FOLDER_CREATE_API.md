# Google Drive Folder Create API

## 배경

`Google Drive` 도착 노드에서 `folder_id`를 직접 입력하는 방식만으로는 사용자 경험이 좋지 않습니다.

- 사용자는 Drive URL에서 폴더 ID를 복사해 와야 합니다.
- 원하는 폴더가 없으면 Drive에서 직접 폴더를 만든 뒤 다시 돌아와야 합니다.

이 문제를 줄이기 위해, 에디터 설정 단계에서 바로 폴더를 생성할 수 있는 Spring API를 추가했습니다.

## 이번 변경

Spring editor catalog에 `Google Drive` 폴더 생성 endpoint를 추가했습니다.

- `POST /api/editor-catalog/sinks/google_drive/folders`

이 endpoint는 현재 로그인한 사용자의 `google_drive` OAuth 토큰을 사용해:

1. 현재 위치의 상위 폴더 ID를 받고
2. Google Drive API로 새 폴더를 만든 뒤
3. FE picker가 바로 사용할 수 있는 `TargetOptionItem` 형태로 반환합니다.

## 적용 파일

- `src/main/java/org/github/flowify/catalog/controller/CatalogController.java`
- `src/main/java/org/github/flowify/catalog/service/picker/TargetOptionService.java`
- `src/main/java/org/github/flowify/catalog/service/picker/GoogleDriveTargetOptionProvider.java`
- `src/main/java/org/github/flowify/catalog/dto/picker/CreateGoogleDriveFolderRequest.java`
- `src/test/java/org/github/flowify/catalog/TargetOptionServiceTest.java`

## 요청/응답

요청:

```json
{
  "name": "강의자료",
  "parentId": "1AbCdEfGh"
}
```

응답:

```json
{
  "success": true,
  "data": {
    "id": "folder-id",
    "label": "강의자료",
    "description": "Google Drive folder",
    "type": "folder",
    "metadata": {
      "mimeType": "application/vnd.google-apps.folder"
    }
  }
}
```

## 구현 메모

- 토큰 조회는 `OAuthTokenService.getDecryptedToken(userId, "google_drive")`를 사용합니다.
- 실제 폴더 생성은 `GoogleDriveTargetOptionProvider.createFolder(...)`가 담당합니다.
- 상위 폴더가 없으면 `parents`를 보내지 않고 사용자의 루트에 생성합니다.
- 반환은 기존 picker와 바로 연동할 수 있도록 `TargetOptionItem`으로 맞췄습니다.

## 현재 제한

- Shared Drive 전용 파라미터는 아직 추가하지 않았습니다.
- 같은 이름의 폴더 중복 생성 방지는 별도 처리하지 않았습니다.
- 이 endpoint는 에디터 설정 UX용이며, 실행 시점의 자동 폴더 생성 로직은 아닙니다.

## 검증

실행한 검증:

- `./gradlew.bat compileJava compileTestJava --no-daemon --console=plain`
- `./gradlew.bat test --tests org.github.flowify.catalog.TargetOptionServiceTest --no-daemon --console=plain`
- `./gradlew.bat test --no-daemon --console=plain`

확인 결과:

- `compileJava`, `compileTestJava` 통과
- `TargetOptionServiceTest` 통과
- Spring 전체 `test` 통과

## Windows 경로 이슈 메모

이 프로젝트는 Windows에서 사용자 홈 경로에 한글이 포함되면, Gradle test worker가 생성하는
`gradle-worker-classpath*.txt` 내부 경로가 깨져 `GradleWorkerMain`을 찾지 못하는 문제가 있었습니다.

로컬 검증은 ASCII 경로만 사용하도록 드라이브를 임시 매핑해서 해결했습니다.

예시:

```powershell
subst W: "C:\Users\김민호\CD2\flowify-BE-spring"
subst J: "C:\Users\김민호\.vscode\extensions\redhat.java-1.54.0-win32-x64\jre\21.0.10-win32-x86_64"

$env:JAVA_HOME = "J:\"
$env:GRADLE_USER_HOME = "W:\.gradle-user-home"

cd W:\
.\gradlew.bat test --no-daemon --console=plain
```

정리할 때:

```powershell
subst W: /d
subst J: /d
```
