package org.github.flowify.execution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.workflow.entity.EdgeDefinition;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.github.flowify.workflow.entity.Workflow;
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
            runtimeEdges.add(e);
        }
        runtime.put("edges", runtimeEdges);

        if (workflow.getTrigger() != null) {
            Map<String, Object> trigger = new HashMap<>();
            trigger.put("type", workflow.getTrigger().getType());
            trigger.put("config", workflow.getTrigger().getConfig());
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
        String runtimeType = resolveRuntimeType(node);
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
            runtimeConfig.put("node_type", nullSafe(node.getType()));
            runtimeConfig.put("output_data_type", nullSafe(node.getOutputDataType()));
            if (node.getConfig() != null) {
                runtimeConfig.put("action", node.getConfig().getOrDefault("action", ""));
                runtimeConfig.putAll(node.getConfig());
            }
            runtime.put("runtime_config", runtimeConfig);
        }

        return runtime;
    }

    private String resolveRuntimeType(NodeDefinition node) {
        // role 기반 판단 (최우선)
        if ("start".equals(node.getRole())) {
            return "input";
        }
        if ("end".equals(node.getRole())) {
            return "output";
        }

        // node type 기반 판단
        String nodeType = node.getType();
        if (nodeType != null) {
            String upperType = nodeType.toUpperCase();
            if (LOOP_TYPES.contains(upperType)) {
                return "loop";
            }
            if (BRANCH_TYPES.contains(upperType)) {
                return "if_else";
            }
        }

        // 기본값: middle 노드는 llm
        return "llm";
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
