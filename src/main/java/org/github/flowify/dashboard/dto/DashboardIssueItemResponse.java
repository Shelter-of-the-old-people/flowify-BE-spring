package org.github.flowify.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class DashboardIssueItemResponse {

    private String id;
    private String service;
    private String message;
}
