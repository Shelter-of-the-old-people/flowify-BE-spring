package org.github.flowify.execution.repository;

import org.github.flowify.execution.entity.WorkflowExecution;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExecutionRepository extends MongoRepository<WorkflowExecution, String> {

    List<WorkflowExecution> findByWorkflowId(String workflowId);

    List<WorkflowExecution> findByUserId(String userId);

    List<WorkflowExecution> findByUserIdAndFinishedAtIsNotNull(String userId);

    List<WorkflowExecution> findByUserIdAndFinishedAtBetween(String userId, Instant from, Instant to);

    List<WorkflowExecution> findByUserIdAndStateInAndFinishedAtBetween(String userId,
                                                                       Collection<String> states,
                                                                       Instant from,
                                                                       Instant to);

    List<WorkflowExecution> findTop50ByUserIdOrderByStartedAtDesc(String userId);

    void deleteByUserId(String userId);

    List<WorkflowExecution> findByWorkflowIdInOrderByStartedAtDesc(Collection<String> workflowIds);

    Optional<WorkflowExecution> findFirstByWorkflowIdOrderByStartedAtDesc(String workflowId);
}
