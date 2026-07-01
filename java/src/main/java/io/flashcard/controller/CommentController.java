package io.flashcard.controller;

import io.flashcard.model.WordComment;
import io.flashcard.repository.CommentRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static io.flashcard.controller.UserController.getUserId;

@RestController
@RequestMapping("/api/comments")
public class CommentController {

    private final CommentRepository commentRepo;

    public CommentController(CommentRepository commentRepo) {
        this.commentRepo = commentRepo;
    }

    @GetMapping("/{wordId}")
    public List<WordComment> getWordComments(@PathVariable String wordId,
                                              @RequestParam(defaultValue = "20") int limit) {
        return commentRepo.getComments(wordId, Math.min(limit, 50));
    }

    @PostMapping("/{wordId}")
    public ResponseEntity<?> postComment(HttpServletRequest request, @PathVariable String wordId,
                                          @RequestBody Map<String, Object> body) {
        long chatId = getUserId(request);
        String comment = (String) body.get("comment");

        if (comment == null || comment.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "comment is required"));
        }
        if (comment.length() > 500) {
            return ResponseEntity.badRequest().body(Map.of("error", "comment must be 500 characters or less"));
        }

        WordComment result = commentRepo.addComment(chatId, wordId, comment.trim());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
