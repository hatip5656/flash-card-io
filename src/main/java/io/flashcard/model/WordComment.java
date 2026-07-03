package io.flashcard.model;

import java.time.Instant;

public record WordComment(
    int id,
    long chatId,
    String firstName,
    String comment,
    Instant createdAt
) {}
