package org.github.flowify.workflow.service;

import org.github.flowify.workflow.entity.TriggerConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class WorkflowTriggerSupport {

    public static final String TYPE_MANUAL = "manual";
    public static final String TYPE_SCHEDULE = "schedule";
    public static final String DEFAULT_TIMEZONE = "Asia/Seoul";
    public static final boolean DEFAULT_SKIP_IF_RUNNING = true;
    public static final int DEFAULT_INTERVAL_HOURS = 4;
    public static final Set<String> ALLOWED_TYPES = Set.of(TYPE_MANUAL, TYPE_SCHEDULE);
    public static final Set<String> ALLOWED_SCHEDULE_MODES = Set.of("interval", "daily", "weekly");
    public static final Set<String> ALLOWED_WEEKDAYS = Set.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

    private WorkflowTriggerSupport() {
    }

    public static TriggerConfig manualTrigger() {
        return TriggerConfig.builder()
                .type(TYPE_MANUAL)
                .config(Map.of())
                .build();
    }

    public static TriggerConfig normalizeTrigger(TriggerConfig trigger) {
        if (trigger == null || !hasText(trigger.getType())) {
            return manualTrigger();
        }

        String type = trigger.getType().trim().toLowerCase(Locale.ROOT);
        if (TYPE_MANUAL.equals(type)) {
            return manualTrigger();
        }

        Map<String, Object> config = mutableConfig(trigger.getConfig());
        if (TYPE_SCHEDULE.equals(type)) {
            String scheduleMode = asText(config.get("schedule_mode")).toLowerCase(Locale.ROOT);
            if (!hasText(scheduleMode)) {
                config.put("schedule_mode", "interval");
            } else {
                config.put("schedule_mode", scheduleMode);
            }
            config.putIfAbsent("timezone", DEFAULT_TIMEZONE);
            config.putIfAbsent("skip_if_running", DEFAULT_SKIP_IF_RUNNING);
            if ("interval".equals(asText(config.get("schedule_mode")))
                    && asInteger(config.get("interval_hours")) == null) {
                config.put("interval_hours", DEFAULT_INTERVAL_HOURS);
            }
        }
        config.entrySet().removeIf(entry -> entry.getValue() == null);

        return TriggerConfig.builder()
                .type(type)
                .config(Map.copyOf(config))
                .build();
    }

    public static boolean isManual(TriggerConfig trigger) {
        return TYPE_MANUAL.equals(normalizeTrigger(trigger).getType());
    }

    public static boolean isSchedule(TriggerConfig trigger) {
        return TYPE_SCHEDULE.equals(normalizeTrigger(trigger).getType());
    }

    public static boolean normalizeActive(TriggerConfig trigger, boolean active) {
        return isManual(trigger) || active;
    }

    public static String getCron(TriggerConfig trigger) {
        return asText(normalizeTrigger(trigger).getConfig().get("cron"));
    }

    public static String getTimezone(TriggerConfig trigger) {
        String timezone = asText(normalizeTrigger(trigger).getConfig().get("timezone"));
        return hasText(timezone) ? timezone : DEFAULT_TIMEZONE;
    }

    public static String getScheduleMode(TriggerConfig trigger) {
        return asText(normalizeTrigger(trigger).getConfig().get("schedule_mode"));
    }

    public static Integer getIntervalHours(TriggerConfig trigger) {
        return asInteger(normalizeTrigger(trigger).getConfig().get("interval_hours"));
    }

    public static String getTimeOfDay(TriggerConfig trigger) {
        return asText(normalizeTrigger(trigger).getConfig().get("time_of_day"));
    }

    public static List<String> getWeekdays(TriggerConfig trigger) {
        Object rawWeekdays = normalizeTrigger(trigger).getConfig().get("weekdays");
        if (!(rawWeekdays instanceof List<?> weekdays)) {
            return List.of();
        }

        List<String> normalizedWeekdays = new ArrayList<>();
        for (Object weekday : weekdays) {
            if (weekday == null) {
                continue;
            }
            normalizedWeekdays.add(String.valueOf(weekday).trim().toUpperCase(Locale.ROOT));
        }
        return List.copyOf(normalizedWeekdays);
    }

    public static boolean isSkipIfRunning(TriggerConfig trigger) {
        Object rawValue = normalizeTrigger(trigger).getConfig().get("skip_if_running");
        if (rawValue instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return DEFAULT_SKIP_IF_RUNNING;
    }

    public static String asText(Object value) {
        return value != null ? String.valueOf(value).trim() : "";
    }

    public static Integer asInteger(Object value) {
        if (value instanceof Integer integerValue) {
            return integerValue;
        }
        if (value instanceof Number numberValue) {
            double doubleValue = numberValue.doubleValue();
            if (doubleValue % 1 != 0) {
                return null;
            }
            return numberValue.intValue();
        }
        if (value instanceof String stringValue && hasText(stringValue)) {
            try {
                return Integer.parseInt(stringValue.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static Map<String, Object> mutableConfig(Map<String, Object> config) {
        return config != null ? new LinkedHashMap<>(config) : new LinkedHashMap<>();
    }
}
