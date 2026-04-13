import { Cron } from "croner";
import type { Subscriber } from "../db/progress.js";
import { errMsg } from "../utils.js";

interface UserJob {
  chatId: number;
  cron: Cron;
  schedule: string;
}

const userJobs = new Map<number, UserJob>();

export function startGlobalScheduler(
  defaultSchedule: string,
  timezone: string,
  onTick: () => Promise<void>,
): { stop: () => void } {
  console.error(`[scheduler] Starting global fallback cron: "${defaultSchedule}" (${timezone})`);

  const job = new Cron(defaultSchedule, { timezone }, async () => {
    console.error(`[scheduler] Global tick at ${new Date().toISOString()}`);
    try {
      await onTick();
    } catch (err) {
      console.error("[scheduler] Error in global tick:", errMsg(err));
    }
  });

  return {
    stop: () => {
      job.stop();
      stopAllUserJobs();
      console.error("[scheduler] All jobs stopped");
    },
  };
}

export function syncUserJobs(
  subscribers: Subscriber[],
  timezone: string,
  deliverToUser: (chatId: number) => Promise<void>,
): void {
  const activeIds = new Set(subscribers.map((s) => s.chatId));

  // Remove jobs for unsubscribed users
  for (const [chatId, job] of userJobs) {
    if (!activeIds.has(chatId)) {
      job.cron.stop();
      userJobs.delete(chatId);
      console.error(`[scheduler] Removed job for chat ${chatId}`);
    }
  }

  // Add/update jobs for active subscribers
  for (const sub of subscribers) {
    const existing = userJobs.get(sub.chatId);

    if (existing && existing.schedule === sub.schedule) {
      continue; // No change needed
    }

    // Stop old job if schedule changed
    if (existing) {
      existing.cron.stop();
      console.error(`[scheduler] Updated schedule for chat ${sub.chatId}: ${existing.schedule} → ${sub.schedule}`);
    }

    const cron = new Cron(sub.schedule, { timezone }, async () => {
      console.error(`[scheduler] User tick for chat ${sub.chatId} at ${new Date().toISOString()}`);
      try {
        await deliverToUser(sub.chatId);
      } catch (err) {
        console.error(`[scheduler] Error delivering to ${sub.chatId}:`, errMsg(err));
      }
    });

    userJobs.set(sub.chatId, {
      chatId: sub.chatId,
      cron,
      schedule: sub.schedule,
    });

    if (!existing) {
      console.error(`[scheduler] Created job for chat ${sub.chatId}: ${sub.schedule}`);
    }
  }
}

export function stopAllUserJobs(): void {
  for (const [, job] of userJobs) {
    job.cron.stop();
  }
  userJobs.clear();
}

// --- Random grammar card scheduling ---

const grammarJobs = new Map<number, Cron>();

function randomTimeBetween(startHour: number, endHour: number): { hour: number; minute: number } {
  const totalMinutes = (endHour - startHour) * 60;
  const randomMinute = Math.floor(Math.random() * totalMinutes);
  return {
    hour: startHour + Math.floor(randomMinute / 60),
    minute: randomMinute % 60,
  };
}

export function scheduleRandomGrammarJobs(
  subscribers: Subscriber[],
  timezone: string,
  deliverGrammarCard: (chatId: number) => Promise<void>,
): void {
  // Clear existing grammar jobs
  stopAllGrammarJobs();

  for (const sub of subscribers) {
    const { hour, minute } = randomTimeBetween(8, 22);
    const schedule = `${minute} ${hour} * * *`;

    const cron = new Cron(schedule, { timezone }, async () => {
      console.error(`[scheduler] Grammar tick for chat ${sub.chatId} at ${new Date().toISOString()}`);
      try {
        await deliverGrammarCard(sub.chatId);
      } catch (err) {
        console.error(`[scheduler] Error delivering grammar to ${sub.chatId}:`, errMsg(err));
      }
    });

    grammarJobs.set(sub.chatId, cron);
    console.error(`[scheduler] Scheduled grammar card for chat ${sub.chatId} at ${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`);
  }
}

export function scheduleGrammarJobForUser(
  chatId: number,
  timezone: string,
  deliverGrammarCard: (chatId: number) => Promise<void>,
): void {
  // Skip if already scheduled
  if (grammarJobs.has(chatId)) return;

  const { hour, minute } = randomTimeBetween(8, 22);
  const schedule = `${minute} ${hour} * * *`;

  const cron = new Cron(schedule, { timezone }, async () => {
    console.error(`[scheduler] Grammar tick for chat ${chatId} at ${new Date().toISOString()}`);
    try {
      await deliverGrammarCard(chatId);
    } catch (err) {
      console.error(`[scheduler] Error delivering grammar to ${chatId}:`, errMsg(err));
    }
  });

  grammarJobs.set(chatId, cron);
  console.error(`[scheduler] Scheduled grammar card for chat ${chatId} at ${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`);
}

export function stopAllGrammarJobs(): void {
  for (const [, job] of grammarJobs) {
    job.stop();
  }
  grammarJobs.clear();
}

export function getActiveJobCount(): number {
  return userJobs.size;
}
