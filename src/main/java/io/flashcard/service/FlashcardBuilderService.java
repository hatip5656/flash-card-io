package io.flashcard.service;

import io.flashcard.config.AppProperties;
import io.flashcard.model.Word;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class FlashcardBuilderService {

    private static final Logger log = LoggerFactory.getLogger(FlashcardBuilderService.class);

    private final ImageService imageService;
    private final SentenceService sentenceService;
    private final TtsService ttsService;
    private final EkilexService ekilexService;
    private final GrammarBuilderService grammarBuilder;
    private final AppProperties appProperties;

    public FlashcardBuilderService(ImageService imageService, SentenceService sentenceService,
                                   TtsService ttsService, EkilexService ekilexService,
                                   GrammarBuilderService grammarBuilder, AppProperties appProperties) {
        this.imageService = imageService;
        this.sentenceService = sentenceService;
        this.ttsService = ttsService;
        this.ekilexService = ekilexService;
        this.grammarBuilder = grammarBuilder;
        this.appProperties = appProperties;
    }

    public record Flashcard(
        Word word,
        SentenceService.Sentence sentence,
        String imageUrl,
        String photographer,
        String photographerUrl,
        String caption,
        byte[] audio
    ) {}

    public record BuildOptions(boolean audioEnabled, String voiceName, boolean wordFormsEnabled, boolean isReview, String nativeLang) {
        public BuildOptions() { this(true, null, true, false, "english"); }
        public BuildOptions(boolean audioEnabled, String voiceName, boolean wordFormsEnabled, boolean isReview) {
            this(audioEnabled, voiceName, wordFormsEnabled, isReview, "english");
        }
    }

    public Flashcard buildFlashcard(Word word, BuildOptions options) {
        SentenceService.Sentence sentence = sentenceService.resolveSentence(word);
        String query = word.getImageQuery() != null ? word.getImageQuery() : word.getEnglish();

        // Run image fetch and TTS in parallel
        var imageFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> imageService.fetchImage(query));

        boolean shouldTts = options.audioEnabled() && ttsService.isAvailable();
        var audioFuture = shouldTts
            ? java.util.concurrent.CompletableFuture.supplyAsync(() ->
                ttsService.synthesizeSpeech(word.getEstonian(), sentence.estonian(), options.voiceName()))
            : java.util.concurrent.CompletableFuture.completedFuture((byte[]) null);

        ImageService.ImageResult photo = imageFuture.join();
        byte[] audio = audioFuture.join();

        String caption = buildCaption(word.getEstonian(), word.getEnglish(), word.getTurkish(), word.getCefrLevel(),
            sentence, photo, List.of(), null, null, options.isReview(), options.nativeLang());

        return new Flashcard(word, sentence,
            photo != null ? photo.url() : null,
            photo != null ? photo.photographer() : null,
            null, caption, audio);
    }

    public Flashcard buildFlashcardFromEkilex(EkilexService.EkilexWord ekilexWord, BuildOptions options) {
        String query = ekilexWord.english() != null ? ekilexWord.english() : ekilexWord.wordValue();

        SentenceService.Sentence sentence;
        if (!ekilexWord.usages().isEmpty()) {
            var usage = ekilexWord.usages().get(ThreadLocalRandom.current().nextInt(ekilexWord.usages().size()));
            sentence = new SentenceService.Sentence(usage.estonian(), usage.english());
        } else {
            sentence = new SentenceService.Sentence(ekilexWord.wordValue(), ekilexWord.english() != null ? ekilexWord.english() : "");
        }

        // Run image fetch and TTS in parallel
        var imageFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> imageService.fetchImage(query));

        boolean shouldTts = options.audioEnabled() && ttsService.isAvailable();
        var audioFuture = shouldTts
            ? java.util.concurrent.CompletableFuture.supplyAsync(() ->
                ttsService.synthesizeSpeech(ekilexWord.wordValue(), sentence.estonian(), options.voiceName()))
            : java.util.concurrent.CompletableFuture.completedFuture((byte[]) null);

        ImageService.ImageResult photo = imageFuture.join();
        byte[] audio = audioFuture.join();

        Word word = new Word();
        word.setId("ekilex-" + ekilexWord.wordId());
        word.setEstonian(ekilexWord.wordValue());
        word.setEnglish(ekilexWord.english() != null ? ekilexWord.english() : "");
        word.setCefrLevel(ekilexWord.cefrLevel() != null ? ekilexWord.cefrLevel() : "A1");
        word.setSentences(ekilexWord.usages().stream()
            .map(u -> new Word.Sentence(u.estonian(), u.english(), null)).toList());

        String caption = buildCaption(ekilexWord.wordValue(),
            ekilexWord.english() != null ? ekilexWord.english() : "",
            ekilexWord.cefrLevel() != null ? ekilexWord.cefrLevel() : "A1",
            sentence, photo, List.of(), ekilexWord.pos(), "Source: Ekilex/Sonaveeb", options.isReview());

        return new Flashcard(word, sentence,
            photo != null ? photo.url() : null,
            photo != null ? photo.photographer() : null,
            null, caption, audio);
    }

    private String buildCaption(String estonian, String english, String cefrLevel,
                                SentenceService.Sentence sentence,
                                ImageService.ImageResult photo,
                                List<GrammarBuilderService.SelectedForm> forms,
                                String pos, String source, boolean isReview) {
        return buildCaption(estonian, english, null, cefrLevel, sentence, photo, forms, pos, source, isReview, "english");
    }

    public String buildCaption(String estonian, String english, String turkish, String cefrLevel,
                                SentenceService.Sentence sentence,
                                ImageService.ImageResult photo,
                                List<GrammarBuilderService.SelectedForm> forms,
                                String pos, String source, boolean isReview, String nativeLang) {
        boolean useTurkish = "turkish".equals(nativeLang) && turkish != null && !turkish.isBlank();
        String translation = useTurkish ? turkish : english;
        String langLabel = useTurkish ? "TR" : "EN";

        String translationText = TextUtils.escapeHtml(translation);
        String translationDisplay = isReview ? "<tg-spoiler>" + translationText + "</tg-spoiler>" : translationText;

        StringBuilder sb = new StringBuilder();
        sb.append("\uD83D\uDCDA <b>").append(TextUtils.escapeHtml(estonian)).append("</b>\n");
        sb.append("\uD83D\uDD04 ").append(translationDisplay).append("\n");
        sb.append("\uD83C\uDF10 ET \u2192 ").append(langLabel).append("\n");
        sb.append("\uD83C\uDFF7\uFE0F ").append(TextUtils.escapeHtml(cefrLevel));

        sb.append("\n\n\uD83D\uDCAC <i>").append(TextUtils.escapeHtml(sentence.estonian())).append("</i>");
        if (sentence.english() != null && !sentence.english().equals(sentence.estonian())) {
            String sentTrans = TextUtils.escapeHtml(sentence.english());
            sb.append("\n\uD83D\uDCDD ").append(isReview ? "<tg-spoiler>" + sentTrans + "</tg-spoiler>" : sentTrans);
        }

        if (forms != null && !forms.isEmpty()) {
            var selected = forms.size() > 5 ? forms.subList(0, 5) : forms;
            sb.append("\n\n<b>Forms:</b>");
            for (var f : selected) {
                sb.append("\n").append(TextUtils.escapeHtml(f.label())).append(": <code>").append(TextUtils.escapeHtml(f.value())).append("</code>");
            }
        }

        if (photo != null) {
            sb.append("\n\n\uD83D\uDCF7 ").append(TextUtils.escapeHtml(photo.photographer())).append(" / Unsplash");
        }

        if (source != null) {
            sb.append("\n\n\uD83D\uDCD6 <i>").append(TextUtils.escapeHtml(source)).append("</i>");
        }

        return sb.toString();
    }

}
