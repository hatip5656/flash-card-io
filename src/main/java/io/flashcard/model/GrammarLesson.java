package io.flashcard.model;

public record GrammarLesson(
    String id,
    String cefrLevel,
    String topic,
    String topicTr,
    String content,
    String contentTr
) {
    public String getContent(String lang) {
        if ("turkish".equals(lang) && contentTr != null && !contentTr.isBlank()) {
            return contentTr;
        }
        return content;
    }

    public String getTopic(String lang) {
        if ("turkish".equals(lang) && topicTr != null && !topicTr.isBlank()) {
            return topicTr;
        }
        return topic;
    }
}
