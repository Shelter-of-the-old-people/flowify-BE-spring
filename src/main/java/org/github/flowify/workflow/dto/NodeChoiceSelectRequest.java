package org.github.flowify.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NodeChoiceSelectRequest {

    @NotBlank
    private String selectedOptionId;

    @NotBlank
    private String dataType;

    /**
     * 추가 컨텍스트 (예: file_subtype, service, fields 등).
     * applicable_when 필터링이나 options_source 해석에 사용된다.
     */
    private Map<String, Object> context;
}
