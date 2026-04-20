package org.github.flowify.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SinkCatalog {

    @JsonProperty("_meta")
    private SourceCatalog.Meta meta;

    private List<SinkService> services;
}
