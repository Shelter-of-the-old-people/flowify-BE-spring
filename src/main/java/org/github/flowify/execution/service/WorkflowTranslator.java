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

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowTranslator {

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
        runtime.put("id", node.getId());
        runtime.put("category", node.getCategory());
        runtime.put("type", node.getType());
        runtime.put("label", node.getLabel());
        runtime.put("config", node.getConfig());
        runtime.put("dataType", node.getDataType());
        runtime.put("outputDataType", node.getOutputDataType());
        runtime.put("role", node.getRole());

        // source 노드: config에서 source_mode, target 정보를 runtime 형태로 전달
        if ("start".equals(node.getRole()) && node.getConfig() != null) {
            runtime.put("runtime_source", Map.of(
                    "service", node.getType(),
                    "mode", node.getConfig().getOrDefault("source_mode", ""),
                    "target", node.getConfig().getOrDefault("target", "")
            ));
        }

        // sink 노드: config에서 sink 설정을 runtime 형태로 전달
        if ("end".equals(node.getRole()) && node.getConfig() != null) {
            runtime.put("runtime_sink", Map.of(
                    "service", node.getType(),
                    "config", node.getConfig()
            ));
        }

        return runtime;
    }
}
