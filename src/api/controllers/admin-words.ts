import type { Request, Response } from "express";
import { getPool } from "../../db/progress.js";
import { searchWord, getRandomWordForLevel } from "../../services/ekilex.js";
import { loadWordBankFromDb } from "../../flashcard/word-bank.js";

const VALID_LEVELS = ["A1", "A2", "B1", "B2"];

/**
 * POST /api/admin/words
 * Add a word manually. Body: { estonian, english, turkish?, cefrLevel, sentences? }
 */
export async function addWord(req: Request, res: Response): Promise<void> {
  const { estonian, english, turkish, cefrLevel, sentences } = req.body;

  if (!estonian || !english || !cefrLevel) {
    res.status(400).json({ error: "estonian, english, cefrLevel required" });
    return;
  }
  if (!VALID_LEVELS.includes(cefrLevel)) {
    res.status(400).json({ error: `cefrLevel must be one of: ${VALID_LEVELS.join(", ")}` });
    return;
  }

  const pool = getPool();
  const id = `${cefrLevel.toLowerCase()}-${estonian.toLowerCase().replace(/\s+/g, "-")}`;

  // Check if exists
  const existing = await pool.query("SELECT id FROM words WHERE id = $1 OR estonian = $2", [id, estonian.toLowerCase()]);
  if (existing.rowCount && existing.rowCount > 0) {
    res.status(409).json({ error: "Word already exists", id: existing.rows[0].id });
    return;
  }

  await pool.query(
    `INSERT INTO words (id, estonian, english, turkish, cefr_level)
     VALUES ($1, $2, $3, $4, $5)`,
    [id, estonian.toLowerCase(), english.toLowerCase(), turkish ?? null, cefrLevel],
  );

  // Add sentences if provided
  if (sentences && Array.isArray(sentences)) {
    for (let i = 0; i < sentences.length; i++) {
      const s = sentences[i];
      await pool.query(
        `INSERT INTO word_sentences (word_id, estonian, english, turkish, sort_order)
         VALUES ($1, $2, $3, $4, $5)`,
        [id, s.estonian, s.english, s.turkish ?? null, i],
      );
    }
  }

  // Reload word bank
  await loadWordBankFromDb(pool);

  res.status(201).json({ id, estonian, english, turkish, cefrLevel });
}

/**
 * POST /api/admin/words/from-ekilex
 * Discover words from Ekilex by search term. Adds them to DB with English translations.
 * Body: { search: string } or { level: string, count: number }
 */
