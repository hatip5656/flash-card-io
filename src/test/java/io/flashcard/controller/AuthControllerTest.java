package io.flashcard.controller;

import io.flashcard.repository.SubscriberRepository;
import io.flashcard.service.AuthService;
import io.flashcard.service.GoogleTokenVerifier;
import io.flashcard.service.GoogleTokenVerifier.GoogleProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest {

    private static final GoogleProfile PROFILE = new GoogleProfile("google-sub-1", "hatip@example.com", "Hatip Aksunger", "Hatip");

    private MockMvc mvc;
    private GoogleTokenVerifier google;
    private AuthService auth;
    private SubscriberRepository subscribers;

    @BeforeEach
    void setUp() throws Exception {
        google = mock(GoogleTokenVerifier.class);
        auth = mock(AuthService.class);
        subscribers = mock(SubscriberRepository.class);
        when(google.isConfigured()).thenReturn(true);
        when(google.verify("good")).thenReturn(PROFILE);
        when(google.verify("bad")).thenThrow(new GoogleTokenVerifier.InvalidTokenException("Google did not accept the sign-in token"));
        when(auth.createSession(anyLong())).thenReturn("session-token");
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(google, auth, subscribers)).build();
    }

    @Test
    void connectedAccountSignsBackIntoItsLearner() throws Exception {
        when(auth.findLearner("google", "google-sub-1")).thenReturn(Optional.of(8607749953L));

        mvc.perform(post("/api/auth/google").contentType("application/json").content("{\"idToken\":\"good\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(8607749953L))
            .andExpect(jsonPath("$.token").value("session-token"))
            .andExpect(jsonPath("$.isNew").value(false));
        verify(subscribers, never()).addSubscriber(anyLong(), any(), any(), any());
    }

    @Test
    void newAccountCreatesALearner() throws Exception {
        when(auth.findLearner("google", "google-sub-1")).thenReturn(Optional.empty());
        when(subscribers.getNextMobileUserId()).thenReturn(2000000077L);

        mvc.perform(post("/api/auth/google").contentType("application/json").content("{\"idToken\":\"good\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(2000000077L))
            .andExpect(jsonPath("$.isNew").value(true))
            .andExpect(jsonPath("$.name").value("Hatip"));
        verify(subscribers).addSubscriber(2000000077L, "mobile", null, "Hatip");
        verify(auth).link(2000000077L, "google", "google-sub-1", "hatip@example.com", "Hatip Aksunger");
    }

    @Test
    void rejectedGoogleTokenIs400NotASessionError() throws Exception {
        mvc.perform(post("/api/auth/google").contentType("application/json").content("{\"idToken\":\"bad\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Google did not accept the sign-in token"));
        verifyNoInteractions(subscribers);
    }

    @Test
    void signInIsOffUntilClientIdsAreSet() throws Exception {
        when(google.isConfigured()).thenReturn(false);
        mvc.perform(post("/api/auth/google").contentType("application/json").content("{\"idToken\":\"good\"}"))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    void connectingAnAccountOwnedByAnotherLearnerIs409() throws Exception {
        doThrow(new AuthService.IdentityTakenException()).when(auth).link(eq(8607749953L), eq("google"), any(), any(), any());

        mvc.perform(post("/api/users/me/identities/google").requestAttr("userId", 8607749953L)
                .contentType("application/json").content("{\"idToken\":\"good\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("This Google account is already connected to another Wordagram account"));
        verify(auth, never()).createSession(anyLong());
    }

    @Test
    void aLearnerWithSessionsCannotBeLinkedByItsIdAlone() throws Exception {
        when(auth.hasSessions(8607749953L)).thenReturn(true);

        mvc.perform(post("/api/users/me/identities/google").requestAttr("userId", 8607749953L)
                .contentType("application/json").content("{\"idToken\":\"good\"}"))
            .andExpect(status().isForbidden());
        verify(auth, never()).link(anyLong(), any(), any(), any(), any());
    }

    @Test
    void aSignedInDeviceCanConnectAndGetsAFreshSession() throws Exception {
        when(auth.hasSessions(8607749953L)).thenReturn(true);

        mvc.perform(post("/api/users/me/identities/google").requestAttr("userId", 8607749953L)
                .requestAttr("viaSession", true).header("Authorization", "Bearer old-token")
                .contentType("application/json").content("{\"idToken\":\"good\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("session-token"));
        verify(auth).revokeSession("old-token");
    }

    @Test
    void connectingGivesThisDeviceASession() throws Exception {
        mvc.perform(post("/api/users/me/identities/google").requestAttr("userId", 8607749953L)
                .contentType("application/json").content("{\"idToken\":\"good\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("hatip@example.com"))
            .andExpect(jsonPath("$.token").value("session-token"));
        verify(auth).link(8607749953L, "google", "google-sub-1", "hatip@example.com", "Hatip Aksunger");
    }
}
