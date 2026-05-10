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
public class GoogleSheetsTargetOptionProvider implements TargetOptionProvider, SinkTargetOptionProvider {

    private static final String SERVICE_KEY = "google_sheets";
    private static final String SHEETS_MIME_TYPE = "application/vnd.google-apps.spreadsheet";

    @Qualifier("googleDriveWebClient")
    private final WebClient googleDriveWebClient;

    @Qualifier("googleSheetsWebClient")
    private final WebClient googleSheetsWebClient;

    @Override
    public String getServiceKey() {
        return SERVICE_KEY;
    }

    @Override
    public TargetOptionResponse getOptions(
            String first,
            String second,
            String parentId,
            String query,
            String cursor
    ) {
        if (isSupportedSourceMode(first)) {
            return listOptions(second, parentId, query, cursor);
        }

        String token = first;
        String type = second;
        if (!"sheet".equals(type)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Google Sheets sink target option type is not supported: " + type
            );
        }
        return listOptions(token, parentId, query, cursor);
    }

    private boolean isSupportedSourceMode(String sourceMode) {
        return "sheet_all".equals(sourceMode)
                || "new_row".equals(sourceMode)
                || "row_updated".equals(sourceMode);
    }

    private TargetOptionResponse listOptions(
            String token,
            String parentId,
            String query,
            String cursor
    ) {
        if (parentId == null || parentId.isBlank()) {
            return listSpreadsheets(token, query, cursor);
        }
        return listSheets(token, parentId, query);
    }

    @SuppressWarnings("unchecked")
    private TargetOptionResponse listSpreadsheets(String token, String query, String cursor) {
        try {
            String driveQuery = buildSpreadsheetQuery(query);
            Map<String, Object> response = googleDriveWebClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .path("/files")
                                .queryParam("q", driveQuery)
                                .queryParam("fields", "nextPageToken,files(id,name,modifiedTime)")
                                .queryParam("pageSize", 20)
                                .queryParam("orderBy", "modifiedTime desc");
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
                    .map(file -> TargetOptionItem.builder()
                            .id(asString(file.get("id")))
                            .label(asString(file.get("name")))
                            .description("Google Sheets spreadsheet")
                            .type("spreadsheet")
                            .metadata(buildSpreadsheetMetadata(file))
                            .build())
                    .toList();

            return TargetOptionResponse.builder()
                    .items(items)
                    .nextCursor(asString(response.get("nextPageToken")))
                    .build();
        } catch (WebClientResponseException e) {
            throw toBusinessException("Google Sheets spreadsheet list request failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private TargetOptionResponse listSheets(String token, String spreadsheetId, String query) {
        try {
            Map<String, Object> spreadsheet = googleSheetsWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{spreadsheetId}")
                            .queryParam("fields", "properties.title,sheets.properties(sheetId,title)")
                            .build(spreadsheetId))
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .blockOptional()
                    .orElse(Map.of());

            String spreadsheetTitle = spreadsheet.get("properties") instanceof Map<?, ?> properties
                    ? asString(((Map<String, Object>) properties).get("title"))
                    : null;

            List<Map<String, Object>> sheets = spreadsheet.get("sheets") instanceof List<?> rawSheets
                    ? (List<Map<String, Object>>) rawSheets
                    : List.of();

            String normalizedQuery = query != null ? query.trim().toLowerCase() : "";

            List<TargetOptionItem> items = sheets.stream()
                    .map(this::extractSheetProperties)
                    .filter(properties -> properties != null)
                    .filter(properties -> {
                        if (normalizedQuery.isEmpty()) {
                            return true;
                        }
                        String title = asString(properties.get("title"));
                        return title != null && title.toLowerCase().contains(normalizedQuery);
                    })
                    .map(properties -> {
                        String sheetTitle = asString(properties.get("title"));
                        Integer sheetId = properties.get("sheetId") instanceof Number number
                                ? number.intValue()
                                : null;
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("spreadsheetId", spreadsheetId);
                        putIfPresent(metadata, "spreadsheetTitle", spreadsheetTitle);
                        putIfPresent(metadata, "sheetName", sheetTitle);
                        if (sheetId != null) {
                            metadata.put("sheetId", sheetId);
                        }

                        String label = spreadsheetTitle != null && sheetTitle != null
                                ? spreadsheetTitle + " / " + sheetTitle
                                : sheetTitle;

                        return TargetOptionItem.builder()
                                .id(spreadsheetId)
                                .label(label)
                                .description("Google Sheets tab")
                                .type("sheet")
                                .metadata(metadata)
                                .build();
                    })
                    .toList();

            return TargetOptionResponse.builder()
                    .items(items)
                    .nextCursor(null)
                    .build();
        } catch (WebClientResponseException e) {
            throw toBusinessException("Google Sheets sheet list request failed", e);
        }
    }

    private Map<String, Object> buildSpreadsheetMetadata(Map<String, Object> file) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("spreadsheetId", asString(file.get("id")));
        putIfPresent(metadata, "spreadsheetTitle", file.get("name"));
        putIfPresent(metadata, "modifiedTime", file.get("modifiedTime"));
        return metadata;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractSheetProperties(Map<String, Object> sheet) {
        if (!(sheet.get("properties") instanceof Map<?, ?> properties)) {
            return null;
        }
        return (Map<String, Object>) properties;
    }

    private String buildSpreadsheetQuery(String query) {
        StringBuilder builder = new StringBuilder("trashed = false and mimeType = '")
                .append(SHEETS_MIME_TYPE)
                .append("'");
        if (query != null && !query.isBlank()) {
            builder.append(" and name contains '").append(escapeDriveQuery(query)).append("'");
        }
        return builder.toString();
    }

    private String escapeDriveQuery(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private BusinessException toBusinessException(String message, WebClientResponseException e) {
        log.error("{}: status={}, body={}", message, e.getStatusCode().value(), e.getResponseBodyAsString());

        if (e.getStatusCode().value() == 401) {
            return new BusinessException(
                    ErrorCode.OAUTH_TOKEN_EXPIRED,
                    "Google Sheets token expired. Please reconnect the service."
            );
        }
        if (e.getStatusCode().value() == 403) {
            return new BusinessException(
                    ErrorCode.OAUTH_SCOPE_INSUFFICIENT,
                    "Google Sheets access scope is insufficient. Please reconnect the service."
            );
        }

        return new BusinessException(
                ErrorCode.EXTERNAL_API_ERROR,
                message + ": " + e.getStatusCode().value()
        );
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
