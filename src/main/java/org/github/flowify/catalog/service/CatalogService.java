package org.github.flowify.catalog.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.catalog.dto.SchemaPreviewResponse;
import org.github.flowify.catalog.dto.SinkCatalog;
import org.github.flowify.catalog.dto.SinkService;
import org.github.flowify.catalog.dto.SourceCatalog;
import org.github.flowify.catalog.dto.SourceService;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final ObjectMapper objectMapper;

    private SourceCatalog sourceCatalog;
    private SinkCatalog sinkCatalog;
    private Map<String, SchemaPreviewResponse> schemaTypes;

    @PostConstruct
    private void loadCatalogs() {
        try {
            ClassPathResource sourceResource = new ClassPathResource("catalog/source_catalog.json");
            try (InputStream is = sourceResource.getInputStream()) {
                sourceCatalog = objectMapper.readValue(is, SourceCatalog.class);
            }

            ClassPathResource sinkResource = new ClassPathResource("catalog/sink_catalog.json");
            try (InputStream is = sinkResource.getInputStream()) {
                sinkCatalog = objectMapper.readValue(is, SinkCatalog.class);
            }

            ClassPathResource schemaResource = new ClassPathResource("catalog/schema_types.json");
            try (InputStream is = schemaResource.getInputStream()) {
                schemaTypes = objectMapper.readValue(is, new TypeReference<>() {});
            }

            log.info("Catalogs loaded: {} sources, {} sinks, {} schema types",
                    sourceCatalog.getServices().size(),
                    sinkCatalog.getServices().size(),
                    schemaTypes.size());
        } catch (Exception e) {
            throw new RuntimeException("카탈로그 로딩에 실패했습니다.", e);
        }
    }

    public SourceCatalog getSourceCatalog() {
        return sourceCatalog;
    }

    public SinkCatalog getSinkCatalog() {
        return sinkCatalog;
    }

    public SourceService findSourceService(String serviceKey) {
        return sourceCatalog.getServices().stream()
                .filter(s -> s.getKey().equals(serviceKey))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.CATALOG_SERVICE_NOT_FOUND,
                        "source 서비스를 찾을 수 없습니다: " + serviceKey));
    }

    public SinkService findSinkService(String serviceKey) {
        return sinkCatalog.getServices().stream()
                .filter(s -> s.getKey().equals(serviceKey))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.CATALOG_SERVICE_NOT_FOUND,
                        "sink 서비스를 찾을 수 없습니다: " + serviceKey));
    }

    public Map<String, Object> getSinkSchema(String serviceKey, String inputType) {
        SinkService sink = findSinkService(serviceKey);
        if (!sink.getAcceptedInputTypes().contains(inputType)) {
            throw new BusinessException(ErrorCode.CATALOG_INVALID_INPUT_TYPE,
                    "sink '" + serviceKey + "'은(는) inputType '" + inputType + "'을(를) 지원하지 않습니다.");
        }
        return sink.getConfigSchema();
    }

    public SchemaPreviewResponse getSchemaTypeDefinition(String canonicalType) {
        SchemaPreviewResponse schema = schemaTypes.get(canonicalType);
        if (schema == null) {
            throw new BusinessException(ErrorCode.CATALOG_INVALID_INPUT_TYPE,
                    "알 수 없는 canonical type: " + canonicalType);
        }
        return schema;
    }

    public boolean isAuthRequired(String serviceKey) {
        if (serviceKey == null) {
            return false;
        }
        // source catalog에서 먼저 검색
        for (SourceService s : sourceCatalog.getServices()) {
            if (s.getKey().equals(serviceKey)) {
                return s.isAuthRequired();
            }
        }
        // sink catalog에서 검색
        for (SinkService s : sinkCatalog.getServices()) {
            if (s.getKey().equals(serviceKey)) {
                return s.isAuthRequired();
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public List<String> getSinkRequiredFields(String serviceKey) {
        try {
            SinkService sink = findSinkService(serviceKey);
            Map<String, Object> schema = sink.getConfigSchema();
            if (schema == null || !schema.containsKey("fields")) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> fields = (List<Map<String, Object>>) schema.get("fields");
            return fields.stream()
                    .filter(f -> Boolean.TRUE.equals(f.get("required")))
                    .map(f -> (String) f.get("key"))
                    .toList();
        } catch (BusinessException e) {
            return Collections.emptyList();
        }
    }
}
