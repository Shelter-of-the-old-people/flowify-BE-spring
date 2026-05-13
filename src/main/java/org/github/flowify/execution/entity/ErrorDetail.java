package org.github.flowify.execution.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorDetail {

    private String code;
    private String message;
    private Map<String, Object> context;
    private String stackTrace;
}
