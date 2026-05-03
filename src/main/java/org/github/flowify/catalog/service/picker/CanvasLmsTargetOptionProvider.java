package org.github.flowify.catalog.service.picker;

import lombok.RequiredArgsConstructor;
import org.github.flowify.catalog.dto.picker.TargetOptionItem;
import org.github.flowify.catalog.dto.picker.TargetOptionResponse;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CanvasLmsTargetOptionProvider implements TargetOptionProvider {

    private static final String SERVICE_KEY = "canvas_lms";

    @Qualifier("canvasWebClient")
    private final WebClient canvasWebClient;

    @Value("${app.oauth.canvas-lms.token:}")
    private String canvasToken;

    @Override
    public String getServiceKey() {
        return SERVICE_KEY;
    }

    @Override
    public TargetOptionResponse getOptions(String sourceMode, String token, String parentId, String query, String cursor) {
        if (canvasToken == null || canvasToken.isBlank()) {
            throw new BusinessException(ErrorCode.OAUTH_NOT_CONNECTED, "Canvas LMS 토큰이 설정되지 않았습니다.");
        }

        if ("term_all_files".equals(sourceMode)) {
            return TargetOptionResponse.builder()
                    .items(toTermOptions(fetchCourses(true), query))
                    .nextCursor(null)
                    .build();
        }

        if ("course_files".equals(sourceMode)) {
            return TargetOptionResponse.builder()
                    .items(toCourseOptions(fetchCourses(true), query))
                    .nextCursor(null)
                    .build();
        }

        if ("course_new_file".equals(sourceMode)) {
            return TargetOptionResponse.builder()
                    .items(toCourseOptions(filterCurrentCourses(fetchCourses(false)), query))
                    .nextCursor(null)
                    .build();
        }

        throw new BusinessException(ErrorCode.INVALID_REQUEST,
                "Canvas LMS source mode는 target option을 지원하지 않습니다: " + sourceMode);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchCourses(boolean includeCompleted) {
        try {
            return canvasWebClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .path("/api/v1/courses")
                                .queryParam("enrollment_state[]", "active")
                                .queryParam("include[]", "term");

                        if (includeCompleted) {
                            builder.queryParam("enrollment_state[]", "completed");
                        }

                        return builder.build();
                    })
                    .headers(headers -> headers.setBearerAuth(canvasToken))
                    .retrieve()
                    .bodyToMono(List.class)
                    .timeout(Duration.ofSeconds(30))
                    .blockOptional()
                    .orElse(List.of());
        } catch (WebClientResponseException e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR,
                    "Canvas LMS 과목 목록 조회에 실패했습니다.");
        }
    }

    private List<Map<String, Object>> filterCurrentCourses(List<Map<String, Object>> courses) {
        Instant now = Instant.now();
        return courses.stream()
                .filter(course -> isCurrentCourse(course, now))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private boolean isCurrentCourse(Map<String, Object> course, Instant now) {
        Instant courseEndAt = parseInstant(asString(course.get("end_at")));
        if (courseEndAt != null && courseEndAt.isBefore(now)) {
            return false;
        }

        Map<String, Object> term = course.get("term") instanceof Map<?, ?> rawTerm
                ? (Map<String, Object>) rawTerm
                : Map.of();
        Instant termEndAt = parseInstant(asString(term.get("end_at")));
        return termEndAt == null || !termEndAt.isBefore(now);
    }

    @SuppressWarnings("unchecked")
    private List<TargetOptionItem> toCourseOptions(List<Map<String, Object>> courses, String query) {
        return courses.stream()
                .filter(course -> containsIgnoreCase(asString(course.get("name")), query))
                .map(course -> {
                    Map<String, Object> term = course.get("term") instanceof Map<?, ?> rawTerm
                            ? (Map<String, Object>) rawTerm
                            : Map.of();
                    String termName = asString(term.get("name"));
                    return TargetOptionItem.builder()
                            .id(asString(course.get("id")))
                            .label(asString(course.get("name")))
                            .description(termName)
                            .type("course")
                            .metadata(termName == null ? Map.of() : Map.of("term", termName))
                            .build();
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<TargetOptionItem> toTermOptions(List<Map<String, Object>> courses, String query) {
        Map<String, Integer> courseCountByTerm = new LinkedHashMap<>();
        for (Map<String, Object> course : courses) {
            Map<String, Object> term = course.get("term") instanceof Map<?, ?> rawTerm
                    ? (Map<String, Object>) rawTerm
                    : Map.of();
            String termName = asString(term.get("name"));
            if (termName == null || termName.isBlank()) {
                termName = "미지정 학기";
            }
            courseCountByTerm.merge(termName, 1, Integer::sum);
        }

        List<TargetOptionItem> items = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : courseCountByTerm.entrySet()) {
            if (!containsIgnoreCase(entry.getKey(), query)) {
                continue;
            }
            items.add(TargetOptionItem.builder()
                    .id(entry.getKey())
                    .label(entry.getKey())
                    .description(entry.getValue() + "개 과목")
                    .type("term")
                    .metadata(Map.of("courseCount", entry.getValue()))
                    .build());
        }
        return items;
    }

    private boolean containsIgnoreCase(String value, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        if (value == null) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
