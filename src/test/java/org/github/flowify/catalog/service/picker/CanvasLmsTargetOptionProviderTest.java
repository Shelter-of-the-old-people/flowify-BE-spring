package org.github.flowify.catalog.service.picker;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CanvasLmsTargetOptionProviderTest {

    private final CanvasLmsTargetOptionProvider provider =
            new CanvasLmsTargetOptionProvider(mock(WebClient.class));

    @SuppressWarnings("unchecked")
    @Test
    void filterCurrentCourses_excludesPastTermCourses() {
        String past = Instant.now().minus(30, ChronoUnit.DAYS).toString();
        String future = Instant.now().plus(30, ChronoUnit.DAYS).toString();
        List<Map<String, Object>> courses = List.of(
                Map.of(
                        "id", "past-course",
                        "name", "Past Course",
                        "term", Map.of(
                                "name", "2025-2",
                                "end_at", past
                        )
                ),
                Map.of(
                        "id", "current-course",
                        "name", "Current Course",
                        "term", Map.of(
                                "name", "2026-1",
                                "end_at", future
                        )
                )
        );

        List<Map<String, Object>> filteredCourses =
                (List<Map<String, Object>>) ReflectionTestUtils.invokeMethod(
                        provider,
                        "filterCurrentCourses",
                        courses
                );

        assertThat(filteredCourses)
                .extracting(course -> String.valueOf(course.get("id")))
                .containsExactly("current-course");
    }

    @SuppressWarnings("unchecked")
    @Test
    void filterCurrentCourses_excludesCoursesWithPastCourseEndAt() {
        String past = Instant.now().minus(7, ChronoUnit.DAYS).toString();
        List<Map<String, Object>> courses = List.of(
                Map.of(
                        "id", "ended-course",
                        "name", "Ended Course",
                        "end_at", past
                ),
                Map.of(
                        "id", "undated-course",
                        "name", "Undated Course"
                )
        );

        List<Map<String, Object>> filteredCourses =
                (List<Map<String, Object>>) ReflectionTestUtils.invokeMethod(
                        provider,
                        "filterCurrentCourses",
                        courses
                );

        assertThat(filteredCourses)
                .extracting(course -> String.valueOf(course.get("id")))
                .containsExactly("undated-course");
    }
}
