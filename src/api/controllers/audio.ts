import type { Request, Response } from "express";
import { synthesizeSpeech } from "../../services/tts.js";

export async function getWordAudio(req: Request, res: Response): Promise<void> {
  const word = req.params.word as string;

  if (!word || word.length > 100) {
    res.status(400).json({ error: "Invalid word" });
    return;
  }

  const voice = (req.query.voice as string) || "mari";

  try {
    const audio = await synthesizeSpeech(word, undefined, voice);
    if (!audio) {
      res.status(503).json({ error: "TTS service unavailable" });
      return;
    }
    res.set("Content-Type", "audio/ogg");
    res.set("Cache-Control", "public, max-age=86400");
    res.send(audio);
  } catch {
    res.status(503).json({ error: "TTS service unavailable" });
  }
}
