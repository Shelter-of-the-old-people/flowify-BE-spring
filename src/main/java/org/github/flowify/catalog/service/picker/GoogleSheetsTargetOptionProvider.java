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
    private static final String SPREADSHEET_FIELDS =
            "spreadsheetId,properties.title,sheets.properties(sheetId,title)";

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

    public TargetOptionItem createSpreadsheet(String token, String name) {
        String trimmedName = name == null ? "" : name.trim();
        try {
            Map<String, Object> requestBody = Map.of(
                    "properties", Map.of("title", trimmedName)
            );

            Map<String, Object> response = googleSheetsWebClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("fields", "spreadsheetId,properties.title")
                            .build())
                    .headers(headers -> headers.setBearerAuth(token))
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .blockOptional()
                    .orElse(Map.of());

            return toSpreadsheetOption(response);
        } catch (WebClientResponseException e) {
            throw toBusinessException("Google Sheets spreadsheet create request failed", e);
        }
    }

    public TargetOptionItem createSheet(String token, String spreadsheetId, String sheetName) {
        String trimmedSheetName = sheetName == null ? "" : sheetName.trim();
        try {
            Map<String, Object> spreadsheet = fetchSpreadsheet(token, spreadsheetId);
            String spreadsheetTitle = extractSpreadsheetTitle(spreadsheet);
            List<Map<String, Object>> sheets = extractSheets(spreadsheet);

            for (Map<String, Object> properties : sheets.stream()
                    .map(this::extractSheetProperties)
                    .filter(properties -> properties != null)
                    .toList()) {
                String existingTitle = asString(properties.get("title"));
                if (trimmedSheetName.equals(existingTitle)) {
                    Integer existingSheetId = properties.get("sheetId") instanceof Number number
                            ? number.intValue()
                            : null;
                    return toSheetOption(spreadsheetId, spreadsheetTitle, trimmedSheetName, existingSheetId);
                }
            }

            Map<String, Object> requestBody = Map.of(
                    "requests", List.of(Map.of(
                            "addSheet", Map.of(
                                    "properties", Map.of("title", trimmedSheetName)
                            )
                    ))
            );

            Map<String, Object> response = googleSheetsWebClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/{spreadsheetId}:batchUpdate")
                            .queryParam("fields", "replies.addSheet.properties(sheetId,title)")
                            .build(spreadsheetId))
                    .headers(headers -> headers.setBearerAuth(token))
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .blockOptional()
                    .orElse(Map.of());

            Integer createdSheetId = extractCreatedSheetId(response);
            return toSheetOption(spreadsheetId, spreadsheetTitle, trimmedSheetName, createdSheetId);
        } catch (WebClientResponseException e) {
            throw toBusinessException("Google Sheets sheet create request failed", e);
        }
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
                    .map(this::toSpreadsheetOption)
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
            Map<String, Object> spreadsheet = fetchSpreadsheet(token, spreadsheetId);
            String spreadsheetTitle = extractSpreadsheetTitle(spreadsheet);
            List<Map<String, Object>> sheets = extractSheets(spreadsheet);

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
                    .map(properties -> toSheetOption(
                            spreadsheetId,
                            spreadsheetTitle,
                            asString(properties.get("title")),
                            properties.get("sheetId") instanceof Number number ? number.intValue() : null
                    ))
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
        String spreadsheetId = asString(file.get("spreadsheetId"));
        if (spreadsheetId == null) {
            spreadsheetId = asString(file.get("id"));
        }
        metadata.put("spreadsheetId", spreadsheetId);

        Object title = null;
        if (file.get("properties") instanceof Map<?, ?> properties) {
            title = ((Map<?, ?>) properties).get("title");
        }
        if (title == null) {
            title = file.get("name");
        }

        putIfPresent(metadata, "spreadsheetTitle", title);
        putIfPresent(metadata, "modifiedTime", file.get("modifiedTime"));
        return metadata;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchSpreadsheet(String token, String spreadsheetId) {
        return googleSheetsWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/{spreadsheetId}")
                        .queryParam("fields", SPREADSHEET_FIELDS)
                        .build(spreadsheetId))
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(30))
                .blockOptional()
                .orElse(Map.of());
    }

    private String extractSpreadsheetTitle(Map<String, Object> spreadsheet) {
        if (!(spreadsheet.get("properties") instanceof Map<?, ?> properties)) {
            return null;
        }
        return asString(((Map<String, Object>) properties).get("title"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractSheets(Map<String, Object> spreadsheet) {
        return spreadsheet.get("sheets") instanceof List<?> rawSheets
                ? (List<Map<String, Object>>) rawSheets
                : List.of();
    }

    private TargetOptionItem toSpreadsheetOption(Map<String, Object> file) {
        String spreadsheetId = asString(file.get("spreadsheetId"));
        if (spreadsheetId == null) {
            spreadsheetId = asString(file.get("id"));
        }

        String label = null;
        if (file.get("properties") instanceof Map<?, ?> properties) {
            label = asString(((Map<?, ?>) properties).get("title"));
        }
        if (label == null) {
            label = asString(file.get("name"));
        }

        return TargetOptionItem.builder()
                .id(spreadsheetId)
                .label(label)
                .description("Google Sheets spreadsheet")
                .type("spreadsheet")
                .metadata(buildSpreadsheetMetadata(file))
                .build();
    }

    private TargetOptionItem toSheetOption(
            String spreadsheetId,
            String spreadsheetTitle,
            String sheetTitle,
            Integer sheetId
    ) {
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
                .id(buildSheetOptionId(spreadsheetId, sheetTitle))
                .label(label)
                .description("Google Sheets tab")
                .type("sheet")
                .metadata(metadata)
                .build();
    }

    private String buildSheetOptionId(String spreadsheetId, String sheetTitle) {
        if (sheetTitle == null || sheetTitle.isBlank()) {
            return spreadsheetId;
        }

        return spreadsheetId + "::sheet::" + sheetTitle;
    }

    @SuppressWarnings("unchecked")
    private Integer extractCreatedSheetId(Map<String, Object> response) {
        if (!(response.get("replies") instanceof List<?> rawReplies)) {
            return null;
        }

        for (Object rawReply : rawReplies) {
            if (!(rawReply instanceof Map<?, ?> reply)) {
                continue;
            }
            Object addSheet = reply.get("addSheet");
            if (!(addSheet instanceof Map<?, ?> addSheetMap)) {
                continue;
            }
            Object properties = addSheetMap.get("properties");
            if (!(properties instanceof Map<?, ?> propertiesMap)) {
                continue;
            }
            Object sheetId = propertiesMap.get("sheetId");
            if (sheetId instanceof Number number) {
                return number.intValue();
            }
        }

        return null;
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
