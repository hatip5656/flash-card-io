package io.flashcard.controller;

import io.flashcard.model.DailyGoalSettings;
import io.flashcard.repository.DailyGoalRepository;
import io.flashcard.service.DailyGoalService;
import io.flashcard.service.DailyGoalService.Task;
import io.flashcard.service.DailyGoalService.Today;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DailyGoalControllerTest {

    private static final long USER = 42L;

    private MockMvc mvc;
    private DailyGoalService goals;
    private DailyGoalRepository repo;

    @BeforeEach
    void setUp() {
        goals = mock(DailyGoalService.class);
        repo = mock(DailyGoalRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new DailyGoalController(goals, repo)).build();
        when(goals.settings(USER)).thenReturn(DailyGoalSettings.defaults());
        when(goals.saveSettings(eq(USER), any())).thenAnswer(inv -> inv.getArgument(1));
    }

    @Test
    void getTodayReturnsTasks() throws Exception {
        when(goals.today(USER)).thenReturn(new Today(LocalDate.of(2026, 11, 6),
            List.of(new Task("words", 10, 4, true), new Task("grammar", 1, 0, true)),
            "a2-pron", "Kişi Zamirleri", false, 3));

        mvc.perform(get("/api/users/me/daily-goal").requestAttr("userId", USER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.date").value("2026-11-06"))
            .andExpect(jsonPath("$.tasks[0].key").value("words"))
            .andExpect(jsonPath("$.tasks[0].target").value(10))
            .andExpect(jsonPath("$.tasks[0].done").value(4))
            .andExpect(jsonPath("$.grammar.lessonId").value("a2-pron"))
            .andExpect(jsonPath("$.grammar.topic").value("Kişi Zamirleri"))
            .andExpect(jsonPath("$.complete").value(false))
            .andExpect(jsonPath("$.streak").value(3));
    }

    @Test
    void getSettingsFormatsTimes() throws Exception {
        mvc.perform(get("/api/users/me/daily-goal/settings").requestAttr("userId", USER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.wordsTarget").value(10))
            .andExpect(jsonPath("$.morningTime").value("09:00"))
            .andExpect(jsonPath("$.eveningTime").value("20:00"));
    }

    @Test
    void putSettingsParsesTimes() throws Exception {
        mvc.perform(put("/api/users/me/daily-goal/settings").requestAttr("userId", USER)
                .contentType("application/json").content(settingsJson(20, "07:30", "21:00")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.morningTime").value("07:30"));

        var saved = ArgumentCaptor.forClass(DailyGoalSettings.class);
        verify(goals).saveSettings(eq(USER), saved.capture());
        assertEquals(20, saved.getValue().wordsTarget());
        assertEquals(LocalTime.of(7, 30), saved.getValue().morningTime());
    }

    @Test
    void putSettingsRejectsBadTarget() throws Exception {
        when(goals.saveSettings(eq(USER), any())).thenThrow(new IllegalArgumentException("wordsTarget must be one of [5, 10, 20]"));

        mvc.perform(put("/api/users/me/daily-goal/settings").requestAttr("userId", USER)
                .contentType("application/json").content(settingsJson(7, "09:00", "20:00")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void putSettingsRejectsBadTime() throws Exception {
        mvc.perform(put("/api/users/me/daily-goal/settings").requestAttr("userId", USER)
                .contentType("application/json").content(settingsJson(10, "25:00", "20:00")))
            .andExpect(status().isBadRequest());
        verify(goals, never()).saveSettings(anyLong(), any());
    }

    @Test
    void registerDevice() throws Exception {
        mvc.perform(post("/api/users/me/devices").requestAttr("userId", USER)
                .contentType("application/json").content("{\"token\":\"fcm-1\",\"platform\":\"android\"}"))
            .andExpect(status().isNoContent());
        verify(repo).upsertDevice("fcm-1", USER, "android");
    }

    @Test
    void registerDeviceRequiresToken() throws Exception {
        mvc.perform(post("/api/users/me/devices").requestAttr("userId", USER)
                .contentType("application/json").content("{\"platform\":\"android\"}"))
            .andExpect(status().isBadRequest());
        verify(repo, never()).upsertDevice(any(), anyLong(), any());
    }

    @Test
    void deleteDevice() throws Exception {
        mvc.perform(delete("/api/users/me/devices/fcm-1").requestAttr("userId", USER))
            .andExpect(status().isNoContent());
        verify(repo).deleteDevice(USER, "fcm-1");
    }

    private static String settingsJson(int words, String morning, String evening) {
        return """
            {"wordsEnabled":true,"wordsTarget":%d,"quizEnabled":true,"quizTarget":20,"reviewsEnabled":true,
             "grammarEnabled":true,"notifyEnabled":true,"morningTime":"%s","eveningTime":"%s"}
            """.formatted(words, morning, evening);
    }
}
