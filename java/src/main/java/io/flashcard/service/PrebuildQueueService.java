package io.flashcard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

@Service
public class PrebuildQueueService {

    private static final Logger log = LoggerFactory.getLogger(PrebuildQueueService.class);
    public static final int QUEUE_SIZE = 5;
    private static final long STALE_MS = 24 * 60 * 60 * 1000;

    private final Path queueDir;
    private final ObjectMapper objectMapper;

    public record StoredCard(String wordId, String wordValue, String english, String level, long createdAt, String audioFile) {}

    public PrebuildQueueService(DiskCacheService diskCache, ObjectMapper objectMapper) {
        this.queueDir = diskCache.getCacheDir().resolve("prebuild");
        this.objectMapper = objectMapper;
    }

    private Path userDir(long chatId) {
        return queueDir.resolve(String.valueOf(chatId));
    }

    public FlashcardBuilderService.Flashcard popPrebuilt(long chatId, String currentLevel) {
        Path dir = userDir(chatId);
        if (!Files.isDirectory(dir)) return null;

        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> jsonFiles = stream
                .filter(p -> p.toString().endsWith(".json") && !p.toString().endsWith(".meta.json"))
                .sorted()
                .toList();

            for (Path path : jsonFiles) {
                Path claimedPath = Path.of(path + ".claimed");
                try {
                    Files.move(path, claimedPath, StandardCopyOption.ATOMIC_MOVE);
                } catch (Exception e) {
                    continue; // another thread claimed it
                }

                try {
                    StoredCard stored = objectMapper.readValue(Files.readString(claimedPath), StoredCard.class);
                    if (System.currentTimeMillis() - stored.createdAt() > STALE_MS || !stored.level().equals(currentLevel)) {
                        Files.deleteIfExists(claimedPath);
                        if (stored.audioFile() != null) Files.deleteIfExists(dir.resolve(stored.audioFile()));
                        continue;
                    }

                    byte[] audio = null;
                    if (stored.audioFile() != null) {
                        Path audioPath = dir.resolve(stored.audioFile());
                        if (Files.exists(audioPath)) {
                            audio = Files.readAllBytes(audioPath);
                            Files.deleteIfExists(audioPath);
                        }
                    }

                    Path metaPath = Path.of(path.toString().replace(".json", ".meta.json"));
                    FlashcardBuilderService.Flashcard flashcard;
                    try {
                        String metaJson = Files.readString(metaPath);
                        Files.deleteIfExists(metaPath);
                        // Reconstruct from meta - simplified: return null and let caller live-build
                        // In production, deserialize the full flashcard from meta JSON
                    } catch (Exception e) {
                        continue;
                    }

                    Files.deleteIfExists(claimedPath);
                    // For now return null — full deserialization needs FlashcardBuilderService.Flashcard to be serializable
                    // The prebuild queue stores flashcard data; reconstruction requires full model mapping
                    return null;
                } catch (Exception e) {
                    try { Files.deleteIfExists(claimedPath); } catch (IOException ignored) {}
                    continue;
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    public void pushPrebuilt(long chatId, FlashcardBuilderService.Flashcard flashcard, String wordId,
                             String wordValue, String english, String level) {
        try {
            Path dir = userDir(chatId);
            Files.createDirectories(dir);

            try (Stream<Path> s = Files.list(dir)) {
                long count = s.filter(p -> p.toString().endsWith(".json") && !p.toString().endsWith(".meta.json")).count();
                if (count >= QUEUE_SIZE) return;
            }

            long ts = System.currentTimeMillis();
            String id = ts + "-" + UUID.randomUUID().toString().substring(0, 8);

            String audioFile = null;
            if (flashcard.audio() != null) {
                audioFile = id + ".ogg";
                Files.write(dir.resolve(audioFile), flashcard.audio());
            }

            // Store flashcard meta (everything except audio)
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("word", Map.of(
                "id", flashcard.word().getId(),
                "estonian", flashcard.word().getEstonian(),
                "english", flashcard.word().getEnglish(),
                "cefrLevel", flashcard.word().getCefrLevel(),
                "sentences", flashcard.word().getSentences() != null ? flashcard.word().getSentences() : List.of()));
            meta.put("sentence", Map.of("estonian", flashcard.sentence().estonian(), "english", flashcard.sentence().english()));
            meta.put("imageUrl", flashcard.imageUrl());
            meta.put("photographer", flashcard.photographer());
            meta.put("caption", flashcard.caption());
            Files.writeString(dir.resolve(id + ".meta.json"), objectMapper.writeValueAsString(meta));

            StoredCard stored = new StoredCard(wordId, wordValue, english, level, ts, audioFile);
            Files.writeString(dir.resolve(id + ".json"), objectMapper.writeValueAsString(stored));
        } catch (Exception e) {
            log.error("[prebuild] Push error for {}: {}", chatId, e.getMessage());
        }
    }

    public int getQueueSize(long chatId) {
        Path dir = userDir(chatId);
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> s = Files.list(dir)) {
            return (int) s.filter(p -> p.toString().endsWith(".json") && !p.toString().endsWith(".meta.json")).count();
        } catch (Exception e) {
            return 0;
        }
    }

    public Set<String> getQueuedWordIds(long chatId) {
        Set<String> ids = new HashSet<>();
        Path dir = userDir(chatId);
        if (!Files.isDirectory(dir)) return ids;
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path p : stream.toList()) {
                if (!p.toString().endsWith(".json") || p.toString().endsWith(".meta.json")) continue;
                try {
                    StoredCard stored = objectMapper.readValue(Files.readString(p), StoredCard.class);
                    ids.add(stored.wordId());
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return ids;
    }

    public void invalidateQueue(long chatId) {
        Path dir = userDir(chatId);
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> stream = Files.list(dir)) {
            stream.forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (Exception ignored) {}
    }

    public int cleanupStaleEntries() {
        int cleaned = 0;
        if (!Files.isDirectory(queueDir)) return 0;
        try (Stream<Path> users = Files.list(queueDir)) {
            for (Path userPath : users.toList()) {
                if (!Files.isDirectory(userPath)) continue;
                try (Stream<Path> files = Files.list(userPath)) {
                    long now = System.currentTimeMillis();
                    for (Path f : files.toList()) {
                        if (!f.toString().endsWith(".json") || f.toString().endsWith(".meta.json")) continue;
                        try {
                            if (now - Files.getLastModifiedTime(f).toMillis() > STALE_MS) {
                                String base = f.getFileName().toString().replace(".json", "");
                                Files.deleteIfExists(f);
                                Files.deleteIfExists(f.getParent().resolve(base + ".meta.json"));
                                Files.deleteIfExists(f.getParent().resolve(base + ".ogg"));
                                Files.deleteIfExists(Path.of(f + ".claimed"));
                                cleaned++;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
        return cleaned;
    }
}
