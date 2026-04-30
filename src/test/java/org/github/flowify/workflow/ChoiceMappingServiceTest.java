package org.github.flowify.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.workflow.service.choice.ChoiceMappingService;
import org.github.flowify.workflow.service.choice.dto.ChoiceResponse;
import org.github.flowify.workflow.service.choice.dto.NodeSelectionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChoiceMappingServiceTest {

    private ChoiceMappingService choiceMappingService;

    @BeforeEach
    void setUp() {
        choiceMappingService = new ChoiceMappingService(new ObjectMapper());
        ReflectionTestUtils.setField(choiceMappingService, "mappingRulesPath", "docs/mapping_rules.json");
        ReflectionTestUtils.invokeMethod(choiceMappingService, "loadMappingRules");
    }

    @Test
    @DisplayName("GET 선택지 조회는 applicable_when 조건에 맞는 action만 반환한다")
    void getOptionsForNode_filtersByApplicableWhen() {
        ChoiceResponse response = choiceMappingService.getOptionsForNode(
                "SINGLE_FILE",
                Map.of("file_subtype", "document"));

        assertThat(response.getOptions())
                .extracting("id")
                .doesNotContain("describe_image", "ocr");
    }

    @Test
    @DisplayName("POST action 선택은 applicable_when 조건이 불일치하면 거부한다")
    void onUserSelect_rejectsInapplicableAction() {
        assertThatThrownBy(() -> choiceMappingService.onUserSelect(
                "ocr",
                "SINGLE_FILE",
                Map.of("file_subtype", "document")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("POST action 선택은 applicable_when 조건이 맞으면 허용한다")
    void onUserSelect_allowsApplicableAction() {
        NodeSelectionResult result = choiceMappingService.onUserSelect(
                "ocr",
                "SINGLE_FILE",
                Map.of("file_subtype", "image"));

        assertThat(result.getNodeType()).isEqualTo("AI");
        assertThat(result.getOutputDataType()).isEqualTo("TEXT");
    }

    @Test
    @DisplayName("POST action 선택은 context가 없으면 applicable_when action을 거부한다")
    void onUserSelect_rejectsApplicableWhenActionWithoutContext() {
        assertThatThrownBy(() -> choiceMappingService.onUserSelect(
                "describe_image",
                "SINGLE_FILE",
                null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }
}
