import { Router } from "express";
import { authenticate } from "./middleware/auth.js";
import { asyncHandler as h } from "./middleware/async.js";
import * as users from "./controllers/users.js";
import * as flashcards from "./controllers/flashcards.js";
import * as quiz from "./controllers/quiz.js";
import * as stats from "./controllers/stats.js";
import * as content from "./controllers/content.js";
import * as feed from "./controllers/feed.js";
import * as saved from "./controllers/saved.js";
import * as stories from "./controllers/stories.js";
import * as comments from "./controllers/comments.js";
import * as audio from "./controllers/audio.js";
import * as mobileQuiz from "./controllers/mobile-quiz.js";
import * as grammarPractice from "./controllers/grammar-practice.js";
import * as wordCrush from "./controllers/word-crush.js";
import * as adminWords from "./controllers/admin-words.js";

export function createApiRouter(): Router {
  const router = Router();

  // --- Public routes (no auth) ---
  router.get("/levels", h(content.getLevels));
  router.get("/categories", h(content.getCategories));
  router.get("/idioms", h(content.getIdioms));
  router.get("/idioms/random", h(content.getRandomIdiom));
  router.get("/schedules", h(users.getSchedulePresets));
  router.get("/audio/:word", h(audio.getWordAudio));

  // --- User registration ---
  router.post("/users", h(users.register));
  router.post("/users/auto", h(users.autoRegister));

  // --- Authenticated routes ---
  router.use(authenticate);

  // User profile & settings
  router.get("/users/me", h(users.getUser));
  router.patch("/users/me/level", h(users.setLevel));
  router.patch("/users/me/schedule", h(users.setSchedule));
  router.get("/users/me/preferences", h(users.getUserPreferences));
  router.patch("/users/me/preferences", h(users.setPreference));
  router.delete("/users/me", h(users.unsubscribe));

  // Stats
  router.get("/users/me/stats", h(stats.getUserStats));

  // Flashcards
  router.get("/flashcards/next", h(flashcards.getNextFlashcard));
  router.get("/flashcards/grammar", h(flashcards.getGrammarCard));
  router.get("/flashcards/audio/latest", h(flashcards.getAudio));

  // Review (SM-2)
  router.get("/review/due", h(flashcards.getDueWords));
  router.post("/review/recall", h(flashcards.submitRecall));

  // Quiz (Telegram bot — session-based)
  router.post("/quiz/start", h(quiz.startQuiz));
  router.post("/quiz/answer", h(quiz.submitAnswer));
  router.get("/quiz/history", h(quiz.getHistory));
  router.get("/quiz/stats", h(quiz.getStats));
  router.get("/quiz/missed", h(quiz.getMissedWords));

  // Quiz (Mobile — all questions at once)
  router.get("/mobile/quiz/generate", h(mobileQuiz.generateQuiz));
  router.post("/mobile/quiz/submit", h(mobileQuiz.submitQuiz));

  // Grammar practice (Mobile)
  router.get("/mobile/grammar/practice", h(grammarPractice.generatePractice));

  // Word Crush game (Mobile)
  router.get("/mobile/word-crush", h(wordCrush.getWordCrushData));

  // Admin: word catalog management
  router.post("/admin/words", h(adminWords.addWord));
  router.post("/admin/words/from-ekilex", h(adminWords.addFromEkilex));
  router.get("/admin/words/untranslated", h(adminWords.getUntranslated));
  router.get("/admin/words/untranslated-full", h(adminWords.getUntranslatedFull));
  router.get("/admin/words/stats", h(adminWords.getWordStats));
  router.get("/admin/words/:id", h(adminWords.getWordDetail));
  router.patch("/admin/words/:id/translate", h(adminWords.translateWord));
  router.post("/admin/words/bulk-translate", h(adminWords.bulkTranslate));

  // Feed (mobile app)
  router.get("/feed", h(feed.getFeed));
  router.post("/feed/seen/:wordId", h(feed.markSeen));

  // Saved words (mobile bookmarks)
  router.get("/saved", h(saved.getSavedWords));
  router.post("/saved/:wordId", h(saved.addSavedWord));
  router.delete("/saved/:wordId", h(saved.removeSavedWord));

  // Grammar stories (mobile app)
  router.get("/stories", h(stories.getStories));
  router.post("/stories/:storyId/read", h(stories.markStoryAsRead));

  // Comments
  router.get("/comments/:wordId", h(comments.getWordComments));
  router.post("/comments/:wordId", h(comments.postComment));

  return router;
}
