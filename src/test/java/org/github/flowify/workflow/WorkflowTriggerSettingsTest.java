package org.github.flowify.workflow;

import org.github.flowify.catalog.service.CatalogService;
import org.github.flowify.catalog.service.NodeLifecycleService;
import org.github.flowify.common.exception.BusinessException;
import org.github.flowify.common.exception.ErrorCode;
import org.github.flowify.execution.repository.ExecutionRepository;
import org.github.flowify.workflow.dto.WorkflowCreateRequest;
import org.github.flowify.workflow.dto.WorkflowResponse;
import org.github.flowify.workflow.dto.WorkflowUpdateRequest;
import org.github.flowify.workflow.entity.TriggerConfig;
import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.repository.WorkflowRepository;
import org.github.flowify.workflow.service.WorkflowScheduleEvent;
import org.github.flowify.workflow.service.WorkflowService;
import org.github.flowify.workflow.service.WorkflowTriggerSupport;
import org.github.flowify.workflow.service.WorkflowValidator;
import org.github.flowify.workflow.service.choice.ChoiceMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowTriggerSettingsTest {

    @Mock
    private WorkflowRepository workflowRepository;
    @Mock
    private ExecutionRepository executionRepository;
    @Mock
    private WorkflowValidator workflowValidator;
    @Mock
    private ChoiceMappingService choiceMappingService;
    @Mock
    private NodeLifecycleService nodeLifecycleService;
    @Mock
    private CatalogService catalogService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private WorkflowService workflowService;
    private WorkflowValidator validator;

    @BeforeEach
    void setUp() {
        workflowService = new WorkflowService(
                workflowRepository,
                executionRepository,
                workflowValidator,
                choiceMappingService,
                nodeLifecycleService,
                catalogService,
                eventPublisher);
        validator = new WorkflowValidator();
    }

    @Test
    @DisplayName("null trigger is normalized to manual with the default timezone policy untouched")
    void normalizeTrigger_nullBecomesManual() {
        TriggerConfig normalized = WorkflowTriggerSupport.normalizeTrigger(null);

        assertThat(normalized.getType()).isEqualTo("manual");
        assertThat(normalized.getConfig()).isEmpty();
    }

    @Test
    @DisplayName("schedule trigger is normalized with Asia Seoul timezone and skip flag defaults")
    void normalizeTrigger_scheduleAddsDefaults() {
        TriggerConfig normalized = WorkflowTriggerSupport.normalizeTrigger(
                TriggerConfig.builder()
                        .type("schedule")
                        .config(Map.of(
                                "schedule_mode", "interval",
                                "cron", "0 0 */4 * * *",
                                "interval_hours", 4
                        ))
                        .build());

        assertThat(normalized.getConfig())
                .containsEntry("timezone", "Asia/Seoul")
                .containsEntry("skip_if_running", true)
                .containsEntry("interval_hours", 4);
    }

    @Test
    @DisplayName("validator rejects schedule interval outside the supported hour range")
    void validate_rejectsOutOfRangeInterval() {
        Workflow workflow = Workflow.builder()
                .trigger(TriggerConfig.builder()
                        .type("schedule")
                        .config(Map.of(
                                "schedule_mode", "interval",
                                "cron", "0 0 */25 * * *",
                                "timezone", "Asia/Seoul",
                                "interval_hours", 25
                        ))
                        .build())
                .build();

        assertThatThrownBy(() -> validator.validate(workflow))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("validator rejects invalid schedule cron expressions")
    void validate_rejectsInvalidCron() {
        Workflow workflow = Workflow.builder()
                .trigger(TriggerConfig.builder()
                        .type("schedule")
                        .config(Map.of(
                                "schedule_mode", "interval",
                                "cron", "0 */4 * * *",
                                "timezone", "Asia/Seoul",
                                "interval_hours", 4
                        ))
                        .build())
                .build();

        assertThatThrownBy(() -> validator.validate(workflow))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("validator rejects unsupported public schedule modes")
    void validate_rejectsUnsupportedScheduleMode() {
        Workflow workflow = Workflow.builder()
                .trigger(TriggerConfig.builder()
                        .type("schedule")
                        .config(Map.of(
                                "schedule_mode", "cron",
                                "cron", "0 0 */4 * * *",
                                "timezone", "Asia/Seoul"
                        ))
                        .build())
                .build();

        assertThatThrownBy(() -> validator.validate(workflow))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("creating a schedule workflow publishes an immediate register event")
    void createWorkflow_schedulePublishesRegisterEvent() {
        when(workflowValidator.validate(any(Workflow.class))).thenReturn(Collections.emptyList());
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(invocation -> {
            Workflow workflow = invocation.getArgument(0);
            workflow.setId("wf-schedule");
            return workflow;
        });

        WorkflowCreateRequest request = toCreateRequest(Map.of(
                "name", "schedule workflow",
                "trigger", Map.of(
                        "type", "schedule",
                        "config", Map.of(
                                "schedule_mode", "interval",
                                "cron", "0 0 */4 * * *",
                                "interval_hours", 4
                        )
                )));

        workflowService.createWorkflow("user-1", request);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        assertThat(eventCaptor.getValue()).isInstanceOf(WorkflowScheduleEvent.class);
        WorkflowScheduleEvent scheduleEvent = (WorkflowScheduleEvent) eventCaptor.getValue();
        assertThat(scheduleEvent.workflowId()).isEqualTo("wf-schedule");
        assertThat(scheduleEvent.register()).isTrue();
        assertThat(scheduleEvent.cron()).isEqualTo("0 0 */4 * * *");
        assertThat(scheduleEvent.timezone()).isEqualTo("Asia/Seoul");
    }

    @Test
    @DisplayName("manual workflows are normalized back to active true on update")
    void updateWorkflow_manualNormalizesActive() {
        Workflow workflow = Workflow.builder()
                .id("wf-1")
                .name("workflow")
                .userId("user-1")
                .trigger(TriggerConfig.builder().type("manual").config(Map.of()).build())
                .isActive(true)
                .build();

        when(workflowRepository.findById("wf-1")).thenReturn(Optional.of(workflow));
        when(workflowValidator.validate(any(Workflow.class))).thenReturn(Collections.emptyList());
        when(workflowRepository.save(any(Workflow.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowUpdateRequest request = toUpdateRequest(Map.of(
                "trigger", Map.of("type", "manual", "config", Map.of()),
                "active", false
        ));

        WorkflowResponse response = workflowService.updateWorkflow("user-1", "wf-1", request);

        assertThat(response.isActive()).isTrue();
        assertThat(response.getTrigger().getType()).isEqualTo("manual");
    }

    private WorkflowCreateRequest toCreateRequest(Map<String, Object> payload) {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper.convertValue(payload, WorkflowCreateRequest.class);
    }

    private WorkflowUpdateRequest toUpdateRequest(Map<String, Object> payload) {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper.convertValue(payload, WorkflowUpdateRequest.class);
    }
}
