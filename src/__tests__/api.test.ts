import { describe, it, expect, vi, beforeAll, beforeEach } from "vitest";
import request from "supertest";
import express from "express";
import { createApiRouter } from "../api/router.js";
import { errorHandler } from "../api/middleware/errors.js";

// --- Mock database layer ---
vi.mock("../db/progress.js", () => ({
  SCHEDULE_OFF: "off",
  DEFAULT_SCHEDULE: "0 9 * * *",
  addSubscriber: vi.fn(),
  removeSubscriber: vi.fn(),
  getStats: vi.fn().mockResolvedValue({ sent: 10, level: "A1", schedule: "0 9 * * *" }),
  getSubscriberLevel: vi.fn().mockResolvedValue("A1"),
  setSubscriberLevel: vi.fn(),
  getSubscriberSchedule: vi.fn().mockResolvedValue("0 9 * * *"),
  setSubscriberSchedule: vi.fn(),
  getPreferences: vi.fn().mockResolvedValue({
    audio: true, voiceName: "mari", wordForms: true,
    grammarCards: true, dailySummary: true, weeklyReport: true,
  }),
  updatePreference: vi.fn(),
  updateNextDelivery: vi.fn(),
  scheduleNextGrammar: vi.fn(),
  getSentWordIds: vi.fn().mockResolvedValue([]),
  getSentGrammarIds: vi.fn().mockResolvedValue(new Set()),
  markWordSent: vi.fn(),
  markGrammarSent: vi.fn(),
  logWordActivity: vi.fn().mockResolvedValue({ totalWords: 11, milestone: null }),
  getWordsDueForReview: vi.fn().mockResolvedValue([
    { wordId: "1", wordValue: "tere", english: "hello" },
  ]),
  updateSm2: vi.fn(),
  getQuizStats: vi.fn().mockResolvedValue({ totalQuizzes: 5, avgPercentage: 80, recentTrend: 3 }),
  getQuizHistory: vi.fn().mockResolvedValue([]),
  getLearnedWordsForQuiz: vi.fn().mockResolvedValue([
    { estonian: "tere", english: "hello", quizCount: 0 },
    { estonian: "aitäh", english: "thank you", quizCount: 1 },
    { estonian: "jah", english: "yes", quizCount: 0 },
    { estonian: "ei", english: "no", quizCount: 2 },
    { estonian: "palun", english: "please", quizCount: 0 },
  ]),
  getMostMissedWords: vi.fn().mockResolvedValue([]),
  incrementQuizCount: vi.fn(),
  saveQuizResult: vi.fn(),
  logQuizActivity: vi.fn(),
  getStreak: vi.fn().mockResolvedValue(3),
  getTodayActivity: vi.fn().mockResolvedValue({ wordsLearned: 2, quizzesTaken: 1 }),
}));

vi.mock("../services/prebuild.js", () => ({
  popPrebuilt: vi.fn().mockResolvedValue(null),
  invalidateQueue: vi.fn(),
}));

vi.mock("../flashcard/word-bank.js", () => ({
  getWordsForLevel: vi.fn().mockReturnValue(new Array(50)),
  loadWordBank: vi.fn(),
}));

vi.mock("../flashcard/grammar-bank.js", () => ({
  getRandomLesson: vi.fn().mockReturnValue({
    id: "lesson-1",
    topic: "Verb 'olema'",
    cefrLevel: "A1",
    content: "<b>olema</b> = to be",
  }),
  loadGrammarBank: vi.fn(),
}));

vi.mock("../flashcard/categories.js", () => ({
  getAllCategories: vi.fn().mockReturnValue([
    { emoji: "🍎", label: "Food", wordCount: 20 },
    { emoji: "🏠", label: "Home", wordCount: 15 },
  ]),
  loadCategories: vi.fn(),
}));

// --- Create test app ---
function createTestApp() {
  const app = express();
  app.set("cronTimezone", "Europe/Tallinn");
  app.use(express.json());
  app.use("/api", createApiRouter());
  app.use(errorHandler);
  return app;
}

const AUTH_HEADER = { "X-User-Id": "12345" };

