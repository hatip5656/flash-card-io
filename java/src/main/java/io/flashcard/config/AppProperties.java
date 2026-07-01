package io.flashcard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String unsplashAccessKey = "";
    private String pexelsApiKey = "";
    private String ekilexApiKey = "";
    private String ttsApiUrl = "http://tts-api:8000";
    private String ttsSpeaker = "mari";
    private String cronTimezone = "Europe/Tallinn";
    private String cronSchedule = "0 9 * * *";
    private List<String> cefrLevels = List.of("A1", "A2");

    public String getUnsplashAccessKey() { return unsplashAccessKey; }
    public void setUnsplashAccessKey(String v) { this.unsplashAccessKey = v; }

    public String getPexelsApiKey() { return pexelsApiKey; }
    public void setPexelsApiKey(String v) { this.pexelsApiKey = v; }

    public String getEkilexApiKey() { return ekilexApiKey; }
    public void setEkilexApiKey(String v) { this.ekilexApiKey = v; }

    public String getTtsApiUrl() { return ttsApiUrl; }
    public void setTtsApiUrl(String v) { this.ttsApiUrl = v; }

    public String getTtsSpeaker() { return ttsSpeaker; }
    public void setTtsSpeaker(String v) { this.ttsSpeaker = v; }

    public String getCronTimezone() { return cronTimezone; }
    public void setCronTimezone(String v) { this.cronTimezone = v; }

    public String getCronSchedule() { return cronSchedule; }
    public void setCronSchedule(String v) { this.cronSchedule = v; }

    public List<String> getCefrLevels() { return cefrLevels; }
    public void setCefrLevels(List<String> v) { this.cefrLevels = v; }
}
