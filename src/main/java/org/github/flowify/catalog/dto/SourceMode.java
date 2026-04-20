package org.github.flowify.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SourceMode {

    private String key;
    private String label;

    @JsonProperty("canonical_input_type")
    private String canonicalInputType;

    @JsonProperty("trigger_kind")
    private String triggerKind;

    @JsonProperty("target_schema")
    private Map<String, Object> targetSchema;
}
