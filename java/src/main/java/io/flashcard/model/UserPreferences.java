package io.flashcard.model;

public class UserPreferences {

    private boolean audio = true;
    private String voiceName = "mari";
    private boolean wordForms = true;
    private boolean grammarCards = true;
    private boolean dailySummary = true;
    private boolean weeklyReport = true;
    private String nativeLanguage = "turkish";
    private String theme = "system";

    public boolean isAudio() { return audio; }
    public void setAudio(boolean audio) { this.audio = audio; }

    public String getVoiceName() { return voiceName; }
    public void setVoiceName(String voiceName) { this.voiceName = voiceName; }

    public boolean isWordForms() { return wordForms; }
    public void setWordForms(boolean wordForms) { this.wordForms = wordForms; }

    public boolean isGrammarCards() { return grammarCards; }
    public void setGrammarCards(boolean grammarCards) { this.grammarCards = grammarCards; }

    public boolean isDailySummary() { return dailySummary; }
    public void setDailySummary(boolean dailySummary) { this.dailySummary = dailySummary; }

    public boolean isWeeklyReport() { return weeklyReport; }
    public void setWeeklyReport(boolean weeklyReport) { this.weeklyReport = weeklyReport; }

    public String getNativeLanguage() { return nativeLanguage; }
    public void setNativeLanguage(String nativeLanguage) { this.nativeLanguage = nativeLanguage; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public static UserPreferences defaults() {
        return new UserPreferences();
    }
}
