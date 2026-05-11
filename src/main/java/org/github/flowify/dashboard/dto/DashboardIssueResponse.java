package org.github.flowify.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class DashboardIssueResponse {

    private String id;
    private String type;
    private String workflowId;
    private String workflowName;
    @JsonProperty("isActive")
    private boolean isActive;
    private String startService;
    private String endService;
    private Instant occurredAt;
    private String message;
    private List<DashboardIssueItemResponse> items;
}
