package org.github.flowify.template.service;

import lombok.RequiredArgsConstructor;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.template.dto.CreateTemplateRequest;
import org.github.flowify.template.entity.Template;
import org.github.flowify.template.repository.TemplateRepository;
import org.github.flowify.workflow.dto.WorkflowCreateRequest;
import org.github.flowify.workflow.dto.WorkflowResponse;
import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.service.WorkflowService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final WorkflowService workflowService;

    public List<Template> getTemplates(String category) {
        if (category != null && !category.isBlank()) {
            return templateRepository.findByCategory(category);
        }
        return templateRepository.findAll();
    }

    public Template getTemplateById(String id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND));
    }

    public WorkflowResponse instantiateTemplate(String userId, String templateId) {
        Template template = getTemplateById(templateId);

        // 노드/엣지를 복사해서 새 워크플로우를 생성하기 위해 직접 Workflow 빌드
        Workflow workflow = Workflow.builder()
                .name(template.getName())
                .description(template.getDescription())
                .userId(userId)
                .templateId(templateId)
                .nodes(new ArrayList<>(template.getNodes()))
                .edges(new ArrayList<>(template.getEdges()))
                .build();

        // WorkflowService.createWorkflow는 DTO를 받으므로, 여기서 직접 저장 위임
        // 대신 workflowService의 repository에 접근하기 위해 createRequest 형태로 변환
        WorkflowCreateRequest createRequest = new WorkflowCreateRequest();
        // createRequest는 NoArgsConstructor만 있으므로 ObjectMapper로 변환
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.findAndRegisterModules();
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("name", template.getName());
        map.put("description", template.getDescription());
        map.put("nodes", template.getNodes());
        map.put("edges", template.getEdges());
        WorkflowCreateRequest request = mapper.convertValue(map, WorkflowCreateRequest.class);

        WorkflowResponse response = workflowService.createWorkflow(userId, request);

        template.setUseCount(template.getUseCount() + 1);
        templateRepository.save(template);

        return response;
    }

    public Template createUserTemplate(String userId, CreateTemplateRequest request) {
        Workflow workflow = workflowService.findWorkflowOrThrow(request.getWorkflowId());

        Template template = Template.builder()
                .name(request.getName())
                .description(request.getDescription())
                .category(request.getCategory())
                .folderKey(request.getFolderKey())
                .icon(request.getIcon())
                .nodes(new ArrayList<>(workflow.getNodes()))
                .edges(new ArrayList<>(workflow.getEdges()))
                .requiredServices(extractRequiredServices(workflow))
                .isSystem(false)
                .authorId(userId)
                .build();

        return templateRepository.save(template);
    }

    private List<String> extractRequiredServices(Workflow workflow) {
        return workflow.getNodes().stream()
                .filter(node -> "service".equals(node.getCategory()))
                .map(node -> node.getType())
                .distinct()
                .toList();
    }
}
