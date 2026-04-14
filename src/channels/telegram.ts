import { Bot, InputFile } from "grammy";
import type { DeliveryChannel } from "./types.js";
import type { Flashcard } from "../flashcard/types.js";
import { recallKeyboard } from "../bot/keyboards.js";
import { errMsg } from "../utils.js";

export function createTelegramChannel(token: string): { channel: DeliveryChannel; bot: Bot } {
  const bot = new Bot(token);

  const channel: DeliveryChannel = {
    name: "telegram",

    async sendFlashcard(chatId: number, flashcard: Flashcard): Promise<boolean> {
      try {
        const caption = flashcard.caption;

        if (flashcard.imageUrl && caption.length <= 1024) {
          await bot.api.sendPhoto(chatId, flashcard.imageUrl, {
            caption,
            parse_mode: "HTML",
          });
        } else if (flashcard.imageUrl) {
          await bot.api.sendPhoto(chatId, flashcard.imageUrl);
          await bot.api.sendMessage(chatId, caption, { parse_mode: "HTML" });
        } else {
          await bot.api.sendMessage(chatId, caption, {
            parse_mode: "HTML",
          });
        }

        // Send pronunciation audio if available
        if (flashcard.audio) {
          await bot.api.sendVoice(chatId, new InputFile(flashcard.audio, "pronunciation.wav"));
        }

        // Send recall buttons if this is a real flashcard (has a word value)
        if (flashcard.word.estonian) {
          await bot.api.sendMessage(chatId, "How did you do?", {
            reply_markup: recallKeyboard(flashcard.word.estonian),
          });
        }

        return true;
      } catch (err) {
        console.error(`[telegram] Failed to send to ${chatId}:`, errMsg(err));
        return false;
      }
    },
  };

  return { channel, bot };
}
