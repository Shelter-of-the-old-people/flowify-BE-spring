package org.github.flowify.catalog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class SourceConfigSummary {

    private final String service;
    private final String serviceLabel;
    private final String mode;
    private final String modeLabel;
    private final String target;
    private final String targetLabel;
    private final String canonicalInputType;
    private final String triggerKind;
}
