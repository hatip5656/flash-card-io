package io.flashcard.model;

import java.util.List;

public class GrammarStory {

    private String id;
    private String cefrLevel;
    private String topic;
    private String icon;
    private List<Slide> slides;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCefrLevel() { return cefrLevel; }
    public void setCefrLevel(String cefrLevel) { this.cefrLevel = cefrLevel; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public List<Slide> getSlides() { return slides; }
    public void setSlides(List<Slide> slides) { this.slides = slides; }

    public record Slide(
        String title,
        String body,
        String highlight,
        List<Example> examples
    ) {}

    public record Example(String estonian, String english, String turkish) {}
}
