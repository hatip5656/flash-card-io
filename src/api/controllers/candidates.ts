import type { Request, Response } from "express";
import { getPool } from "../../db/progress.js";
import { loadWordBankFromDb } from "../../flashcard/word-bank.js";

/**
 * GET /api/admin/candidates?status=pending&level=A1&limit=20
 */
export async function listCandidates(req: Request, res: Response): Promise<void> {
  const pool = getPool();
  const status = (req.query.status as string) || "pending";
  const level = req.query.level as string | undefined;
  const limit = Math.min(Number(req.query.limit) || 20, 100);

  let query = "SELECT * FROM candidate_words WHERE status = $1";
  const params: any[] = [status];

  if (level) {
    params.push(level);
    query += ` AND cefr_level = $${params.length}`;
  }

  params.push(limit);
  query += ` ORDER BY discovered_at DESC LIMIT $${params.length}`;

  const words = await pool.query(query, params);
  const wordIds = words.rows.map((w: any) => w.id);

  // Batch-fetch all sentences in one query
  const sents = wordIds.length > 0
    ? await pool.query(
        "SELECT candidate_id, estonian, english, turkish, sort_order FROM candidate_sentences WHERE candidate_id = ANY($1) ORDER BY candidate_id, sort_order",
        [wordIds],
      )
    : { rows: [] };
  const sentMap = new Map<number, any[]>();
  for (const s of sents.rows) {
    if (!sentMap.has(s.candidate_id)) sentMap.set(s.candidate_id, []);
    sentMap.get(s.candidate_id)!.push({ estonian: s.estonian, english: s.english, turkish: s.turkish, sort_order: s.sort_order });
  }
  const result = words.rows.map((w: any) => ({ ...w, sentences: sentMap.get(w.id) ?? [] }));

  // Stats
  const stats = await pool.query(`
    SELECT status, cefr_level, COUNT(*) as count
    FROM candidate_words
    GROUP BY status, cefr_level
    ORDER BY status, cefr_level
  `);

  res.json({ count: result.length, words: result, stats: stats.rows });
}

/**
 * PATCH /api/admin/candidates/:id
 * Add Turkish translation to candidate word + sentences
 * Body: { turkish, sentences?: [{estonian, turkish}] }
 */
export async function translateCandidate(req: Request, res: Response): Promise<void> {
  const pool = getPool();
  const { id } = req.params;
  const { turkish, sentences } = req.body;

  if (!turkish) {
    res.status(400).json({ error: "turkish translation required" });
    return;
  }

  const result = await pool.query(
    `UPDATE candidate_words SET turkish = $1, translated_at = NOW(), status = 'translated'
     WHERE id = $2 RETURNING *`,
    [turkish, id],
  );

  if (result.rowCount === 0) {
    res.status(404).json({ error: "Candidate not found" });
    return;
  }

  let sentencesUpdated = 0;
  if (sentences && Array.isArray(sentences)) {
    for (const s of sentences) {
      if (!s.estonian || !s.turkish) continue;
      const r = await pool.query(
        "UPDATE candidate_sentences SET turkish = $1 WHERE candidate_id = $2 AND estonian = $3",
        [s.turkish, id, s.estonian],
      );
      if (r.rowCount && r.rowCount > 0) sentencesUpdated++;
    }
  }

  res.json({ ...result.rows[0], sentencesUpdated });
}

/**
 * POST /api/admin/candidates/:id/approve
 * Move candidate to words table
 */
export async function approveCandidate(req: Request, res: Response): Promise<void> {
  const pool = getPool();
  const { id } = req.params;

  const candidate = await pool.query("SELECT * FROM candidate_words WHERE id = $1", [id]);
  if (candidate.rowCount === 0) {
    res.status(404).json({ error: "Candidate not found" });
    return;
  }

  const w = candidate.rows[0];
  if (!w.turkish) {
    res.status(400).json({ error: "Add Turkish translation before approving" });
    return;
  }

  const wordId = `${w.cefr_level.toLowerCase()}-${w.estonian.replace(/\s+/g, "-")}`;

  // Check duplicate
  const dup = await pool.query("SELECT id FROM words WHERE estonian = $1", [w.estonian]);
  if (dup.rowCount && dup.rowCount > 0) {
    await pool.query("UPDATE candidate_words SET status = 'rejected' WHERE id = $1", [id]);
    res.status(409).json({ error: "Word already exists in words table", existingId: dup.rows[0].id });
    return;
  }

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    await client.query(
      `INSERT INTO words (id, estonian, english, turkish, cefr_level)
       VALUES ($1, $2, $3, $4, $5)`,
      [wordId, w.estonian, w.english, w.turkish, w.cefr_level],
    );

    // Batch-insert sentences
    const sents = await client.query(
      "SELECT estonian, english, turkish, sort_order FROM candidate_sentences WHERE candidate_id = $1 ORDER BY sort_order",
      [id],
    );
    if (sents.rows.length > 0) {
      const values: any[] = [];
      const placeholders: string[] = [];
      sents.rows.forEach((s: any, i: number) => {
        const o = i * 5;
        placeholders.push(`($${o + 1}, $${o + 2}, $${o + 3}, $${o + 4}, $${o + 5})`);
        values.push(wordId, s.estonian, s.english, s.turkish, s.sort_order);
      });
      await client.query(
        `INSERT INTO word_sentences (word_id, estonian, english, turkish, sort_order) VALUES ${placeholders.join(", ")}`,
        values,
      );
    }

    await client.query("UPDATE candidate_words SET status = 'approved' WHERE id = $1", [id]);
    await client.query("COMMIT");
  } catch (err) {
    await client.query("ROLLBACK");
    throw err;
  } finally {
    client.release();
  }

  // Reload word bank
  await loadWordBankFromDb(pool);

  res.json({ wordId, estonian: w.estonian, english: w.english, turkish: w.turkish, cefrLevel: w.cefr_level });
}

