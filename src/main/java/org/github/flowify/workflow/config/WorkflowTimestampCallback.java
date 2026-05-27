package org.github.flowify.workflow.config;

import org.github.flowify.workflow.entity.Workflow;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class WorkflowTimestampCallback implements BeforeConvertCallback<Workflow> {

    @Override
    public Workflow onBeforeConvert(Workflow workflow, String collection) {
        Instant now = Instant.now();
        if (workflow.getCreatedAt() == null) {
            workflow.setCreatedAt(now);
        }
        workflow.setUpdatedAt(now);
        return workflow;
    }
}
