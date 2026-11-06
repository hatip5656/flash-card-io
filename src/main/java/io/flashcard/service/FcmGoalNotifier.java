package io.flashcard.service;

import io.flashcard.repository.DailyGoalRepository;
import io.flashcard.repository.SubscriberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/** Sends daily goal messages to every device of a learner, forgetting devices FCM no longer knows. */
@Service
public class FcmGoalNotifier implements GoalNotifier {

    private static final Logger log = LoggerFactory.getLogger(FcmGoalNotifier.class);

    private final FcmClient fcm;
    private final DailyGoalRepository repo;
    private final SubscriberRepository subscribers;
    private final AtomicBoolean loggedOff = new AtomicBoolean();

    public FcmGoalNotifier(FcmClient fcm, DailyGoalRepository repo, SubscriberRepository subscribers) {
        this.fcm = fcm;
        this.repo = repo;
        this.subscribers = subscribers;
    }

    @Override
    public void sendMorning(long chatId, DailyGoalService.Today today) {
        send(chatId, GoalMessages.morning(today, lang(chatId)));
    }

    @Override
    public void sendEvening(long chatId, DailyGoalService.Today today) {
        send(chatId, GoalMessages.evening(today, lang(chatId)));
    }

    @Override
    public void sendDone(long chatId, int streak) {
        send(chatId, GoalMessages.done(streak, lang(chatId)));
    }

    private void send(long chatId, String body) {
        if (!fcm.isConfigured()) {
            if (loggedOff.compareAndSet(false, true)) log.info("FIREBASE_SERVICE_ACCOUNT not set; daily goal notifications are off");
            return;
        }
        for (String token : repo.deviceTokens(chatId)) {
            if (fcm.send(token, GoalMessages.TITLE, body) == FcmClient.SendResult.DEAD_TOKEN) repo.deleteDevice(token);
        }
    }

    private String lang(long chatId) {
        return subscribers.getPreferences(chatId).getNativeLanguage();
    }
}
