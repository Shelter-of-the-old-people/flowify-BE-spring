package org.github.flowify.execution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.workflow.entity.EdgeDefinition;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.TriggerConfig;
import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.service.choice.BranchRuntimeConfigResolver;
import org.github.flowify.workflow.service.choice.ChoiceNodeTypeResolver;
import org.github.flowify.workflow.service.choice.ChoicePromptResolver;
import org.github.flowify.workflow.service.WorkflowTriggerSupport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowTranslator {

    private static final Set<String> LOOP_TYPES = Set.of("LOOP");
    private static final Set<String> BRANCH_TYPES = Set.of("CONDITION_BRANCH");
    private static final Set<String> LLM_TYPES = Set.of("AI", "DATA_FILTER", "AI_FILTER", "PASSTHROUGH");

    private final ChoicePromptResolver choicePromptResolver;
    private final ChoiceNodeTypeResolver choiceNodeTypeResolver;
    private final BranchRuntimeConfigResolver branchRuntimeConfigResolver;

    public Map<String, Object> toRuntimeModel(Workflow workflow) {
        Map<String, Object> runtime = new HashMap<>();
        runtime.put("id", workflow.getId());
        runtime.put("name", workflow.getName());
        runtime.put("userId", workflow.getUserId());

        List<Map<String, Object>> runtimeNodes = new ArrayList<>();
        for (NodeDefinition node : workflow.getNodes()) {
            runtimeNodes.add(translateNode(node));
        }
        runtime.put("nodes", runtimeNodes);

        List<Map<String, Object>> runtimeEdges = new ArrayList<>();
        for (EdgeDefinition edge : workflow.getEdges()) {
            Map<String, Object> e = new HashMap<>();
            e.put("id", edge.getId());
            e.put("source", edge.getSource());
            e.put("target", edge.getTarget());
            putIfHasText(e, "label", edge.getLabel());
            putIfHasText(e, "sourceHandle", edge.getSourceHandle());
            putIfHasText(e, "targetHandle", edge.getTargetHandle());
            runtimeEdges.add(e);
        }
        runtime.put("edges", runtimeEdges);

        TriggerConfig normalizedTrigger = WorkflowTriggerSupport.normalizeTrigger(workflow.getTrigger());
        if (normalizedTrigger != null) {
            Map<String, Object> trigger = new HashMap<>();
            trigger.put("type", normalizedTrigger.getType());
            trigger.put("config", normalizedTrigger.getConfig());
            runtime.put("trigger", trigger);
        }

        log.debug("Workflow translated to runtime model: {} nodes, {} edges",
                runtimeNodes.size(), runtimeEdges.size());
        return runtime;
    }

    private Map<String, Object> translateNode(NodeDefinition node) {
        Map<String, Object> runtime = new HashMap<>();

        // editor 필드 유지 (하위 호환)
        runtime.put("id", node.getId());
        runtime.put("category", node.getCategory());
        runtime.put("type", node.getType());
        runtime.put("label", node.getLabel());
        runtime.put("config", node.getConfig());
        runtime.put("dataType", node.getDataType());
        runtime.put("outputDataType", node.getOutputDataType());
        runtime.put("role", node.getRole());

        // runtime_type 결정 (Spring authoritative)
        String semanticNodeType = choiceNodeTypeResolver.resolve(node);
        String runtimeType = resolveRuntimeType(node, semanticNodeType);
        runtime.put("runtime_type", runtimeType);

        // role별 runtime 구조화 정보 (config null이어도 항상 방출)
        if ("input".equals(runtimeType)) {
            Map<String, Object> source = new HashMap<>();
            source.put("service", nullSafe(node.getType()));
            source.put("canonical_input_type", nullSafe(node.getOutputDataType()));
            if (node.getConfig() != null) {
                source.put("mode", node.getConfig().getOrDefault("source_mode", ""));
                source.put("target", node.getConfig().getOrDefault("target", ""));
            } else {
                source.put("mode", "");
                source.put("target", "");
            }
            runtime.put("runtime_source", source);
        }

        if ("output".equals(runtimeType)) {
            Map<String, Object> sink = new HashMap<>();
            sink.put("service", nullSafe(node.getType()));
            sink.put("config", node.getConfig() != null ? node.getConfig() : Map.of());
            runtime.put("runtime_sink", sink);
        }

        if ("llm".equals(runtimeType) || "loop".equals(runtimeType) || "if_else".equals(runtimeType)) {
            Map<String, Object> runtimeConfig = new HashMap<>();
            if (node.getConfig() != null) {
                runtimeConfig.putAll(node.getConfig());
            }

            Map<String, Object> resolvedPromptConfig = choicePromptResolver.resolve(node, semanticNodeType);
            if (resolvedPromptConfig != null) {
                runtimeConfig.putAll(resolvedPromptConfig);
            }

            Map<String, Object> resolvedBranchConfig = branchRuntimeConfigResolver.resolve(node, semanticNodeType);
            if (resolvedBranchConfig != null) {
                runtimeConfig.putAll(resolvedBranchConfig);
            }

            // Spring이 판정한 런타임 메타데이터는 프론트 config보다 우선한다.
            runtimeConfig.put("node_type", nullSafe(semanticNodeType));
            runtimeConfig.put("output_data_type", nullSafe(node.getOutputDataType()));
            runtime.put("runtime_config", runtimeConfig);
        }

        return runtime;
    }

    private String resolveRuntimeType(NodeDefinition node, String semanticNodeType) {
        // role 기반 판단 (최우선)
        if ("start".equals(node.getRole())) {
            return "input";
        }
        if ("end".equals(node.getRole())) {
            return "output";
        }

        // node type 기반 판단
        String upperType = semanticNodeType != null ? semanticNodeType.toUpperCase() : "";
        if (LOOP_TYPES.contains(upperType)) {
            return "loop";
        }
        if (BRANCH_TYPES.contains(upperType)) {
            return "if_else";
        }
        if (LLM_TYPES.contains(upperType)) {
            return "llm";
        }

        // 기본값: middle 노드는 llm
        return "llm";
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    private void putIfHasText(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }
}
