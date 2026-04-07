import { InlineKeyboard } from "grammy";
import type { CefrLevel } from "../config.js";
import { SCHEDULE_PRESETS } from "./commands.js";

export function mainMenuKeyboard(): InlineKeyboard {
  return new InlineKeyboard()
    .text("📚 Next Card", "action:next")
    .text("📖 Grammar", "action:grammar")
    .row()
    .text("🧠 Quiz", "action:quiz")
    .text("📊 Stats", "action:stats")
    .row()
    .text("🏷️ Level", "edit_level")
    .text("⏰ Schedule", "edit_schedule")
    .row()
    .text("🛑 Stop", "action:stop");
}

export function levelPicker(current: CefrLevel): InlineKeyboard {
  const kb = new InlineKeyboard();
  for (const level of ["A1", "A2", "B1", "B2"] as CefrLevel[]) {
    kb.text(level === current ? `✅ ${level}` : level, `set:level:${level}`);
  }
  kb.row().text("« Back", "back_menu");
  return kb;
}

export function schedulePicker(currentCron: string): InlineKeyboard {
  const kb = new InlineKeyboard();
  for (const [key, p] of Object.entries(SCHEDULE_PRESETS)) {
    kb.text(p.cron === currentCron ? `✅ ${p.label}` : p.label, `set:schedule:${key}`);
    kb.row();
  }
  kb.text("« Back", "back_menu");
  return kb;
}
