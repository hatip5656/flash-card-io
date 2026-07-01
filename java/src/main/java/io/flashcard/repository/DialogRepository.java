package io.flashcard.repository;

import io.flashcard.model.Dialog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class DialogRepository {

    private final JdbcTemplate jdbc;

    public DialogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Dialog> listDialogs(String level) {
        String query = "SELECT id, title, title_tr, cefr_level, category, situation, situation_tr, icon, sort_order FROM dialogs";
        List<Map<String, Object>> rows;
        if (level != null) {
            rows = jdbc.queryForList(query + " WHERE cefr_level = ? ORDER BY cefr_level, sort_order", level);
        } else {
            rows = jdbc.queryForList(query + " ORDER BY cefr_level, sort_order");
        }

        List<String> dialogIds = rows.stream().map(r -> (String) r.get("id")).toList();
        Map<String, Integer> lineCountMap = new HashMap<>();
        if (!dialogIds.isEmpty()) {
            jdbc.queryForList(
                "SELECT dialog_id, COUNT(*) as line_count FROM dialog_lines WHERE dialog_id = ANY(?) GROUP BY dialog_id",
                (Object) dialogIds.toArray(new String[0]))
                .forEach(r -> lineCountMap.put(
                    (String) r.get("dialog_id"),
                    ((Number) r.get("line_count")).intValue()));
        }

        return rows.stream().map(r -> {
            Dialog d = new Dialog();
            d.setId((String) r.get("id"));
            d.setTitle((String) r.get("title"));
            d.setTitleTr((String) r.get("title_tr"));
            d.setCefrLevel((String) r.get("cefr_level"));
            d.setCategory((String) r.get("category"));
            d.setSituation((String) r.get("situation"));
            d.setSituationTr((String) r.get("situation_tr"));
            d.setIcon((String) r.get("icon"));
            d.setSortOrder(((Number) r.get("sort_order")).intValue());
            d.setLineCount(lineCountMap.getOrDefault(d.getId(), 0));
            return d;
        }).toList();
    }

    public Dialog getDialog(String id) {
        var rows = jdbc.queryForList(
            "SELECT id, title, title_tr, cefr_level, category, situation, situation_tr, icon FROM dialogs WHERE id = ?", id);
        if (rows.isEmpty()) return null;

        Map<String, Object> r = rows.get(0);
        Dialog d = new Dialog();
        d.setId((String) r.get("id"));
        d.setTitle((String) r.get("title"));
        d.setTitleTr((String) r.get("title_tr"));
        d.setCefrLevel((String) r.get("cefr_level"));
        d.setCategory((String) r.get("category"));
        d.setSituation((String) r.get("situation"));
        d.setSituationTr((String) r.get("situation_tr"));
        d.setIcon((String) r.get("icon"));

        List<Dialog.Line> lines = jdbc.query(
            "SELECT speaker, estonian, english, turkish, sort_order FROM dialog_lines WHERE dialog_id = ? ORDER BY sort_order",
            (rs, rowNum) -> new Dialog.Line(
                rs.getString("speaker"),
                rs.getString("estonian"),
                rs.getString("english"),
                rs.getString("turkish"),
                rs.getInt("sort_order")),
            id);
        d.setLines(lines);
        return d;
    }
}
