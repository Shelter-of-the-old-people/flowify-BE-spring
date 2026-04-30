package org.github.flowify.execution.repository;

import org.github.flowify.execution.entity.WorkflowExecution;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ExecutionRepository extends MongoRepository<WorkflowExecution, String> {

    List<WorkflowExecution> findByWorkflowId(String workflowId);

    List<WorkflowExecution> findByUserId(String userId);

    void deleteByUserId(String userId);

    Optional<WorkflowExecution> findFirstByWorkflowIdOrderByStartedAtDesc(String workflowId);
}