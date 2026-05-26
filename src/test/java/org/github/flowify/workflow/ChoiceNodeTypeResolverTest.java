package org.github.flowify.workflow;

import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.service.choice.ChoiceMappingService;
import org.github.flowify.workflow.service.choice.ChoiceNodeTypeResolver;
import org.github.flowify.workflow.service.choice.dto.Action;
import org.github.flowify.workflow.service.choice.dto.DataTypeConfig;
import org.github.flowify.workflow.service.choice.dto.MappingRules;
import org.github.flowify.workflow.service.choice.dto.Option;
import org.github.flowify.workflow.service.choice.dto.ProcessingMethod;
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
    @DisplayName("config choiceNodeType을 UI dataType보다 먼저 사용")
    void resolve_prefersChoiceNodeTypeFromConfig() {
        NodeDefinition node = NodeDefinition.builder()
                .type("condition")
                .dataType("FILE_LIST")
                .config(Map.of(
                        "choiceActionId", "branch_by_file_type",
                        "choiceNodeType", "CONDITION_BRANCH"))
                .build();

        assertThat(choiceNodeTypeResolver.resolve(node)).isEqualTo("CONDITION_BRANCH");
    }

    @Test
    @DisplayName("기존 저장본은 choiceActionId와 dataType으로 의미 타입 복원")
    void resolve_infersChoiceNodeTypeFromActionIdAndDataType() {
        when(choiceMappingService.getMappingRules()).thenReturn(MappingRules.builder()
                .dataTypes(Map.of(
                        "TEXT",
                        DataTypeConfig.builder()
                                .actions(List.of(Action.builder()
                                        .id("classify_by_content")
                                        .nodeType("CONDITION_BRANCH")
                                        .outputDataType("TEXT")
                                        .build()))
                                .build()))
                .build());

        NodeDefinition node = NodeDefinition.builder()
                .type("condition")
                .dataType("TEXT")
                .config(Map.of("choiceActionId", "classify_by_content"))
                .build();

        assertThat(choiceNodeTypeResolver.resolve(node)).isEqualTo("CONDITION_BRANCH");
    }

    @Test
    @DisplayName("CONTENT_EXTRACTOR action을 매핑 규칙에서 복원한다")
    void resolve_infersContentExtractorChoiceNodeTypeFromActionId() {
        when(choiceMappingService.getMappingRules()).thenReturn(MappingRules.builder()
                .dataTypes(Map.of(
                        "SINGLE_FILE",
                        DataTypeConfig.builder()
                                .actions(List.of(Action.builder()
                                        .id("extract_text")
                                        .nodeType("CONTENT_EXTRACTOR")
                                        .outputDataType("TEXT")
                                        .build()))
                                .build()))
                .build());

        NodeDefinition node = NodeDefinition.builder()
                .type("data-process")
                .dataType("SINGLE_FILE")
                .config(Map.of("choiceActionId", "extract_text"))
                .build();

        assertThat(choiceNodeTypeResolver.resolve(node)).isEqualTo("CONTENT_EXTRACTOR");
    }

    @Test
    @DisplayName("processing method 선택지는 choiceActionId로 노드 타입을 복원한다")
    void resolve_infersChoiceNodeTypeFromProcessingMethodOption() {
        when(choiceMappingService.getMappingRules()).thenReturn(MappingRules.builder()
                .dataTypes(Map.of(
                        "FILE_LIST",
                        DataTypeConfig.builder()
                                .processingMethod(ProcessingMethod.builder()
                                        .options(List.of(Option.builder()
                                                .id("branch_by_file_type")
                                                .nodeType("CONDITION_BRANCH")
                                                .outputDataType("FILE_LIST")
                                                .build()))
                                        .build())
                                .actions(List.of())
                                .build()))
                .build());

        NodeDefinition node = NodeDefinition.builder()
                .type("condition")
                .dataType("FILE_LIST")
                .config(Map.of("choiceActionId", "branch_by_file_type"))
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
