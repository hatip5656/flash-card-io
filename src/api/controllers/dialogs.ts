import type { Request, Response } from "express";
import { getPool } from "../../db/progress.js";

/**
 * GET /api/dialogs?level=A1
 */
export async function listDialogs(req: Request, res: Response): Promise<void> {
  const pool = getPool();
  const level = req.query.level as string | undefined;

  let query = "SELECT id, title, title_tr, cefr_level, category, situation, situation_tr, icon, sort_order FROM dialogs";
  const params: any[] = [];

  if (level) {
    params.push(level);
    query += ` WHERE cefr_level = $${params.length}`;
  }

  query += " ORDER BY cefr_level, sort_order";

  const result = await pool.query(query, params);

  // Count lines per dialog
  const dialogIds = result.rows.map((r: any) => r.id);
  const lineCounts = dialogIds.length > 0
    ? await pool.query(
        "SELECT dialog_id, COUNT(*) as line_count FROM dialog_lines WHERE dialog_id = ANY($1) GROUP BY dialog_id",
        [dialogIds],
      )
    : { rows: [] };
  const countMap = new Map<string, number>();
  for (const r of lineCounts.rows) countMap.set(r.dialog_id, Number(r.line_count));

  const dialogs = result.rows.map((r: any) => ({
    ...r,
    lineCount: countMap.get(r.id) ?? 0,
  }));

  res.json(dialogs);
}

/**
 * GET /api/dialogs/:id
 */
export async function getDialog(req: Request, res: Response): Promise<void> {
  const pool = getPool();
  const { id } = req.params;

  const dialog = await pool.query(
    "SELECT id, title, title_tr, cefr_level, category, situation, situation_tr, icon FROM dialogs WHERE id = $1",
    [id],
  );

  if (dialog.rowCount === 0) {
    res.status(404).json({ error: "Dialog not found" });
    return;
  }

  const lines = await pool.query(
    "SELECT speaker, estonian, english, turkish, sort_order FROM dialog_lines WHERE dialog_id = $1 ORDER BY sort_order",
    [id],
  );

  res.json({ ...dialog.rows[0], lines: lines.rows });
}
