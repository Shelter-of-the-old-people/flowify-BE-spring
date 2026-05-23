package org.github.flowify.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.workflow.service.choice.ChoiceMappingService;
import org.github.flowify.workflow.service.choice.dto.Action;
import org.github.flowify.workflow.service.choice.dto.ChoiceResponse;
import org.github.flowify.workflow.service.choice.dto.NodeSelectionResult;
import org.github.flowify.workflow.service.choice.dto.Option;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
    @DisplayName("FILE_LIST 처리 방식은 파일 종류 분기 선택지를 반환한다")
    void getOptionsForNode_includesFileListBranchByFileType() {
        ChoiceResponse response = choiceMappingService.getOptionsForNode("FILE_LIST", Map.of());

        assertThat(response.isRequiresProcessingMethod()).isTrue();
        assertThat(response.getOptions())
                .extracting("id")
                .contains("branch_by_file_type");

        assertThat(response.getOptions().stream()
                .filter(option -> "branch_by_file_type".equals(option.getId()))
                .findFirst())
                .hasValueSatisfying(option -> {
                    assertThat(option.getBranchConfig()).isNotNull();
                    assertThat(option.getBranchConfig().getOptions())
                            .extracting("id")
                            .contains("pdf", "image", "other");
                });
    }

    @Test
    @DisplayName("FILE_LIST 파일 종류 분기 선택 시 branchConfig를 반환한다")
    void onUserSelect_returnsBranchConfigForFileListBranchByFileType() {
        NodeSelectionResult result = choiceMappingService.onUserSelect(
                "branch_by_file_type",
                "FILE_LIST",
                Map.of());

        assertThat(result.getNodeType()).isEqualTo("CONDITION_BRANCH");
        assertThat(result.getOutputDataType()).isEqualTo("FILE_LIST");
        assertThat(result.getBranchConfig()).isNotNull();
        assertThat(result.getBranchConfig().getOptions())
                .extracting("id")
                .contains("pdf", "image", "other");
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

    @Test
    @DisplayName("ARTICLE_LIST processing options are returned")
    void getOptionsForNode_includesArticleListProcessingOptions() {
        ChoiceResponse response = choiceMappingService.getOptionsForNode("ARTICLE_LIST", Map.of());

        assertThat(response.isRequiresProcessingMethod()).isTrue();
        assertThat(response.getOptions())
                .extracting("id")
                .contains("one_by_one")
                .doesNotContain("all_at_once");
    }

    @Test
    @DisplayName("SINGLE_EMAIL 선택지 조회는 표로 정리해서 저장 action을 포함한다")
    void getOptionsForNode_includesSingleEmailTableAction() {
        ChoiceResponse response = choiceMappingService.getOptionsForNode("SINGLE_EMAIL", Map.of());

        assertThat(response.getOptions())
                .extracting("id")
                .contains("filter_fields_table");
    }

    @Test
    @DisplayName("SINGLE_FILE 선택지 조회는 파일 정보를 표로 정리 action을 포함한다")
    void getOptionsForNode_includesSingleFileMetadataTableAction() {
        ChoiceResponse response = choiceMappingService.getOptionsForNode(
                "SINGLE_FILE",
                Map.of("file_subtype", "document"));

        assertThat(response.getOptions())
                .extracting("id")
                .contains("filter_metadata_table");
    }

    @Test
    @DisplayName("GitHub service fields는 payload key와 한국어 label을 함께 제공한다")
    void getServiceFields_returnsGithubPayloadKeysWithKoreanLabels() {
        assertThat(choiceMappingService.getServiceFields("github"))
                .extracting(Option::getId, Option::getLabel)
                .contains(
                        tuple("repository", "저장소"),
                        tuple("pr_number", "PR 번호"),
                        tuple("title", "PR 제목"),
                        tuple("author", "작성자"),
                        tuple("url", "PR 링크"),
                        tuple("changed_files", "변경 파일"),
                        tuple("changed_files_count", "변경 파일 수")
                );
    }

    @Test
    @DisplayName("API_RESPONSE 선택지 조회는 런타임 미지원 action을 숨긴다")
    void getOptionsForNode_hidesUnsupportedApiResponseActions() {
        ChoiceResponse response = choiceMappingService.getOptionsForNode(
                "API_RESPONSE",
                Map.of("service", "github"));

        assertThat(response.getOptions())
                .extracting("id")
                .contains("filter_fields", "ai_analyze", "loop")
                .doesNotContain("ai_filter", "condition_value", "merge");
    }

    @Test
    @DisplayName("SPREADSHEET_DATA 선택지 조회는 런타임 미지원 action을 숨긴다")
    void getOptionsForNode_hidesUnsupportedSpreadsheetActions() {
        @SuppressWarnings("unchecked")
        List<Action> filteredActions = (List<Action>) ReflectionTestUtils.invokeMethod(
                choiceMappingService,
                "filterAvailableActions",
                choiceMappingService.getMappingRules().getDataTypes().get("SPREADSHEET_DATA").getActions(),
                Map.of("fields", List.of("status", "owner")));

        assertThat(filteredActions)
                .extracting(Action::getId)
                .contains("filter_fields", "filter_fields_table", "ai_generate", "ai_analyze")
                .doesNotContain("classify_by_field", "filter_condition");
    }

    @Test
    @DisplayName("SCHEDULE_DATA와 TEXT 선택지 조회는 런타임 미지원 action을 숨긴다")
    void getOptionsForNode_hidesUnsupportedScheduleAndTextActions() {
        ChoiceResponse scheduleResponse = choiceMappingService.getOptionsForNode(
                "SCHEDULE_DATA",
                Map.of("service", "google_calendar"));
        ChoiceResponse textResponse = choiceMappingService.getOptionsForNode(
                "TEXT",
                Map.of());

        assertThat(scheduleResponse.getOptions())
                .extracting("id")
                .contains("ai_summarize", "filter_fields")
                .doesNotContain("filter_type", "classify");
        assertThat(textResponse.getOptions())
                .extracting("id")
                .contains("ai_refine", "classify_by_content")
                .doesNotContain("filter_content");
    }

    @Test
    @DisplayName("service key로 legacy service field 매핑을 찾는다")
    void getServiceFields_resolvesLegacyDisplayLabelMappingByServiceKey() {
        assertThat(choiceMappingService.getServiceFields("google_calendar"))
                .hasSize(6);
    }
}
