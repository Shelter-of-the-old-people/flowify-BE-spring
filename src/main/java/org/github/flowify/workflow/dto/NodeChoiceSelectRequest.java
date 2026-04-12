package org.github.flowify.workflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NodeChoiceSelectRequest {

    @JsonProperty("actionId")
    @NotBlank
    private String selectedOptionId;

    /**
     * 추가 컨텍스트 (예: file_subtype, service, fields 등).
     * applicable_when 필터링이나 options_source 해석에 사용된다.
     */
    private Map<String, Object> context;
}