/**
 * POST /api/admin/candidates/approve-all
 * Approve all translated candidates at once
 */
export async function approveAll(req: Request, res: Response): Promise<void> {
  const pool = getPool();
  const level = req.query.level as string | undefined;

  let query = "SELECT * FROM candidate_words WHERE status = 'translated' AND turkish IS NOT NULL";
  const params: any[] = [];
  if (level) {
    params.push(level);
    query += ` AND cefr_level = $${params.length}`;
  }

  const candidates = await pool.query(query, params);
  let approved = 0;
  let skipped = 0;

  // Pre-fetch existing words to avoid N+1 duplicate checks
  const existingWords = await pool.query("SELECT estonian FROM words");
  const existingSet = new Set(existingWords.rows.map((r: any) => r.estonian));

  // Pre-fetch all candidate sentences in one query
  const candidateIds = candidates.rows.map((w: any) => w.id);
  const allSents = candidateIds.length > 0
    ? await pool.query(
        "SELECT candidate_id, estonian, english, turkish, sort_order FROM candidate_sentences WHERE candidate_id = ANY($1) ORDER BY candidate_id, sort_order",
        [candidateIds],
      )
    : { rows: [] };
  const sentMap = new Map<number, any[]>();
  for (const s of allSents.rows) {
    if (!sentMap.has(s.candidate_id)) sentMap.set(s.candidate_id, []);
    sentMap.get(s.candidate_id)!.push(s);
  }

  const client = await pool.connect();
  try {
    await client.query("BEGIN");

    for (const w of candidates.rows) {
      const wordId = `${w.cefr_level.toLowerCase()}-${w.estonian.replace(/\s+/g, "-")}`;

      if (existingSet.has(w.estonian)) {
        await client.query("UPDATE candidate_words SET status = 'rejected' WHERE id = $1", [w.id]);
        skipped++;
        continue;
      }

      await client.query(
        `INSERT INTO words (id, estonian, english, turkish, cefr_level)
         VALUES ($1, $2, $3, $4, $5)`,
        [wordId, w.estonian, w.english, w.turkish, w.cefr_level],
      );
      existingSet.add(w.estonian);

      const sents = sentMap.get(w.id) ?? [];
      if (sents.length > 0) {
        const values: any[] = [];
        const placeholders: string[] = [];
        sents.forEach((s: any, i: number) => {
          const o = i * 5;
          placeholders.push(`($${o + 1}, $${o + 2}, $${o + 3}, $${o + 4}, $${o + 5})`);
          values.push(wordId, s.estonian, s.english, s.turkish, s.sort_order);
        });
        await client.query(
          `INSERT INTO word_sentences (word_id, estonian, english, turkish, sort_order) VALUES ${placeholders.join(", ")}`,
          values,
        );
      }

      await client.query("UPDATE candidate_words SET status = 'approved' WHERE id = $1", [w.id]);
      approved++;
    }

    await client.query("COMMIT");
  } catch (err) {
    await client.query("ROLLBACK");
    throw err;
  } finally {
    client.release();
  }

  if (approved > 0) {
    await loadWordBankFromDb(pool);
  }

  res.json({ approved, skipped, total: candidates.rowCount });
}

/**
 * DELETE /api/admin/candidates/:id
 */
export async function rejectCandidate(req: Request, res: Response): Promise<void> {
  const pool = getPool();
  const { id } = req.params;
  await pool.query("UPDATE candidate_words SET status = 'rejected' WHERE id = $1", [id]);
  res.json({ ok: true });
}

/**
 * GET /api/admin/candidates/stats
 */
export async function candidateStats(req: Request, res: Response): Promise<void> {
  const pool = getPool();
  const stats = await pool.query(`
    SELECT cefr_level, status, COUNT(*) as count
    FROM candidate_words
    GROUP BY cefr_level, status
    ORDER BY cefr_level, status
  `);
  const total = await pool.query("SELECT COUNT(*) as count FROM candidate_words WHERE status = 'pending'");
  const translated = await pool.query("SELECT COUNT(*) as count FROM candidate_words WHERE status = 'translated'");

  res.json({
    pending: Number(total.rows[0].count),
    readyToApprove: Number(translated.rows[0].count),
    breakdown: stats.rows,
  });
}
