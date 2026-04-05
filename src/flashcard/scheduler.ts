import { Cron } from "croner";

export function startScheduler(
  cronExpression: string,
  timezone: string,
  onTick: () => Promise<void>,
): { stop: () => void } {
  console.error(`[scheduler] Starting cron: "${cronExpression}" (${timezone})`);

  const job = new Cron(cronExpression, { timezone }, async () => {
    console.error(`[scheduler] Tick at ${new Date().toISOString()}`);
    try {
      await onTick();
    } catch (err) {
      console.error("[scheduler] Error in tick:", err instanceof Error ? err.message : err);
    }
  });

  return {
    stop: () => {
      job.stop();
      console.error("[scheduler] Stopped");
    },
  };
}
