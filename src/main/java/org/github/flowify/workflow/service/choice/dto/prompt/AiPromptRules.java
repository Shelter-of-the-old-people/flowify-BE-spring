package org.github.flowify.workflow.service.choice.dto.prompt;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPromptRules {

    private String version;

    @JsonProperty("base_prompt")
    private String basePrompt;

    @JsonProperty("data_type_prompts")
    private Map<String, String> dataTypePrompts;

    @JsonProperty("action_prompts")
    private Map<String, Map<String, String>> actionPrompts;

    private Map<String, String> modifiers;
}
