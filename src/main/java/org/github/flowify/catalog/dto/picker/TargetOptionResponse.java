package org.github.flowify.catalog.dto.picker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class TargetOptionResponse {

    private final List<TargetOptionItem> items;
    private final String nextCursor;
}
