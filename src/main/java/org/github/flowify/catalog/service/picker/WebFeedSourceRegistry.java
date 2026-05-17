package org.github.flowify.catalog.service.picker;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class WebFeedSourceRegistry {

    private static final String FEED_SOURCES_PATH = "catalog/web_feed_sources.json";

    private final ObjectMapper objectMapper;
    private List<WebFeedSource> cachedSources;

    public List<WebFeedSource> search(String query) {
        String normalizedQuery = normalize(query);
        return sources().stream()
                .filter(source -> normalizedQuery == null || matches(source, normalizedQuery))
                .toList();
    }

    private List<WebFeedSource> sources() {
        if (cachedSources == null) {
            cachedSources = loadSources();
        }
        return cachedSources;
    }

    private List<WebFeedSource> loadSources() {
        try {
            ClassPathResource resource = new ClassPathResource(FEED_SOURCES_PATH);
            WebFeedSourceCatalog catalog = objectMapper.readValue(
                    resource.getInputStream(),
                    WebFeedSourceCatalog.class
            );
            return catalog.sources().stream()
                    .filter(source -> hasText(source.url()))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load web feed sources.", e);
        }
    }

    private boolean matches(WebFeedSource source, String normalizedQuery) {
        return contains(source.label(), normalizedQuery)
                || contains(source.description(), normalizedQuery)
                || contains(source.category(), normalizedQuery)
                || contains(source.language(), normalizedQuery)
                || contains(source.region(), normalizedQuery)
                || contains(source.sourceType(), normalizedQuery)
                || source.tags().stream().anyMatch(tag -> contains(tag, normalizedQuery));
    }

    private static String normalize(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean contains(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record WebFeedSourceCatalog(
            String version,
            List<WebFeedSource> sources
    ) {
        public WebFeedSourceCatalog {
            sources = Objects.requireNonNullElse(sources, List.of());
        }
    }

    public record WebFeedSource(
            String id,
            String label,
            String description,
            String category,
            String language,
            String region,
            String sourceType,
            String url,
            String homepage,
            List<String> tags
    ) {
        public WebFeedSource {
            tags = Objects.requireNonNullElse(tags, List.of());
        }
    }
}
