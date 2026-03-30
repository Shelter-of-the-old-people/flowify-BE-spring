package org.github.flowify.workflow.service.choice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MappingRules {

    @JsonProperty("_meta")
    private Meta meta;

    @JsonProperty("data_types")
    private Map<String, DataTypeConfig> dataTypes;

    @JsonProperty("node_types")
    private Map<String, NodeTypeInfo> nodeTypes;

    @JsonProperty("service_fields")
    private Map<String, List<String>> serviceFields;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Meta {
        private String version;
        private String description;

        @JsonProperty("updated_at")
        private String updatedAt;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeTypeInfo {
        private String label;
        private String description;
    }
}
