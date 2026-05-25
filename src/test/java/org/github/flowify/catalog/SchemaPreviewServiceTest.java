package org.github.flowify.catalog;

import org.github.flowify.catalog.dto.AiPromptMetadata;
import org.github.flowify.catalog.dto.EnhancedNodePreviewResponse;
import org.github.flowify.catalog.dto.SchemaPreviewResponse;
import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.catalog.service.SchemaPreviewService;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.service.choice.ChoiceNodeTypeResolver;
import org.github.flowify.workflow.service.choice.ChoicePromptResolver;
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
class SchemaPreviewServiceTest {

    @Mock
    private CatalogService catalogService;

    @Mock
    private ChoicePromptResolver choicePromptResolver;

    @Mock
    private ChoiceNodeTypeResolver choiceNodeTypeResolver;

    private SchemaPreviewService schemaPreviewService;

    @BeforeEach
    void setUp() {
        schemaPreviewService = new SchemaPreviewService(
                catalogService,
                choicePromptResolver,
                choiceNodeTypeResolver);
    }

    @Test
    @DisplayName("enhanced node schema preview는 AI prompt metadata를 함께 반환한다")
    void enhancedPreviewNode_includesAiPromptMetadata() {
        NodeDefinition node = NodeDefinition.builder()
                .id("node_ai")
                .type("AI")
                .label("AI")
                .dataType("SINGLE_EMAIL")
                .outputDataType("TEXT")
                .build();

        when(catalogService.getSchemaTypeDefinition("SINGLE_EMAIL")).thenReturn(schema("SINGLE_EMAIL"));
        when(catalogService.getSchemaTypeDefinition("TEXT")).thenReturn(schema("TEXT"));
        when(choiceNodeTypeResolver.resolve(node)).thenReturn("AI");
        when(choicePromptResolver.describe(node, "AI")).thenReturn(AiPromptMetadata.builder()
                .available(true)
                .mode("custom")
                .promptSource("choice_rule_augmented")
                .customPromptMode("append")
                .choiceActionId("summarize")
                .basePromptSummary("이메일 내용을 요약하고 중요 일정과 후속 액션을 정리합니다.")
                .includedInstructions(List.of(
                        "입력 유형: 단일 이메일",
                        "핵심 내용 요약"))
                .build());

        EnhancedNodePreviewResponse response = schemaPreviewService.enhancedPreviewNode(
                "node_ai",
                List.of(node),
                List.of(),
                null);

        assertThat(response.getAiPrompt()).isNotNull();
        assertThat(response.getAiPrompt().getPromptSource()).isEqualTo("choice_rule_augmented");
        assertThat(response.getAiPrompt().getCustomPromptMode()).isEqualTo("append");
        assertThat(response.getAiPrompt().getIncludedInstructions())
                .contains("입력 유형: 단일 이메일");
    }

    private SchemaPreviewResponse schema(String schemaType) {
        return SchemaPreviewResponse.builder()
                .schemaType(schemaType)
                .isList(false)
                .fields(List.of())
                .displayHints(Map.of())
                .build();
    }
}
