package org.github.flowify.workflow;

import org.github.flowify.catalog.dto.SinkService;
import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.catalog.service.NodeLifecycleService;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.workflow.dto.NodeStatusResponse;
import org.github.flowify.workflow.dto.ValidationWarning;
import org.github.flowify.workflow.entity.EdgeDefinition;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.service.WorkflowValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowValidatorTest {

    private WorkflowValidator validator;

    @BeforeEach
    void setUp() {
        validator = new WorkflowValidator();
    }

    @Test
    @DisplayName("정상 워크플로우 검증 통과")
    void validate_validWorkflow() {
        Workflow workflow = buildLinearWorkflow();

        List<ValidationWarning> warnings = validator.validate(workflow);

        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("빈 노드 리스트 검증 통과")
    void validate_emptyNodes() {
        Workflow workflow = Workflow.builder()
                .nodes(new ArrayList<>())
                .edges(new ArrayList<>())
                .build();

        List<ValidationWarning> warnings = validator.validate(workflow);

        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("순환 참조 검출")
    void validate_cyclicReference() {
        NodeDefinition node1 = NodeDefinition.builder().id("n1").category("ai").type("AI").build();
        NodeDefinition node2 = NodeDefinition.builder().id("n2").category("ai").type("AI").build();
        EdgeDefinition edge1 = EdgeDefinition.builder().source("n1").target("n2").build();
        EdgeDefinition edge2 = EdgeDefinition.builder().source("n2").target("n1").build();

        Workflow workflow = Workflow.builder()
                .nodes(List.of(node1, node2))
                .edges(List.of(edge1, edge2))
                .build();

        assertThatThrownBy(() -> validator.validate(workflow))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("순환 참조");
    }

    @Test
    @DisplayName("고립 노드 검출")
    void validate_isolatedNode() {
        NodeDefinition node1 = NodeDefinition.builder().id("n1").category("ai").type("AI").build();
        NodeDefinition node2 = NodeDefinition.builder().id("n2").category("ai").type("AI").build();
        NodeDefinition isolated = NodeDefinition.builder().id("n3").category("ai").type("AI").build();
        EdgeDefinition edge = EdgeDefinition.builder().source("n1").target("n2").build();

        Workflow workflow = Workflow.builder()
                .nodes(List.of(node1, node2, isolated))
                .edges(List.of(edge))
                .build();

        assertThatThrownBy(() -> validator.validate(workflow))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("n3");
    }

    @Test
    @DisplayName("필수 설정값(category) 누락 검출")
    void validate_missingCategory() {
        NodeDefinition node = NodeDefinition.builder().id("n1").type("AI").build();

        Workflow workflow = Workflow.builder()
                .nodes(List.of(node))
                .edges(new ArrayList<>())
                .build();

        assertThatThrownBy(() -> validator.validate(workflow))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("category");
    }

    @Test
    @DisplayName("필수 설정값(type) 누락 검출")
    void validate_missingType() {
        NodeDefinition node = NodeDefinition.builder().id("n1").category("ai").build();

        Workflow workflow = Workflow.builder()
                .nodes(List.of(node))
                .edges(new ArrayList<>())
                .build();

        assertThatThrownBy(() -> validator.validate(workflow))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("type");
    }

    @Test
    @DisplayName("데이터 타입 비호환 시 경고 생성")
    void validate_dataTypeIncompatibility_warning() {
        NodeDefinition node1 = NodeDefinition.builder()
                .id("n1").category("storage").type("google_drive")
                .dataType("FILE_LIST").outputDataType("TEXT")
                .build();
        NodeDefinition node2 = NodeDefinition.builder()
                .id("n2").category("spreadsheet").type("google_sheets")
                .dataType("SPREADSHEET_DATA").outputDataType("SPREADSHEET_DATA")
                .build();
        EdgeDefinition edge = EdgeDefinition.builder().source("n1").target("n2").build();

        Workflow workflow = Workflow.builder()
                .nodes(List.of(node1, node2))
                .edges(List.of(edge))
                .build();

        List<ValidationWarning> warnings = validator.validate(workflow);

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0).getNodeId()).isEqualTo("n2");
        assertThat(warnings.get(0).getSourceType()).isEqualTo("TEXT");
        assertThat(warnings.get(0).getTargetType()).isEqualTo("SPREADSHEET_DATA");
    }

    @Test
    @DisplayName("데이터 타입 호환 시 경고 없음")
    void validate_dataTypeCompatible_noWarning() {
        NodeDefinition node1 = NodeDefinition.builder()
                .id("n1").category("ai").type("AI")
                .dataType("SINGLE_FILE").outputDataType("TEXT")
                .build();
        NodeDefinition node2 = NodeDefinition.builder()
                .id("n2").category("storage").type("notion")
                .dataType("TEXT").outputDataType("TEXT")
                .build();
        EdgeDefinition edge = EdgeDefinition.builder().source("n1").target("n2").build();

        Workflow workflow = Workflow.builder()
                .nodes(List.of(node1, node2))
                .edges(List.of(edge))
                .build();

        List<ValidationWarning> warnings = validator.validate(workflow);

        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("announcement part branch edge output types are validated per edge")
    void validate_announcementPartsBranchEdgeTypes_noWarning() {
        NodeDefinition branchNode = NodeDefinition.builder()
                .id("branch")
                .category("logic")
                .type("condition")
                .dataType("SINGLE_ANNOUNCEMENT")
                .outputDataType("SINGLE_ANNOUNCEMENT")
                .config(Map.of("choiceActionId", "split_announcement_parts"))
                .build();
        NodeDefinition bodyTarget = NodeDefinition.builder()
                .id("body-target")
                .category("communication")
                .type("discord")
                .dataType("TEXT")
                .build();
        NodeDefinition attachmentsTarget = NodeDefinition.builder()
                .id("attachments-target")
                .category("communication")
                .type("gmail")
                .dataType("FILE_LIST")
                .build();
        Workflow workflow = Workflow.builder()
                .nodes(List.of(branchNode, bodyTarget, attachmentsTarget))
                .edges(List.of(
                        EdgeDefinition.builder()
                                .source("branch")
                                .target("body-target")
                                .label("Body")
                                .sourceHandle("body")
                                .build(),
                        EdgeDefinition.builder()
                                .source("branch")
                                .target("attachments-target")
                                .label("Attachments")
                                .sourceHandle("attachments")
                                .build()))
                .build();

        List<ValidationWarning> warnings = validator.validate(workflow);

        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("announcement part branch warns when an edge target expects the wrong type")
    void validate_announcementPartsBranchEdgeTypeMismatch_warning() {
        NodeDefinition branchNode = NodeDefinition.builder()
                .id("branch")
                .category("logic")
                .type("condition")
                .dataType("SINGLE_ANNOUNCEMENT")
                .outputDataType("SINGLE_ANNOUNCEMENT")
                .config(Map.of("choiceActionId", "split_announcement_parts"))
                .build();
        NodeDefinition wrongTarget = NodeDefinition.builder()
                .id("wrong-target")
                .category("communication")
                .type("gmail")
                .dataType("FILE_LIST")
                .build();
        Workflow workflow = Workflow.builder()
                .nodes(List.of(branchNode, wrongTarget))
                .edges(List.of(EdgeDefinition.builder()
                        .source("branch")
                        .target("wrong-target")
                        .label("body")
                        .sourceHandle("body")
                        .build()))
                .build();

        List<ValidationWarning> warnings = validator.validate(workflow);

        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst().getNodeId()).isEqualTo("wrong-target");
        assertThat(warnings.getFirst().getSourceType()).isEqualTo("TEXT");
        assertThat(warnings.getFirst().getTargetType()).isEqualTo("FILE_LIST");
    }

    @Test
    @DisplayName("email part branch edge output types are validated per edge")
    void validate_emailPartsBranchEdgeTypes_noWarning() {
        NodeDefinition branchNode = NodeDefinition.builder()
                .id("branch")
                .category("logic")
                .type("condition")
                .dataType("SINGLE_EMAIL")
                .outputDataType("SINGLE_EMAIL")
                .config(Map.of("choiceActionId", "split_email_parts"))
                .build();
        NodeDefinition bodyTarget = NodeDefinition.builder()
                .id("body-target")
                .category("communication")
                .type("discord")
                .dataType("TEXT")
                .build();
        NodeDefinition attachmentsTarget = NodeDefinition.builder()
                .id("attachments-target")
                .category("communication")
                .type("gmail")
                .dataType("FILE_LIST")
                .build();
        Workflow workflow = Workflow.builder()
                .nodes(List.of(branchNode, bodyTarget, attachmentsTarget))
                .edges(List.of(
                        EdgeDefinition.builder()
                                .source("branch")
                                .target("body-target")
                                .label("Body")
                                .sourceHandle("body")
                                .build(),
                        EdgeDefinition.builder()
                                .source("branch")
                                .target("attachments-target")
                                .label("Attachments")
                                .sourceHandle("attachments")
                                .build()))
                .build();

        List<ValidationWarning> warnings = validator.validate(workflow);

        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("email part branch warns when an edge target expects the wrong type")
    void validate_emailPartsBranchEdgeTypeMismatch_warning() {
        NodeDefinition branchNode = NodeDefinition.builder()
                .id("branch")
                .category("logic")
                .type("condition")
                .dataType("SINGLE_EMAIL")
                .outputDataType("SINGLE_EMAIL")
                .config(Map.of("choiceActionId", "split_email_parts"))
                .build();
        NodeDefinition wrongTarget = NodeDefinition.builder()
                .id("wrong-target")
                .category("communication")
                .type("gmail")
                .dataType("FILE_LIST")
                .build();
        Workflow workflow = Workflow.builder()
                .nodes(List.of(branchNode, wrongTarget))
                .edges(List.of(EdgeDefinition.builder()
                        .source("branch")
                        .target("wrong-target")
                        .label("body")
                        .sourceHandle("body")
                        .build()))
                .build();

        List<ValidationWarning> warnings = validator.validate(workflow);

        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst().getNodeId()).isEqualTo("wrong-target");
        assertThat(warnings.getFirst().getSourceType()).isEqualTo("TEXT");
        assertThat(warnings.getFirst().getTargetType()).isEqualTo("FILE_LIST");
    }

    @Test
    @DisplayName("dataType이 null인 노드는 경고 생략")
    void validate_nullDataType_noWarning() {
        NodeDefinition node1 = NodeDefinition.builder()
                .id("n1").category("ai").type("AI")
                .outputDataType("TEXT")
                .build();
        NodeDefinition node2 = NodeDefinition.builder()
                .id("n2").category("ai").type("AI")
                .build(); // dataType null

        EdgeDefinition edge = EdgeDefinition.builder().source("n1").target("n2").build();

        Workflow workflow = Workflow.builder()
                .nodes(List.of(node1, node2))
                .edges(List.of(edge))
                .build();

        List<ValidationWarning> warnings = validator.validate(workflow);

        assertThat(warnings).isEmpty();
    }

    @Test
    @DisplayName("여러 비호환 엣지에서 복수 경고 생성")
    void validate_multipleIncompatibilities() {
        NodeDefinition n1 = NodeDefinition.builder()
                .id("n1").category("ai").type("AI").outputDataType("TEXT").build();
        NodeDefinition n2 = NodeDefinition.builder()
                .id("n2").category("ai").type("AI").dataType("FILE_LIST").outputDataType("EMAIL_LIST").build();
        NodeDefinition n3 = NodeDefinition.builder()
                .id("n3").category("ai").type("AI").dataType("SPREADSHEET_DATA").build();

        EdgeDefinition e1 = EdgeDefinition.builder().source("n1").target("n2").build();
        EdgeDefinition e2 = EdgeDefinition.builder().source("n2").target("n3").build();

        Workflow workflow = Workflow.builder()
                .nodes(List.of(n1, n2, n3))
                .edges(List.of(e1, e2))
                .build();

        List<ValidationWarning> warnings = validator.validate(workflow);

        assertThat(warnings).hasSize(2);
    }

    @Test
    @DisplayName("실행 전 검증에서 Gmail draft action을 차단한다")
    void validateForExecution_blocksGmailDraftAction() {
        NodeDefinition node = NodeDefinition.builder()
                .id("gmail-sink")
                .role("end")
                .category("communication")
                .type("gmail")
                .dataType("TEXT")
                .outputDataType("TEXT")
                .config(Map.of(
                        "to", "receiver@example.com",
                        "subject", "Hello",
                        "action", "draft"
                ))
                .build();
        Workflow workflow = Workflow.builder()
                .nodes(List.of(node))
                .edges(new ArrayList<>())
                .build();
        NodeLifecycleService lifecycleService = mock(NodeLifecycleService.class);
        CatalogService catalogService = mock(CatalogService.class);

        when(lifecycleService.evaluateAll(List.of(node), "user1"))
                .thenReturn(List.of(NodeStatusResponse.builder()
                        .nodeId("gmail-sink")
                        .configured(true)
                        .executable(true)
                        .build()));
        when(catalogService.findSinkService("gmail"))
                .thenReturn(new SinkService("gmail", "Gmail", true, List.of("TEXT"), "per_service", Map.of(), Map.of()));

        assertThatThrownBy(() -> validator.validateForExecution(workflow, lifecycleService, catalogService, "user1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PREFLIGHT_VALIDATION_FAILED);
    }

    @Test
    @DisplayName("execution validation rejects unsupported sink input type")
    void validateForExecution_rejectsUnsupportedSinkInputType() {
        NodeDefinition node = NodeDefinition.builder()
                .id("drive-sink")
                .role("end")
                .category("storage")
                .type("google_drive")
                .dataType("SINGLE_ANNOUNCEMENT")
                .config(Map.of("folder_id", "folder_1"))
                .build();
        Workflow workflow = Workflow.builder()
                .nodes(List.of(node))
                .edges(new ArrayList<>())
                .build();
        NodeLifecycleService lifecycleService = mock(NodeLifecycleService.class);
        CatalogService catalogService = mock(CatalogService.class);

        when(lifecycleService.evaluateAll(List.of(node), "user1"))
                .thenReturn(List.of(NodeStatusResponse.builder()
                        .nodeId("drive-sink")
                        .configured(true)
                        .executable(true)
                        .build()));
        when(catalogService.findSinkService("google_drive"))
                .thenReturn(new SinkService("google_drive", "Google Drive", true,
                        List.of("TEXT", "SINGLE_FILE", "FILE_LIST", "SPREADSHEET_DATA"),
                        "per_service", Map.of()));

        assertThatThrownBy(() -> validator.validateForExecution(workflow, lifecycleService, catalogService, "user1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PREFLIGHT_VALIDATION_FAILED);
    }

    @Test
    @DisplayName("실행 전 파일 종류 분기 edge label 중복을 거부한다")
    void validateForExecution_rejectsDuplicateFileTypeBranchLabels() {
        NodeDefinition branchNode = NodeDefinition.builder()
                .id("branch")
                .category("control")
                .type("condition")
                .dataType("FILE_LIST")
                .outputDataType("FILE_LIST")
                .config(Map.of("choiceActionId", "branch_by_file_type"))
                .build();
        NodeDefinition pdfNode = NodeDefinition.builder()
                .id("pdf")
                .category("processing")
                .type("loop")
                .dataType("FILE_LIST")
                .outputDataType("SINGLE_FILE")
                .build();
        NodeDefinition duplicateNode = NodeDefinition.builder()
                .id("pdf_duplicate")
                .category("processing")
                .type("loop")
                .dataType("FILE_LIST")
                .outputDataType("SINGLE_FILE")
                .build();
        Workflow workflow = Workflow.builder()
                .nodes(List.of(branchNode, pdfNode, duplicateNode))
                .edges(List.of(
                        EdgeDefinition.builder().source("branch").target("pdf").label("pdf").build(),
                        EdgeDefinition.builder().source("branch").target("pdf_duplicate").label("pdf").build()))
                .build();
        NodeLifecycleService lifecycleService = mock(NodeLifecycleService.class);
        CatalogService catalogService = mock(CatalogService.class);
        when(lifecycleService.evaluateAll(workflow.getNodes(), "user-1"))
                .thenReturn(List.of(
                        executableStatus("branch"),
                        executableStatus("pdf"),
                        executableStatus("pdf_duplicate")));

        assertThatThrownBy(() -> validator.validateForExecution(
                workflow,
                lifecycleService,
                catalogService,
                "user-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PREFLIGHT_VALIDATION_FAILED);
    }

    @Test
    @DisplayName("실행 전 GitHub 저장소 대상은 owner/repo 형식이 아니면 거절한다")
    void validateForExecution_rejectsInvalidGithubTarget() {
        NodeDefinition githubNode = NodeDefinition.builder()
                .id("github-start")
                .role("start")
                .category("service")
                .type("github")
                .outputDataType("API_RESPONSE")
                .config(Map.of(
                        "source_mode", "new_pr",
                        "target", "https://github.com/openai/openai-python"
                ))
                .build();
        Workflow workflow = Workflow.builder()
                .nodes(List.of(githubNode))
                .edges(List.of())
                .build();
        NodeLifecycleService lifecycleService = mock(NodeLifecycleService.class);
        CatalogService catalogService = mock(CatalogService.class);

        when(lifecycleService.evaluateAll(List.of(githubNode), "user-1"))
                .thenReturn(List.of(NodeStatusResponse.builder()
                        .nodeId("github-start")
                        .configured(true)
                        .executable(true)
                        .build()));
        when(catalogService.findSourceService("github"))
                .thenReturn(new org.github.flowify.catalog.dto.SourceService(
                        "github",
                        "GitHub",
                        true,
                        List.of(new org.github.flowify.catalog.dto.SourceMode(
                                "new_pr",
                                "새 PR / 리뷰 요청",
                                "API_RESPONSE",
                                "event",
                                Map.of("type", "text_input")
                        ))
                ));

        assertThatThrownBy(() -> validator.validateForExecution(workflow, lifecycleService, catalogService, "user-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PREFLIGHT_VALIDATION_FAILED);
    }

    private Workflow buildLinearWorkflow() {
        NodeDefinition node1 = NodeDefinition.builder()
                .id("n1").category("storage").type("google_drive")
                .dataType("FILE_LIST").outputDataType("TEXT")
                .build();
        NodeDefinition node2 = NodeDefinition.builder()
                .id("n2").category("storage").type("notion")
                .dataType("TEXT").outputDataType("TEXT")
                .build();
        EdgeDefinition edge = EdgeDefinition.builder().source("n1").target("n2").build();

        return Workflow.builder()
                .nodes(List.of(node1, node2))
                .edges(List.of(edge))
                .build();
    }

    private NodeStatusResponse executableStatus(String nodeId) {
        return NodeStatusResponse.builder()
                .nodeId(nodeId)
                .configured(true)
                .saveable(true)
                .choiceable(true)
                .executable(true)
                .missingFields(List.of())
                .build();
    }
}
