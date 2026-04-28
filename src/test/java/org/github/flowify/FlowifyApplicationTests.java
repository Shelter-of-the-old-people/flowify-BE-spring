package org.github.flowify;

import org.github.flowify.auth.repository.ExchangeCodeRepository;
import org.github.flowify.execution.repository.ExecutionRepository;
import org.github.flowify.oauth.repository.OAuthTokenRepository;
import org.github.flowify.template.repository.TemplateRepository;
import org.github.flowify.user.repository.UserRepository;
import org.github.flowify.workflow.repository.WorkflowRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class FlowifyApplicationTests {

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private WorkflowRepository workflowRepository;

    @MockitoBean
    private TemplateRepository templateRepository;

    @MockitoBean
    private OAuthTokenRepository oauthTokenRepository;

    @MockitoBean
    private ExecutionRepository executionRepository;

    @MockitoBean
    private ExchangeCodeRepository exchangeCodeRepository;

    @MockitoBean
    private MongoTemplate mongoTemplate;

    @Test
    void contextLoads() {
    }
}
