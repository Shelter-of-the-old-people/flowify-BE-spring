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
}
