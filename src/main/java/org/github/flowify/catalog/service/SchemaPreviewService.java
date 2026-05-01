package org.github.flowify.catalog.service;

import lombok.RequiredArgsConstructor;
import org.github.flowify.catalog.dto.EnhancedNodePreviewResponse;
import org.github.flowify.catalog.dto.NodeSchemaPreviewResponse;
import org.github.flowify.catalog.dto.NodeInputPreview;
import org.github.flowify.catalog.dto.NodeOutputPreview;
import org.github.flowify.catalog.dto.NodeStatusSummary;
import org.github.flowify.catalog.dto.SchemaPreviewResponse;
import org.github.flowify.catalog.dto.SourceConfigSummary;
import org.github.flowify.catalog.dto.SourceMode;
import org.github.flowify.catalog.dto.SourceService;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.workflow.dto.NodeStatusResponse;
import org.github.flowify.workflow.entity.EdgeDefinition;
import org.github.flowify.workflow.entity.NodeDefinition;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    public NodeSchemaPreviewResponse previewNode(String nodeId, String dataType, String outputDataType) {
        SchemaPreviewResponse inputSchema = resolveSchema(dataType);
        SchemaPreviewResponse outputSchema = resolveSchema(outputDataType);

        return NodeSchemaPreviewResponse.builder()
                .nodeId(nodeId)
                .input(inputSchema)
                .output(outputSchema)
                .build();
    }

    public EnhancedNodePreviewResponse enhancedPreviewNode(String nodeId,
                                                           List<NodeDefinition> allNodes,
                                                           List<EdgeDefinition> edges,
                                                           NodeStatusResponse status) {
        NodeDefinition node = findNodeOrThrow(allNodes, nodeId);
        NodeDefinition sourceNode = findSourceNode(nodeId, allNodes, edges);

        String inputType = sourceNode != null ? sourceNode.getOutputDataType() : node.getDataType();
        NodeInputPreview input = inputType == null || inputType.isBlank()
                ? null
                : NodeInputPreview.builder()
                        .dataType(inputType)
                        .label(labelForType(inputType))
                        .sourceNodeId(sourceNode != null ? sourceNode.getId() : null)
                        .sourceNodeLabel(sourceNode != null ? sourceNode.getLabel() : null)
                        .schema(resolveSchema(inputType))
                        .build();

        String outputType = node.getOutputDataType();
        NodeOutputPreview output = outputType == null || outputType.isBlank()
                ? null
                : NodeOutputPreview.builder()
                        .dataType(outputType)
                        .label(labelForType(outputType))
                        .schema(resolveSchema(outputType))
                        .build();

        return EnhancedNodePreviewResponse.builder()
                .nodeId(nodeId)
                .input(input)
                .output(output)
                .source("start".equals(node.getRole()) ? resolveSourceSummary(node) : null)
                .nodeStatus(toNodeStatusSummary(status))
                .build();
    }

    public SourceConfigSummary resolveSourceSummary(NodeDefinition startNode) {
        Map<String, Object> config = startNode.getConfig();
        if (config == null) {
            return null;
        }

        String serviceKey = asString(config.getOrDefault("service", startNode.getType()));
        String modeKey = asString(config.get("source_mode"));
        String target = asString(config.get("target"));
        String targetLabel = asString(config.getOrDefault("target_label", target));

        if (serviceKey == null || serviceKey.isBlank()) {
            return null;
        }

        SourceService sourceService = catalogService.findSourceService(serviceKey);
        SourceMode sourceMode = sourceService.getSourceModes() == null ? null
                : sourceService.getSourceModes().stream()
                        .filter(mode -> Objects.equals(mode.getKey(), modeKey))
                        .findFirst()
                        .orElse(null);

        return SourceConfigSummary.builder()
                .service(serviceKey)
                .serviceLabel(sourceService.getLabel())
                .mode(modeKey)
                .modeLabel(sourceMode != null ? sourceMode.getLabel() : null)
                .target(target)
                .targetLabel(targetLabel)
                .canonicalInputType(sourceMode != null
                        ? sourceMode.getCanonicalInputType()
                        : asString(config.get("canonical_input_type")))
                .triggerKind(sourceMode != null
                        ? sourceMode.getTriggerKind()
                        : asString(config.get("trigger_kind")))
                .build();
    }

    private SchemaPreviewResponse resolveSchema(String schemaType) {
        if (schemaType == null || schemaType.isBlank()) {
            return SchemaPreviewResponse.builder()
                    .schemaType("UNKNOWN")
                    .isList(false)
                    .fields(List.of())
                    .displayHints(Map.of("preferred_view", "empty"))
                    .build();
        }
        return catalogService.getSchemaTypeDefinition(schemaType);
    }

    private NodeDefinition findNodeOrThrow(List<NodeDefinition> nodes, String nodeId) {
        if (nodes == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "노드 '" + nodeId + "'을(를) 찾을 수 없습니다.");
        }
        return nodes.stream()
                .filter(node -> nodeId.equals(node.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST,
                        "노드 '" + nodeId + "'을(를) 찾을 수 없습니다."));
    }

    private NodeDefinition findSourceNode(String nodeId, List<NodeDefinition> nodes, List<EdgeDefinition> edges) {
        if (edges == null || nodes == null) {
            return null;
        }

        String sourceNodeId = edges.stream()
                .filter(edge -> nodeId.equals(edge.getTarget()))
                .map(EdgeDefinition::getSource)
                .findFirst()
                .orElse(null);

        if (sourceNodeId == null) {
            return null;
        }

        return nodes.stream()
                .filter(node -> sourceNodeId.equals(node.getId()))
                .findFirst()
                .orElse(null);
    }

    private NodeStatusSummary toNodeStatusSummary(NodeStatusResponse status) {
        if (status == null) {
            return null;
        }

        return NodeStatusSummary.builder()
                .configured(status.isConfigured())
                .executable(status.isExecutable())
                .missingFields(status.getMissingFields())
                .build();
    }

    private String labelForType(String type) {
        return switch (type) {
            case "FILE_LIST" -> "파일 목록";
            case "SINGLE_FILE" -> "단일 파일";
            case "EMAIL_LIST" -> "이메일 목록";
            case "SINGLE_EMAIL" -> "단일 이메일";
            case "SPREADSHEET_DATA" -> "스프레드시트 데이터";
            case "API_RESPONSE" -> "API 응답";
            case "SCHEDULE_DATA" -> "일정 데이터";
            case "TEXT" -> "텍스트";
            default -> type;
        };
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
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
