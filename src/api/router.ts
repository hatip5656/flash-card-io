import { Router } from "express";
import { authenticate } from "./middleware/auth.js";
import { asyncHandler as h } from "./middleware/async.js";
import * as users from "./controllers/users.js";
import * as flashcards from "./controllers/flashcards.js";
import * as quiz from "./controllers/quiz.js";
import * as stats from "./controllers/stats.js";
import * as content from "./controllers/content.js";

export function createApiRouter(): Router {
  const router = Router();

  // --- Public routes (no auth) ---
  router.get("/levels", h(content.getLevels));
  router.get("/categories", h(content.getCategories));
  router.get("/idioms", h(content.getIdioms));
  router.get("/idioms/random", h(content.getRandomIdiom));
  router.get("/schedules", h(users.getSchedulePresets));

  // --- User registration ---
  router.post("/users", h(users.register));

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

  // Quiz
  router.post("/quiz/start", h(quiz.startQuiz));
  router.post("/quiz/answer", h(quiz.submitAnswer));
  router.get("/quiz/history", h(quiz.getHistory));
  router.get("/quiz/stats", h(quiz.getStats));
  router.get("/quiz/missed", h(quiz.getMissedWords));

  return router;
}
