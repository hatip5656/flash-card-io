package io.flashcard.controller;

import io.flashcard.service.TtsService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/audio")
public class AudioController {

    private final TtsService ttsService;

    public AudioController(TtsService ttsService) {
        this.ttsService = ttsService;
    }

    @GetMapping("/{word}")
    public ResponseEntity<?> getWordAudio(@PathVariable String word) {
        if (word.length() > 100) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid word"));
        }

        if (!ttsService.isAvailable()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "TTS service unavailable"));
        }

        byte[] audio = ttsService.synthesizeSpeech(word, null, null);
        if (audio == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "TTS service unavailable"));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "audio/ogg");
        headers.setCacheControl("public, max-age=86400");
        return new ResponseEntity<>(audio, headers, HttpStatus.OK);
    }
}
