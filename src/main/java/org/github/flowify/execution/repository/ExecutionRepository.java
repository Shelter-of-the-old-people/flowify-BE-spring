package org.github.flowify.execution.repository;

import org.github.flowify.execution.entity.WorkflowExecution;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExecutionRepository extends MongoRepository<WorkflowExecution, String> {

    List<WorkflowExecution> findByWorkflowId(String workflowId);

    List<WorkflowExecution> findByUserId(String userId);

    @Query(value = "{ 'userId': ?0, 'finishedAt': { '$exists': true, '$ne': null } }", count = true)
    long countByUserIdAndFinishedAtIsNotNull(String userId);

    @Query(value = "{ 'userId': ?0, 'finishedAt': { '$gte': ?1, '$lt': ?2 } }", count = true)
    long countByUserIdAndFinishedAtBetween(String userId, Instant from, Instant to);

    @Aggregation(pipeline = {
            "{ '$match': { 'userId': ?0, 'finishedAt': { '$exists': true, '$ne': null }, "
                    + "'durationMs': { '$exists': true, '$ne': null } } }",
            "{ '$group': { '_id': null, 'totalDurationMs': { '$sum': '$durationMs' } } }",
            "{ '$project': { '_id': 0, 'totalDurationMs': 1 } }"
    })
    Optional<DurationSumProjection> sumDurationMsByUserId(String userId);

    List<WorkflowExecution> findByUserIdAndStateInAndFinishedAtBetween(String userId,
                                                                       Collection<String> states,
                                                                       Instant from,
                                                                       Instant to);

    List<WorkflowExecution> findTop50ByUserIdOrderByStartedAtDesc(String userId);

    void deleteByUserId(String userId);

    @Aggregation(pipeline = {
            "{ '$match': { 'workflowId': { '$in': ?0 } } }",
            "{ '$sort': { 'workflowId': 1, 'startedAt': -1 } }",
            "{ '$group': { '_id': '$workflowId', 'execution': { '$first': '$$ROOT' } } }",
            "{ '$replaceRoot': { 'newRoot': '$execution' } }"
    })
    List<WorkflowExecution> findLatestByWorkflowIdIn(Collection<String> workflowIds);

    Optional<WorkflowExecution> findFirstByWorkflowIdOrderByStartedAtDesc(String workflowId);

    interface DurationSumProjection {

        Long getTotalDurationMs();
    }
}
