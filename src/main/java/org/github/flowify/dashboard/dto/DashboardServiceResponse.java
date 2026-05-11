package org.github.flowify.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class DashboardServiceResponse {

    private String service;
    private boolean connected;
    private String accountEmail;
    private String expiresAt;
    private String aliasOf;
    private Boolean disconnectable;
    private String reason;
}
