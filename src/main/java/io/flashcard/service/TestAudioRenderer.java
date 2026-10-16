package io.flashcard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flashcard.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/** Voices generated test audio with our TTS (one podcast call per file) and stores it as MP3 in exam_media. */
@Service
public class TestAudioRenderer {

    private static final Logger log = LoggerFactory.getLogger(TestAudioRenderer.class);

    private final HttpClient httpClient;
    private final AppProperties appProperties;
    private final ExamMediaService media;
    private final ObjectMapper objectMapper;

    public TestAudioRenderer(HttpClient httpClient, AppProperties appProperties, ExamMediaService media,
                             ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.appProperties = appProperties;
        this.media = media;
        this.objectMapper = objectMapper;
    }

    /** Renders and stores every job; throws with the first failure so the draft can show it. */
    public void render(List<TestDraftConverter.AudioJob> jobs) throws Exception {
        for (var job : jobs) {
            byte[] wav = synthesize(job.segments());
            byte[] mp3 = toMp3(wav);
            if (!media.store(job.key(), mp3)) throw new IllegalStateException("could not store " + job.key());
            log.info("[test-audio] {} ({} segments, {} bytes)", job.key(), job.segments().size(), mp3.length);
        }
    }

    private byte[] synthesize(List<TestDraftConverter.Segment> segments) throws Exception {
        List<Map<String, Object>> body = new ArrayList<>();
        for (var s : segments) {
            body.add(Map.of("text", s.text(), "language", "et", "voice", s.voice(), "pause_after_ms", s.pauseAfterMs()));
        }
        String json = objectMapper.writeValueAsString(Map.of("segments", body, "output_sample_rate", 24000));
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(appProperties.getTtsApiUrl() + "/v1/podcast/generate"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .timeout(Duration.ofMinutes(5))
            .build();
        HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) {
            String detail = new String(resp.body(), java.nio.charset.StandardCharsets.UTF_8);
            throw new IllegalStateException("TTS returned " + resp.statusCode() + ": " + detail.substring(0, Math.min(200, detail.length())));
        }
        return resp.body();
    }

    /** Speech-quality MP3 (mono, 64 kbps), the format exam media is served in. */
    private byte[] toMp3(byte[] wav) throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 12);
        Path dir = Path.of(System.getProperty("java.io.tmpdir"));
        Path in = dir.resolve("testaudio-" + id + ".wav");
        Path out = dir.resolve("testaudio-" + id + ".mp3");
        try {
            Files.write(in, wav);
            Process p = new ProcessBuilder("ffmpeg", "-loglevel", "error", "-y", "-i", in.toString(),
                "-af", "loudnorm=I=-16:TP=-1.5:LRA=11", "-ac", "1", "-b:a", "64k", out.toString())
                .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes());
            if (p.waitFor() != 0) throw new IllegalStateException("ffmpeg failed: " + output);
            return Files.readAllBytes(out);
        } finally {
            Files.deleteIfExists(in);
            Files.deleteIfExists(out);
        }
    }
}
