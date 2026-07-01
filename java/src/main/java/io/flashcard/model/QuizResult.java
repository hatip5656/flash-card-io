package io.flashcard.model;

import java.time.Instant;
import java.util.List;

public class QuizResult {

    private int id;
    private int score;
    private int total;
    private int percentage;
    private Instant completedAt;
    private List<QuizAnswer> answers;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }

    public int getPercentage() { return percentage; }
    public void setPercentage(int percentage) { this.percentage = percentage; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public List<QuizAnswer> getAnswers() { return answers; }
    public void setAnswers(List<QuizAnswer> answers) { this.answers = answers; }
}
