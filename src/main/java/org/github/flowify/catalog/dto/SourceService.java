package org.github.flowify.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SourceService {

    private String key;
    private String label;

    @JsonProperty("auth_required")
    private boolean authRequired;

    @JsonProperty("source_modes")
    private List<SourceMode> sourceModes;
}
