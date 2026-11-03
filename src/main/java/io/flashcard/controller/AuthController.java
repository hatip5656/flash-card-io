package io.flashcard.controller;

import io.flashcard.filter.AuthFilter;
import io.flashcard.repository.SubscriberRepository;
import io.flashcard.service.AuthService;
import io.flashcard.service.GoogleTokenVerifier;
import io.flashcard.service.GoogleTokenVerifier.GoogleProfile;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Google sign-in for the app. Signing in with an account that is already connected returns that learner;
 * a new account creates a new learner. A learner who started without signing in connects Google from
 * the profile, which is how an existing account is kept when moving to a new phone.
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final GoogleTokenVerifier google;
    private final AuthService auth;
    private final SubscriberRepository subscriberRepo;

    public AuthController(GoogleTokenVerifier google, AuthService auth, SubscriberRepository subscriberRepo) {
        this.google = google;
        this.auth = auth;
        this.subscriberRepo = subscriberRepo;
    }

    // POST /api/auth/google { idToken } — sign in, creating a learner for a new Google account
    @PostMapping("/auth/google")
    public ResponseEntity<?> signInWithGoogle(@RequestBody Map<String, Object> body) {
        ResponseEntity<?> unavailable = checkConfigured();
        if (unavailable != null) return unavailable;

        GoogleProfile profile;
        try {
            profile = google.verify(asString(body.get("idToken")));
        } catch (GoogleTokenVerifier.InvalidTokenException e) {
            // 400, not 401: a 401 tells the app its own session is gone
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.warn("Google sign-in check failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "Could not reach Google, try again"));
        }

        Optional<Long> existing = auth.findLearner(AuthService.GOOGLE, profile.subject());
        long chatId;
        boolean isNew = existing.isEmpty();
        if (existing.isPresent()) {
            chatId = existing.get();
            auth.recordLogin(AuthService.GOOGLE, profile.subject(), profile.email(), profile.name());
        } else {
            chatId = subscriberRepo.getNextMobileUserId();
            String firstName = profile.givenName() != null ? profile.givenName() : profile.name();
            subscriberRepo.addSubscriber(chatId, "mobile", null, firstName);
            try {
                auth.link(chatId, AuthService.GOOGLE, profile.subject(), profile.email(), profile.name());
            } catch (AuthService.IdentityTakenException e) {
                // Two first sign-ins raced: use the learner the other one created
                chatId = auth.findLearner(AuthService.GOOGLE, profile.subject()).orElseThrow();
                isNew = false;
            } catch (AuthService.AlreadyLinkedException e) {
                throw new IllegalStateException("A brand-new learner already has a Google account", e);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", chatId);
        result.put("token", auth.createSession(chatId));
        result.put("isNew", isNew);
        result.put("email", profile.email());
        result.put("name", profile.givenName() != null ? profile.givenName() : profile.name());
        return ResponseEntity.ok(result);
    }

    // POST /api/auth/logout — end this device's session
    @PostMapping("/auth/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        auth.revokeSession(bearerToken(request));
        return ResponseEntity.ok(Map.of("loggedOut", true));
    }

    // GET /api/users/me/identities — connected sign-in accounts
    @GetMapping("/users/me/identities")
    public ResponseEntity<?> identities(HttpServletRequest request) {
        return ResponseEntity.ok(Map.of(
            "identities", auth.identities(UserController.getUserId(request)),
            "googleAvailable", google.isConfigured()));
    }

    // POST /api/users/me/identities/google { idToken } — connect Google to the current learner
    @PostMapping("/users/me/identities/google")
    public ResponseEntity<?> connectGoogle(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        ResponseEntity<?> unavailable = checkConfigured();
        if (unavailable != null) return unavailable;
        long chatId = UserController.getUserId(request);
        // A learner that has signed in before must prove it with a session: the bare learner id is
        // guessable, and linking hands out a session for the account
        boolean viaSession = Boolean.TRUE.equals(request.getAttribute(AuthFilter.SESSION_ATTRIBUTE));
        if (!viaSession && auth.hasSessions(chatId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Sign in to this account first"));
        }

        GoogleProfile profile;
        try {
            profile = google.verify(asString(body.get("idToken")));
        } catch (GoogleTokenVerifier.InvalidTokenException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.warn("Google connect check failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", "Could not reach Google, try again"));
        }

        try {
            auth.link(chatId, AuthService.GOOGLE, profile.subject(), profile.email(), profile.name());
        } catch (AuthService.IdentityTakenException | AuthService.AlreadyLinkedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", AuthService.GOOGLE);
        result.put("email", profile.email());
        result.put("name", profile.name());
        // Give this device a session like a sign-in would, replacing the one it had
        auth.revokeSession(bearerToken(request));
        result.put("token", auth.createSession(chatId));
        return ResponseEntity.ok(result);
    }

    // DELETE /api/users/me/identities/google — disconnect Google
    @DeleteMapping("/users/me/identities/google")
    public ResponseEntity<?> disconnectGoogle(HttpServletRequest request) {
        boolean removed = auth.unlink(UserController.getUserId(request), AuthService.GOOGLE);
        return ResponseEntity.ok(Map.of("disconnected", removed));
    }

    private ResponseEntity<?> checkConfigured() {
        if (google.isConfigured()) return null;
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("error", "Google sign-in is not set up on the server yet"));
    }

    static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : null;
    }

    private static String asString(Object o) {
        return o instanceof String s ? s : null;
    }
}
