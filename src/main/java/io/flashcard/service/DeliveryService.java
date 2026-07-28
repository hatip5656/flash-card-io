package io.flashcard.service;

import io.flashcard.config.AppProperties;
import io.flashcard.model.UserPreferences;
import io.flashcard.model.Word;
import io.flashcard.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);
    private static final int MAX_CONCURRENT_BUILDS = 3;

    private final SubscriberRepository subscriberRepo;
    private final SentWordRepository sentWordRepo;
    private final ActivityRepository activityRepo;
    private final GrammarRepository grammarRepo;
    private final AppProperties appProperties;
    private final WordBankService wordBankService;
    private final GrammarBankService grammarBankService;
    private final FlashcardBuilderService flashcardBuilder;
    private final EkilexService ekilexService;
    private final PrebuildQueueService prebuildQueue;
    private final DiskCacheService diskCache;
    private final Semaphore buildSemaphore = new Semaphore(MAX_CONCURRENT_BUILDS);

    private volatile TelegramDeliveryChannel telegramChannel;

    public DeliveryService(SubscriberRepository subscriberRepo, SentWordRepository sentWordRepo,
                           ActivityRepository activityRepo, GrammarRepository grammarRepo,
                           AppProperties appProperties, WordBankService wordBankService,
                           GrammarBankService grammarBankService, FlashcardBuilderService flashcardBuilder,
                           EkilexService ekilexService, PrebuildQueueService prebuildQueue,
                           DiskCacheService diskCache) {
        this.subscriberRepo = subscriberRepo;
        this.sentWordRepo = sentWordRepo;
        this.activityRepo = activityRepo;
        this.grammarRepo = grammarRepo;
        this.appProperties = appProperties;
        this.wordBankService = wordBankService;
        this.grammarBankService = grammarBankService;
        this.flashcardBuilder = flashcardBuilder;
        this.ekilexService = ekilexService;
        this.prebuildQueue = prebuildQueue;
        this.diskCache = diskCache;
    }

    public interface TelegramDeliveryChannel {
        boolean sendFlashcard(long chatId, FlashcardBuilderService.Flashcard flashcard);
        void sendMessage(long chatId, String html);
        void sendTyping(long chatId);
    }

    public void setTelegramChannel(TelegramDeliveryChannel channel) {
        this.telegramChannel = channel;
    }

    public void deliverFlashcard(long chatId) {
        long startTime = System.currentTimeMillis();
        String level = subscriberRepo.getSubscriberLevel(chatId);
        UserPreferences prefs = subscriberRepo.getPreferences(chatId);
        var buildOpts = new FlashcardBuilderService.BuildOptions(prefs.isAudio(), prefs.getVoiceName(), prefs.isWordForms(), false, prefs.getNativeLanguage());

        List<String> sentIds = sentWordRepo.getSentWordIds(chatId);
        List<Word> unsent = wordBankService.getUnsentUpToLevel(level, sentIds);
        log.info("[delivery] chat={} level={} sentCount={} unsentCount={}", chatId, level, sentIds.size(), unsent.size());

        FlashcardBuilderService.Flashcard flashcard;
        String wordId, wordValue, english;

        if (!unsent.isEmpty()) {
            Word word = unsent.get(ThreadLocalRandom.current().nextInt(unsent.size()));
            log.info("[delivery] Building flashcard for \"{}\" ({}) -> chat {} audio={} wordForms={}",
                word.getEstonian(), word.getCefrLevel(), chatId, prefs.isAudio(), prefs.isWordForms());

            if (telegramChannel != null) telegramChannel.sendTyping(chatId);

            long buildStart = System.currentTimeMillis();
            try {
                buildSemaphore.acquire();
                flashcard = flashcardBuilder.buildFlashcard(word, buildOpts);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } finally {
                buildSemaphore.release();
            }
            log.info("[delivery] Flashcard built for \"{}\" in {}ms (image={} audio={}bytes)",
                word.getEstonian(), System.currentTimeMillis() - buildStart,
                flashcard.imageUrl() != null, flashcard.audio() != null ? flashcard.audio().length : 0);

            wordId = word.getId();
            wordValue = word.getEstonian();
            english = word.getEnglish();
        } else {
            String ekilexKey = appProperties.getEkilexApiKey();
            if (ekilexKey != null && !ekilexKey.isBlank()) {
                log.info("[delivery] Local {} words exhausted for chat={}, querying Ekilex", level, chatId);
                long ekilexStart = System.currentTimeMillis();
                Set<String> sentValues = sentWordRepo.getSentWordValues(chatId);
                var ekilexWord = ekilexService.getRandomWordForLevel(level, sentValues, ekilexKey);
                log.info("[delivery] Ekilex lookup took {}ms result={}", System.currentTimeMillis() - ekilexStart,
                    ekilexWord != null ? ekilexWord.wordValue() : "null");
                if (ekilexWord != null) {
                    long buildStart = System.currentTimeMillis();
                    try {
                        buildSemaphore.acquire();
                        flashcard = flashcardBuilder.buildFlashcardFromEkilex(ekilexWord, buildOpts);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    } finally {
                        buildSemaphore.release();
                    }
                    log.info("[delivery] Ekilex flashcard built for \"{}\" in {}ms",
                        ekilexWord.wordValue(), System.currentTimeMillis() - buildStart);
                    wordId = "ekilex-" + ekilexWord.wordId();
                    wordValue = ekilexWord.wordValue();
                    english = flashcard.word().getEnglish();
                } else {
                    log.info("[delivery] No new {} words found in Ekilex for chat {}", level, chatId);
                    return;
                }
            } else {
                log.info("[delivery] All local {} words sent for chat {}, no Ekilex key", level, chatId);
                return;
            }
        }

        long sendStart = System.currentTimeMillis();
        if (telegramChannel != null && telegramChannel.sendFlashcard(chatId, flashcard)) {
            sentWordRepo.markWordSent(chatId, wordId, wordValue, english);
            int totalWords = activityRepo.logWordActivity(chatId);
            Integer milestone = activityRepo.checkMilestone(totalWords);
            log.info("[delivery] Total delivery for \"{}\" -> chat {} took {}ms (send={}ms)",
                wordValue, chatId, System.currentTimeMillis() - startTime, System.currentTimeMillis() - sendStart);
            if (milestone != null && telegramChannel != null) {
                telegramChannel.sendMessage(chatId,
                    "\uD83C\uDF89 <b>Milestone!</b> You've learned <b>" + milestone + "</b> words!");
            }
            log.info("[delivery] Sent \"{}\" to chat {}", wordValue, chatId);
        }
    }

    public void deliverGrammarCard(long chatId) {
        if (telegramChannel == null) return;
        UserPreferences prefs = subscriberRepo.getPreferences(chatId);
        if (!prefs.isGrammarCards()) return;

        String level = subscriberRepo.getSubscriberLevel(chatId);
        Set<String> sentIds = grammarRepo.getSentGrammarIds(chatId);
        var lesson = grammarBankService.getRandomLesson(level, sentIds);

        if (lesson != null) {
            telegramChannel.sendMessage(chatId, lesson.content());
            grammarRepo.markGrammarSent(chatId, lesson.id());
            log.info("[delivery] Sent grammar lesson \"{}\" -> chat {}", lesson.topic(), chatId);
        }
    }

    // --- Scheduled Jobs ---

    @Scheduled(cron = "0 * * * * *") // every minute
    public void deliveryTick() {
        if (telegramChannel == null) return;
        List<Long> due = subscriberRepo.getUsersDueForDelivery();
        if (due.isEmpty()) return;
        log.info("[scheduler] Delivery tick: {} user(s) due", due.size());

        String timezone = appProperties.getCronTimezone();
        for (long chatId : due) {
            int delay = ThreadLocalRandom.current().nextInt(30_000);
            Thread.ofVirtual().start(() -> {
                try { Thread.sleep(delay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                try {
                    deliverFlashcard(chatId);
                    String schedule = subscriberRepo.getSubscriberSchedule(chatId);
                    Instant next = computeNextCronRun(schedule, timezone);
                    subscriberRepo.updateNextDelivery(chatId, next);
                } catch (Exception e) {
                    log.error("[scheduler] Delivery error for chat {}: {}", chatId, e.getMessage());
                }
            });
        }
    }

    @Scheduled(cron = "0 * * * * *") // every minute
    public void grammarTick() {
        if (telegramChannel == null) return;
        List<Long> due = subscriberRepo.getUsersDueForGrammar();
        if (due.isEmpty()) return;
        log.info("[scheduler] Grammar tick: {} user(s) due", due.size());

        String timezone = appProperties.getCronTimezone();
        for (long chatId : due) {
            int delay = ThreadLocalRandom.current().nextInt(30_000);
            Thread.ofVirtual().start(() -> {
                try { Thread.sleep(delay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                try {
                    deliverGrammarCard(chatId);
                    scheduleNextGrammar(chatId, timezone);
                } catch (Exception e) {
                    log.error("[scheduler] Grammar error for chat {}: {}", chatId, e.getMessage());
                }
            });
        }
    }

    @Scheduled(cron = "0 0 10 ? * SUN") // Sundays at 10 AM
    public void weeklyReport() {
        if (telegramChannel == null) return;
        var subs = subscriberRepo.getActiveSubscribers();
        for (var sub : subs) {
            try {
                UserPreferences prefs = subscriberRepo.getPreferences(sub.getChatId());
                if (!prefs.isWeeklyReport()) continue;
                Map<String, Object> stats = activityRepo.getStats(sub.getChatId());
                int sent = (int) stats.get("sent");
                if (sent == 0) continue;
                String level = (String) stats.get("level");
                int streak = activityRepo.getStreak(sub.getChatId());
                String streakEmoji = TextUtils.streakEmoji(streak);
                String msg = "\uD83D\uDCCA <b>Weekly Progress Report</b>\n\n"
                    + streakEmoji + " Streak: <b>" + streak + " day" + (streak != 1 ? "s" : "") + "</b>\n"
                    + "\uD83C\uDFF7\uFE0F Level: <b>" + level + "</b>\n"
                    + "\uD83D\uDCDA Total words learned: <b>" + sent + "</b>";
                telegramChannel.sendMessage(sub.getChatId(), msg);
            } catch (Exception e) {
                log.error("[broadcast] Weekly report error for {}: {}", sub.getChatId(), e.getMessage());
            }
        }
    }

    @Scheduled(cron = "0 0 21 * * *") // Daily at 9 PM
    public void dailySummary() {
        if (telegramChannel == null) return;
        var subs = subscriberRepo.getActiveSubscribers();
        for (var sub : subs) {
            try {
                UserPreferences prefs = subscriberRepo.getPreferences(sub.getChatId());
                if (!prefs.isDailySummary()) continue;
                Map<String, Object> today = activityRepo.getTodayActivity(sub.getChatId());
                int wordsLearned = (int) today.get("wordsLearned");
                int quizzesTaken = (int) today.get("quizzesTaken");
                if (wordsLearned == 0 && quizzesTaken == 0) continue;
                int streak = activityRepo.getStreak(sub.getChatId());
                String streakEmoji = TextUtils.streakEmoji(streak);
                String msg = streakEmoji + " <b>Daily Summary</b>\n\n"
                    + "\uD83D\uDCDA Words learned today: <b>" + wordsLearned + "</b>\n"
                    + "\uD83E\uDDE0 Quizzes taken: <b>" + quizzesTaken + "</b>\n"
                    + streakEmoji + " Streak: <b>" + streak + " day" + (streak != 1 ? "s" : "") + "</b>";
                if (streak >= 3) msg += "\n\nKeep it up! \uD83D\uDCAA";
                telegramChannel.sendMessage(sub.getChatId(), msg);
            } catch (Exception e) {
                log.error("[broadcast] Daily summary error for {}: {}", sub.getChatId(), e.getMessage());
            }
        }
    }

    @Scheduled(cron = "0 0 */6 * * *") // Every 6 hours
    public void cacheEviction() {
        int tts = diskCache.evictExpired("tts", DiskCacheService.TTS_TTL_MS);
        int unsplash = diskCache.evictExpired("unsplash", DiskCacheService.UNSPLASH_TTL_MS);
        int ekilex = diskCache.evictExpired("ekilex", DiskCacheService.EKILEX_TTL_MS);
        if (tts + unsplash + ekilex > 0) {
            log.info("[cache] Evicted {} TTS, {} Unsplash, {} Ekilex expired entries", tts, unsplash, ekilex);
        }
    }

    @Scheduled(cron = "0 0 3 * * *") // Daily at 3 AM
    public void prebuildWarmup() {
        int cleaned = prebuildQueue.cleanupStaleEntries();
        if (cleaned > 0) log.info("[prebuild] Cleaned {} stale entries", cleaned);
        // Queue warm-up would iterate active subscribers and refill their queues
        // Simplified — real implementation would call refillQueue for each
    }

    public void scheduleNextGrammar(long chatId, String timezone) {
        int hour = 8 + ThreadLocalRandom.current().nextInt(14);
        int minute = ThreadLocalRandom.current().nextInt(60);
        ZonedDateTime next = ZonedDateTime.now(ZoneId.of(timezone))
            .plusDays(1).withHour(hour).withMinute(minute).withSecond(0);
        subscriberRepo.updateNextGrammar(chatId, next.toInstant());
    }

    private Instant computeNextCronRun(String cronExpression, String timezone) {
        // Simple next-run computation for common patterns
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of(timezone));
        if ("off".equals(cronExpression)) return null;

        // Parse "M H * * *" patterns
        String[] parts = cronExpression.split("\\s+");
        if (parts.length >= 5) {
            int minute = Integer.parseInt(parts[0]);
            String hourPart = parts[1];

            if (hourPart.contains(",")) {
                // Multiple hours: find next
                int[] hours = Arrays.stream(hourPart.split(",")).mapToInt(Integer::parseInt).sorted().toArray();
                for (int h : hours) {
                    ZonedDateTime candidate = now.withHour(h).withMinute(minute).withSecond(0);
                    if (candidate.isAfter(now)) return candidate.toInstant();
                }
                return now.plusDays(1).withHour(hours[0]).withMinute(minute).withSecond(0).toInstant();
            } else if (hourPart.contains("-")) {
                // Range: e.g. "9-21"
                String[] range = hourPart.split("-");
                int start = Integer.parseInt(range[0]);
                int end = Integer.parseInt(range[1]);
                for (int h = start; h <= end; h++) {
                    ZonedDateTime candidate = now.withHour(h).withMinute(minute).withSecond(0);
                    if (candidate.isAfter(now)) return candidate.toInstant();
                }
                return now.plusDays(1).withHour(start).withMinute(minute).withSecond(0).toInstant();
            } else if ("*".equals(hourPart)) {
                // Every hour
                ZonedDateTime next = now.withMinute(minute).withSecond(0);
                if (!next.isAfter(now)) next = next.plusHours(1);
                return next.toInstant();
            } else {
                // Single hour
                int hour = Integer.parseInt(hourPart);
                ZonedDateTime next = now.withHour(hour).withMinute(minute).withSecond(0);
                if (!next.isAfter(now)) next = next.plusDays(1);
                return next.toInstant();
            }
        }
        return now.plusDays(1).withHour(9).withMinute(0).toInstant();
    }
}
