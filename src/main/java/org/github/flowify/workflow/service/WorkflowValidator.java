package org.github.flowify.workflow.service;

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
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "워크플로우 trigger type이 유효하지 않습니다");
        }

        if (WorkflowTriggerSupport.TYPE_MANUAL.equals(triggerType)) {
            return;
        }

        validateScheduleTrigger(normalizedTrigger);
    }

    private void validateScheduleTrigger(TriggerConfig trigger) {
        String scheduleMode = WorkflowTriggerSupport.getScheduleMode(trigger);
        if (!WorkflowTriggerSupport.ALLOWED_SCHEDULE_MODES.contains(scheduleMode)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "워크플로우 schedule mode가 유효하지 않습니다");
        }

        String cron = WorkflowTriggerSupport.getCron(trigger);
        if (!WorkflowTriggerSupport.hasText(cron)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "schedule trigger의 cron 값이 비어 있습니다");
        }

        String timezone = WorkflowTriggerSupport.getTimezone(trigger);
        if (!WorkflowTriggerSupport.hasText(timezone)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "schedule trigger의 timezone 값이 비어 있습니다");
        }

        try {
            ZoneId zoneId = ZoneId.of(timezone);
            new CronTrigger(cron, zoneId);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "schedule trigger 설정이 올바르지 않습니다");
        }

        switch (scheduleMode) {
            case "interval" -> validateIntervalSchedule(trigger);
            case "daily" -> validateDailySchedule(trigger);
            case "weekly" -> validateWeeklySchedule(trigger);
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST, "워크플로우 schedule mode가 유효하지 않습니다");
        }
    }

    private void validateIntervalSchedule(TriggerConfig trigger) {
        Integer intervalHours = WorkflowTriggerSupport.getIntervalHours(trigger);
        if (intervalHours == null || intervalHours < 1 || intervalHours > 24) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "interval_hours는 1 이상 24 이하여야 합니다");
        }
    }

    private void validateDailySchedule(TriggerConfig trigger) {
        if (!WorkflowTriggerSupport.hasText(WorkflowTriggerSupport.getTimeOfDay(trigger))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "daily schedule의 time_of_day 값이 비어 있습니다");
        }
    }

    private void validateWeeklySchedule(TriggerConfig trigger) {
        if (!WorkflowTriggerSupport.hasText(WorkflowTriggerSupport.getTimeOfDay(trigger))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "weekly schedule의 time_of_day 값이 비어 있습니다");
        }

        List<String> weekdays = WorkflowTriggerSupport.getWeekdays(trigger);
        if (weekdays.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "weekly schedule은 최소 1개의 weekday가 필요합니다");
        }

        boolean hasInvalidWeekday = weekdays.stream()
                .anyMatch(weekday -> !WorkflowTriggerSupport.ALLOWED_WEEKDAYS.contains(weekday));
        if (hasInvalidWeekday) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "weekly schedule에 허용되지 않은 weekday가 포함되어 있습니다.");
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
            adjacency.computeIfAbsent(edge.getSource(), key -> new ArrayList<>()).add(edge.getTarget());
        }

        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (NodeDefinition node : nodes) {
            if (hasCycle(node.getId(), adjacency, visited, recursionStack)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, "워크플로우에는 순환 참조가 포함될 수 없습니다");
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
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "노드가 2개 이상이면 최소 한 개의 연결이 필요합니다");
        }

        Set<String> connectedNodes = new HashSet<>();
        for (EdgeDefinition edge : edges) {
            connectedNodes.add(edge.getSource());
            connectedNodes.add(edge.getTarget());
        }

        for (NodeDefinition node : nodes) {
            if (!connectedNodes.contains(node.getId())) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST,
                        "연결되지 않은 노드 '" + node.getId() + "'가 있습니다");
            }
        }
    }

    private void checkRequiredConfig(List<NodeDefinition> nodes) {
        for (NodeDefinition node : nodes) {
            if (node.getCategory() == null || node.getType() == null) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST,
                        "노드 '" + node.getId() + "'의 category 또는 type이 비어 있습니다");
            }
        }
    }

    public void validateForExecution(Workflow workflow, NodeLifecycleService lifecycleService,
                                     CatalogService catalogService, String userId) {
        // 워크플로우 기본 구조를 먼저 검증한다.
        validate(workflow);

        List<NodeDefinition> nodes = workflow.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        List<String> errors = new ArrayList<>();

        // 노드별 lifecycle 검증 결과를 점검한다.
        List<NodeStatusResponse> statuses = lifecycleService.evaluateAll(nodes, userId);
        for (NodeStatusResponse status : statuses) {
            if (!status.isExecutable()) {
                StringBuilder sb = new StringBuilder();
                sb.append("노드 '").append(status.getNodeId()).append("'");
                if (!status.isConfigured()) {
                    sb.append(" 설정이 완료되지 않았습니다");
                } else {
                    sb.append(" 실행 가능 상태가 아닙니다");
                }
                if (status.getMissingFields() != null && !status.getMissingFields().isEmpty()) {
                    sb.append(" (누락 필드: ").append(String.join(", ", status.getMissingFields())).append(")");
                }
                errors.add(sb.toString());
            }
        }

        // source/sink service key가 catalog에 존재하는지 검증한다.
        for (NodeDefinition node : nodes) {
            if ("start".equals(node.getRole()) && node.getType() != null) {
                try {
                    SourceService sourceService = catalogService.findSourceService(node.getType());
                    // source_mode key가 catalog에 존재하는지 검증한다.
                    if (node.getConfig() != null && node.getConfig().containsKey("source_mode")) {
                        String sourceMode = (String) node.getConfig().get("source_mode");
                        if (sourceMode != null && !sourceMode.isBlank()) {
                            if ("github".equals(node.getType())
                                    && "new_pr".equals(sourceMode)
                                    && !NodeLifecycleService.isValidGitHubRepoTarget(resolveSourceTarget(node))) {
                                errors.add("노드 '%s': GitHub 저장소 대상은 owner/repo 형식이어야 합니다"
                                        .formatted(node.getId()));
                            }
                            boolean modeExists = sourceService.getSourceModes().stream()
                                    .anyMatch(mode -> mode.getKey().equals(sourceMode));
                            if (!modeExists) {
                                errors.add("노드 '%s': source 모드 '%s'가 서비스 '%s'의 카탈로그에 존재하지 않습니다"
                                        .formatted(node.getId(), sourceMode, node.getType()));
                            }
                        }
                    }
                } catch (BusinessException e) {
                    errors.add("노드 '%s': source 서비스 '%s'가 카탈로그에 존재하지 않습니다"
                            .formatted(node.getId(), node.getType()));
                }
            }
            if ("end".equals(node.getRole()) && node.getType() != null) {
                try {
                    catalogService.findSinkService(node.getType());
                    if ("gmail".equals(node.getType()) && isGmailDraftAction(node)) {
                        errors.add("노드 '%s': Gmail draft action은 아직 지원되지 않습니다"
                                .formatted(node.getId()));
                    }
                } catch (BusinessException e) {
                    errors.add("노드 '%s': sink 서비스 '%s'가 카탈로그에 존재하지 않습니다"
                            .formatted(node.getId(), node.getType()));
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

    private void checkBranchEdgesForExecution(List<NodeDefinition> nodes,
                                              List<EdgeDefinition> edges,
                                              List<String> errors) {
        if (edges == null || edges.isEmpty()) {
            return;
        }

        Map<String, List<EdgeDefinition>> outgoingEdges = edges.stream()
                .collect(Collectors.groupingBy(EdgeDefinition::getSource));

        for (NodeDefinition node : nodes) {
            if (!isFileTypeBranchNode(node)) {
                continue;
            }

            List<EdgeDefinition> outgoing = outgoingEdges.getOrDefault(node.getId(), List.of());
            if (outgoing.isEmpty()) {
                errors.add("파일 종류 분기 노드 '" + node.getId() + "': 분기 결과로 나가는 연결이 하나 이상 필요합니다");
                continue;
            }

            Set<String> labels = new HashSet<>();
            for (EdgeDefinition edge : outgoing) {
                String label = edge.getLabel();
                if (label == null || label.isBlank()) {
                    errors.add("파일 종류 분기 노드 '" + node.getId() + "': 각 분기 연결에는 edge label이 필요합니다");
                    continue;
                }
                if (!labels.add(label)) {
                    errors.add("파일 종류 분기 노드 '" + node.getId() + "': edge label '" + label + "'이 중복됩니다");
                }
            }
        }
    }

    private boolean isFileTypeBranchNode(NodeDefinition node) {
        if (node.getConfig() == null) {
            return false;
        }
        String choiceActionId = asText(firstPresent(
                node.getConfig().get("choiceActionId"),
                node.getConfig().get("choice_action_id"),
                node.getConfig().get("actionId"),
                node.getConfig().get("action_id")
        ));
        return "branch_by_file_type".equals(choiceActionId);
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

            String sourceOutput = source.getOutputDataType();
            String targetInput = target.getDataType();

            if (sourceOutput == null || sourceOutput.isBlank()
                    || targetInput == null || targetInput.isBlank()) {
                continue;
            }

            if (!sourceOutput.equals(targetInput)) {
                warnings.add(ValidationWarning.builder()
                        .nodeId(target.getId())
                        .message("노드 '" + source.getId() + "'의 출력 타입(" + sourceOutput
                                + ")과 노드 '" + target.getId() + "'의 입력 타입(" + targetInput
                                + ")이 다릅니다")
                        .sourceType(sourceOutput)
                        .targetType(targetInput)
                        .build());
            }
        }

        return warnings;
    }
}