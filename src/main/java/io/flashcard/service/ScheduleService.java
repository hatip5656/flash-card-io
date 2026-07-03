package io.flashcard.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ScheduleService {

    public static final String SCHEDULE_OFF = "off";

    public static final Map<String, SchedulePreset> PRESETS;

    static {
        PRESETS = new LinkedHashMap<>();
        PRESETS.put("off", new SchedulePreset("off", "Off"));
        PRESETS.put("morning", new SchedulePreset("0 9 * * *", "Daily at 9 AM"));
        PRESETS.put("3x", new SchedulePreset("0 9,14,20 * * *", "3x daily (9, 14, 20)"));
        PRESETS.put("morning_hourly", new SchedulePreset("0 9-12 * * *", "Hourly 9 AM - 12 PM"));
        PRESETS.put("daytime", new SchedulePreset("0 9-21 * * *", "Hourly 9 AM - 9 PM"));
        PRESETS.put("1h", new SchedulePreset("0 * * * *", "Every hour (24h)"));
    }

    public record SchedulePreset(String cron, String label) {}

    public SchedulePreset getPreset(String key) {
        return PRESETS.get(key);
    }

    public List<Map<String, Object>> getSchedulePresets() {
        return PRESETS.entrySet().stream()
            .map(e -> Map.<String, Object>of(
                "key", e.getKey(),
                "cron", e.getValue().cron(),
                "label", e.getValue().label(),
                "isOff", e.getValue().cron().equals(SCHEDULE_OFF)))
            .toList();
    }

    public String findLabelForCron(String cron) {
        return PRESETS.values().stream()
            .filter(p -> p.cron().equals(cron))
            .findFirst()
            .map(SchedulePreset::label)
            .orElse(cron);
    }
}
