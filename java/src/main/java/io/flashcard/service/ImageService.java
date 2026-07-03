package io.flashcard.service;

import io.flashcard.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ImageService {

    private static final Logger log = LoggerFactory.getLogger(ImageService.class);
    private static final String UNSPLASH_API = "https://api.unsplash.com";
    private static final String PEXELS_API = "https://api.pexels.com/v1";

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final TokenBucketRateLimiter unsplashLimiter = new TokenBucketRateLimiter(40, 3_600_000);
    private final AtomicLong unsplashRateLimitedUntil = new AtomicLong(0);

    public ImageService(AppProperties appProperties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public record ImageResult(String url, String photographer) {}

    public ImageResult fetchImage(String query) {
        // Try Pexels first
        String pexelsKey = appProperties.getPexelsApiKey();
        if (pexelsKey != null && !pexelsKey.isBlank()) {
            ImageResult pexels = searchPexels(query, pexelsKey);
            if (pexels != null) return pexels;
        }

        // Fallback to Unsplash
        String unsplashKey = appProperties.getUnsplashAccessKey();
        if (unsplashKey != null && !unsplashKey.isBlank()) {
            return searchUnsplash(query, unsplashKey);
        }

        return null;
    }

    private ImageResult searchPexels(String query, String apiKey) {
        try {
            String url = PEXELS_API + "/search?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&per_page=1&orientation=landscape";
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", apiKey)
                .timeout(Duration.ofSeconds(10))
                .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("[pexels] API error: {}", response.statusCode());
                return null;
            }
            JsonNode data = objectMapper.readTree(response.body());
            JsonNode photos = data.path("photos");
            if (!photos.isArray() || photos.isEmpty()) return null;
            JsonNode photo = photos.get(0);
            return new ImageResult(
                photo.path("src").path("landscape").asText(),
                photo.path("photographer").asText(""));
        } catch (Exception e) {
            log.error("[pexels] Error searching for \"{}\": {}", query, e.getMessage());
            return null;
        }
    }

    private ImageResult searchUnsplash(String query, String accessKey) {
        if (!tryConsumeUnsplash()) return null;
        try {
            String url = UNSPLASH_API + "/search/photos?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&per_page=1&orientation=landscape";
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Client-ID " + accessKey)
                .timeout(Duration.ofSeconds(10))
                .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 403 || response.statusCode() == 429) {
                unsplashRateLimitedUntil.set(System.currentTimeMillis() + 5 * 60_000);
                log.warn("[unsplash] Rate limited ({}), backing off 5 min", response.statusCode());
                return null;
            }
            if (response.statusCode() != 200) {
                log.error("[unsplash] API error: {}", response.statusCode());
                return null;
            }

            JsonNode data = objectMapper.readTree(response.body());
            JsonNode results = data.path("results");
            if (!results.isArray() || results.isEmpty()) return null;
            JsonNode photo = results.get(0);

            // Trigger download (fire and forget)
            String downloadUrl = photo.path("links").path("download_location").asText(null);
            if (downloadUrl != null) {
                triggerDownloadAsync(downloadUrl, accessKey);
            }

            return new ImageResult(
                photo.path("urls").path("regular").asText(),
                photo.path("user").path("name").asText(""));
        } catch (Exception e) {
            log.error("[unsplash] Error searching for \"{}\": {}", query, e.getMessage());
            return null;
        }
    }

    private boolean tryConsumeUnsplash() {
        if (System.currentTimeMillis() < unsplashRateLimitedUntil.get()) return false;
        return unsplashLimiter.tryConsume();
    }

    private void triggerDownloadAsync(String downloadUrl, String accessKey) {
        Thread.ofVirtual().start(() -> {
            try {
                httpClient.send(HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .header("Authorization", "Client-ID " + accessKey)
                    .GET().build(), HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {}
        });
    }
}
