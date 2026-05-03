package org.github.flowify.catalog.service.picker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.catalog.dto.picker.TargetOptionItem;
import org.github.flowify.catalog.dto.picker.TargetOptionResponse;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleDriveTargetOptionProvider implements TargetOptionProvider {

    private static final String SERVICE_KEY = "google_drive";
    private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";

    @Qualifier("googleDriveWebClient")
    private final WebClient googleDriveWebClient;

    @Override
    public String getServiceKey() {
        return SERVICE_KEY;
    }

    @Override
    public TargetOptionResponse getOptions(
            String sourceMode, String token, String parentId, String query, String cursor) {
        if (isFilePickerMode(sourceMode)) {
            return listDriveOptions(token, buildFileQuery(parentId, query), cursor, "file");
        }
        if (isFolderPickerMode(sourceMode)) {
            return listDriveOptions(token, buildFolderQuery(parentId, query), cursor, "folder");
        }

        throw new BusinessException(ErrorCode.INVALID_REQUEST,
                "Google Drive source mode는 target option을 지원하지 않습니다: " + sourceMode);
    }

    @SuppressWarnings("unchecked")
    public TargetOptionItem createFolder(String token, String parentId, String name) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("name", name);
            requestBody.put("mimeType", FOLDER_MIME_TYPE);
            if (parentId != null && !parentId.isBlank()) {
                requestBody.put("parents", List.of(parentId));
            }

            Map<String, Object> response = googleDriveWebClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/files")
                            .queryParam("fields", "id,name,mimeType,modifiedTime")
                            .build())
                    .headers(headers -> headers.setBearerAuth(token))
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .blockOptional()
                    .orElse(Map.of());

            return toTargetOption(response, "folder");
        } catch (WebClientResponseException e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR,
                    "Google Drive 폴더 생성에 실패했습니다.");
        }
    }

    @SuppressWarnings("unchecked")
    private TargetOptionResponse listDriveOptions(
            String token, String driveQuery, String cursor, String type) {
        try {
            Map<String, Object> response = googleDriveWebClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .path("/files")
                                .queryParam("q", driveQuery)
                                .queryParam("fields", "nextPageToken,files(id,name,mimeType,modifiedTime,size)")
                                .queryParam("pageSize", 20);
                        if (cursor != null && !cursor.isBlank()) {
                            builder.queryParam("pageToken", cursor);
                        }
                        return builder.build();
                    })
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .blockOptional()
                    .orElse(Map.of());

            List<Map<String, Object>> files = response.get("files") instanceof List<?> rawFiles
                    ? (List<Map<String, Object>>) rawFiles
                    : List.of();

            List<TargetOptionItem> items = files.stream()
                    .map(file -> toTargetOption(file, resolveDriveItemType(file, type)))
                    .toList();

            return TargetOptionResponse.builder()
                    .items(items)
                    .nextCursor(asString(response.get("nextPageToken")))
                    .build();
        } catch (WebClientResponseException e) {
            log.error("Google Drive API error: status={}, body={}",
                    e.getStatusCode().value(), e.getResponseBodyAsString());

            if (e.getStatusCode().value() == 401) {
                throw new BusinessException(ErrorCode.OAUTH_TOKEN_EXPIRED,
                        "Google Drive 토큰이 만료되었습니다. 재연결이 필요합니다.");
            }
            if (e.getStatusCode().value() == 403) {
                throw new BusinessException(ErrorCode.OAUTH_SCOPE_INSUFFICIENT,
                        "Google Drive 접근 권한이 부족합니다. 서비스 재연결이 필요합니다.");
            }
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR,
                    "Google Drive API 호출에 실패했습니다: " + e.getStatusCode().value());
        }
    }

    private TargetOptionItem toTargetOption(Map<String, Object> file, String type) {
        Map<String, Object> metadata = new HashMap<>();
        putIfPresent(metadata, "mimeType", file.get("mimeType"));
        putIfPresent(metadata, "modifiedTime", file.get("modifiedTime"));
        putIfPresent(metadata, "size", file.get("size"));

        return TargetOptionItem.builder()
                .id(asString(file.get("id")))
                .label(asString(file.get("name")))
                .description(
                        "folder".equals(type) ? "Google Drive folder" : asString(file.get("mimeType")))
                .type(type)
                .metadata(metadata)
                .build();
    }

    private String resolveDriveItemType(Map<String, Object> file, String fallbackType) {
        String mimeType = asString(file.get("mimeType"));
        if (FOLDER_MIME_TYPE.equals(mimeType)) {
            return "folder";
        }
        return fallbackType;
    }

    private String buildFileQuery(String parentId, String query) {
        StringBuilder q = new StringBuilder("trashed = false and mimeType != '")
                .append(FOLDER_MIME_TYPE)
                .append("'");
        appendParentFilter(q, parentId);
        appendNameFilter(q, query);
        return q.toString();
    }

    private String buildFolderQuery(String parentId, String query) {
        StringBuilder q = new StringBuilder("trashed = false and mimeType = '")
                .append(FOLDER_MIME_TYPE)
                .append("'");
        appendParentFilter(q, parentId == null || parentId.isBlank() ? "root" : parentId);
        appendNameFilter(q, query);
        return q.toString();
    }

    private void appendParentFilter(StringBuilder q, String parentId) {
        if (parentId != null && !parentId.isBlank()) {
            q.append(" and '").append(escapeDriveQuery(parentId)).append("' in parents");
        }
    }

    private void appendNameFilter(StringBuilder q, String query) {
        if (query != null && !query.isBlank()) {
            q.append(" and name contains '").append(escapeDriveQuery(query)).append("'");
        }
    }

    private boolean isFilePickerMode(String sourceMode) {
        return "single_file".equals(sourceMode) || "file_changed".equals(sourceMode);
    }

    private boolean isFolderPickerMode(String sourceMode) {
        return "new_file".equals(sourceMode)
                || "folder_new_file".equals(sourceMode)
                || "folder_all_files".equals(sourceMode);
    }

    private String escapeDriveQuery(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
