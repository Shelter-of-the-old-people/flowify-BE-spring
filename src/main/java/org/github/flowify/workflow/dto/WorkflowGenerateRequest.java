package org.github.flowify.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
public class WorkflowGenerateRequest {

    @NotBlank(message = "프롬프트는 필수입니다.")
    private String prompt;
    private Map<String, Object> clarificationContext;
}
