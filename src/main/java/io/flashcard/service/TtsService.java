package io.flashcard.service;

import io.flashcard.config.AppProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
public class TtsService {

    private static final Logger log = LoggerFactory.getLogger(TtsService.class);
    private static final String CACHE_VERSION = "v4";
    private static final int TTS_TIMEOUT_SECONDS = 8;

    private final AppProperties appProperties;
    private final DiskCacheService diskCache;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final CircuitBreaker circuitBreaker;

    public TtsService(AppProperties appProperties, DiskCacheService diskCache,
                      HttpClient httpClient, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.diskCache = diskCache;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.circuitBreaker = CircuitBreaker.of("tts", CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .slowCallRateThreshold(80)
            .slowCallDurationThreshold(Duration.ofSeconds(TTS_TIMEOUT_SECONDS))
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(5)
            .minimumNumberOfCalls(3)
            .waitDurationInOpenState(Duration.ofMinutes(5))
            .permittedNumberOfCallsInHalfOpenState(1)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build());

        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> log.info("[tts] Circuit breaker: {}", event))
            .onCallNotPermitted(event -> log.debug("[tts] Call blocked by circuit breaker"));
    }

    public boolean isAvailable() {
        return circuitBreaker.getState() != CircuitBreaker.State.OPEN;
    }

    public byte[] synthesizeSpeech(String word, String sentence, String voiceName) {
        String voice = voiceName != null ? voiceName : appProperties.getTtsSpeaker();
        String text = sentence != null && !sentence.equals(word) ? word + ". ... " + sentence : word;
        String cacheKey = CACHE_VERSION + "\0" + text + "\0" + voice;

        // Disk cache check — outside circuit breaker
        byte[] cached = diskCache.getCachedBuffer("tts", cacheKey, "ogg", DiskCacheService.TTS_TTL_MS);
        if (cached != null) {
            log.debug("[tts] Cache hit for \"{}\" ({})", word, voice);
            return cached;
        }

        // Circuit breaker wraps the API call
        try {
            return circuitBreaker.executeSupplier(() -> callTtsApi(word, text, voice, cacheKey));
        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
            log.debug("[tts] Circuit open, skipping \"{}\"", word);
            return null;
        } catch (Exception e) {
            log.warn("[tts] Failed for \"{}\": {}", word, e.getMessage());
            return null;
        }
    }

    private byte[] callTtsApi(String word, String text, String voice, String cacheKey) {
        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of("text", text, "speaker", voice, "speed", 0.85));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize TTS request", e);
        }

        long start = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(appProperties.getTtsApiUrl() + "/v2"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(TTS_TIMEOUT_SECONDS))
                .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                throw new RuntimeException("TTS API error " + response.statusCode());
            }

            byte[] audio = convertWavToOgg(response.body());
            diskCache.setCachedBuffer("tts", cacheKey, "ogg", audio);
            log.info("[tts] Synthesized \"{}\" in {}ms ({}bytes)", word, System.currentTimeMillis() - start, audio.length);
            return audio;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private byte[] convertWavToOgg(byte[] wavBuffer) {
        String id = UUID.randomUUID().toString().substring(0, 12);
        Path tmpDir = Path.of(System.getProperty("java.io.tmpdir"));
        Path wavFile = tmpDir.resolve("tts-" + id + ".wav");
        Path oggFile = tmpDir.resolve("tts-" + id + ".ogg");

        try {
            Files.write(wavFile, wavBuffer);
            ProcessBuilder pb = new ProcessBuilder("ffmpeg",
                "-i", wavFile.toString(),
                "-af", String.join(",",
                    "silenceremove=start_periods=1:start_silence=0.05:start_threshold=-40dB",
                    "asetpts=PTS-STARTPTS",
                    "aresample=out_sample_rate=48000",
                    "highpass=f=80",
                    "lowpass=f=12000",
                    "equalizer=f=3000:t=q:w=2.0:g=1.5",
                    "acompressor=threshold=0.1:ratio=2:attack=10:release=150:makeup=1",
                    "afade=t=in:d=0.02",
                    "loudnorm=I=-16:TP=-1.5:LRA=11"),
                "-c:a", "libopus",
                "-b:a", "32k",
                "-application", "voip",
                "-vbr", "on",
                "-compression_level", "10",
                "-frame_duration", "20",
                "-ar", "48000",
                "-y", oggFile.toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.getInputStream().readAllBytes();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("[tts] ffmpeg exited with code {}, returning raw WAV", exitCode);
                return wavBuffer;
            }
            return Files.readAllBytes(oggFile);
        } catch (Exception e) {
            log.warn("[tts] ffmpeg conversion failed: {}, returning raw WAV", e.getMessage());
            return wavBuffer;
        } finally {
            try { Files.deleteIfExists(wavFile); } catch (IOException ignored) {}
            try { Files.deleteIfExists(oggFile); } catch (IOException ignored) {}
        }
    }
}
