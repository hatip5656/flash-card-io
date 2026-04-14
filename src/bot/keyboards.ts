import { InlineKeyboard } from "grammy";
import type { CefrLevel } from "../config.js";
import type { UserPreferences } from "../db/progress.js";
import { SCHEDULE_PRESETS } from "./commands.js";

export function mainMenuKeyboard(): InlineKeyboard {
  return new InlineKeyboard()
    .text("📚 Next Card", "action:next")
    .text("📖 Grammar", "action:grammar")
    .text("🧠 Quiz", "action:quiz")
    .row()
    .text("📊 Stats", "action:stats")
    .text("🏷️ Level", "edit_level")
    .text("⏰ Schedule", "edit_schedule")
    .row()
    .text("⚙️ Preferences", "action:preferences");
}

export function recallKeyboard(wordValue: string): InlineKeyboard {
  return new InlineKeyboard()
    .text("Got it ✅", `recall:got:${wordValue}`)
    .text("Again 🔄", `recall:again:${wordValue}`);
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

export function preferencesKeyboard(prefs: UserPreferences): InlineKeyboard {
  const toggle = (on: boolean) => on ? "✅" : "❌";
  return new InlineKeyboard()
    .text(`${toggle(prefs.audio)} Audio`, "pref:audio")
    .text(`${toggle(prefs.wordForms)} Word Forms`, "pref:wordForms")
    .row()
    .text(`${toggle(prefs.grammarCards)} Grammar Cards`, "pref:grammarCards")
    .row()
    .text(`${toggle(prefs.dailySummary)} Daily Summary`, "pref:dailySummary")
    .text(`${toggle(prefs.weeklyReport)} Weekly Report`, "pref:weeklyReport")
    .row()
    .text(`🎤 Voice: ${prefs.voiceName}`, "pref:voicePicker")
    .row()
    .text("« Back", "back_menu");
}

export function voicePicker(current: string): InlineKeyboard {
  const voices = ["mari", "albert", "indrek", "kalev", "kylli", "liivika", "meelis", "peeter", "tambet", "vesta"];
  const kb = new InlineKeyboard();
  for (let i = 0; i < voices.length; i++) {
    const v = voices[i];
    kb.text(v === current ? `✅ ${v}` : v, `set:voice:${v}`);
    if (i % 3 === 2) kb.row();
  }
  kb.row().text("« Back to preferences", "action:preferences");
  return kb;
}
