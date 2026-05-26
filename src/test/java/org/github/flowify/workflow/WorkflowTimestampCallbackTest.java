package org.github.flowify.workflow;

import org.github.flowify.workflow.config.WorkflowTimestampCallback;
import org.github.flowify.workflow.entity.Workflow;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowTimestampCallbackTest {

    private final WorkflowTimestampCallback callback = new WorkflowTimestampCallback();

    @Test
    void setsCreatedAndUpdatedTimestampsForNewWorkflow() {
        Workflow workflow = Workflow.builder()
                .name("new workflow")
                .build();

        Instant before = Instant.now();
        Workflow updatedWorkflow = callback.onBeforeConvert(workflow, "workflows");
        Instant after = Instant.now();

        assertNotNull(updatedWorkflow.getCreatedAt());
        assertNotNull(updatedWorkflow.getUpdatedAt());
        assertEquals(updatedWorkflow.getCreatedAt(), updatedWorkflow.getUpdatedAt());
        assertTrue(!updatedWorkflow.getCreatedAt().isBefore(before));
        assertTrue(!updatedWorkflow.getUpdatedAt().isAfter(after));
    }

    @Test
    void preservesCreatedTimestampAndRefreshesUpdatedTimestampForExistingWorkflow() {
        Instant createdAt = Instant.parse("2026-05-26T18:05:49Z");
        Instant previousUpdatedAt = createdAt.plusSeconds(30);
        Workflow workflow = Workflow.builder()
                .id("6a15e0fd1835c531ba768ee8")
                .name("existing workflow")
                .createdAt(createdAt)
                .updatedAt(previousUpdatedAt)
                .build();

        Instant before = Instant.now();
        Workflow updatedWorkflow = callback.onBeforeConvert(workflow, "workflows");
        Instant after = Instant.now();

        assertEquals(createdAt, updatedWorkflow.getCreatedAt());
        assertNotNull(updatedWorkflow.getUpdatedAt());
        assertTrue(updatedWorkflow.getUpdatedAt().isAfter(previousUpdatedAt));
        assertTrue(!updatedWorkflow.getUpdatedAt().isBefore(before));
        assertTrue(!updatedWorkflow.getUpdatedAt().isAfter(after));
    }
}
