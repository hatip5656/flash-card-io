package io.flashcard.controller;

import io.flashcard.model.UserPreferences;
import io.flashcard.repository.ActivityRepository;
import io.flashcard.repository.SubscriberRepository;
import io.flashcard.service.ScheduleService;
import io.flashcard.service.WordBankService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean private SubscriberRepository subscriberRepo;
    @MockitoBean private ActivityRepository activityRepo;
    @MockitoBean private ScheduleService scheduleService;
    @MockitoBean private WordBankService wordBankService;

    @Test
    void registerCreatesUser() throws Exception {
        when(activityRepo.getStats(12345L)).thenReturn(Map.of("sent", 0, "level", "A1", "schedule", "0 9 * * *"));

        mvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\": 12345}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.userId").value(12345));

        verify(subscriberRepo).addSubscriber(eq(12345L), eq("api"), isNull(), isNull());
    }

    @Test
    void registerRejectsMissingUserId() throws Exception {
        mvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("userId is required"));
    }

    @Test
    void autoRegisterCreatesUser() throws Exception {
        when(subscriberRepo.getNextMobileUserId()).thenReturn(2000000001L);
        when(activityRepo.getStats(2000000001L)).thenReturn(Map.of("sent", 0, "level", "A1", "schedule", "0 9 * * *"));

        mvc.perform(post("/api/users/auto")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\": \"Test\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.userId").value(2000000001));
    }

    @Test
    void getUserReturnsProfile() throws Exception {
        when(activityRepo.getStats(12345L)).thenReturn(Map.of("sent", 10, "level", "A1", "schedule", "0 9 * * *"));
        when(subscriberRepo.getPreferences(12345L)).thenReturn(UserPreferences.defaults());
        when(scheduleService.findLabelForCron("0 9 * * *")).thenReturn("Daily at 9 AM");
        when(wordBankService.getWordsForLevel("A1")).thenReturn(List.of());

        mvc.perform(get("/api/users/me").requestAttr("userId", 12345L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(12345))
            .andExpect(jsonPath("$.level").value("A1"))
            .andExpect(jsonPath("$.preferences").exists())
            .andExpect(jsonPath("$.wordsLearned").value(10));
    }

    @Test
    void setLevelWithValidLevel() throws Exception {
        when(wordBankService.getWordsForLevel("B1")).thenReturn(List.of());

        mvc.perform(patch("/api/users/me/level")
                .requestAttr("userId", 12345L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"level\": \"B1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.level").value("B1"))
            .andExpect(jsonPath("$.totalForLevel").value(0));
    }

    @Test
    void setLevelRejectsInvalidLevel() throws Exception {
        mvc.perform(patch("/api/users/me/level")
                .requestAttr("userId", 12345L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"level\": \"C1\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void setScheduleRejectsInvalidPreset() throws Exception {
        when(scheduleService.getPreset("invalid")).thenReturn(null);

        mvc.perform(patch("/api/users/me/schedule")
                .requestAttr("userId", 12345L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"schedule\": \"invalid\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void setScheduleWithValidPreset() throws Exception {
        when(scheduleService.getPreset("morning")).thenReturn(new ScheduleService.SchedulePreset("0 9 * * *", "Daily at 9 AM"));
        when(subscriberRepo.getSubscriberSchedule(12345L)).thenReturn("off");

        mvc.perform(patch("/api/users/me/schedule")
                .requestAttr("userId", 12345L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"schedule\": \"morning\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.label").value("Daily at 9 AM"))
            .andExpect(jsonPath("$.changed").value(true));
    }

    @Test
    void getPreferencesReturnsDefaults() throws Exception {
        when(subscriberRepo.getPreferences(12345L)).thenReturn(UserPreferences.defaults());

        mvc.perform(get("/api/users/me/preferences").requestAttr("userId", 12345L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.audio").value(true))
            .andExpect(jsonPath("$.voiceName").value("mari"));
    }

    @Test
    void setPreferenceRejectsInvalidKey() throws Exception {
        mvc.perform(patch("/api/users/me/preferences")
                .requestAttr("userId", 12345L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\": \"invalid\", \"value\": true}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void setPreferenceRejectsWrongTypeForBoolean() throws Exception {
        mvc.perform(patch("/api/users/me/preferences")
                .requestAttr("userId", 12345L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\": \"audio\", \"value\": \"yes\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void setPreferenceAcceptsVoiceName() throws Exception {
        when(subscriberRepo.getPreferences(12345L)).thenReturn(UserPreferences.defaults());

        mvc.perform(patch("/api/users/me/preferences")
                .requestAttr("userId", 12345L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"key\": \"voiceName\", \"value\": \"albert\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void unsubscribeReturnsTrue() throws Exception {
        mvc.perform(delete("/api/users/me").requestAttr("userId", 12345L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unsubscribed").value(true));
    }

    @Test
    void getSchedulePresetsReturnsList() throws Exception {
        when(scheduleService.getSchedulePresets()).thenReturn(List.of(
            Map.of("key", "off", "cron", "off", "label", "Off", "isOff", true)
        ));

        mvc.perform(get("/api/schedules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].key").value("off"));
    }
}
