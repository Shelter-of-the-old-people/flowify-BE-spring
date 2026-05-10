package org.github.flowify.execution;

import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.catalog.service.NodeLifecycleService;
import org.github.flowify.execution.entity.WorkflowExecution;
import org.github.flowify.execution.repository.ExecutionRepository;
import org.github.flowify.execution.service.ExecutionService;
import org.github.flowify.execution.service.FastApiClient;
import org.github.flowify.execution.service.SnapshotService;
import org.github.flowify.execution.service.WorkflowTranslator;
import org.github.flowify.oauth.service.OAuthTokenService;
import org.github.flowify.workflow.entity.TriggerConfig;
import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.service.WorkflowService;
import org.github.flowify.workflow.service.WorkflowValidator;
import org.github.flowify.workflow.state.service.WorkflowNodeStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledExecutionGuardTest {

    @Mock
    private ExecutionRepository executionRepository;
    @Mock
    private WorkflowService workflowService;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private FastApiClient fastApiClient;
    @Mock
    private OAuthTokenService oauthTokenService;
    @Mock
    private CatalogService catalogService;
    @Mock
    private NodeLifecycleService nodeLifecycleService;
    @Mock
    private SnapshotService snapshotService;
    @Mock
    private WorkflowValidator workflowValidator;
    @Mock
    private WorkflowTranslator workflowTranslator;
    @Mock
    private WorkflowNodeStateService workflowNodeStateService;

    private ExecutionService executionService;
    private Workflow scheduleWorkflow;

    @BeforeEach
    void setUp() {
        executionService = new ExecutionService(
                executionRepository,
                workflowService,
                mongoTemplate,
                fastApiClient,
                oauthTokenService,
                catalogService,
                nodeLifecycleService,
                snapshotService,
                workflowValidator,
                workflowTranslator,
                workflowNodeStateService);

        scheduleWorkflow = Workflow.builder()
                .id("wf-schedule")
                .userId("user-1")
                .nodes(new ArrayList<>())
                .edges(new ArrayList<>())
                .trigger(TriggerConfig.builder()
                        .type("schedule")
                        .config(Map.of(
                                "schedule_mode", "interval",
                                "cron", "0 0 */4 * * *",
                                "timezone", "Asia/Seoul"
                        ))
                        .build())
                .build();
    }

    @Test
    @DisplayName("scheduled execution is skipped when another execution is already running")
    void executeScheduled_skipsWhileRunning() {
        WorkflowExecution runningExecution = WorkflowExecution.builder()
                .id("exec-running")
                .workflowId("wf-schedule")
                .userId("user-1")
                .state("running")
                .build();

        when(workflowService.findWorkflowOrThrow("wf-schedule")).thenReturn(scheduleWorkflow);
        when(executionRepository.findFirstByWorkflowIdOrderByStartedAtDesc("wf-schedule"))
                .thenReturn(Optional.of(runningExecution));

        String executionId = executionService.executeScheduled("wf-schedule");

        assertThat(executionId).isNull();
        verify(workflowValidator, never()).validateForExecution(any(), any(), any(), any());
        verify(fastApiClient, never()).execute(any(), any(), any(), anyMap());
    }

    @Test
    @DisplayName("scheduled execution proceeds when skip_if_running is disabled")
    void executeScheduled_runsWhenSkipFlagIsDisabled() {
        scheduleWorkflow.setTrigger(TriggerConfig.builder()
                .type("schedule")
                .config(Map.of(
                        "schedule_mode", "interval",
                        "cron", "0 0 */4 * * *",
                        "timezone", "Asia/Seoul",
                        "skip_if_running", false
                ))
                .build());

        when(workflowService.findWorkflowOrThrow("wf-schedule")).thenReturn(scheduleWorkflow);
        when(workflowTranslator.toRuntimeModel(scheduleWorkflow)).thenReturn(Map.of());
        when(fastApiClient.execute(eq("wf-schedule"), eq("user-1"), any(), anyMap()))
                .thenReturn("exec-new");

        String executionId = executionService.executeScheduled("wf-schedule");

        assertThat(executionId).isEqualTo("exec-new");
        verify(workflowValidator).validateForExecution(scheduleWorkflow, nodeLifecycleService, catalogService, "user-1");
        verify(executionRepository, never()).findFirstByWorkflowIdOrderByStartedAtDesc("wf-schedule");
    }
}
