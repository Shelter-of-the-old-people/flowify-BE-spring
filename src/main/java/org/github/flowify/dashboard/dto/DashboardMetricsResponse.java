package org.github.flowify.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class DashboardMetricsResponse {

    private long todayProcessedCount;
    private long totalProcessedCount;
    private long totalDurationMs;
}
