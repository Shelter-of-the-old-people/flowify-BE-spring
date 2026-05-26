package org.github.flowify.workflow.repository;

import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.dto.WorkflowListProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface WorkflowRepository extends MongoRepository<Workflow, String> {

    String ACCESSIBLE_WORKFLOWS_QUERY = "{ '$or': [ { 'userId': ?0 }, { 'sharedWith': ?1 } ] }";
    String SCHEDULE_ACTIVE_QUERY = "{ '$and': [ "
            + "{ 'trigger.type': 'schedule' }, "
            + "{ '$or': [ { 'isActive': true }, { 'active': true } ] } "
            + "] }";

    String WORKFLOW_LIST_FIELDS = "{ "
            + "'name': 1, "
            + "'description': 1, "
            + "'userId': 1, "
            + "'sharedWith': 1, "
            + "'isTemplate': 1, "
            + "'template': 1, "
            + "'templateId': 1, "
            + "'isActive': 1, "
            + "'active': 1, "
            + "'trigger': 1, "
            + "'latestExecutionId': 1, "
            + "'latestExecutionState': 1, "
            + "'latestExecutionStartedAt': 1, "
            + "'latestExecutionFinishedAt': 1, "
            + "'createdAt': 1, "
            + "'updatedAt': 1, "
            + "'nodes.id': 1, "
            + "'nodes.category': 1, "
            + "'nodes.type': 1, "
            + "'nodes.label': 1, "
            + "'nodes.role': 1, "
            + "'nodes.config.service': 1, "
            + "'nodes.config.source_mode': 1, "
            + "'nodes.config.isConfigured': 1, "
            + "'nodes.dataType': 1, "
            + "'nodes.outputDataType': 1, "
            + "'edges.source': 1, "
            + "'edges.target': 1, "
            + "'edges.label': 1 "
            + "}";

    Page<Workflow> findByUserIdOrSharedWithContaining(String userId, String sharedUserId, Pageable pageable);

    List<Workflow> findByUserIdOrSharedWithContainingOrderByUpdatedAtDesc(String userId, String sharedUserId);

    @Query(value = ACCESSIBLE_WORKFLOWS_QUERY, fields = WORKFLOW_LIST_FIELDS)
    Page<WorkflowListProjection> findListProjectionsByUserIdOrSharedWith(
            String userId,
            String sharedUserId,
            Pageable pageable
    );

    @Query(value = ACCESSIBLE_WORKFLOWS_QUERY, fields = WORKFLOW_LIST_FIELDS)
    List<WorkflowListProjection> findListProjectionsByUserIdOrSharedWith(
            String userId,
            String sharedUserId,
            Sort sort
    );

    @Query(value = "{ '$and': [ "
            + ACCESSIBLE_WORKFLOWS_QUERY + ", "
            + "{ '$or': [ "
            + "{ 'latestExecutionState': { '$in': [ 'pending', 'running' ] } }, "
            + SCHEDULE_ACTIVE_QUERY + " "
            + "] } "
            + "] }", fields = WORKFLOW_LIST_FIELDS)
    Page<WorkflowListProjection> findRunningListProjectionsByUserIdOrSharedWith(
            String userId,
            String sharedUserId,
            Pageable pageable
    );

    @Query(value = "{ '$and': [ "
            + ACCESSIBLE_WORKFLOWS_QUERY + ", "
            + "{ '$nor': [ "
            + "{ 'latestExecutionState': { '$in': [ 'pending', 'running' ] } }, "
            + SCHEDULE_ACTIVE_QUERY + " "
            + "] } "
            + "] }", fields = WORKFLOW_LIST_FIELDS)
    Page<WorkflowListProjection> findStoppedListProjectionsByUserIdOrSharedWith(
            String userId,
            String sharedUserId,
            Pageable pageable
    );

    List<Workflow> findByUserId(String userId);

    void deleteByUserId(String userId);

    List<Workflow> findByTrigger_TypeAndIsActive(String type, boolean active);

    @Query("{ 'trigger.config.webhookId': ?0 }")
    Optional<Workflow> findByWebhookId(String webhookId);
}
