package org.github.flowify.workflow;

import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.service.choice.ChoiceMappingService;
import org.github.flowify.workflow.service.choice.ChoiceNodeTypeResolver;
import org.github.flowify.workflow.service.choice.dto.Action;
import org.github.flowify.workflow.service.choice.dto.DataTypeConfig;
import org.github.flowify.workflow.service.choice.dto.MappingRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChoiceNodeTypeResolverTest {

    @Mock
    private ChoiceMappingService choiceMappingService;

    private ChoiceNodeTypeResolver choiceNodeTypeResolver;

    @BeforeEach
    void setUp() {
        choiceNodeTypeResolver = new ChoiceNodeTypeResolver(choiceMappingService);
    }

    @Test
    @DisplayName("config choiceNodeType? UI ??낅낫??먼저 사용")
    void resolve_prefersChoiceNodeTypeFromConfig() {
        NodeDefinition node = NodeDefinition.builder()
                .type("condition")
                .dataType("SINGLE_FILE")
                .config(Map.of(
                        "choiceActionId", "classify_by_type",
                        "choiceNodeType", "CONDITION_BRANCH"))
                .build();

        assertThat(choiceNodeTypeResolver.resolve(node)).isEqualTo("CONDITION_BRANCH");
    }

    @Test
    @DisplayName("기존 저장본은 choiceActionId와 dataType으로 의미 타입 복원")
    void resolve_infersChoiceNodeTypeFromActionIdAndDataType() {
        when(choiceMappingService.getMappingRules()).thenReturn(MappingRules.builder()
                .dataTypes(Map.of(
                        "SINGLE_FILE",
                        DataTypeConfig.builder()
                                .actions(List.of(Action.builder()
                                        .id("classify_by_type")
                                        .nodeType("CONDITION_BRANCH")
                                        .outputDataType("SINGLE_FILE")
                                        .build()))
                                .build()))
                .build());

        NodeDefinition node = NodeDefinition.builder()
                .type("condition")
                .dataType("SINGLE_FILE")
                .config(Map.of("choiceActionId", "classify_by_type"))
                .build();

        assertThat(choiceNodeTypeResolver.resolve(node)).isEqualTo("CONDITION_BRANCH");
    }

    @Test
    @DisplayName("LOOP UI 타입은 의미 타입 LOOP로 보정")
    void resolve_mapsVisualLoopToChoiceNodeType() {
        NodeDefinition node = NodeDefinition.builder()
                .type("loop")
                .config(Map.of())
                .build();

        assertThat(choiceNodeTypeResolver.resolve(node)).isEqualTo("LOOP");
    }
}
