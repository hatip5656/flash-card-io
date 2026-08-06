package io.flashcard.controller;

import io.flashcard.model.Dialog;
import io.flashcard.repository.ActivityRepository;
import io.flashcard.repository.DialogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.flashcard.controller.UserController.getUserId;

@RestController
@RequestMapping("/api/dialogs")
public class DialogController {

    private final DialogRepository dialogRepo;
    private final ActivityRepository activityRepo;

    public DialogController(DialogRepository dialogRepo, ActivityRepository activityRepo) {
        this.dialogRepo = dialogRepo;
        this.activityRepo = activityRepo;
    }

    @GetMapping
    public List<Dialog> listDialogs(@RequestParam(required = false) String level) {
        return dialogRepo.listDialogs(level);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDialog(@PathVariable String id) {
        Dialog dialog = dialogRepo.getDialog(id);
        if (dialog == null) return ResponseEntity.status(404).body(Map.of("error", "Dialog not found"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", dialog.getId());
        result.put("title", dialog.getTitle());
        result.put("title_tr", dialog.getTitleTr());
        result.put("cefr_level", dialog.getCefrLevel());
        result.put("category", dialog.getCategory());
        result.put("situation", dialog.getSituation());
        result.put("situation_tr", dialog.getSituationTr());
        result.put("icon", dialog.getIcon());
        result.put("lines", dialog.getLines());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/complete")
    public Map<String, Object> markCompleted(HttpServletRequest request, @PathVariable String id) {
        long chatId = getUserId(request);
        dialogRepo.markCompleted(chatId, id);
        activityRepo.logDialogActivity(chatId);
        return Map.of("ok", true, "dialogId", id);
    }
}