export async function addFromEkilex(req: Request, res: Response): Promise<void> {
  const apiKey = process.env.EKILEX_API_KEY;
  if (!apiKey) {
    res.status(500).json({ error: "EKILEX_API_KEY not configured" });
    return;
  }

  const pool = getPool();
  const { search, level, count } = req.body;
  const added: Array<{ id: string; estonian: string; english: string; cefrLevel: string }> = [];
  const skipped: string[] = [];

  if (search) {
    // Search for a specific word
    const results = await searchWord(search, apiKey);
    for (const w of results) {
      if (!w.cefrLevel || !w.english) continue;
      const id = `${w.cefrLevel.toLowerCase()}-${w.wordValue.toLowerCase().replace(/\s+/g, "-")}`;

      const existing = await pool.query("SELECT id FROM words WHERE id = $1 OR estonian = $2", [id, w.wordValue.toLowerCase()]);
      if (existing.rowCount && existing.rowCount > 0) {
        skipped.push(w.wordValue);
        continue;
      }

      await pool.query(
        `INSERT INTO words (id, estonian, english, cefr_level)
         VALUES ($1, $2, $3, $4)`,
        [id, w.wordValue.toLowerCase(), w.english, w.cefrLevel],
      );

      // Add usage sentences
      for (let i = 0; i < w.usages.length; i++) {
        const u = w.usages[i];
        if (u.estonian && u.english) {
          await pool.query(
            `INSERT INTO word_sentences (word_id, estonian, english, sort_order)
             VALUES ($1, $2, $3, $4)`,
            [id, u.estonian, u.english, i],
          );
        }
      }

      added.push({ id, estonian: w.wordValue, english: w.english, cefrLevel: w.cefrLevel });
    }
  } else if (level && VALID_LEVELS.includes(level)) {
    // Discover random words for a level
    const target = Math.min(Number(count) || 10, 50);
    // Check ALL words, not just current level — prevent cross-level duplicates
    const existingWords = await pool.query("SELECT estonian FROM words");
    const existingSet = new Set(existingWords.rows.map((r: any) => r.estonian));

    for (let attempt = 0; attempt < target * 3 && added.length < target; attempt++) {
      const w = await getRandomWordForLevel(level, existingSet, apiKey);
      if (!w || !w.english || existingSet.has(w.wordValue.toLowerCase())) continue;

      const id = `${level.toLowerCase()}-${w.wordValue.toLowerCase().replace(/\s+/g, "-")}`;
      existingSet.add(w.wordValue.toLowerCase());

      try {
        await pool.query(
          `INSERT INTO words (id, estonian, english, cefr_level)
           VALUES ($1, $2, $3, $4) ON CONFLICT (id) DO NOTHING`,
          [id, w.wordValue.toLowerCase(), w.english, level],
        );

        for (let i = 0; i < w.usages.length; i++) {
          const u = w.usages[i];
          if (u.estonian && u.english) {
            await pool.query(
              `INSERT INTO word_sentences (word_id, estonian, english, sort_order)
               VALUES ($1, $2, $3, $4)`,
              [id, u.estonian, u.english, i],
            );
          }
        }

        added.push({ id, estonian: w.wordValue, english: w.english, cefrLevel: level });
      } catch {
        skipped.push(w.wordValue);
      }
    }
  } else {
    res.status(400).json({ error: "Provide {search} or {level, count}" });
    return;
  }

  // Reload word bank
  if (added.length > 0) {
    await loadWordBankFromDb(pool);
  }

  res.json({ added: added.length, skipped: skipped.length, words: added });
}

/**
 * GET /api/admin/words/untranslated?lang=turkish&limit=50
 * List words missing a specific translation
 */
export async function getUntranslated(req: Request, res: Response): Promise<void> {
  const lang = (req.query.lang as string) || "turkish";
  const limit = Math.min(Number(req.query.limit) || 50, 200);
  const level = req.query.level as string | undefined;

  const pool = getPool();

  let query = `SELECT id, estonian, english, cefr_level FROM words WHERE turkish IS NULL`;
  const params: any[] = [];

  if (level && VALID_LEVELS.includes(level)) {
    params.push(level);
    query += ` AND cefr_level = $${params.length}`;
  }

  params.push(limit);
  query += ` ORDER BY cefr_level, estonian LIMIT $${params.length}`;

  const result = await pool.query(query, params);
  res.json({ count: result.rowCount, words: result.rows });
}

/**
 * PATCH /api/admin/words/:id/translate
 * Add/update translation for word AND its sentences.
 * Body: { turkish: string, sentences?: [{estonian: string, turkish: string}] }
 */
export async function translateWord(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const { turkish, sentences } = req.body;

  if (!turkish) {
    res.status(400).json({ error: "turkish translation required" });
    return;
  }

  const pool = getPool();

  // Update word translation
  const result = await pool.query(
    "UPDATE words SET turkish = $1 WHERE id = $2 RETURNING id, estonian, english, turkish, cefr_level",
    [turkish, id],
  );

  if (result.rowCount === 0) {
    res.status(404).json({ error: "Word not found" });
    return;
  }

  // Update sentence translations if provided
  let sentencesUpdated = 0;
  if (sentences && Array.isArray(sentences)) {
    for (const s of sentences) {
      if (!s.estonian || !s.turkish) continue;
      const r = await pool.query(
        "UPDATE word_sentences SET turkish = $1 WHERE word_id = $2 AND estonian = $3",
        [s.turkish, id, s.estonian],
      );
      if (r.rowCount && r.rowCount > 0) sentencesUpdated++;
    }
  }

  res.json({ ...result.rows[0], sentencesUpdated });
}

