package io.flashcard.service;

import io.flashcard.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationRepository repo;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    public void info(String category, String title, String message) {
        emit("info", category, title, message, null);
    }

    public void success(String category, String title, String message) {
        emit("success", category, title, message, null);
    }

    public void warning(String category, String title, String message) {
        emit("warning", category, title, message, null);
    }

    public void error(String category, String title, String message) {
        emit("error", category, title, message, null);
    }

    public void emit(String type, String category, String title, String message, String metadataJson) {
        try {
            repo.create(type, category, title, message, metadataJson);
        } catch (Exception e) {
            log.warn("Failed to save notification: {}", e.getMessage());
        }
    }
}
