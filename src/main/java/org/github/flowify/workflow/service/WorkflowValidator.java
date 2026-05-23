package org.github.flowify.workflow.service;

import org.github.flowify.catalog.dto.SinkService;
import org.github.flowify.catalog.dto.SourceService;
import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.catalog.service.NodeLifecycleService;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.workflow.dto.NodeStatusResponse;
import org.github.flowify.workflow.dto.ValidationWarning;
import org.github.flowify.workflow.entity.EdgeDefinition;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.TriggerConfig;
import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.service.choice.BranchRuntimeConfigResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class WorkflowValidator {

    private static final String CONDITION_BRANCH = "CONDITION_BRANCH";
    private static final Set<String> BRANCH_ACTIONS_REQUIRING_LABELED_EDGES = Set.of(
            "branch_by_file_type",
            "split_announcement_parts"
    );

    private final BranchRuntimeConfigResolver branchRuntimeConfigResolver;

    public WorkflowValidator() {
        this(new BranchRuntimeConfigResolver());
    }

    @Autowired
    public WorkflowValidator(BranchRuntimeConfigResolver branchRuntimeConfigResolver) {
        this.branchRuntimeConfigResolver = branchRuntimeConfigResolver;
    }

    public List<ValidationWarning> validate(Workflow workflow) {
        validateTrigger(workflow.getTrigger());

        List<NodeDefinition> nodes = workflow.getNodes();
        List<EdgeDefinition> edges = workflow.getEdges();

        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyList();
        }

        checkCyclicReference(nodes, edges);
        checkIsolatedNodes(nodes, edges);
        checkRequiredConfig(nodes);

        return checkDataTypeCompatibility(nodes, edges);
    }

    private void validateTrigger(TriggerConfig trigger) {
        TriggerConfig normalizedTrigger = WorkflowTriggerSupport.normalizeTrigger(trigger);
        String triggerType = normalizedTrigger.getType();

        if (!WorkflowTriggerSupport.ALLOWED_TYPES.contains(triggerType)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "지원하지 않는 trigger type입니다.");
        }

        if (WorkflowTriggerSupport.TYPE_MANUAL.equals(triggerType)) {
            return;
        }

        validateScheduleTrigger(normalizedTrigger);
    }

    private void validateScheduleTrigger(TriggerConfig trigger) {
        String scheduleMode = WorkflowTriggerSupport.getScheduleMode(trigger);
        if (!WorkflowTriggerSupport.ALLOWED_SCHEDULE_MODES.contains(scheduleMode)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "지원하지 않는 schedule mode입니다.");
        }

        String cron = WorkflowTriggerSupport.getCron(trigger);
        if (!WorkflowTriggerSupport.hasText(cron)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "schedule trigger에는 cron이 필요합니다.");
        }

        String timezone = WorkflowTriggerSupport.getTimezone(trigger);
        if (!WorkflowTriggerSupport.hasText(timezone)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "schedule trigger에는 timezone이 필요합니다.");
        }

        try {
            ZoneId zoneId = ZoneId.of(timezone);
            new CronTrigger(cron, zoneId);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "유효하지 않은 schedule trigger 설정입니다.");
        }

        switch (scheduleMode) {
            case "interval" -> validateIntervalSchedule(trigger);
            case "daily" -> validateDailySchedule(trigger);
            case "weekly" -> validateWeeklySchedule(trigger);
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST, "지원하지 않는 schedule mode입니다.");
        }
    }

    private void validateIntervalSchedule(TriggerConfig trigger) {
        Integer intervalHours = WorkflowTriggerSupport.getIntervalHours(trigger);
        if (intervalHours == null || intervalHours < 1 || intervalHours > 24) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "interval_hours는 1 이상 24 이하의 정수여야 합니다.");
        }
    }

    private void validateDailySchedule(TriggerConfig trigger) {
        if (!WorkflowTriggerSupport.hasText(WorkflowTriggerSupport.getTimeOfDay(trigger))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "daily schedule에는 time_of_day가 필요합니다.");
        }
    }

    private void validateWeeklySchedule(TriggerConfig trigger) {
        if (!WorkflowTriggerSupport.hasText(WorkflowTriggerSupport.getTimeOfDay(trigger))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "weekly schedule에는 time_of_day가 필요합니다.");
        }

        List<String> weekdays = WorkflowTriggerSupport.getWeekdays(trigger);
        if (weekdays.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "weekly schedule에는 최소 1개 이상의 weekday가 필요합니다.");
        }

        boolean hasInvalidWeekday = weekdays.stream()
                .anyMatch(weekday -> !WorkflowTriggerSupport.ALLOWED_WEEKDAYS.contains(weekday));
        if (hasInvalidWeekday) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "weekly schedule에 유효하지 않은 weekday가 포함되어 있습니다.");
        }
    }

    private void checkCyclicReference(List<NodeDefinition> nodes, List<EdgeDefinition> edges) {
        if (edges == null || edges.isEmpty()) {
            return;
        }

        Map<String, List<String>> adjacency = new HashMap<>();
        for (NodeDefinition node : nodes) {
            adjacency.put(node.getId(), new ArrayList<>());
        }
        for (EdgeDefinition edge : edges) {
            adjacency.computeIfAbsent(edge.getSource(), k -> new ArrayList<>()).add(edge.getTarget());
        }

        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (NodeDefinition node : nodes) {
            if (hasCycle(node.getId(), adjacency, visited, recursionStack)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "워크플로우에 순환 참조가 존재합니다.");
            }
        }
    }

    private boolean hasCycle(String nodeId, Map<String, List<String>> adjacency,
                             Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(nodeId)) {
            return true;
        }
        if (visited.contains(nodeId)) {
            return false;
        }

        visited.add(nodeId);
        recursionStack.add(nodeId);

        List<String> neighbors = adjacency.getOrDefault(nodeId, List.of());
        for (String neighbor : neighbors) {
            if (hasCycle(neighbor, adjacency, visited, recursionStack)) {
                return true;
            }
        }

        recursionStack.remove(nodeId);
        return false;
    }

    private void checkIsolatedNodes(List<NodeDefinition> nodes, List<EdgeDefinition> edges) {
        if (nodes.size() <= 1) {
            return;
        }

        if (edges == null || edges.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "연결되지 않은 노드가 존재합니다.");
        }

        Set<String> connectedNodes = new HashSet<>();
        for (EdgeDefinition edge : edges) {
            connectedNodes.add(edge.getSource());
            connectedNodes.add(edge.getTarget());
        }

        for (NodeDefinition node : nodes) {
            if (!connectedNodes.contains(node.getId())) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST,
                        "노드 '" + node.getId() + "'이(가) 연결되지 않았습니다.");
            }
        }
    }

    private void checkRequiredConfig(List<NodeDefinition> nodes) {
        for (NodeDefinition node : nodes) {
            if (node.getCategory() == null || node.getType() == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST,
                        "노드 '" + node.getId() + "'의 category 또는 type이 누락되었습니다.");
            }
        }
    }

    public void validateForExecution(Workflow workflow, NodeLifecycleService lifecycleService,
                                      CatalogService catalogService, String userId) {
        // 기존 구조 검증 수행
        validate(workflow);

        List<NodeDefinition> nodes = workflow.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        List<String> errors = new ArrayList<>();

        // 노드별 lifecycle 상태 검증
        List<NodeStatusResponse> statuses = lifecycleService.evaluateAll(nodes, userId);
        for (NodeStatusResponse status : statuses) {
            if (!status.isExecutable()) {
                StringBuilder sb = new StringBuilder();
                sb.append("노드 '").append(status.getNodeId()).append("'");
                if (!status.isConfigured()) {
                    sb.append(" 설정이 완료되지 않았습니다");
                } else {
                    sb.append(" 실행 조건을 충족하지 않습니다");
                }
                if (status.getMissingFields() != null && !status.getMissingFields().isEmpty()) {
                    sb.append(" (누락: ").append(String.join(", ", status.getMissingFields())).append(")");
                }
                errors.add(sb.toString());
            }
        }

        // source/sink service key가 catalog에 존재하는지 검증
        for (NodeDefinition node : nodes) {
            if ("start".equals(node.getRole()) && node.getType() != null) {
                try {
                    SourceService sourceService = catalogService.findSourceService(node.getType());
                    // source_mode key도 catalog에 존재하는지 검증
                    if (node.getConfig() != null && node.getConfig().containsKey("source_mode")) {
                        String sourceMode = (String) node.getConfig().get("source_mode");
                        if (sourceMode != null && !sourceMode.isBlank()) {
                            if ("github".equals(node.getType())
                                    && "new_pr".equals(sourceMode)
                                    && !NodeLifecycleService.isValidGitHubRepoTarget(resolveSourceTarget(node))) {
                                errors.add("?몃뱶 '" + node.getId() + "': GitHub ??μ냼 ??곸? owner/repo ?뺤떇?댁뼱???⑸땲??");
                            }
                            boolean modeExists = sourceService.getSourceModes().stream()
                                    .anyMatch(m -> m.getKey().equals(sourceMode));
                            if (!modeExists) {
                                errors.add("노드 '" + node.getId() + "': source 모드 '" + sourceMode
                                        + "'가 서비스 '" + node.getType() + "'의 카탈로그에 존재하지 않습니다");
                            }
                        }
                    }
                } catch (BusinessException e) {
                    errors.add("노드 '" + node.getId() + "': source 서비스 '" + node.getType() + "'가 카탈로그에 존재하지 않습니다");
                }
            }
            if ("end".equals(node.getRole()) && node.getType() != null) {
                try {
                    SinkService sinkService = catalogService.findSinkService(node.getType());
                    if (!isAcceptedSinkInputType(sinkService, node.getDataType())) {
                        errors.add("Node '" + node.getId() + "': sink '" + node.getType()
                                + "' does not accept input type '" + node.getDataType() + "'");
                    }
                    if ("gmail".equals(node.getType()) && isGmailDraftAction(node)) {
                        errors.add("노드 '" + node.getId() + "': Gmail draft action은 아직 지원되지 않습니다");
                    }
                } catch (BusinessException e) {
                    errors.add("노드 '" + node.getId() + "': sink 서비스 '" + node.getType() + "'가 카탈로그에 존재하지 않습니다");
                }
            }
        }

        checkBranchEdgesForExecution(nodes, workflow.getEdges(), errors);

        if (!errors.isEmpty()) {
            throw new BusinessException(ErrorCode.PREFLIGHT_VALIDATION_FAILED,
                    String.join("; ", errors));
        }
    }

    private boolean isGmailDraftAction(NodeDefinition node) {
        Map<String, Object> config = node.getConfig();
        return config != null && "draft".equals(config.get("action"));
    }

    private boolean isAcceptedSinkInputType(SinkService sinkService, String dataType) {
        if (dataType == null || dataType.isBlank()) {
            return true;
        }
        List<String> acceptedInputTypes = sinkService.getAcceptedInputTypes();
        return acceptedInputTypes != null && acceptedInputTypes.contains(dataType);
    }

    private void checkBranchEdgesForExecution(List<NodeDefinition> nodes,
                                              List<EdgeDefinition> edges,
                                              List<String> errors) {
        if (edges == null || edges.isEmpty()) {
            return;
        }

        Map<String, List<EdgeDefinition>> outgoingEdges = edges.stream()
                .collect(Collectors.groupingBy(EdgeDefinition::getSource));

        for (NodeDefinition node : nodes) {
            if (!isBranchNodeRequiringLabeledEdges(node)) {
                continue;
            }

            List<EdgeDefinition> outgoing = outgoingEdges.getOrDefault(node.getId(), List.of());
            if (outgoing.isEmpty()) {
                errors.add("노드 '" + node.getId() + "': 분기의 다음 경로가 없습니다.");
                continue;
            }

            Set<String> labels = new HashSet<>();
            for (EdgeDefinition edge : outgoing) {
                String label = edge.getLabel();
                if (label == null || label.isBlank()) {
                    errors.add("노드 '" + node.getId() + "': 분기 edge label이 필요합니다.");
                    continue;
                }
                if (!labels.add(label)) {
                    errors.add("노드 '" + node.getId() + "': 분기 edge label '" + label + "'이 중복되었습니다.");
                }
            }
        }
    }

    private boolean isBranchNodeRequiringLabeledEdges(NodeDefinition node) {
        if (node.getConfig() == null) {
            return false;
        }
        String choiceActionId = asText(firstPresent(
                node.getConfig().get("choiceActionId"),
                node.getConfig().get("choice_action_id"),
                node.getConfig().get("actionId"),
                node.getConfig().get("action_id")
        ));
        return BRANCH_ACTIONS_REQUIRING_LABELED_EDGES.contains(choiceActionId);
    }

    private String asText(Object value) {
        return value != null ? String.valueOf(value).trim() : "";
    }

    private Object resolveSourceTarget(NodeDefinition node) {
        if (node.getConfig() == null) {
            return null;
        }

        if ("google_sheets".equals(node.getType())) {
            Object spreadsheetId = node.getConfig().get("spreadsheet_id");
            if (spreadsheetId instanceof String value && !value.isBlank()) {
                return value;
            }
        }

        return node.getConfig().get("target");
    }

    private Object firstPresent(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private List<ValidationWarning> checkDataTypeCompatibility(List<NodeDefinition> nodes,
                                                                List<EdgeDefinition> edges) {
        if (edges == null || edges.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, NodeDefinition> nodeMap = nodes.stream()
                .collect(Collectors.toMap(NodeDefinition::getId, Function.identity()));

        List<ValidationWarning> warnings = new ArrayList<>();

        for (EdgeDefinition edge : edges) {
            NodeDefinition source = nodeMap.get(edge.getSource());
            NodeDefinition target = nodeMap.get(edge.getTarget());

            if (source == null || target == null) {
                continue;
            }

            String sourceOutput = resolveEdgeOutputDataType(source, edge);
            String targetInput = target.getDataType();

            if (sourceOutput == null || sourceOutput.isBlank()
                    || targetInput == null || targetInput.isBlank()) {
                continue;
            }

            if (!sourceOutput.equals(targetInput)) {
                warnings.add(ValidationWarning.builder()
                        .nodeId(target.getId())
                        .message("노드 '" + source.getId() + "'의 출력 타입(" + sourceOutput
                                + ")이 노드 '" + target.getId() + "'의 입력 타입(" + targetInput
                                + ")과 호환되지 않습니다.")
                        .sourceType(sourceOutput)
                        .targetType(targetInput)
                        .build());
            }
        }

        return warnings;
    }

    private String resolveEdgeOutputDataType(NodeDefinition source, EdgeDefinition edge) {
        String branchOutput = resolveBranchEdgeOutputDataType(source, edge);
        if (branchOutput != null && !branchOutput.isBlank()) {
            return branchOutput;
        }
        return source.getOutputDataType();
    }

    private String resolveBranchEdgeOutputDataType(NodeDefinition source, EdgeDefinition edge) {
        if (!isBranchNodeRequiringLabeledEdges(source)) {
            return null;
        }

        Map<String, Object> runtimeConfig = branchRuntimeConfigResolver.resolve(source, CONDITION_BRANCH);
        Object rawRules = runtimeConfig.get("branch_rules");
        if (!(rawRules instanceof List<?> rules)) {
            return null;
        }

        for (String edgeKey : edgeKeys(edge)) {
            for (Object rawRule : rules) {
                if (!(rawRule instanceof Map<?, ?> rule)) {
                    continue;
                }
                if (edgeKey.equals(asText(rule.get("key")))) {
                    return asText(rule.get("output_data_type"));
                }
            }
        }

        return null;
    }

    private List<String> edgeKeys(EdgeDefinition edge) {
        List<String> keys = new ArrayList<>();
        appendIfPresent(keys, edge.getLabel());
        appendIfPresent(keys, edge.getSourceHandle());
        return keys;
    }

    private void appendIfPresent(List<String> values, Object value) {
        String text = asText(value);
        if (!text.isBlank() && !values.contains(text)) {
            values.add(text);
        }
    }

}
