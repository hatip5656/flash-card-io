/**
 * Shared utilities — single source of truth for common patterns.
 */

/** Fisher-Yates shuffle (in-place, returns same array). */
export function shuffle<T>(arr: T[]): T[] {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}

/** Extract error message safely from unknown catch values. */
export function errMsg(err: unknown): string {
  return err instanceof Error ? err.message : String(err);
}
