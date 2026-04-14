import { Cron } from "croner";
import { getUsersDueForDelivery, getUsersDueForGrammar, updateNextDelivery, scheduleNextGrammar, getSubscriberSchedule } from "../db/progress.js";
import { errMsg } from "../utils.js";

interface SchedulerHandle {
  stop: () => void;
}

/**
 * Dispatch to due users with random jitter spread over `spreadMs` to avoid
 * thundering-herd when many users share the same minute slot.
 */
async function dispatchWithJitter(
  users: Array<{ chatId: number }>,
  fn: (chatId: number) => Promise<void>,
  spreadMs: number,
): Promise<void> {
  const tasks = users.map(({ chatId }) => {
    const delay = Math.floor(Math.random() * spreadMs);
    return new Promise<void>((resolve) => {
      setTimeout(async () => {
        try {
          await fn(chatId);
        } catch (err) {
          console.error(`[scheduler] Dispatch error for chat ${chatId}:`, errMsg(err));
        }
        resolve();
      }, delay);
    });
  });
  await Promise.all(tasks);
}

/**
 * Start the single-cron scheduler. Creates exactly 2 Cron jobs (one for
 * flashcard delivery, one for grammar) that tick every minute, query the DB
 * for users whose next_delivery_at / next_grammar_at has passed, dispatch,
 * and reschedule.
 */
export function startScheduler(
  timezone: string,
  deliverFlashcard: (chatId: number) => Promise<void>,
  deliverGrammarCard: (chatId: number) => Promise<void>,
): SchedulerHandle {
  const JITTER_SPREAD_MS = 30_000;

  const deliveryJob = new Cron("* * * * *", { timezone, protect: true }, async () => {
    const due = await getUsersDueForDelivery();
    if (due.length === 0) return;
    console.error(`[scheduler] Delivery tick: ${due.length} user(s) due`);

    await dispatchWithJitter(due, async (chatId) => {
      await deliverFlashcard(chatId);
      const schedule = await getSubscriberSchedule(chatId);
      await updateNextDelivery(chatId, schedule, timezone);
    }, JITTER_SPREAD_MS);
  });

  const grammarJob = new Cron("* * * * *", { timezone, protect: true }, async () => {
    const due = await getUsersDueForGrammar();
    if (due.length === 0) return;
    console.error(`[scheduler] Grammar tick: ${due.length} user(s) due`);

    await dispatchWithJitter(due, async (chatId) => {
      await deliverGrammarCard(chatId);
      await scheduleNextGrammar(chatId, timezone);
    }, JITTER_SPREAD_MS);
  });

  console.error("[scheduler] Single-cron scheduler started (delivery + grammar, every minute)");

  return {
    stop: () => {
      deliveryJob.stop();
      grammarJob.stop();
      console.error("[scheduler] Scheduler stopped");
    },
  };
}
