package org.github.flowify.catalog.service;

import lombok.RequiredArgsConstructor;
import org.github.flowify.catalog.dto.SchemaPreviewResponse;
import org.github.flowify.workflow.entity.EdgeDefinition;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SchemaPreviewService {

    private final CatalogService catalogService;

    public SchemaPreviewResponse preview(List<NodeDefinition> nodes, List<EdgeDefinition> edges) {
        if (nodes == null || nodes.isEmpty()) {
            return SchemaPreviewResponse.builder()
                    .schemaType("UNKNOWN")
                    .isList(false)
                    .fields(List.of())
                    .displayHints(Map.of("preferred_view", "empty"))
                    .build();
        }

        // end role 노드 또는 마지막 노드에서 outputDataType을 찾는다
        String lastOutputType = resolveLastOutputType(nodes, edges);

        if (lastOutputType == null) {
            return SchemaPreviewResponse.builder()
                    .schemaType("UNDETERMINED")
                    .isList(false)
                    .fields(List.of())
                    .displayHints(Map.of("preferred_view", "empty"))
                    .build();
        }

        return catalogService.getSchemaTypeDefinition(lastOutputType);
    }

    private String resolveLastOutputType(List<NodeDefinition> nodes, List<EdgeDefinition> edges) {
        // end role 노드가 있으면 해당 노드의 직전 노드 outputDataType 사용
        NodeDefinition endNode = nodes.stream()
                .filter(n -> "end".equals(n.getRole()))
                .findFirst()
                .orElse(null);

        if (endNode != null && endNode.getOutputDataType() != null) {
            return endNode.getOutputDataType();
        }

        // end 노드의 직전 노드를 찾는다
        if (endNode != null && edges != null) {
            Map<String, NodeDefinition> nodeMap = new HashMap<>();
            for (NodeDefinition n : nodes) {
                nodeMap.put(n.getId(), n);
            }

            for (EdgeDefinition edge : edges) {
                if (edge.getTarget().equals(endNode.getId())) {
                    NodeDefinition prevNode = nodeMap.get(edge.getSource());
                    if (prevNode != null && prevNode.getOutputDataType() != null) {
                        return prevNode.getOutputDataType();
                    }
                }
            }
        }

        // end 노드가 없으면 마지막으로 outputDataType이 있는 노드를 찾는다
        for (int i = nodes.size() - 1; i >= 0; i--) {
            String outputType = nodes.get(i).getOutputDataType();
            if (outputType != null && !outputType.isBlank()) {
                return outputType;
            }
        }

        return null;
    }
}
