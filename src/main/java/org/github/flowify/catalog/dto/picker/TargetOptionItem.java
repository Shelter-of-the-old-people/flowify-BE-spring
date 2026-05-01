package org.github.flowify.catalog.dto.picker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
public class TargetOptionItem {

    private final String id;
    private final String label;
    private final String description;
    private final String type;
    private final Map<String, Object> metadata;
}