describe("API", () => {
  let app: express.Express;

  beforeAll(() => {
    app = createTestApp();
  });

  // --- Public endpoints ---

  describe("GET /api/levels", () => {
    it("returns all CEFR levels", async () => {
      const res = await request(app).get("/api/levels");
      expect(res.status).toBe(200);
      expect(res.body).toBeInstanceOf(Array);
      expect(res.body.length).toBe(4);
      expect(res.body[0]).toHaveProperty("level");
      expect(res.body[0]).toHaveProperty("wordCount");
    });
  });

  describe("GET /api/categories", () => {
    it("returns vocabulary categories", async () => {
      const res = await request(app).get("/api/categories");
      expect(res.status).toBe(200);
      expect(res.body).toBeInstanceOf(Array);
      expect(res.body[0]).toHaveProperty("emoji");
      expect(res.body[0]).toHaveProperty("label");
      expect(res.body[0]).toHaveProperty("wordCount");
    });
  });

  describe("GET /api/idioms", () => {
    it("returns all idioms", async () => {
      const res = await request(app).get("/api/idioms");
      expect(res.status).toBe(200);
      expect(res.body).toBeInstanceOf(Array);
      expect(res.body.length).toBeGreaterThan(0);
      expect(res.body[0]).toHaveProperty("estonian");
      expect(res.body[0]).toHaveProperty("english");
      expect(res.body[0]).toHaveProperty("meaning");
    });
  });

  describe("GET /api/idioms/random", () => {
    it("returns a single idiom", async () => {
      const res = await request(app).get("/api/idioms/random");
      expect(res.status).toBe(200);
      expect(res.body).toHaveProperty("estonian");
      expect(res.body).toHaveProperty("english");
      expect(res.body).toHaveProperty("meaning");
    });
  });

  describe("GET /api/schedules", () => {
    it("returns schedule presets", async () => {
      const res = await request(app).get("/api/schedules");
      expect(res.status).toBe(200);
      expect(res.body).toBeInstanceOf(Array);
      expect(res.body[0]).toHaveProperty("key");
      expect(res.body[0]).toHaveProperty("cron");
      expect(res.body[0]).toHaveProperty("label");
    });
  });

  // --- Auth required ---

  describe("Authentication", () => {
    it("rejects requests without X-User-Id", async () => {
      const res = await request(app).get("/api/users/me");
      expect(res.status).toBe(401);
      expect(res.body.error).toContain("Missing or invalid");
    });

    it("rejects non-numeric X-User-Id", async () => {
      const res = await request(app).get("/api/users/me").set("X-User-Id", "abc");
      expect(res.status).toBe(401);
    });
  });

  // --- User endpoints ---

  describe("POST /api/users", () => {
    it("registers a new user", async () => {
      const res = await request(app).post("/api/users").send({ userId: 12345 });
      expect(res.status).toBe(201);
      expect(res.body).toHaveProperty("userId", 12345);
    });

    it("rejects missing userId", async () => {
      const res = await request(app).post("/api/users").send({});
      expect(res.status).toBe(400);
    });
  });

  describe("GET /api/users/me", () => {
    it("returns user profile", async () => {
      const res = await request(app).get("/api/users/me").set(AUTH_HEADER);
      expect(res.status).toBe(200);
      expect(res.body).toHaveProperty("userId", 12345);
      expect(res.body).toHaveProperty("level");
      expect(res.body).toHaveProperty("schedule");
      expect(res.body).toHaveProperty("preferences");
      expect(res.body).toHaveProperty("wordsLearned");
    });
  });

  describe("PATCH /api/users/me/level", () => {
    it("sets a valid level", async () => {
      const res = await request(app).patch("/api/users/me/level").set(AUTH_HEADER).send({ level: "B1" });
      expect(res.status).toBe(200);
      expect(res.body).toHaveProperty("level", "B1");
      expect(res.body).toHaveProperty("totalForLevel");
    });

    it("rejects invalid level", async () => {
      const res = await request(app).patch("/api/users/me/level").set(AUTH_HEADER).send({ level: "C1" });
      expect(res.status).toBe(400);
    });
  });

  describe("PATCH /api/users/me/schedule", () => {
    it("sets a valid schedule", async () => {
      const res = await request(app).patch("/api/users/me/schedule").set(AUTH_HEADER).send({ schedule: "morning" });
      expect(res.status).toBe(200);
      expect(res.body).toHaveProperty("label");
    });

    it("rejects invalid schedule", async () => {
      const res = await request(app).patch("/api/users/me/schedule").set(AUTH_HEADER).send({ schedule: "invalid" });
      expect(res.status).toBe(400);
    });
  });

  describe("GET /api/users/me/preferences", () => {
    it("returns user preferences", async () => {
      const res = await request(app).get("/api/users/me/preferences").set(AUTH_HEADER);
      expect(res.status).toBe(200);
      expect(res.body).toHaveProperty("audio");
      expect(res.body).toHaveProperty("voiceName");
    });
  });

  describe("PATCH /api/users/me/preferences", () => {
    it("updates a boolean preference", async () => {
      const res = await request(app).patch("/api/users/me/preferences").set(AUTH_HEADER).send({ key: "audio", value: false });
      expect(res.status).toBe(200);
    });

    it("updates voiceName preference", async () => {
      const res = await request(app).patch("/api/users/me/preferences").set(AUTH_HEADER).send({ key: "voiceName", value: "albert" });
      expect(res.status).toBe(200);
    });

    it("rejects invalid preference key", async () => {
      const res = await request(app).patch("/api/users/me/preferences").set(AUTH_HEADER).send({ key: "invalid", value: true });
      expect(res.status).toBe(400);
    });

    it("rejects wrong type for boolean pref", async () => {
      const res = await request(app).patch("/api/users/me/preferences").set(AUTH_HEADER).send({ key: "audio", value: "yes" });
      expect(res.status).toBe(400);
    });

    it("rejects wrong type for voiceName", async () => {
      const res = await request(app).patch("/api/users/me/preferences").set(AUTH_HEADER).send({ key: "voiceName", value: 123 });
      expect(res.status).toBe(400);
    });
  });

  describe("DELETE /api/users/me", () => {
    it("unsubscribes user", async () => {
      const res = await request(app).delete("/api/users/me").set(AUTH_HEADER);
      expect(res.status).toBe(200);
      expect(res.body).toHaveProperty("unsubscribed", true);
    });
  });

  // --- Stats ---

  describe("GET /api/users/me/stats", () => {
    it("returns full stats", async () => {
      const res = await request(app).get("/api/users/me/stats").set(AUTH_HEADER);
      expect(res.status).toBe(200);
      expect(res.body).toHaveProperty("level");
      expect(res.body).toHaveProperty("wordsLearned");
      expect(res.body).toHaveProperty("progressPercent");
      expect(res.body).toHaveProperty("streak");
      expect(res.body).toHaveProperty("streakEmoji");
      expect(res.body).toHaveProperty("today");
      expect(res.body).toHaveProperty("quiz");
      expect(res.body.today).toHaveProperty("wordsLearned");
      expect(res.body.quiz).toHaveProperty("totalQuizzes");
    });
  });

  // --- Flashcards ---

  describe("GET /api/flashcards/next", () => {
    it("returns 503 when no cards available", async () => {
      const res = await request(app).get("/api/flashcards/next").set(AUTH_HEADER);
      // No prebuilt and no buildFn set on test app → 503
      expect(res.status).toBe(503);
    });
  });

  describe("GET /api/flashcards/grammar", () => {
    it("returns a grammar card", async () => {
      const res = await request(app).get("/api/flashcards/grammar").set(AUTH_HEADER);
      expect(res.status).toBe(200);
      expect(res.body).toHaveProperty("id");
      expect(res.body).toHaveProperty("topic");
      expect(res.body).toHaveProperty("cefrLevel");
      expect(res.body).toHaveProperty("content");
    });
  });

  describe("GET /api/flashcards/audio/latest", () => {
    it("returns 404 when no audio cached", async () => {
      const res = await request(app).get("/api/flashcards/audio/latest").set(AUTH_HEADER);
      expect(res.status).toBe(404);
    });
  });

  // --- Review ---

  describe("GET /api/review/due", () => {
    it("returns words due for review", async () => {
      const res = await request(app).get("/api/review/due").set(AUTH_HEADER);
      expect(res.status).toBe(200);
      expect(res.body).toBeInstanceOf(Array);
      expect(res.body[0]).toHaveProperty("wordValue");
      expect(res.body[0]).toHaveProperty("english");
    });
  });

  describe("POST /api/review/recall", () => {
    it("submits SM-2 recall", async () => {
      const res = await request(app).post("/api/review/recall").set(AUTH_HEADER).send({ wordValue: "tere", quality: 4 });
      expect(res.status).toBe(200);
      expect(res.body).toHaveProperty("updated", true);
    });

    it("rejects missing wordValue", async () => {
      const res = await request(app).post("/api/review/recall").set(AUTH_HEADER).send({ quality: 4 });
      expect(res.status).toBe(400);
    });

    it("rejects invalid quality", async () => {
      const res = await request(app).post("/api/review/recall").set(AUTH_HEADER).send({ wordValue: "tere", quality: 6 });
      expect(res.status).toBe(400);
    });
  });

  // --- Quiz ---

  describe("POST /api/quiz/start", () => {
    it("starts a quiz session", async () => {
      const res = await request(app).post("/api/quiz/start").set(AUTH_HEADER);
      expect(res.status).toBe(200);
      expect(res.body).toHaveProperty("totalQuestions", 5);
      expect(res.body).toHaveProperty("question");
      expect(res.body.question).toHaveProperty("index", 0);
      expect(res.body.question).toHaveProperty("type");
      expect(res.body.question).toHaveProperty("prompt");
      expect(res.body.question).toHaveProperty("options");
      expect(res.body.question.options).toHaveLength(4);
    });
  });

  describe("POST /api/quiz/answer", () => {
    it("submits an answer and gets next question", async () => {
      // Start a fresh quiz first
      await request(app).post("/api/quiz/start").set(AUTH_HEADER);
      const res = await request(app).post("/api/quiz/answer").set(AUTH_HEADER).send({ chosenIndex: 0 });
      expect(res.status).toBe(200);
      expect(res.body).toHaveProperty("correct");
      expect(res.body).toHaveProperty("correctAnswer");
      expect(res.body).toHaveProperty("chosenAnswer");
      expect(typeof res.body.complete).toBe("boolean");
    });

    it("rejects without active session", async () => {
      // Use a different user with no session
      const res = await request(app).post("/api/quiz/answer").set({ "X-User-Id": "99999" }).send({ chosenIndex: 0 });
      expect(res.status).toBe(404);
    });

    it("rejects invalid chosenIndex", async () => {
      await request(app).post("/api/quiz/start").set(AUTH_HEADER);
      const res = await request(app).post("/api/quiz/answer").set(AUTH_HEADER).send({ chosenIndex: 99 });
      expect(res.status).toBe(400);
    });

    it("completes a full quiz", async () => {
      await request(app).post("/api/quiz/start").set(AUTH_HEADER);
      let res;
      for (let i = 0; i < 5; i++) {
        res = await request(app).post("/api/quiz/answer").set(AUTH_HEADER).send({ chosenIndex: 0 });
      }
      expect(res!.status).toBe(200);
      expect(res!.body.complete).toBe(true);
      expect(res!.body).toHaveProperty("score");
      expect(res!.body).toHaveProperty("total", 5);
      expect(res!.body).toHaveProperty("percentage");
      expect(res!.body).toHaveProperty("results");
    });
  });

  describe("GET /api/quiz/history", () => {
    it("returns quiz history", async () => {
      const res = await request(app).get("/api/quiz/history").set(AUTH_HEADER);
      expect(res.status).toBe(200);
      expect(res.body).toBeInstanceOf(Array);
    });
  });

  describe("GET /api/quiz/stats", () => {
    it("returns quiz statistics", async () => {
      const res = await request(app).get("/api/quiz/stats").set(AUTH_HEADER);
      expect(res.status).toBe(200);
      expect(res.body).toHaveProperty("totalQuizzes");
      expect(res.body).toHaveProperty("avgPercentage");
    });
  });

  describe("GET /api/quiz/missed", () => {
    it("returns most-missed words", async () => {
      const res = await request(app).get("/api/quiz/missed").set(AUTH_HEADER);
      expect(res.status).toBe(200);
      expect(res.body).toBeInstanceOf(Array);
    });
  });
});
