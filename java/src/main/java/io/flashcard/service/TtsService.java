package io.flashcard.service;

import io.flashcard.config.AppProperties;
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

    private final AppProperties appProperties;
    private final DiskCacheService diskCache;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TtsService(AppProperties appProperties, DiskCacheService diskCache,
                      HttpClient httpClient, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.diskCache = diskCache;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public byte[] synthesizeSpeech(String word, String sentence, String voiceName) {
        String voice = voiceName != null ? voiceName : appProperties.getTtsSpeaker();
        String text = sentence != null && !sentence.equals(word) ? word + ". ... " + sentence : word;
        String cacheKey = CACHE_VERSION + "\0" + text + "\0" + voice;

        byte[] cached = diskCache.getCachedBuffer("tts", cacheKey, "ogg", DiskCacheService.TTS_TTL_MS);
        if (cached != null) {
            log.debug("[tts] Cache hit for \"{}\" ({})", word, voice);
            return cached;
        }

        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of("text", text, "speaker", voice, "speed", 0.85));
        } catch (Exception e) {
            log.error("[tts] Failed to serialize request body: {}", e.getMessage());
            return null;
        }

        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(appProperties.getTtsApiUrl() + "/v2"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(25))
                    .build();
                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200) {
                    log.error("[tts] API error {} (attempt {}): {}", response.statusCode(), attempt + 1,
                        new String(response.body()).substring(0, Math.min(200, response.body().length)));
                    continue;
                }

                byte[] wavBuffer = response.body();
                byte[] audio = convertWavToOgg(wavBuffer);

                diskCache.setCachedBuffer("tts", cacheKey, "ogg", audio);

                return audio;
            } catch (Exception e) {
                log.error("[tts] Error synthesizing \"{}\" (attempt {}): {}", word, attempt + 1, e.getMessage());
            }
        }
        return null;
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
            process.getInputStream().readAllBytes(); // consume output
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
