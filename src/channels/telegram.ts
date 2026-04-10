import { Bot, InputFile } from "grammy";
import type { DeliveryChannel } from "./types.js";
import type { Flashcard } from "../flashcard/types.js";

export function createTelegramChannel(token: string): { channel: DeliveryChannel; bot: Bot } {
  const bot = new Bot(token);

  const channel: DeliveryChannel = {
    name: "telegram",

    async sendFlashcard(chatId: number, flashcard: Flashcard): Promise<boolean> {
      try {
        if (flashcard.imageUrl) {
          await bot.api.sendPhoto(chatId, flashcard.imageUrl, {
            caption: flashcard.caption,
            parse_mode: "HTML",
          });
        } else {
          await bot.api.sendMessage(chatId, flashcard.caption, {
            parse_mode: "HTML",
          });
        }

        // Send pronunciation audio if available
        if (flashcard.audio) {
          await bot.api.sendVoice(chatId, new InputFile(flashcard.audio, "pronunciation.ogg"));
        }

        return true;
      } catch (err) {
        console.error(`[telegram] Failed to send to ${chatId}:`, err instanceof Error ? err.message : err);
        return false;
      }
    },
  };

  return { channel, bot };
}
