package io.flashcard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Pushes live events (video progress, podcast generation, notifications) to connected
 * admin UIs over Server-Sent Events. Single backend instance, so subscribers live in memory.
 */
@Service
public class AdminEventService {

    private static final Logger log = LoggerFactory.getLogger(AdminEventService.class);
    // Clients reconnect when a stream ends, so a finite timeout just recycles connections
    private static final long STREAM_TIMEOUT_MS = 30 * 60 * 1000;

    private final ObjectMapper objectMapper;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public AdminEventService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SseEmitter subscribe() {
        var emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            // Ends the request cleanly; otherwise Spring raises AsyncRequestTimeoutException
            emitter.complete();
        });
        emitter.onError(e -> emitters.remove(emitter));
        emitters.add(emitter);
        try {
            emitter.send(SseEmitter.event().name("connected").data("{}"));
        } catch (Exception e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    /** Send an event to every connected admin. Never throws. */
    public void publish(String type, Object data) {
        if (emitters.isEmpty()) return;
        String json;
        try {
            json = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("Could not serialize {} event: {}", type, e.getMessage());
            return;
        }
        for (var emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(type).data(json));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    /** Keeps idle streams open through nginx (60 s read timeout) and drops dead clients. */
    @Scheduled(fixedRate = 15_000)
    public void heartbeat() {
        for (var emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    /** Close streams at shutdown so graceful shutdown doesn't wait on them; clients reconnect to the new pod. */
    @EventListener(ContextClosedEvent.class)
    public void closeStreams() {
        emitters.forEach(SseEmitter::complete);
        emitters.clear();
    }

    public int subscriberCount() {
        return emitters.size();
    }
}
