package io.flashcard.model;

import java.time.Instant;
import java.util.List;

public class QuizSession {

    private long chatId;
    private List<QuizQuestion> questions;
    private List<AnswerEntry> answers;
    private List<QuizWord> words;
    private Instant createdAt;

    public long getChatId() { return chatId; }
    public void setChatId(long chatId) { this.chatId = chatId; }

    public List<QuizQuestion> getQuestions() { return questions; }
    public void setQuestions(List<QuizQuestion> questions) { this.questions = questions; }

    public List<AnswerEntry> getAnswers() { return answers; }
    public void setAnswers(List<AnswerEntry> answers) { this.answers = answers; }

    public List<QuizWord> getWords() { return words; }
    public void setWords(List<QuizWord> words) { this.words = words; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public record QuizQuestion(int index, String type, String prompt, List<String> options, int correctIndex) {}
    public record AnswerEntry(int chosen, boolean correct) {}
    public record QuizWord(String estonian, String english) {}
}
