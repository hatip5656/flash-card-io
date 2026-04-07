import type { WordForm } from "./types.js";
import { escapeHtml } from "./builder.js";

// Estonian POS values from Ekilex and their English labels
const POS_MAP: Record<string, { label: string; isVerb: boolean }> = {
  V: { label: "verb", isVerb: true },
  S: { label: "noun", isVerb: false },
  A: { label: "adjective", isVerb: false },
  D: { label: "adverb", isVerb: false },
  P: { label: "pronoun", isVerb: false },
  N: { label: "numeral", isVerb: false },
  // Estonian language POS values
  tegusõna: { label: "verb", isVerb: true },
  nimisõna: { label: "noun", isVerb: false },
  omadussõna: { label: "adjective", isVerb: false },
  määrsõna: { label: "adverb", isVerb: false },
  asesõna: { label: "pronoun", isVerb: false },
  arvsõna: { label: "numeral", isVerb: false },
};

// Clean up verbose Estonian morphValue labels
const LABEL_SIMPLIFY: Array<{ pattern: RegExp; label: string }> = [
  // Verb forms
  { pattern: /^ma-infinitiiv/i, label: "ma-inf" },
  { pattern: /^da-infinitiiv/i, label: "da-inf" },
  { pattern: /^mata-vorm/i, label: "-mata" },
  { pattern: /^mas-vorm/i, label: "-mas" },
  { pattern: /^mast-vorm/i, label: "-mast" },
  { pattern: /^maks-vorm/i, label: "-maks" },
  { pattern: /^des-vorm/i, label: "-des" },
  { pattern: /^ma-tegevusnime umbisikuline/i, label: "passive ma-inf" },
  { pattern: /kindla kõneviisi oleviku.*1.*pööre$/i, label: "present 1st sg" },
  { pattern: /kindla kõneviisi oleviku.*2.*pööre$/i, label: "present 2nd sg" },
  { pattern: /kindla kõneviisi oleviku.*3.*pööre$/i, label: "present 3rd sg" },
  { pattern: /kindla kõneviisi lihtmineviku.*3.*pööre$/i, label: "past 3rd sg" },
  // Noun cases
  { pattern: /ainsuse suunduv|lühike sisseütlev/i, label: "short illative sg" },
  { pattern: /ainsuse nimetav/i, label: "nominative sg" },
  { pattern: /ainsuse omastav/i, label: "genitive sg" },
  { pattern: /ainsuse osastav/i, label: "partitive sg" },
  { pattern: /ainsuse sisseütlev/i, label: "illative sg" },
  { pattern: /ainsuse seesütlev/i, label: "inessive sg" },
  { pattern: /ainsuse seestütlev/i, label: "elative sg" },
  { pattern: /ainsuse alaleütlev/i, label: "allative sg" },
  { pattern: /ainsuse alalütlev/i, label: "adessive sg" },
  { pattern: /ainsuse alaltütlev/i, label: "ablative sg" },
  { pattern: /mitmuse nimetav/i, label: "nominative pl" },
  { pattern: /mitmuse omastav/i, label: "genitive pl" },
  { pattern: /mitmuse osastav/i, label: "partitive pl" },
];

function simplifyLabel(morphValue: string): string {
  for (const { pattern, label } of LABEL_SIMPLIFY) {
    if (pattern.test(morphValue)) return label;
  }
  // Fallback: use the original but truncate if too long
  return morphValue.length > 25 ? morphValue.slice(0, 22) + "..." : morphValue;
}

function isVerb(pos: string | null): boolean {
  if (!pos) return false;
  return POS_MAP[pos]?.isVerb ?? false;
}

function getPosLabel(pos: string | null): string {
  if (!pos) return "";
  return POS_MAP[pos]?.label ?? pos;
}

function selectForms(forms: WordForm[], pos: string | null): Array<{ label: string; value: string }> {
  // Deduplicate by morphCode, keeping first occurrence
  const unique = new Map<string, WordForm>();
  for (const f of forms) {
    const key = f.morphCode || f.morphValue;
    if (key && !unique.has(key)) unique.set(key, f);
  }

  const selected: Array<{ label: string; value: string }> = [];
  for (const f of unique.values()) {
    // Skip forms with empty or placeholder values
    if (!f.value || f.value === "-" || f.value === "–") continue;
    const label = simplifyLabel(f.morphValue || f.morphCode);
    selected.push({ label, value: f.value });
  }

  // Limit to 10 most useful forms
  return selected.slice(0, 10);
}

export { simplifyLabel, selectForms };

export function buildGrammarCaption(
  wordValue: string,
  english: string,
  pos: string | null,
  cefrLevel: string,
  forms: WordForm[],
): string {
  const posLabel = getPosLabel(pos);
  const verb = isVerb(pos);
  const selected = selectForms(forms, pos);

  let caption = `📖 <b>Grammar: ${escapeHtml(wordValue)}</b>`;
  if (posLabel) caption += ` (${escapeHtml(posLabel)})`;
  caption += `\n🔄 <tg-spoiler>${escapeHtml(english)}</tg-spoiler>`;
  caption += `\n🏷️ ${escapeHtml(cefrLevel)}`;

  caption += `\n\n<b>${verb ? "Conjugation" : "Declension"}:</b>`;
  for (const { label, value } of selected) {
    caption += `\n${escapeHtml(label)}: <code>${escapeHtml(value)}</code>`;
  }

  caption += "\n\n📖 <i>Source: Ekilex/Sõnaveeb</i>";
  return caption;
}
