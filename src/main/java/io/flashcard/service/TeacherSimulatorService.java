package io.flashcard.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.flashcard.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Client for the teacher-simulator service, which renders a classroom video for a podcast.
 * Videos are keyed by podcast id and served publicly by nginx under app.video-public-base-url.
 */
@Service
public class TeacherSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(TeacherSimulatorService.class);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    public TeacherSimulatorService(HttpClient httpClient, ObjectMapper objectMapper, AppProperties appProperties) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
    }

    /** Queue a render. Returns the service's job status (queued / rendering / done). */
    public Map<String, Object> requestVideo(String podcastId, String audioUrl,
                                            List<Map<String, Object>> subtitles, boolean force)
            throws IOException, InterruptedException {
        var body = new LinkedHashMap<String, Object>();
        body.put("id", podcastId);
        body.put("audioUrl", audioUrl);
        body.put("subtitles", subtitles);
        body.put("force", force);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(appProperties.getTeacherSimulatorUrl() + "/videos"))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new IOException("teacher-simulator returned " + resp.statusCode() + ": " + resp.body());
        }
        log.info("Video render requested for podcast {} → {}", podcastId, resp.statusCode());
        return withPublicUrl(objectMapper.readValue(resp.body(), MAP));
    }

    /** Current render status, or empty if the service has never seen this podcast. */
    public Optional<Map<String, Object>> getStatus(String podcastId) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(appProperties.getTeacherSimulatorUrl() + "/videos/" + podcastId))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 404) return Optional.empty();
        if (resp.statusCode() >= 400) {
            throw new IOException("teacher-simulator returned " + resp.statusCode() + ": " + resp.body());
        }
        return Optional.of(withPublicUrl(objectMapper.readValue(resp.body(), MAP)));
    }

    private Map<String, Object> withPublicUrl(Map<String, Object> status) {
        if ("done".equals(status.get("status"))) {
            status.put("videoUrl", appProperties.getVideoPublicBaseUrl() + status.get("id") + ".mp4");
        }
        return status;
    }
}
