import { execFile } from "child_process";
import { promisify } from "util";
import { tmpdir } from "os";
import { join } from "path";
import { readFile, unlink, writeFile } from "fs/promises";
import { randomBytes } from "crypto";

const execFileAsync = promisify(execFile);

const VOICE = "et+f2";   // Female Estonian voice (clearer intonation)
const SPEED = "100";      // Slow and clear for learners
const PITCH = "50";       // Natural pitch
const GAP = "20";         // 20ms gap between words for clarity

async function synthesizeSegment(text: string, outputFile: string): Promise<void> {
  await execFileAsync("espeak-ng", [
    "-v", VOICE,
    "-g", GAP,
    "-s", SPEED,
    "-p", PITCH,
    "-w", outputFile,
    text,
  ], { timeout: 10_000 });
}

export async function synthesizeSpeech(word: string, sentence?: string): Promise<Buffer | null> {
  const id = randomBytes(6).toString("hex");
  const wordFile = join(tmpdir(), `tts-word-${id}.wav`);
  const sentenceFile = join(tmpdir(), `tts-sent-${id}.wav`);
  const combinedFile = join(tmpdir(), `tts-combined-${id}.wav`);

  try {
    // Synthesize the word
    await synthesizeSegment(word, wordFile);

    if (sentence && sentence !== word) {
      // Synthesize the sentence
      await synthesizeSegment(sentence, sentenceFile);

      // Combine: word + pause + sentence
      // Use espeak-ng to generate a silence gap, then concatenate WAV files
      // Simple approach: concatenate the raw audio data (both are same format from espeak-ng)
      const wordAudio = await readFile(wordFile);
      const sentAudio = await readFile(sentenceFile);

      // WAV header is 44 bytes. Both files have same sample rate/format from espeak-ng.
      // Create a silence buffer (~500ms at 22050 Hz, 16-bit mono = 22050 samples)
      const silenceSamples = 22050; // ~1 second pause
      const silenceBytes = silenceSamples * 2; // 16-bit = 2 bytes per sample
      const silence = Buffer.alloc(silenceBytes, 0);

      // Extract raw audio (skip WAV headers)
      const wordRaw = wordAudio.subarray(44);
      const sentRaw = sentAudio.subarray(44);

      // Build new WAV with combined audio
      const totalDataSize = wordRaw.length + silenceBytes + sentRaw.length;
      const header = Buffer.from(wordAudio.subarray(0, 44));

      // Update file size in header (bytes 4-7: file size - 8)
      header.writeUInt32LE(totalDataSize + 36, 4);
      // Update data chunk size (bytes 40-43)
      header.writeUInt32LE(totalDataSize, 40);

      const combined = Buffer.concat([header, wordRaw, silence, sentRaw]);
      await writeFile(combinedFile, combined);

      return combined;
    }

    return await readFile(wordFile);
  } catch (err) {
    console.error(`[tts] Error synthesizing "${word}":`, err instanceof Error ? err.message : err);
    return null;
  } finally {
    await Promise.all([
      unlink(wordFile).catch(() => {}),
      unlink(sentenceFile).catch(() => {}),
      unlink(combinedFile).catch(() => {}),
    ]);
  }
}
