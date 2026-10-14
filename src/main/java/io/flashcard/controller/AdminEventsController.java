package io.flashcard.controller;

import io.flashcard.service.AdminEventService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/admin/events")
public class AdminEventsController {

    private final AdminEventService events;

    public AdminEventsController(AdminEventService events) {
        this.events = events;
    }

    /** Live event stream: video, podcast and notification events. */
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(HttpServletResponse response) {
        // Tell nginx not to buffer the stream
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        return events.subscribe();
    }
}
