package org.github.flowify.workflow.service.generation;

import lombok.RequiredArgsConstructor;
import org.github.flowify.catalog.dto.SinkCatalog;
import org.github.flowify.catalog.dto.SinkService;
import org.github.flowify.catalog.dto.SourceCatalog;
import org.github.flowify.catalog.dto.SourceService;
import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.workflow.dto.NodeStatusResponse;
import org.github.flowify.workflow.dto.WorkflowGenerationAssistantMessageResponse;
import org.github.flowify.workflow.dto.WorkflowGenerationAssistantMessageType;
import org.github.flowify.workflow.dto.WorkflowGenerationNextAction;
import org.github.flowify.workflow.dto.WorkflowGenerationResultResponse;
import org.github.flowify.workflow.dto.WorkflowGenerationStatus;
import org.github.flowify.workflow.dto.WorkflowResponse;
import org.github.flowify.workflow.entity.EdgeDefinition;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkflowGenerationAssistantMessageService {

    private static final int MAX_DISPLAY_NODE_COUNT = 3;
    private static final String ROLE_START = "start";
    private static final String ROLE_END = "end";

    private final CatalogService catalogService;

    public WorkflowGenerationResultResponse buildResult(WorkflowResponse workflow) {
        return buildGeneratedResult(workflow);
    }

    public WorkflowGenerationResultResponse buildGeneratedResult(WorkflowResponse workflow) {
        return buildResult(workflow, AssistantMessageMode.GENERATED);
    }

    public WorkflowGenerationResultResponse buildRefinedResult(WorkflowResponse workflow) {
        return buildResult(workflow, AssistantMessageMode.REFINED);
    }

    private WorkflowGenerationResultResponse buildResult(WorkflowResponse workflow, AssistantMessageMode mode) {
        boolean needsConfiguration = hasConfigurationIssue(workflow);
        List<String> nodeNames = findConfigurationIssueNodeNames(workflow);
        List<String> workflowPathNodeNames = findWorkflowPathNodeNames(workflow);

        return WorkflowGenerationResultResponse.builder()
                .workflow(workflow)
                .assistantMessage(buildAssistantMessage(mode, needsConfiguration, nodeNames, workflowPathNodeNames))
                .assistantMessages(buildAssistantMessages(mode, needsConfiguration, nodeNames, workflowPathNodeNames))
                .status(needsConfiguration
                        ? WorkflowGenerationStatus.NEEDS_CONFIGURATION
                        : WorkflowGenerationStatus.GENERATED)
                .requiresUserAction(needsConfiguration)
                .nextActions(resolveNextActions(needsConfiguration))
                .build();
    }

    private boolean hasConfigurationIssue(WorkflowResponse workflow) {
        if (workflow == null || workflow.getNodeStatuses() == null) {
            return false;
        }

        return workflow.getNodeStatuses().stream()
                .anyMatch(status -> status != null && !status.isConfigured());
    }

    private List<String> findConfigurationIssueNodeNames(WorkflowResponse workflow) {
        if (workflow == null || workflow.getNodeStatuses() == null || workflow.getNodes() == null) {
            return List.of();
        }

        Map<String, NodeDefinition> nodesById = new LinkedHashMap<>();
        for (NodeDefinition node : workflow.getNodes()) {
            if (node != null && hasText(node.getId())) {
                nodesById.put(node.getId(), node);
            }
        }

        Set<String> names = new LinkedHashSet<>();
        for (NodeStatusResponse status : workflow.getNodeStatuses()) {
            if (status == null || status.isConfigured()) {
                continue;
            }

            NodeDefinition node = nodesById.get(status.getNodeId());
            String displayName = displayName(node);
            if (hasText(displayName)) {
                names.add(displayName);
            }
        }

        return new ArrayList<>(names);
    }

    private List<String> findWorkflowPathNodeNames(WorkflowResponse workflow) {
        if (workflow == null || workflow.getNodes() == null || workflow.getNodes().isEmpty()) {
            return List.of();
        }

        Map<String, NodeDefinition> nodesById = new LinkedHashMap<>();
        String startNodeId = null;
        for (NodeDefinition node : workflow.getNodes()) {
            if (node == null || !hasText(node.getId())) {
                continue;
            }

            nodesById.put(node.getId(), node);
            if (ROLE_START.equals(node.getRole())) {
                startNodeId = node.getId();
            }
        }

        if (!hasText(startNodeId) || workflow.getEdges() == null || workflow.getEdges().isEmpty()) {
            return List.of();
        }

        Map<String, String> nextBySource = new LinkedHashMap<>();
        for (EdgeDefinition edge : workflow.getEdges()) {
            if (edge == null || !hasText(edge.getSource()) || !hasText(edge.getTarget())) {
                continue;
            }

            String previousTarget = nextBySource.putIfAbsent(edge.getSource(), edge.getTarget());
            if (previousTarget != null) {
                return List.of();
            }
        }

        List<String> names = new ArrayList<>();
        Set<String> visitedNodeIds = new HashSet<>();
        String currentNodeId = startNodeId;
        while (hasText(currentNodeId)) {
            if (!visitedNodeIds.add(currentNodeId)) {
                return List.of();
            }

            NodeDefinition node = nodesById.get(currentNodeId);
            if (node == null) {
                return List.of();
            }

            String displayName = displayName(node);
            if (hasText(displayName)) {
                names.add(displayName);
            }
            currentNodeId = nextBySource.get(currentNodeId);
        }

        return names.size() >= 2 ? names : List.of();
    }

    private String buildAssistantMessage(
            AssistantMessageMode mode,
            boolean needsConfiguration,
            List<String> nodeNames,
            List<String> workflowPathNodeNames
    ) {
        String prefix = mode == AssistantMessageMode.REFINED
                ? "요청한 내용을 반영했어요."
                : "워크플로우 초안을 만들었어요.";
        String flowSummary = buildFlowSummary(mode, workflowPathNodeNames);
        String nextStepMessage = buildNextStepMessage(needsConfiguration, nodeNames);

        if (hasText(flowSummary)) {
            return prefix + "\n\n" + flowSummary + "\n" + nextStepMessage;
        }

        return prefix + " " + nextStepMessage;
    }

    private List<WorkflowGenerationAssistantMessageResponse> buildAssistantMessages(
            AssistantMessageMode mode,
            boolean needsConfiguration,
            List<String> nodeNames,
            List<String> workflowPathNodeNames
    ) {
        List<WorkflowGenerationAssistantMessageResponse> messages = new ArrayList<>();
        messages.add(assistantMessage(
                WorkflowGenerationAssistantMessageType.SUMMARY,
                "요약",
                mode == AssistantMessageMode.REFINED
                        ? "요청한 내용을 워크플로우 초안에 반영했어요."
                        : "요청한 내용을 바탕으로 워크플로우 초안을 만들었어요.",
                List.of()
        ));

        String flowSummary = buildFlowSummary(mode, workflowPathNodeNames);
        if (hasText(flowSummary)) {
            messages.add(assistantMessage(
                    WorkflowGenerationAssistantMessageType.WORKFLOW_FLOW,
                    "구성한 흐름",
                    flowSummary,
                    workflowPathNodeNames
            ));
        }

        if (needsConfiguration) {
            messages.add(assistantMessage(
                    WorkflowGenerationAssistantMessageType.CONFIGURATION_GUIDE,
                    "설정 확인",
                    buildConfigurationGuideMessage(nodeNames),
                    nodeNames == null ? List.of() : nodeNames
            ));
        }

        messages.add(assistantMessage(
                WorkflowGenerationAssistantMessageType.NEXT_STEP,
                "다음 단계",
                needsConfiguration
                        ? "화면에서 흐름을 검토하고 필요한 설정을 채우면 실행할 수 있습니다."
                        : "화면에서 흐름을 검토한 뒤 바로 실행할 수 있습니다.",
                List.of()
        ));

        return messages;
    }

    private String buildFlowSummary(AssistantMessageMode mode, List<String> workflowPathNodeNames) {
        if (workflowPathNodeNames == null || workflowPathNodeNames.size() < 2) {
            return null;
        }

        String suffix = mode == AssistantMessageMode.REFINED
                ? "흐름으로 정리했습니다."
                : "흐름으로 구성했습니다.";
        return String.join(" → ", workflowPathNodeNames) + " " + suffix;
    }

    private WorkflowGenerationAssistantMessageResponse assistantMessage(
            WorkflowGenerationAssistantMessageType type,
            String title,
            String content,
            List<String> items
    ) {
        return WorkflowGenerationAssistantMessageResponse.builder()
                .type(type)
                .title(title)
                .content(content)
                .items(items == null ? List.of() : List.copyOf(items))
                .build();
    }

    private String buildNextStepMessage(boolean needsConfiguration, List<String> nodeNames) {
        if (!needsConfiguration) {
            return "화면에서 흐름을 검토한 뒤 실행할 수 있습니다.";
        }

        if (nodeNames == null || nodeNames.isEmpty()) {
            return "설정이 필요한 노드를 확인한 뒤 실행할 수 있습니다.";
        }

        String target = formatNodeNames(nodeNames);
        return target + " 설정을 확인하면 실행할 수 있습니다.";
    }

    private String buildConfigurationGuideMessage(List<String> nodeNames) {
        if (nodeNames == null || nodeNames.isEmpty()) {
            return "설정이 필요한 노드를 확인하면 실행할 수 있습니다.";
        }

        return formatNodeNames(nodeNames) + " 설정을 확인하면 실행할 수 있습니다.";
    }

    private List<WorkflowGenerationNextAction> resolveNextActions(boolean needsConfiguration) {
        if (needsConfiguration) {
            return List.of(
                    WorkflowGenerationNextAction.REVIEW_WORKFLOW,
                    WorkflowGenerationNextAction.CONFIGURE_NODES
            );
        }

        return List.of(WorkflowGenerationNextAction.REVIEW_WORKFLOW);
    }

    private String formatNodeNames(List<String> nodeNames) {
        int displayCount = Math.min(nodeNames.size(), MAX_DISPLAY_NODE_COUNT);
        String joinedNames = String.join(", ", nodeNames.subList(0, displayCount));
        int remainingCount = nodeNames.size() - displayCount;

        if (remainingCount <= 0) {
            return joinedNames;
        }

        return joinedNames + " 외 " + remainingCount + "개";
    }

    private String displayName(NodeDefinition node) {
        if (node == null) {
            return null;
        }

        if (ROLE_START.equals(node.getRole())) {
            String catalogLabel = sourceServiceLabel(serviceKey(node));
            if (hasText(catalogLabel)) {
                return catalogLabel;
            }
        }

        if (ROLE_END.equals(node.getRole())) {
            String catalogLabel = sinkServiceLabel(serviceKey(node));
            if (hasText(catalogLabel)) {
                return catalogLabel;
            }
        }

        return fallbackDisplayName(node);
    }

    private String sourceServiceLabel(String serviceKey) {
        if (!hasText(serviceKey) || catalogService == null) {
            return null;
        }

        SourceCatalog sourceCatalog = catalogService.getSourceCatalog();
        if (sourceCatalog == null || sourceCatalog.getServices() == null) {
            return null;
        }

        return sourceCatalog.getServices().stream()
                .filter(service -> service != null && serviceKey.equals(service.getKey()))
                .map(SourceService::getLabel)
                .filter(this::hasText)
                .findFirst()
                .orElse(null);
    }

    private String sinkServiceLabel(String serviceKey) {
        if (!hasText(serviceKey) || catalogService == null) {
            return null;
        }

        SinkCatalog sinkCatalog = catalogService.getSinkCatalog();
        if (sinkCatalog == null || sinkCatalog.getServices() == null) {
            return null;
        }

        return sinkCatalog.getServices().stream()
                .filter(service -> service != null && serviceKey.equals(service.getKey()))
                .map(SinkService::getLabel)
                .filter(this::hasText)
                .findFirst()
                .orElse(null);
    }

    private String serviceKey(NodeDefinition node) {
        if (node.getConfig() != null) {
            Object service = node.getConfig().get("service");
            if (service instanceof String serviceKey && hasText(serviceKey)) {
                return serviceKey.trim();
            }
        }

        if (hasText(node.getType())) {
            return node.getType().trim();
        }

        return null;
    }

    private String fallbackDisplayName(NodeDefinition node) {
        if (hasText(node.getLabel())) {
            return node.getLabel().trim();
        }

        if (hasText(node.getType())) {
            return node.getType().trim();
        }

        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private enum AssistantMessageMode {
        GENERATED,
        REFINED
    }
}
