package org.github.flowify.workflow.repository;

import org.github.flowify.workflow.entity.Workflow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface WorkflowRepository extends MongoRepository<Workflow, String> {

    Page<Workflow> findByUserIdOrSharedWithContaining(String userId, String sharedUserId, Pageable pageable);

    List<Workflow> findByUserIdOrSharedWithContainingOrderByUpdatedAtDesc(String userId, String sharedUserId);

    List<Workflow> findByUserId(String userId);

    void deleteByUserId(String userId);
}