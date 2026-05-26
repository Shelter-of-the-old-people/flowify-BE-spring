package org.github.flowify.config;

import org.github.flowify.execution.entity.WorkflowExecution;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.Date;
import java.util.List;

@Configuration
@EnableMongoAuditing
@ConditionalOnBean(MongoDatabaseFactory.class)
public class MongoConfig {

    @Bean
    public ApplicationRunner workflowExecutionIndexInitializer(MongoTemplate mongoTemplate) {
        return args -> mongoTemplate.indexOps(WorkflowExecution.class)
                .ensureIndex(new Index()
                        .on("workflowId", Sort.Direction.ASC)
                        .on("startedAt", Sort.Direction.DESC)
                        .named("workflow_started_idx"));
    }

    @Bean
    public ApplicationRunner legacyWorkflowAuditTimestampBackfill(MongoTemplate mongoTemplate) {
        return args -> {
            Query query = new Query(new Criteria().orOperator(
                    Criteria.where("createdAt").exists(false),
                    Criteria.where("createdAt").is(null),
                    Criteria.where("updatedAt").exists(false),
                    Criteria.where("updatedAt").is(null)
            ));
            query.fields()
                    .include("_id")
                    .include("createdAt")
                    .include("updatedAt");

            List<Document> workflows = mongoTemplate.find(query, Document.class, "workflows");
            for (Document workflow : workflows) {
                backfillWorkflowAuditTimestamps(mongoTemplate, workflow);
            }
        };
    }

    private void backfillWorkflowAuditTimestamps(MongoTemplate mongoTemplate, Document workflow) {
        Object rawId = workflow.get("_id");
        Instant createdAt = toInstant(workflow.get("createdAt"));
        Instant updatedAt = toInstant(workflow.get("updatedAt"));
        Instant inferredTimestamp = resolveObjectIdTimestamp(rawId);

        Instant effectiveCreatedAt = createdAt != null ? createdAt : inferredTimestamp;
        Instant effectiveUpdatedAt = updatedAt != null ? updatedAt : effectiveCreatedAt;

        boolean shouldSetCreatedAt = createdAt == null && effectiveCreatedAt != null;
        boolean shouldSetUpdatedAt = updatedAt == null && effectiveUpdatedAt != null;

        if (!shouldSetCreatedAt && !shouldSetUpdatedAt) {
            return;
        }

        Update update = new Update();
        if (shouldSetCreatedAt) {
            update.set("createdAt", effectiveCreatedAt);
        }
        if (shouldSetUpdatedAt) {
            update.set("updatedAt", effectiveUpdatedAt);
        }

        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(rawId)), update, "workflows");
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Date date) {
            return date.toInstant();
        }
        return null;
    }

    private Instant resolveObjectIdTimestamp(Object rawId) {
        if (rawId instanceof ObjectId objectId) {
            return objectId.getDate().toInstant();
        }
        if (rawId instanceof String text && ObjectId.isValid(text)) {
            return new ObjectId(text).getDate().toInstant();
        }
        return null;
    }
}
