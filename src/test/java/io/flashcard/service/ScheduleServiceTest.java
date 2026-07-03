package io.flashcard.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleServiceTest {

    private final ScheduleService service = new ScheduleService();

    @Test
    void presetsContainAllKeys() {
        assertNotNull(service.getPreset("off"));
        assertNotNull(service.getPreset("morning"));
        assertNotNull(service.getPreset("3x"));
        assertNotNull(service.getPreset("morning_hourly"));
        assertNotNull(service.getPreset("daytime"));
        assertNotNull(service.getPreset("1h"));
    }

    @Test
    void unknownPresetReturnsNull() {
        assertNull(service.getPreset("invalid"));
    }

    @Test
    void getSchedulePresetsReturnsList() {
        List<Map<String, Object>> presets = service.getSchedulePresets();
        assertEquals(6, presets.size());
        for (Map<String, Object> p : presets) {
            assertTrue(p.containsKey("key"));
            assertTrue(p.containsKey("cron"));
            assertTrue(p.containsKey("label"));
            assertTrue(p.containsKey("isOff"));
        }
    }

    @Test
    void offPresetIsMarkedOff() {
        var presets = service.getSchedulePresets();
        var off = presets.stream().filter(p -> "off".equals(p.get("key"))).findFirst().orElseThrow();
        assertEquals(true, off.get("isOff"));

        var morning = presets.stream().filter(p -> "morning".equals(p.get("key"))).findFirst().orElseThrow();
        assertEquals(false, morning.get("isOff"));
    }

    @Test
    void findLabelForCronReturnsLabel() {
        assertEquals("Daily at 9 AM", service.findLabelForCron("0 9 * * *"));
        assertEquals("Off", service.findLabelForCron("off"));
    }

    @Test
    void findLabelForUnknownCronReturnsCronItself() {
        assertEquals("0 5 * * *", service.findLabelForCron("0 5 * * *"));
    }
}
