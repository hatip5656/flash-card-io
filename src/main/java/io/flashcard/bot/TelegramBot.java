package io.flashcard.bot;

import io.flashcard.config.AppProperties;
import io.flashcard.model.GrammarLesson;
import io.flashcard.model.UserPreferences;
import io.flashcard.repository.*;
import io.flashcard.service.*;
import static io.flashcard.service.TextUtils.streakEmoji;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.*;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;
import java.util.*;

@Component
@ConditionalOnProperty(name = "feature.telegram", havingValue = "true")
public class TelegramBot implements LongPollingSingleThreadUpdateConsumer, DeliveryService.TelegramDeliveryChannel {

    private static final Logger log = LoggerFactory.getLogger(TelegramBot.class);

    private final AppProperties appProperties;
    private final SubscriberRepository subscriberRepo;
    private final ActivityRepository activityRepo;
    private final SentWordRepository sentWordRepo;
    private final GrammarRepository grammarRepo;
    private final WordBankService wordBankService;
    private final ScheduleService scheduleService;
    private final DeliveryService deliveryService;
    private final QuizRepository quizRepo;

    private TelegramClient telegramClient;
    private TelegramBotsLongPollingApplication botApplication;

    public TelegramBot(AppProperties appProperties, SubscriberRepository subscriberRepo,
                       ActivityRepository activityRepo, SentWordRepository sentWordRepo,
                       GrammarRepository grammarRepo, WordBankService wordBankService,
                       ScheduleService scheduleService, DeliveryService deliveryService,
                       QuizRepository quizRepo) {
        this.appProperties = appProperties;
        this.subscriberRepo = subscriberRepo;
        this.activityRepo = activityRepo;
        this.sentWordRepo = sentWordRepo;
        this.grammarRepo = grammarRepo;
        this.wordBankService = wordBankService;
        this.scheduleService = scheduleService;
        this.deliveryService = deliveryService;
        this.quizRepo = quizRepo;
    }

