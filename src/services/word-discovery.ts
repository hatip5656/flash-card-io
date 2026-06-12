import type pg from "pg";
import { getRandomWordForLevel } from "./ekilex.js";
import { errMsg } from "../utils.js";
import type { CefrLevel } from "../config.js";

const LEVELS: CefrLevel[] = ["A1", "A2", "B1", "B2"];
const WORDS_PER_LEVEL = 10;

/**
 * Discover new words from Ekilex and save to candidate_words table.
 * Skips words already in `words` or `candidate_words`.
 */
export async function discoverCandidates(pool: pg.Pool, apiKey: string): Promise<number> {
  // Build exclusion set: all known Estonian words
  const existing = await pool.query("SELECT estonian FROM words");
  const candidates = await pool.query("SELECT estonian FROM candidate_words");
  const known = new Set<string>();
  for (const r of existing.rows) known.add(r.estonian);
  for (const r of candidates.rows) known.add(r.estonian);

  let totalAdded = 0;

  for (const level of LEVELS) {
    let added = 0;
    let attempts = 0;
    const maxAttempts = WORDS_PER_LEVEL * 5;

    while (added < WORDS_PER_LEVEL && attempts < maxAttempts) {
      attempts++;
      try {
        const w = await getRandomWordForLevel(level, known, apiKey);
        if (!w || !w.english) continue;

        const est = w.wordValue.toLowerCase();
        if (known.has(est) || est.includes(" ")) continue;

        // Insert candidate
        const result = await pool.query(
          `INSERT INTO candidate_words (estonian, english, cefr_level, pos)
           VALUES ($1, $2, $3, $4)
           ON CONFLICT (estonian) DO NOTHING
           RETURNING id`,
          [est, w.english, level, w.pos],
        );

        if (result.rowCount && result.rowCount > 0) {
          const candidateId = result.rows[0].id;

          // Add usage sentences
          for (let i = 0; i < w.usages.length; i++) {
            const u = w.usages[i];
            if (u.estonian && u.english) {
              await pool.query(
                `INSERT INTO candidate_sentences (candidate_id, estonian, english, sort_order)
                 VALUES ($1, $2, $3, $4)`,
                [candidateId, u.estonian, u.english, i],
              );
            }
          }

          known.add(est);
          added++;
          totalAdded++;
        }
      } catch (err) {
        console.error(`[discovery] Error for ${level}:`, errMsg(err));
      }
    }

    if (added > 0) {
      console.error(`[discovery] ${level}: added ${added} candidates (${attempts} attempts)`);
    }
  }

  return totalAdded;
}
