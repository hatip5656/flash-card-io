package io.flashcard.model;

import java.util.List;

public class Dialog {

    private String id;
    private String title;
    private String titleTr;
    private String cefrLevel;
    private String category;
    private String situation;
    private String situationTr;
    private String icon;
    private int sortOrder;
    private int lineCount;
    private List<Line> lines;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getTitleTr() { return titleTr; }
    public void setTitleTr(String titleTr) { this.titleTr = titleTr; }

    public String getCefrLevel() { return cefrLevel; }
    public void setCefrLevel(String cefrLevel) { this.cefrLevel = cefrLevel; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSituation() { return situation; }
    public void setSituation(String situation) { this.situation = situation; }

    public String getSituationTr() { return situationTr; }
    public void setSituationTr(String situationTr) { this.situationTr = situationTr; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public int getLineCount() { return lineCount; }
    public void setLineCount(int lineCount) { this.lineCount = lineCount; }

    public List<Line> getLines() { return lines; }
    public void setLines(List<Line> lines) { this.lines = lines; }

    public record Line(String speaker, String estonian, String english, String turkish, int sortOrder) {}
}
