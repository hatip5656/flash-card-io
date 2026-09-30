package io.flashcard.model;

import java.time.LocalDateTime;

public class Podcast {
    private String id;
    private long chatId;
    private String title;
    private String description;
    private String script;        // JSON array of segments
    private String audioCacheKey;
    private int durationSeconds;
    private String cefrLevel;
    private String status;        // pending, generating, ready, failed
    private String errorMessage;
    private LocalDateTime createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public long getChatId() { return chatId; }
    public void setChatId(long chatId) { this.chatId = chatId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getScript() { return script; }
    public void setScript(String script) { this.script = script; }

    public String getAudioCacheKey() { return audioCacheKey; }
    public void setAudioCacheKey(String audioCacheKey) { this.audioCacheKey = audioCacheKey; }

    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }

    public String getCefrLevel() { return cefrLevel; }
    public void setCefrLevel(String cefrLevel) { this.cefrLevel = cefrLevel; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