/**
 * POST /api/admin/words/bulk-translate
 * Bulk translate words AND their sentences.
 * Body: { translations: [{id, turkish, sentences?: [{estonian, turkish}]}] }
 */
export async function bulkTranslate(req: Request, res: Response): Promise<void> {
  const { translations } = req.body;
  if (!translations || !Array.isArray(translations)) {
    res.status(400).json({ error: "translations array required" });
    return;
  }

  const pool = getPool();
  let wordsUpdated = 0;
  let sentencesUpdated = 0;

  for (const t of translations) {
    if (!t.id || !t.turkish) continue;
    const r = await pool.query("UPDATE words SET turkish = $1 WHERE id = $2", [t.turkish, t.id]);
    if (r.rowCount && r.rowCount > 0) wordsUpdated++;

    // Update sentence translations
    if (t.sentences && Array.isArray(t.sentences)) {
      for (const s of t.sentences) {
        if (!s.estonian || !s.turkish) continue;
        const sr = await pool.query(
          "UPDATE word_sentences SET turkish = $1 WHERE word_id = $2 AND estonian = $3",
          [s.turkish, t.id, s.estonian],
        );
        if (sr.rowCount && sr.rowCount > 0) sentencesUpdated++;
      }
    }
  }

  if (wordsUpdated > 0) {
    await loadWordBankFromDb(pool);
  }

  res.json({ wordsUpdated, sentencesUpdated, total: translations.length });
}

/**
 * GET /api/admin/words/:id
 * Get full word details including sentences (for translation workflow)
 */
export async function getWordDetail(req: Request, res: Response): Promise<void> {
  const { id } = req.params;
  const pool = getPool();

  const word = await pool.query(
    "SELECT id, estonian, english, turkish, cefr_level FROM words WHERE id = $1",
    [id],
  );
  if (word.rowCount === 0) {
    res.status(404).json({ error: "Word not found" });
    return;
  }

  const sentences = await pool.query(
    "SELECT estonian, english, turkish, sort_order FROM word_sentences WHERE word_id = $1 ORDER BY sort_order",
    [id],
  );

  res.json({ ...word.rows[0], sentences: sentences.rows });
}

/**
 * GET /api/admin/words/untranslated-full?level=A1&limit=10
 * Get words with their sentences that need Turkish translation
 */
export async function getUntranslatedFull(req: Request, res: Response): Promise<void> {
  const limit = Math.min(Number(req.query.limit) || 10, 50);
  const level = req.query.level as string | undefined;

  const pool = getPool();

  let query = "SELECT id, estonian, english, cefr_level FROM words WHERE turkish IS NULL";
  const params: any[] = [];

  if (level && VALID_LEVELS.includes(level)) {
    params.push(level);
    query += ` AND cefr_level = $${params.length}`;
  }

  params.push(limit);
  query += ` ORDER BY cefr_level, estonian LIMIT $${params.length}`;

  const words = await pool.query(query, params);

  // Fetch sentences for each word
  const result = [];
  for (const w of words.rows) {
    const sentences = await pool.query(
      "SELECT estonian, english, turkish, sort_order FROM word_sentences WHERE word_id = $1 ORDER BY sort_order",
      [w.id],
    );
    result.push({ ...w, sentences: sentences.rows });
  }

  res.json({ count: result.length, words: result });
}

/**
 * GET /api/admin/words/stats
 * Word catalog statistics
 */
export async function getWordStats(req: Request, res: Response): Promise<void> {
  const pool = getPool();
  const stats = await pool.query(`
    SELECT
      cefr_level,
      COUNT(*) as total,
      COUNT(turkish) as with_turkish,
      COUNT(*) - COUNT(turkish) as missing_turkish
    FROM words
    GROUP BY cefr_level
    ORDER BY cefr_level
  `);

  const sentences = await pool.query("SELECT COUNT(*) as total FROM word_sentences");

  res.json({
    levels: stats.rows,
    totalWords: stats.rows.reduce((sum: number, r: any) => sum + Number(r.total), 0),
    totalSentences: Number(sentences.rows[0].total),
  });
}
