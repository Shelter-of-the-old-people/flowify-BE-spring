package org.github.flowify.template;

import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.template.entity.Template;
import org.github.flowify.template.repository.TemplateRepository;
import org.github.flowify.template.service.TemplateService;
import org.github.flowify.workflow.dto.WorkflowResponse;
import org.github.flowify.workflow.entity.EdgeDefinition;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

        assertThat(testTemplate.getUseCount()).isEqualTo(6);
        verify(templateRepository).save(testTemplate);
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
}
