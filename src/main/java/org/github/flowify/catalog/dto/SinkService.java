package org.github.flowify.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SinkService {

    private String key;
    private String label;

    @JsonProperty("auth_required")
    private boolean authRequired;

    @JsonProperty("accepted_input_types")
    private List<String> acceptedInputTypes;

    @JsonProperty("config_schema_scope")
    private String configSchemaScope = "per_service";

    @JsonProperty("config_schema")
    private Map<String, Object> configSchema;

    @JsonProperty("applicable_when")
    private Map<String, Object> applicableWhen;

    public SinkService(
            String key,
            String label,
            boolean authRequired,
            List<String> acceptedInputTypes,
            String configSchemaScope,
            Map<String, Object> configSchema
    ) {
        this(
                key,
                label,
                authRequired,
                acceptedInputTypes,
                configSchemaScope,
                configSchema,
                Map.of()
        );
    }
}
