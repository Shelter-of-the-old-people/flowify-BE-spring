package org.github.flowify.template;

import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.template.entity.Template;
import org.github.flowify.template.repository.TemplateRepository;
import org.github.flowify.template.service.TemplateService;
import org.github.flowify.workflow.dto.WorkflowCreateRequest;
import org.github.flowify.workflow.dto.WorkflowResponse;
import org.github.flowify.workflow.entity.EdgeDefinition;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock
    private TemplateRepository templateRepository;
    @Mock
    private WorkflowService workflowService;

    @InjectMocks
    private TemplateService templateService;

    private Template testTemplate;

    @BeforeEach
    void setUp() {
        NodeDefinition node = NodeDefinition.builder()
                .id("n1").category("ai").type("AI").build();
        EdgeDefinition edge = EdgeDefinition.builder().source("n1").target("n2").build();

        testTemplate = Template.builder()
                .id("tpl1")
                .name("테스트 템플릿")
                .description("설명")
                .category("communication")
                .nodes(new ArrayList<>(List.of(node)))
                .edges(new ArrayList<>(List.of(edge)))
                .requiredServices(List.of("google"))
                .isSystem(true)
                .useCount(5)
                .build();
    }

    @Test
    @DisplayName("전체 템플릿 목록 조회")
    void getTemplates_all() {
        when(templateRepository.findAll()).thenReturn(List.of(testTemplate));

        List<Template> result = templateService.getTemplates(null);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("카테고리별 템플릿 목록 조회")
    void getTemplates_byCategory() {
        when(templateRepository.findByCategory("communication")).thenReturn(List.of(testTemplate));

        List<Template> result = templateService.getTemplates("communication");

        assertThat(result).hasSize(1);
        verify(templateRepository).findByCategory("communication");
    }

    @Test
    @DisplayName("folderKey蹂??쒗뵆由?紐⑸줉 議고쉶")
    void getTemplates_byFolderKey() {
        when(templateRepository.findByFolderKey("gmail")).thenReturn(List.of(testTemplate));

        List<Template> result = templateService.getTemplates(null, "gmail");

        assertThat(result).hasSize(1);
        verify(templateRepository).findByFolderKey("gmail");
    }

    @Test
    @DisplayName("移댄뀒怨좊━? folderKey濡??쒗뵆由?紐⑸줉 議고쉶")
    void getTemplates_byCategoryAndFolderKey() {
        when(templateRepository.findByCategoryAndFolderKey("communication", "gmail"))
                .thenReturn(List.of(testTemplate));

        List<Template> result = templateService.getTemplates("communication", "gmail");

        assertThat(result).hasSize(1);
        verify(templateRepository).findByCategoryAndFolderKey("communication", "gmail");
    }

    @Test
    @DisplayName("빈 카테고리는 전체 조회")
    void getTemplates_blankCategory() {
        when(templateRepository.findAll()).thenReturn(List.of(testTemplate));

        List<Template> result = templateService.getTemplates("  ");

        assertThat(result).hasSize(1);
        verify(templateRepository).findAll();
    }

    @Test
    @DisplayName("템플릿 상세 조회 성공")
    void getTemplateById_success() {
        when(templateRepository.findById("tpl1")).thenReturn(Optional.of(testTemplate));

        Template result = templateService.getTemplateById("tpl1");

        assertThat(result.getName()).isEqualTo("테스트 템플릿");
    }

    @Test
    @DisplayName("존재하지 않는 템플릿 조회")
    void getTemplateById_notFound() {
        when(templateRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> templateService.getTemplateById("unknown"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TEMPLATE_NOT_FOUND);
    }

    @Test
    @DisplayName("템플릿 인스턴스화 시 useCount 증가")
    void instantiateTemplate_incrementsUseCount() {
        when(templateRepository.findById("tpl1")).thenReturn(Optional.of(testTemplate));
        when(workflowService.createWorkflow(any(), any()))
                .thenReturn(WorkflowResponse.builder()
                        .id("wf-new").name("테스트 템플릿").build());

        templateService.instantiateTemplate("user123", "tpl1");

        ArgumentCaptor<WorkflowCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(WorkflowCreateRequest.class);
        verify(workflowService).createWorkflow(eq("user123"), requestCaptor.capture());
        WorkflowCreateRequest request = requestCaptor.getValue();

        assertThat(request.getName()).isEqualTo(testTemplate.getName());
        assertThat(request.getDescription()).isEqualTo(testTemplate.getDescription());
        assertThat(request.getNodes()).hasSize(1);
        assertThat(request.getNodes().get(0).getId()).isEqualTo("n1");
        assertThat(request.getNodes().get(0).getCategory()).isEqualTo("ai");
        assertThat(request.getNodes().get(0).getType()).isEqualTo("AI");
        assertThat(request.getEdges()).hasSize(1);
        assertThat(request.getEdges().get(0).getSource()).isEqualTo("n1");
        assertThat(request.getEdges().get(0).getTarget()).isEqualTo("n2");
        assertThat(testTemplate.getUseCount()).isEqualTo(6);
        verify(templateRepository).save(testTemplate);
    }

    @Test
    @DisplayName("Canvas 템플릿 instantiate 시 nodes/edges/config를 workflow draft로 복제한다")
    void instantiateTemplate_copiesCanvasTemplateWorkflowDraft() {
        NodeDefinition canvas = NodeDefinition.builder()
                .id("node_canvas_start")
                .category("service")
                .type("canvas_lms")
                .role("start")
                .outputDataType("FILE_LIST")
                .config(Map.of(
                        "isConfigured", false,
                        "service", "canvas_lms",
                        "source_mode", "course_files",
                        "target", "",
                        "target_label", "",
                        "target_meta", Map.of("pickerType", "course"),
                        "trigger_kind", "manual"))
                .build();
        NodeDefinition loop = NodeDefinition.builder()
                .id("node_loop_files")
                .category("control")
                .type("loop")
                .role("middle")
                .dataType("FILE_LIST")
                .outputDataType("SINGLE_FILE")
                .config(Map.of(
                        "isConfigured", true,
                        "choiceActionId", "one_by_one",
                        "choiceNodeType", "LOOP",
                        "targetField", "items",
                        "maxIterations", 100,
                        "timeout", 300))
                .build();
        NodeDefinition llm = NodeDefinition.builder()
                .id("node_llm_lecture_summary")
                .category("ai")
                .type("llm")
                .role("middle")
                .dataType("SINGLE_FILE")
                .outputDataType("TEXT")
                .config(Map.of(
                        "isConfigured", true,
                        "action", "summarize",
                        "requires_content", true,
                        "choiceActionId", "summarize",
                        "choiceNodeType", "AI",
                        "choiceSelections", Map.of("follow_up", "lecture_flow_quiz")))
                .build();
        NodeDefinition notion = NodeDefinition.builder()
                .id("node_notion_end")
                .category("service")
                .type("notion")
                .role("end")
                .dataType("TEXT")
                .config(Map.of(
                        "isConfigured", false,
                        "service", "notion",
                        "target_type", "page",
                        "target_id", "",
                        "title_template", "Canvas 강의자료 정리 - {{date}}"))
                .build();
        Template canvasTemplate = Template.builder()
                .id("tpl-canvas")
                .name("Canvas 강의자료 정리 Notion 저장")
                .description("Canvas 강의자료를 정리해 Notion에 저장합니다.")
                .category("canvas_lms")
                .folderKey("canvas")
                .nodes(new ArrayList<>(List.of(canvas, loop, llm, notion)))
                .edges(new ArrayList<>(List.of(
                        EdgeDefinition.builder().id("edge_canvas_to_loop").source("node_canvas_start").target("node_loop_files").build(),
                        EdgeDefinition.builder().id("edge_loop_to_llm").source("node_loop_files").target("node_llm_lecture_summary").build(),
                        EdgeDefinition.builder().id("edge_llm_to_notion").source("node_llm_lecture_summary").target("node_notion_end").build())))
                .requiredServices(List.of("canvas_lms", "notion"))
                .isSystem(true)
                .useCount(2)
                .build();

        when(templateRepository.findById("tpl-canvas")).thenReturn(Optional.of(canvasTemplate));
        when(workflowService.createWorkflow(any(), any()))
                .thenReturn(WorkflowResponse.builder()
                        .id("wf-canvas")
                        .name("Canvas 강의자료 정리 Notion 저장")
                        .build());

        templateService.instantiateTemplate("user123", "tpl-canvas");

        ArgumentCaptor<WorkflowCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(WorkflowCreateRequest.class);
        verify(workflowService).createWorkflow(eq("user123"), requestCaptor.capture());
        WorkflowCreateRequest request = requestCaptor.getValue();

        assertThat(request.getName()).isEqualTo("Canvas 강의자료 정리 Notion 저장");
        assertThat(request.getDescription()).isEqualTo("Canvas 강의자료를 정리해 Notion에 저장합니다.");
        assertThat(request.getNodes()).hasSize(4);
        assertThat(request.getEdges()).hasSize(3);

        NodeDefinition copiedCanvas = nodeById(request.getNodes(), "node_canvas_start");
        assertThat(copiedCanvas.getOutputDataType()).isEqualTo("FILE_LIST");
        assertThat(copiedCanvas.getConfig())
                .containsEntry("service", "canvas_lms")
                .containsEntry("source_mode", "course_files")
                .containsEntry("target", "")
                .containsEntry("trigger_kind", "manual");

        NodeDefinition copiedLoop = nodeById(request.getNodes(), "node_loop_files");
        assertThat(copiedLoop.getDataType()).isEqualTo("FILE_LIST");
        assertThat(copiedLoop.getOutputDataType()).isEqualTo("SINGLE_FILE");
        assertThat(copiedLoop.getConfig())
                .containsEntry("choiceActionId", "one_by_one")
                .containsEntry("choiceNodeType", "LOOP");

        NodeDefinition copiedLlm = nodeById(request.getNodes(), "node_llm_lecture_summary");
        assertThat(copiedLlm.getDataType()).isEqualTo("SINGLE_FILE");
        assertThat(copiedLlm.getOutputDataType()).isEqualTo("TEXT");
        assertThat(copiedLlm.getConfig())
                .containsEntry("action", "summarize")
                .containsEntry("requires_content", true)
                .containsEntry("choiceActionId", "summarize");
        assertThat(copiedLlm.getConfig().get("choiceSelections"))
                .isEqualTo(Map.of("follow_up", "lecture_flow_quiz"));

        NodeDefinition copiedNotion = nodeById(request.getNodes(), "node_notion_end");
        assertThat(copiedNotion.getDataType()).isEqualTo("TEXT");
        assertThat(copiedNotion.getConfig())
                .containsEntry("service", "notion")
                .containsEntry("target_type", "page")
                .containsEntry("target_id", "");

        assertThat(request.getEdges())
                .extracting(EdgeDefinition::getSource)
                .containsExactly("node_canvas_start", "node_loop_files", "node_llm_lecture_summary");
        assertThat(request.getEdges())
                .extracting(EdgeDefinition::getTarget)
                .containsExactly("node_loop_files", "node_llm_lecture_summary", "node_notion_end");
        assertThat(canvasTemplate.getUseCount()).isEqualTo(3);
        verify(templateRepository).save(canvasTemplate);
    }

    @Test
    @DisplayName("사용자 템플릿 생성 - 서비스 노드에서 requiredServices 추출")
    void createUserTemplate_extractsServices() {
        NodeDefinition serviceNode1 = NodeDefinition.builder()
                .id("n1").category("service").type("google").build();
        NodeDefinition serviceNode2 = NodeDefinition.builder()
                .id("n2").category("service").type("notion").build();
        NodeDefinition aiNode = NodeDefinition.builder()
                .id("n3").category("ai").type("AI").build();

        Workflow workflow = Workflow.builder()
                .id("wf1")
                .nodes(List.of(serviceNode1, serviceNode2, aiNode))
                .edges(new ArrayList<>())
                .build();

        when(workflowService.findWorkflowOrThrow("wf1")).thenReturn(workflow);
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> {
            Template t = inv.getArgument(0);
            t.setId("tpl-new");
            return t;
        });

        // CreateTemplateRequest에 setter 없으므로 ObjectMapper 사용
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        org.github.flowify.template.dto.CreateTemplateRequest request = mapper.convertValue(
                java.util.Map.of(
                        "workflowId", "wf1",
                        "name", "내 템플릿",
                        "category", "communication",
                        "folderKey", "gmail"
                ),
                org.github.flowify.template.dto.CreateTemplateRequest.class);

        Template result = templateService.createUserTemplate("user123", request);

        assertThat(result.getFolderKey()).isEqualTo("gmail");
        assertThat(result.getRequiredServices()).containsExactlyInAnyOrder("google", "notion");
        assertThat(result.isSystem()).isFalse();
        assertThat(result.getAuthorId()).isEqualTo("user123");
    }

    private static NodeDefinition nodeById(List<NodeDefinition> nodes, String nodeId) {
        return nodes.stream()
                .filter(node -> nodeId.equals(node.getId()))
                .findFirst()
                .orElseThrow();
    }
}
