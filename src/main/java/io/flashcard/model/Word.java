package io.flashcard.model;

import java.util.List;

public class Word {

    private String id;
    private String estonian;
    private String english;
    private String turkish;
    private String cefrLevel;
    private String imageQuery;
    private String imageUrl;
    private String imagePhotographer;
    private List<Sentence> sentences;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEstonian() { return estonian; }
    public void setEstonian(String estonian) { this.estonian = estonian; }

    public String getEnglish() { return english; }
    public void setEnglish(String english) { this.english = english; }

    public String getTurkish() { return turkish; }
    public void setTurkish(String turkish) { this.turkish = turkish; }

    public String getCefrLevel() { return cefrLevel; }
    public void setCefrLevel(String cefrLevel) { this.cefrLevel = cefrLevel; }

    public String getImageQuery() { return imageQuery; }
    public void setImageQuery(String imageQuery) { this.imageQuery = imageQuery; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getImagePhotographer() { return imagePhotographer; }
    public void setImagePhotographer(String imagePhotographer) { this.imagePhotographer = imagePhotographer; }

    public List<Sentence> getSentences() { return sentences; }
    public void setSentences(List<Sentence> sentences) { this.sentences = sentences; }

    public record Sentence(String estonian, String english, String turkish) {}
}
