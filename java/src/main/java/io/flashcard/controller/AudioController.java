package io.flashcard.controller;

import io.flashcard.config.AppProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/audio")
public class AudioController {

    private final AppProperties appProperties;
    private final HttpClient httpClient;

    public AudioController(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @GetMapping("/{word}")
    public ResponseEntity<?> getWordAudio(@PathVariable String word,
                                           @RequestParam(defaultValue = "mari") String voice) {
        if (word.length() > 100) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid word"));
        }

        try {
            String body = """
                {"text":"%s","speaker":"%s","speed":0.85}
                """.formatted(word.replace("\"", "\\\""), voice.replace("\"", "\\\""));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(appProperties.getTtsApiUrl() + "/v2"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(25))
                .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "TTS service unavailable"));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "audio/ogg");
            headers.setCacheControl("public, max-age=86400");
            return new ResponseEntity<>(response.body(), headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "TTS service unavailable"));
        }
    }
}
