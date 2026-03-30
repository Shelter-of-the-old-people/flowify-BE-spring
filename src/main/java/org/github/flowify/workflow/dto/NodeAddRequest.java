package org.github.flowify.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.github.flowify.workflow.entity.Position;

import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NodeAddRequest {

    @NotBlank
    private String category;

    @NotBlank
    private String type;

    private Map<String, Object> config;
    private Position position;
    private String dataType;
    private String outputDataType;
    private String role;
    private boolean authWarning;

    /**
     * 이 노드를 연결할 이전 노드 ID.
     * 지정 시 prevNodeId → 새 노드로의 edge가 자동 생성된다.
     */
    private String prevNodeId;
}