    @PostConstruct
    public void init() {
        String token = System.getenv("TELEGRAM_BOT_TOKEN");
        if (token == null || token.isBlank()) {
            log.warn("[bot] TELEGRAM_BOT_TOKEN not set, bot disabled");
            return;
        }

        telegramClient = new OkHttpTelegramClient(token);
        deliveryService.setTelegramChannel(this);

        try {
            telegramClient.execute(SetMyCommands.builder()
                .commands(List.of(
                    BotCommand.builder().command("start").description("Start the bot / show menu").build(),
                    BotCommand.builder().command("next").description("Get a flashcard now").build(),
                    BotCommand.builder().command("grammar").description("Get a grammar card").build(),
                    BotCommand.builder().command("quiz").description("Start a vocabulary quiz").build(),
                    BotCommand.builder().command("stats").description("See your progress").build(),
                    BotCommand.builder().command("level").description("Change level").build(),
                    BotCommand.builder().command("schedule").description("Change schedule").build(),
                    BotCommand.builder().command("stop").description("Stop receiving flashcards").build()))
                .build());
            log.info("[bot] Bot commands registered");

            botApplication = new TelegramBotsLongPollingApplication();
            botApplication.registerBot(token, this);
            log.info("[bot] Telegram bot started (long polling)");
        } catch (Exception e) {
            log.error("[bot] Failed to start: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (botApplication != null) {
            try { botApplication.close(); } catch (Exception ignored) {}
            log.info("[bot] Telegram bot stopped");
        }
    }

    @Override
    public void consume(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                handleMessage(update.getMessage());
            } else if (update.hasCallbackQuery()) {
                long chatId = update.getCallbackQuery().getMessage().getChatId();
                String data = update.getCallbackQuery().getData();
                log.info("[bot] Callback from chat={} data={}", chatId, data);
                handleCallback(chatId, data, update.getCallbackQuery().getId());
            } else {
                log.debug("[bot] Ignoring update type: hasMessage={} hasCallback={} hasInline={}",
                    update.hasMessage(), update.hasCallbackQuery(), update.hasInlineQuery());
            }
        } catch (Exception e) {
            log.error("[bot] Error processing update: {}", e.getMessage(), e);
        }
    }

    private void handleMessage(Message message) {
        long chatId = message.getChatId();
        String text = message.getText().trim();
        String username = message.getFrom() != null ? message.getFrom().getUserName() : null;

        if (!text.startsWith("/")) {
            log.debug("[bot] Ignoring non-command text from chat={}: {}", chatId, text.substring(0, Math.min(50, text.length())));
            return;
        }

        String command = text.split("\\s+")[0].replace("@", " ").split(" ")[0].toLowerCase();
        log.info("[bot] Command from chat={} user={}: {}", chatId, username, command);

        try {
            switch (command) {
                case "/start" -> handleStart(chatId, message);
                case "/next" -> {
                    log.info("[bot] /next requested by chat={}", chatId);
                    Thread.ofVirtual().start(() -> {
                        try {
                            deliveryService.deliverFlashcard(chatId);
                        } catch (Exception e) {
                            log.error("[bot] /next delivery failed for chat={}: {}", chatId, e.getMessage(), e);
                            sendText(chatId, "Failed to send flashcard. Try again later.");
                        }
                    });
                }
                case "/grammar" -> {
                    log.info("[bot] /grammar requested by chat={}", chatId);
                    Thread.ofVirtual().start(() -> {
                        try {
                            deliveryService.deliverGrammarCard(chatId);
                        } catch (Exception e) {
                            log.error("[bot] /grammar delivery failed for chat={}: {}", chatId, e.getMessage(), e);
                            sendText(chatId, "Failed to send grammar card. Try again later.");
                        }
                    });
                }
                case "/stats", "/settings" -> sendStats(chatId);
                case "/level" -> sendLevelPicker(chatId);
                case "/schedule" -> sendSchedulePicker(chatId);
                case "/stop" -> {
                    log.info("[bot] /stop from chat={}", chatId);
                    subscriberRepo.removeSubscriber(chatId);
                    sendText(chatId, "Stopped. Send /start to resume.");
                }
                case "/quiz" -> sendText(chatId, "Use the mobile app for quizzes, or tap the Quiz button in settings.");
                default -> log.debug("[bot] Unknown command from chat={}: {}", chatId, command);
            }
        } catch (Exception e) {
            log.error("[bot] Error handling command {} from chat={}: {}", command, chatId, e.getMessage(), e);
            sendText(chatId, "Something went wrong. Please try again.");
        }
    }

    private void handleStart(long chatId, Message message) {
        String username = message.getFrom() != null ? message.getFrom().getUserName() : null;
        String firstName = message.getFrom() != null ? message.getFrom().getFirstName() : null;
        log.info("[bot] /start from chat={} user={} firstName={}", chatId, username, firstName);
        subscriberRepo.addSubscriber(chatId, "telegram", username, firstName);
        sendStats(chatId);
    }

    private void sendStats(long chatId) {
        try {
            Map<String, Object> stats = activityRepo.getStats(chatId);
            int sent = (int) stats.get("sent");
            String level = (String) stats.get("level");
            String schedule = (String) stats.get("schedule");
            int streak = activityRepo.getStreak(chatId);
            String emoji = streakEmoji(streak);
            String scheduleLabel = scheduleService.findLabelForCron(schedule);
            int totalForLevel = wordBankService.getWordsForLevel(level).size();
            int pct = totalForLevel > 0 ? Math.round((float) sent / totalForLevel * 100) : 0;

            String text = "<b>\uD83C\uDDEA\uD83C\uDDEA Flash Card IO</b>\n\n"
                + emoji + " Streak: <b>" + streak + " day" + (streak != 1 ? "s" : "") + "</b>\n"
                + "\uD83C\uDFF7\uFE0F Level: <b>" + level + "</b>\n"
                + "\u23F0 Schedule: <b>" + TextUtils.escapeHtml(scheduleLabel) + "</b>\n"
                + "\uD83D\uDCDA Words learned: <b>" + sent + "</b>\n"
                + "\uD83D\uDCD6 Local " + level + " words: " + totalForLevel + "\n"
                + "\u2705 Progress: " + pct + "%";

            sendHtmlWithKeyboard(chatId, text, mainMenuKeyboard());
            log.debug("[bot] Stats sent to chat={} level={} words={} streak={}", chatId, level, sent, streak);
        } catch (Exception e) {
            log.error("[bot] Failed to build/send stats for chat={}: {}", chatId, e.getMessage(), e);
            sendText(chatId, "Failed to load stats. Please try again.");
        }
    }

    private void handleCallback(long chatId, String data, String callbackId) {
        try {
            if (data.startsWith("action:")) {
                String action = data.split(":")[1];
                log.info("[bot] Action callback chat={} action={}", chatId, action);
                switch (action) {
                    case "next" -> Thread.ofVirtual().start(() -> {
                        try {
                            deliveryService.deliverFlashcard(chatId);
                        } catch (Exception e) {
                            log.error("[bot] Callback next failed for chat={}: {}", chatId, e.getMessage(), e);
                        }
                    });
                    case "grammar" -> Thread.ofVirtual().start(() -> {
                        try {
                            deliveryService.deliverGrammarCard(chatId);
                        } catch (Exception e) {
                            log.error("[bot] Callback grammar failed for chat={}: {}", chatId, e.getMessage(), e);
                        }
                    });
                    case "stats" -> sendStats(chatId);
                    default -> log.warn("[bot] Unknown action callback: {} from chat={}", action, chatId);
                }
            } else if (data.startsWith("edit_level") || data.startsWith("edit_")) {
                String field = data.replace("edit_", "");
                log.info("[bot] Edit callback chat={} field={}", chatId, field);
                switch (field) {
                    case "level" -> sendLevelPicker(chatId);
                    case "schedule" -> sendSchedulePicker(chatId);
                    default -> log.warn("[bot] Unknown edit field: {} from chat={}", field, chatId);
                }
            } else if (data.startsWith("set:level:")) {
                String level = data.split(":")[2];
                log.info("[bot] Set level chat={} level={}", chatId, level);
                subscriberRepo.setSubscriberLevel(chatId, level);
                sendText(chatId, "Level set to " + level + ".");
                sendStats(chatId);
            } else if (data.startsWith("set:schedule:")) {
                String key = data.split(":")[2];
                var preset = scheduleService.getPreset(key);
                if (preset != null) {
                    log.info("[bot] Set schedule chat={} key={} cron={}", chatId, key, preset.cron());
                    subscriberRepo.setSubscriberSchedule(chatId, preset.cron());
                    sendText(chatId, "\u23F0 Schedule: " + preset.label());
                } else {
                    log.warn("[bot] Unknown schedule preset: {} from chat={}", key, chatId);
                }
            } else if (data.startsWith("recall:")) {
                String[] parts = data.split(":", 3);
                String action = parts[1];
                String wordValue = parts.length > 2 ? parts[2] : "";
                int quality = "got".equals(action) ? 4 : 1;
                log.info("[bot] Recall chat={} word={} action={} quality={}", chatId, wordValue, action, quality);
                sentWordRepo.updateSm2(chatId, wordValue, quality);
                sendText(chatId, "got".equals(action) ? "\u2705 Nice! Moving on." : "\uD83D\uDD04 We'll show this again soon.");
            } else {
                log.warn("[bot] Unhandled callback data: {} from chat={}", data, chatId);
            }
        } catch (Exception e) {
            log.error("[bot] Error handling callback data={} from chat={}: {}", data, chatId, e.getMessage(), e);
        }
    }

    // --- Keyboard builders ---

    private InlineKeyboardMarkup mainMenuKeyboard() {
        return InlineKeyboardMarkup.builder()
            .keyboardRow(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("\uD83D\uDCDA Next Card").callbackData("action:next").build(),
                InlineKeyboardButton.builder().text("\uD83D\uDCD6 Grammar").callbackData("action:grammar").build()))
            .keyboardRow(new InlineKeyboardRow(
                InlineKeyboardButton.builder().text("\uD83D\uDCCA Stats").callbackData("action:stats").build(),
                InlineKeyboardButton.builder().text("\uD83C\uDFF7\uFE0F Level").callbackData("edit_level").build(),
                InlineKeyboardButton.builder().text("\u23F0 Schedule").callbackData("edit_schedule").build()))
            .build();
    }

    private void sendLevelPicker(long chatId) {
        String current = subscriberRepo.getSubscriberLevel(chatId);
        log.debug("[bot] Sending level picker to chat={} current={}", chatId, current);
        InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder()
            .keyboardRow(new InlineKeyboardRow(
                List.of("A1", "A2", "B1", "B2").stream()
                    .map(l -> InlineKeyboardButton.builder()
                        .text(l.equals(current) ? "\u2705 " + l : l)
                        .callbackData("set:level:" + l).build())
                    .toList()))
            .build();
        sendHtmlWithKeyboard(chatId, "Select your level:", kb);
    }

    private void sendSchedulePicker(long chatId) {
        String currentCron = subscriberRepo.getSubscriberSchedule(chatId);
        log.debug("[bot] Sending schedule picker to chat={} current={}", chatId, currentCron);
        List<InlineKeyboardRow> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        int i = 0;
        for (var entry : ScheduleService.PRESETS.entrySet()) {
            String label = entry.getValue().cron().equals(currentCron) ? "\u2705 " + entry.getValue().label() : entry.getValue().label();
            row.add(InlineKeyboardButton.builder().text(label).callbackData("set:schedule:" + entry.getKey()).build());
            if (++i % 2 == 0) { rows.add(new InlineKeyboardRow(row)); row = new ArrayList<>(); }
        }
        if (!row.isEmpty()) rows.add(new InlineKeyboardRow(row));
        sendHtmlWithKeyboard(chatId, "Select your schedule:", InlineKeyboardMarkup.builder().keyboard(rows).build());
    }

    // --- DeliveryChannel implementation ---

    @Override
    public boolean sendFlashcard(long chatId, FlashcardBuilderService.Flashcard flashcard) {
        log.info("[telegram] Sending flashcard to chat={} word={} hasImage={} hasAudio={}",
            chatId, flashcard.word().getEstonian(), flashcard.imageUrl() != null, flashcard.audio() != null);
        try {
            String caption = flashcard.caption();

            if (flashcard.imageUrl() != null && caption.length() <= 1024) {
                log.debug("[telegram] Sending photo+caption to chat={} imageUrl={}", chatId, flashcard.imageUrl());
                telegramClient.execute(SendPhoto.builder()
                    .chatId(chatId).photo(new InputFile(flashcard.imageUrl()))
                    .caption(caption).parseMode("HTML").build());
            } else if (flashcard.imageUrl() != null) {
                log.debug("[telegram] Sending photo then text to chat={} (caption too long: {} chars)", chatId, caption.length());
                telegramClient.execute(SendPhoto.builder()
                    .chatId(chatId).photo(new InputFile(flashcard.imageUrl())).build());
                telegramClient.execute(SendMessage.builder()
                    .chatId(chatId).text(caption).parseMode("HTML").build());
            } else {
                log.debug("[telegram] Sending text-only flashcard to chat={}", chatId);
                telegramClient.execute(SendMessage.builder()
                    .chatId(chatId).text(caption).parseMode("HTML").build());
            }

            if (flashcard.audio() != null) {
                log.debug("[telegram] Sending voice audio to chat={} size={}bytes", chatId, flashcard.audio().length);
                telegramClient.execute(SendVoice.builder()
                    .chatId(chatId)
                    .voice(new InputFile(new ByteArrayInputStream(flashcard.audio()), "pronunciation.ogg"))
                    .build());
            }

            if (flashcard.word().getEstonian() != null && !flashcard.word().getEstonian().isBlank()) {
                String wordValue = flashcard.word().getEstonian();
                telegramClient.execute(SendMessage.builder()
                    .chatId(chatId).text("How did you do?")
                    .replyMarkup(InlineKeyboardMarkup.builder()
                        .keyboardRow(new InlineKeyboardRow(
                            InlineKeyboardButton.builder().text("Got it \u2705").callbackData("recall:got:" + wordValue).build(),
                            InlineKeyboardButton.builder().text("Again \uD83D\uDD04").callbackData("recall:again:" + wordValue).build()))
                        .build())
                    .build());
            }

            log.info("[telegram] Flashcard sent successfully to chat={} word={}", chatId, flashcard.word().getEstonian());
            return true;
        } catch (Exception e) {
            log.error("[telegram] Failed to send flashcard to chat={} word={}: {}", chatId,
                flashcard.word().getEstonian(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void sendMessage(long chatId, String html) {
        log.debug("[telegram] Sending message to chat={} length={}", chatId, html.length());
        sendText(chatId, html);
    }

    @Override
    public void sendTyping(long chatId) {
        try {
            telegramClient.execute(SendChatAction.builder().chatId(chatId).action("typing").build());
        } catch (Exception e) {
            log.debug("[telegram] Failed to send typing to chat={}: {}", chatId, e.getMessage());
        }
    }

    private void sendText(long chatId, String html) {
        try {
            telegramClient.execute(SendMessage.builder().chatId(chatId).text(html).parseMode("HTML").build());
        } catch (Exception e) {
            log.error("[telegram] Failed to send text to chat={}: {} | text preview: {}", chatId, e.getMessage(),
                html.substring(0, Math.min(100, html.length())));
        }
    }

    private void sendHtmlWithKeyboard(long chatId, String html, InlineKeyboardMarkup keyboard) {
        try {
            telegramClient.execute(SendMessage.builder()
                .chatId(chatId).text(html).parseMode("HTML").replyMarkup(keyboard).build());
        } catch (Exception e) {
            log.error("[telegram] Failed to send message with keyboard to chat={}: {} | text preview: {}", chatId,
                e.getMessage(), html.substring(0, Math.min(100, html.length())));
        }
    }
}
