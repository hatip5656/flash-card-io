package io.flashcard.config;

import io.flashcard.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final NotificationService notifications;

    public GlobalExceptionHandler(NotificationService notifications) {
        this.notifications = notifications;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "Bad request";
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "Conflict";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception e) {
        log.error("[api] Unhandled error:", e);
        notifications.error("system", "Application Error",
            e.getClass().getSimpleName() + ": " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        return ResponseEntity.internalServerError().body(Map.of("error", "Internal server error"));
    }
}
