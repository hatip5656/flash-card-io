package io.flashcard.model;

public record QuizAnswer(
    String estonian,
    String correctAnswer,
    String userAnswer,
    boolean isCorrect
) {}
