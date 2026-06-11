import { readFileSync, readdirSync } from "fs";
import { join, dirname } from "path";
import { fileURLToPath } from "url";
import type pg from "pg";

const __dirname = dirname(fileURLToPath(import.meta.url));
const MIGRATIONS_DIR = join(__dirname, "..", "..", "migrations");

export async function runMigrations(pool: pg.Pool): Promise<void> {
  // Create migrations tracking table
  await pool.query(`
    CREATE TABLE IF NOT EXISTS _migrations (
      id SERIAL PRIMARY KEY,
      name TEXT NOT NULL UNIQUE,
      applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )
  `);

  // Get already applied migrations
  const applied = await pool.query("SELECT name FROM _migrations ORDER BY name");
  const appliedSet = new Set(applied.rows.map((r) => r.name));

  // Find SQL migration files
  const files = readdirSync(MIGRATIONS_DIR)
    .filter((f) => f.endsWith(".sql"))
    .sort();

  for (const file of files) {
    if (appliedSet.has(file)) continue;

    const sql = readFileSync(join(MIGRATIONS_DIR, file), "utf-8");
    console.error(`[migrate] Applying: ${file}`);

    const client = await pool.connect();
    try {
      await client.query("BEGIN");
      await client.query(sql);
      await client.query("INSERT INTO _migrations (name) VALUES ($1)", [file]);
      await client.query("COMMIT");
      console.error(`[migrate] Applied: ${file}`);
    } catch (err) {
      await client.query("ROLLBACK");
      console.error(`[migrate] Failed: ${file}`, err);
      throw err;
    } finally {
      client.release();
    }
  }
}
