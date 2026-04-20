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

    @JsonProperty("config_schema")
    private Map<String, Object> configSchema;
}
