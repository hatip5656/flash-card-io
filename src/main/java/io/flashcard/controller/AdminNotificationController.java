package io.flashcard.controller;

import io.flashcard.repository.NotificationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/notifications")
public class AdminNotificationController {

    private final NotificationRepository repo;

    public AdminNotificationController(NotificationRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean unreadOnly
    ) {
        List<Map<String, Object>> items = repo.list(limit, offset, category, unreadOnly);
        int unread = repo.countUnread();
        return Map.of("items", items, "unread", unread);
    }

    @GetMapping("/unread-count")
    public Map<String, Integer> unreadCount() {
        return Map.of("count", repo.countUnread());
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable long id) {
        repo.markRead(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        repo.markAllRead();
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/cleanup")
    public ResponseEntity<Map<String, String>> cleanup(@RequestParam(defaultValue = "30") int days) {
        repo.deleteOlderThan(days);
        return ResponseEntity.ok(Map.of("status", "cleaned up notifications older than " + days + " days"));
    }
}
