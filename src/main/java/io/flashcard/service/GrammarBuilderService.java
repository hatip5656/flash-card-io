package io.flashcard.service;

import io.flashcard.model.Word;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class GrammarBuilderService {

    public record WordForm(String morphCode, String morphValue, String value) {}
    public record SelectedForm(String label, String value) {}

    private static final List<LabelRule> LABEL_SIMPLIFY = List.of(
        new LabelRule(Pattern.compile("^ma-infinitiiv", Pattern.CASE_INSENSITIVE), "ma-inf"),
        new LabelRule(Pattern.compile("^da-infinitiiv", Pattern.CASE_INSENSITIVE), "da-inf"),
        new LabelRule(Pattern.compile("^mata-vorm", Pattern.CASE_INSENSITIVE), "-mata"),
        new LabelRule(Pattern.compile("^mas-vorm", Pattern.CASE_INSENSITIVE), "-mas"),
        new LabelRule(Pattern.compile("^mast-vorm", Pattern.CASE_INSENSITIVE), "-mast"),
        new LabelRule(Pattern.compile("^maks-vorm", Pattern.CASE_INSENSITIVE), "-maks"),
        new LabelRule(Pattern.compile("^des-vorm", Pattern.CASE_INSENSITIVE), "-des"),
        new LabelRule(Pattern.compile("kindla kõneviisi oleviku.*1.*pööre$", Pattern.CASE_INSENSITIVE), "present 1st sg"),
        new LabelRule(Pattern.compile("kindla kõneviisi oleviku.*2.*pööre$", Pattern.CASE_INSENSITIVE), "present 2nd sg"),
        new LabelRule(Pattern.compile("kindla kõneviisi oleviku.*3.*pööre$", Pattern.CASE_INSENSITIVE), "present 3rd sg"),
        new LabelRule(Pattern.compile("kindla kõneviisi lihtmineviku.*3.*pööre$", Pattern.CASE_INSENSITIVE), "past 3rd sg"),
        new LabelRule(Pattern.compile("ainsuse suunduv|lühike sisseütlev", Pattern.CASE_INSENSITIVE), "short illative sg"),
        new LabelRule(Pattern.compile("ainsuse nimetav", Pattern.CASE_INSENSITIVE), "nominative sg"),
        new LabelRule(Pattern.compile("ainsuse omastav", Pattern.CASE_INSENSITIVE), "genitive sg"),
        new LabelRule(Pattern.compile("ainsuse osastav", Pattern.CASE_INSENSITIVE), "partitive sg"),
        new LabelRule(Pattern.compile("ainsuse sisseütlev", Pattern.CASE_INSENSITIVE), "illative sg"),
        new LabelRule(Pattern.compile("ainsuse seesütlev", Pattern.CASE_INSENSITIVE), "inessive sg"),
        new LabelRule(Pattern.compile("ainsuse seestütlev", Pattern.CASE_INSENSITIVE), "elative sg"),
        new LabelRule(Pattern.compile("ainsuse alaleütlev", Pattern.CASE_INSENSITIVE), "allative sg"),
        new LabelRule(Pattern.compile("ainsuse alalütlev", Pattern.CASE_INSENSITIVE), "adessive sg"),
        new LabelRule(Pattern.compile("ainsuse alaltütlev", Pattern.CASE_INSENSITIVE), "ablative sg"),
        new LabelRule(Pattern.compile("mitmuse nimetav", Pattern.CASE_INSENSITIVE), "nominative pl"),
        new LabelRule(Pattern.compile("mitmuse omastav", Pattern.CASE_INSENSITIVE), "genitive pl"),
        new LabelRule(Pattern.compile("mitmuse osastav", Pattern.CASE_INSENSITIVE), "partitive pl")
    );

    private record LabelRule(Pattern pattern, String label) {}

    private String simplifyLabel(String morphValue) {
        for (var rule : LABEL_SIMPLIFY) {
            if (rule.pattern.matcher(morphValue).find()) return rule.label;
        }
        return morphValue.length() > 25 ? morphValue.substring(0, 22) + "..." : morphValue;
    }

    public List<SelectedForm> selectForms(List<WordForm> forms, String pos) {
        Map<String, WordForm> unique = new LinkedHashMap<>();
        for (var f : forms) {
            String key = f.morphCode() != null && !f.morphCode().isBlank() ? f.morphCode() : f.morphValue();
            if (key != null && !unique.containsKey(key)) unique.put(key, f);
        }

        List<SelectedForm> selected = new ArrayList<>();
        for (var f : unique.values()) {
            if (f.value() == null || f.value().isBlank() || "-".equals(f.value()) || "\u2013".equals(f.value())) continue;
            String label = simplifyLabel(f.morphValue() != null ? f.morphValue() : f.morphCode());
            selected.add(new SelectedForm(label, f.value()));
        }
        return selected.size() > 10 ? selected.subList(0, 10) : selected;
    }
}
