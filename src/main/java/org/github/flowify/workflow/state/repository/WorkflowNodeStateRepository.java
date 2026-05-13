package org.github.flowify.workflow.state.repository;

import org.github.flowify.workflow.state.entity.WorkflowNodeState;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowNodeStateRepository extends MongoRepository<WorkflowNodeState, String> {

    List<WorkflowNodeState> findByWorkflowId(String workflowId);

    Optional<WorkflowNodeState> findByWorkflowIdAndNodeId(String workflowId, String nodeId);
}
