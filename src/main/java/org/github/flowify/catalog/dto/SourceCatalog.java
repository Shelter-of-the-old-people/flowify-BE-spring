package org.github.flowify.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SourceCatalog {

    @JsonProperty("_meta")
    private Meta meta;

    private List<SourceService> services;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Meta {
        private String version;

        @JsonProperty("updated_at")
        private String updatedAt;
    }
}
