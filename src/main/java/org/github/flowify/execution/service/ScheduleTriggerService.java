package org.github.flowify.execution.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.github.flowify.workflow.entity.TriggerConfig;
import org.github.flowify.workflow.entity.Workflow;
import org.github.flowify.workflow.repository.WorkflowRepository;
import org.github.flowify.workflow.service.WorkflowScheduleEvent;
import org.github.flowify.workflow.service.WorkflowTriggerSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
public class ScheduleTriggerService {

    private final TaskScheduler taskScheduler;
    private final WorkflowRepository workflowRepository;
    private final ExecutionService executionService;

    private final ConcurrentHashMap<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public ScheduleTriggerService(TaskScheduler taskScheduler,
                                   WorkflowRepository workflowRepository,
                                   @Autowired @Lazy ExecutionService executionService) {
        this.taskScheduler = taskScheduler;
        this.workflowRepository = workflowRepository;
        this.executionService = executionService;
    }

    @PostConstruct
    public void reloadSchedulesFromDb() {
        List<Workflow> workflows = workflowRepository.findByTrigger_TypeAndIsActive("schedule", true);
        for (Workflow workflow : workflows) {
            TriggerConfig trigger = workflow.getTrigger();
            if (WorkflowTriggerSupport.isSchedule(trigger)) {
                String cron = WorkflowTriggerSupport.getCron(trigger);
                String timezone = WorkflowTriggerSupport.getTimezone(trigger);
                if (!cron.isBlank()) {
                    registerSchedule(workflow.getId(), cron, timezone);
                }
            }
        }
        log.info("Reloaded {} schedule trigger(s) from DB", scheduledTasks.size());
    }

    @EventListener
    public void onScheduleEvent(WorkflowScheduleEvent event) {
        if (event.register()) {
            registerSchedule(event.workflowId(), event.cron(), event.timezone());
        } else {
            unregisterSchedule(event.workflowId());
        }
    }

    public void registerSchedule(String workflowId, String cron, String timezone) {
        unregisterSchedule(workflowId);

        ZoneId zoneId = WorkflowTriggerSupport.hasText(timezone)
                ? ZoneId.of(timezone)
                : ZoneId.of(WorkflowTriggerSupport.DEFAULT_TIMEZONE);
        CronTrigger cronTrigger = new CronTrigger(cron, zoneId);

        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> {
                    try {
                        String executionId = executionService.executeScheduled(workflowId);
                        if (executionId == null || executionId.isBlank()) {
                            log.info("Schedule trigger skipped for workflow {}", workflowId);
                            return;
                        }
                        log.info("Schedule trigger fired for workflow {} with execution {}", workflowId, executionId);
                    } catch (Exception e) {
                        log.error("Schedule trigger failed for workflow {}: {}", workflowId, e.getMessage());
                    }
                },
                cronTrigger
        );

        scheduledTasks.put(workflowId, future);
        log.info("Registered schedule trigger for workflow {} with cron '{}'", workflowId, cron);
    }

    public void unregisterSchedule(String workflowId) {
        ScheduledFuture<?> future = scheduledTasks.remove(workflowId);
        if (future != null) {
            future.cancel(false);
            log.info("Unregistered schedule trigger for workflow {}", workflowId);
        }
    }
}
