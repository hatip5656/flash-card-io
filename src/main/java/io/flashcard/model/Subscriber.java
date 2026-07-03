package io.flashcard.model;

import java.time.Instant;

public class Subscriber {

    private long chatId;
    private String channel;
    private String cefrLevel;
    private String schedule;
    private boolean active;
    private String username;
    private String firstName;
    private Instant nextDeliveryAt;
    private Instant nextGrammarAt;

    public long getChatId() { return chatId; }
    public void setChatId(long chatId) { this.chatId = chatId; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getCefrLevel() { return cefrLevel; }
    public void setCefrLevel(String cefrLevel) { this.cefrLevel = cefrLevel; }

    public String getSchedule() { return schedule; }
    public void setSchedule(String schedule) { this.schedule = schedule; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public Instant getNextDeliveryAt() { return nextDeliveryAt; }
    public void setNextDeliveryAt(Instant nextDeliveryAt) { this.nextDeliveryAt = nextDeliveryAt; }

    public Instant getNextGrammarAt() { return nextGrammarAt; }
    public void setNextGrammarAt(Instant nextGrammarAt) { this.nextGrammarAt = nextGrammarAt; }
}
