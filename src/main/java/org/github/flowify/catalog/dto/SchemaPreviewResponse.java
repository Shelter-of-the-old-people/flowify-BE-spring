package org.github.flowify.catalog.dto;

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
public class SchemaPreviewResponse {

    @JsonProperty("schema_type")
    private String schemaType;

    @JsonProperty("is_list")
    private boolean isList;

    private List<SchemaField> fields;

    @JsonProperty("display_hints")
    private Map<String, String> displayHints;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SchemaField {
        private String key;
        private String label;

        @JsonProperty("value_type")
        private String valueType;

        private boolean required;
    }
}
