package io.flashcard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class AdminEventServiceTest {

    private final AdminEventService events = new AdminEventService(new ObjectMapper());

    @Test
    void publishingWithoutSubscribersIsANoOp() {
        assertDoesNotThrow(() -> events.publish("video", Map.of("status", "rendering")));
    }

    @Test
    void completedStreamsAreDropped() {
        var emitter = events.subscribe();
        assertEquals(1, events.subscriberCount());

        // Without a servlet response behind it the emitter cannot send, so the next event drops it
        emitter.complete();
        events.publish("video", Map.of("status", "rendering"));
        events.heartbeat();

        assertEquals(0, events.subscriberCount());
    }
}
