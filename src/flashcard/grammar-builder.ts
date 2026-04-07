import type { WordForm } from "./types.js";
import { escapeHtml } from "./builder.js";

// Key morphological codes for Estonian nouns (14-case system)
const NOUN_MORPH_CODES = [
  { code: "SgN", label: "Nominative (sg)" },
  { code: "SgG", label: "Genitive (sg)" },
  { code: "SgP", label: "Partitive (sg)" },
  { code: "SgIll", label: "Illative (sg)" },
  { code: "SgIn", label: "Inessive (sg)" },
  { code: "SgEl", label: "Elative (sg)" },
  { code: "PlN", label: "Nominative (pl)" },
  { code: "PlG", label: "Genitive (pl)" },
  { code: "PlP", label: "Partitive (pl)" },
];

// Key morphological codes for Estonian verbs
const VERB_MORPH_CODES = [
  { code: "Sup", label: "ma-infinitive" },
  { code: "Inf", label: "da-infinitive" },
  { code: "IndPrSg1", label: "Present 1st sg" },
  { code: "IndPrSg2", label: "Present 2nd sg" },
  { code: "IndPrSg3", label: "Present 3rd sg" },
  { code: "IndPrPl1", label: "Present 1st pl" },
  { code: "IndIpfSg3", label: "Past 3rd sg" },
];

function selectForms(forms: WordForm[], pos: string | null): Array<{ label: string; value: string }> {
  const codes = pos === "V" ? VERB_MORPH_CODES : NOUN_MORPH_CODES;

  const selected: Array<{ label: string; value: string }> = [];
  for (const { code, label } of codes) {
    const match = forms.find((f) => f.morphCode === code);
    if (match) {
      selected.push({ label, value: match.value });
    }
  }

  // If no known codes matched, show the first few forms with their morphValue labels
  if (selected.length === 0) {
    const unique = new Map<string, WordForm>();
    for (const f of forms) {
      if (!unique.has(f.morphCode)) unique.set(f.morphCode, f);
    }
    for (const f of [...unique.values()].slice(0, 8)) {
      selected.push({ label: f.morphValue || f.morphCode, value: f.value });
    }
  }

  return selected;
}

export function buildGrammarCaption(
  wordValue: string,
  english: string,
  pos: string | null,
  cefrLevel: string,
  forms: WordForm[],
): string {
  const posLabel = pos === "V" ? "verb" : pos === "S" ? "noun" : pos === "A" ? "adjective" : pos ?? "";
  const selected = selectForms(forms, pos);

  let caption = `📖 <b>Grammar: ${escapeHtml(wordValue)}</b>`;
  if (posLabel) caption += ` (${escapeHtml(posLabel)})`;
  caption += `\n🔄 <tg-spoiler>${escapeHtml(english)}</tg-spoiler>`;
  caption += `\n🏷️ ${escapeHtml(cefrLevel)}`;

  caption += `\n\n<b>${pos === "V" ? "Conjugation" : "Declension"}:</b>`;
  for (const { label, value } of selected) {
    caption += `\n${escapeHtml(label)}: <code>${escapeHtml(value)}</code>`;
  }

  caption += "\n\n📖 <i>Source: Ekilex/Sõnaveeb</i>";
  return caption;
}
