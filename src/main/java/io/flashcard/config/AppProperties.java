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
    private String translatorApiUrl = "http://translator-api:8000";
    private String teacherSimulatorUrl = "http://teacher-simulator:8080";
    private String videoPublicBaseUrl = "https://wordagram.hatip.dev/videos/";
    private String cronTimezone = "Europe/Tallinn";
    private String cronSchedule = "0 9 * * *";
    private List<String> cefrLevels = List.of("A1", "A2");
    /** OAuth client ids whose Google ID tokens are accepted (the web client id the app asks tokens for) */
    private List<String> googleClientIds = List.of();
    // Service-account JSON for sending push notifications (FCM); blank = notifications off
    private String firebaseServiceAccount = "";

    public List<String> getGoogleClientIds() { return googleClientIds; }
    public void setGoogleClientIds(List<String> v) { this.googleClientIds = v; }
    public String getFirebaseServiceAccount() { return firebaseServiceAccount; }
    public void setFirebaseServiceAccount(String v) { this.firebaseServiceAccount = v; }

    public String getUnsplashAccessKey() { return unsplashAccessKey; }
    public void setUnsplashAccessKey(String v) { this.unsplashAccessKey = v; }

    public String getPexelsApiKey() { return pexelsApiKey; }
    public void setPexelsApiKey(String v) { this.pexelsApiKey = v; }

    public String getEkilexApiKey() { return ekilexApiKey; }
    public void setEkilexApiKey(String v) { this.ekilexApiKey = v; }

    public String getTtsApiUrl() { return ttsApiUrl; }
    public void setTtsApiUrl(String v) { this.ttsApiUrl = v; }

    public String getTranslatorApiUrl() { return translatorApiUrl; }
    public void setTranslatorApiUrl(String v) { this.translatorApiUrl = v; }

    public String getTeacherSimulatorUrl() { return teacherSimulatorUrl; }
    public void setTeacherSimulatorUrl(String v) { this.teacherSimulatorUrl = v; }

    public String getVideoPublicBaseUrl() { return videoPublicBaseUrl; }
    public void setVideoPublicBaseUrl(String v) { this.videoPublicBaseUrl = v; }

    public String getCronTimezone() { return cronTimezone; }
    public void setCronTimezone(String v) { this.cronTimezone = v; }

    public String getCronSchedule() { return cronSchedule; }
    public void setCronSchedule(String v) { this.cronSchedule = v; }

    public List<String> getCefrLevels() { return cefrLevels; }
    public void setCefrLevels(List<String> v) { this.cefrLevels = v; }
}
