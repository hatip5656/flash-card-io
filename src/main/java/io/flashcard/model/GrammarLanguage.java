package io.flashcard.model;

import java.util.List;

/** The languages grammar lesson podcasts and videos are made in: Turkish ("tr") and English ("en"). */
public final class GrammarLanguage {

    public static final List<String> ALL = List.of("tr", "en");

    private GrammarLanguage() {}

    /** A learner's podcast language: Turkish speakers get Turkish, everyone else (no language set too) English. */
    public static String forLearner(String nativeLanguage) {
        return "turkish".equals(nativeLanguage) ? "tr" : "en";
    }

    /** An admin's {@code lang} parameter: Turkish when absent. */
    public static String parse(String lang) {
        if (lang == null) return "tr";
        if (!ALL.contains(lang)) throw new IllegalArgumentException("lang must be tr or en");
        return lang;
    }
}
