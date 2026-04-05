# Flash Card IO

Standalone flashcard bot for Estonian-English language learning. Sends visual flashcards with Unsplash images on a configurable schedule via Telegram (WhatsApp planned).

## Features

- CEFR-leveled words (A1 → B2)
- Unsplash images for visual learning
- Example sentences from Tatoeba API (with bundled fallbacks)
- SQLite progress tracking per subscriber
- Configurable cron schedule
- Feature flags for Telegram / WhatsApp
- Docker deployment

## Bot Commands

| Command | Description |
|---|---|
| `/start` | Subscribe to flashcards |
| `/stop` | Unsubscribe |
| `/next` | Get a flashcard now |
| `/level A1\|A2\|B1\|B2` | Change difficulty |
| `/stats` | View your progress |

## Setup

### 1. Create a Telegram Bot

Message [@BotFather](https://t.me/BotFather) → `/newbot` → copy the token.

### 2. Get an Unsplash API Key

Register at [unsplash.com/developers](https://unsplash.com/developers).

### 3. Configure

```bash
cp .env.example .env
# Edit .env with your tokens
```

### 4. Run with Docker

```bash
docker compose up -d
```

### 5. Run locally

```bash
npm install
npm run build
npm start
```

## Configuration

| Variable | Default | Description |
|---|---|---|
| `TELEGRAM_BOT_TOKEN` | (required) | Telegram bot token |
| `UNSPLASH_ACCESS_KEY` | (required) | Unsplash API access key |
| `CRON_SCHEDULE` | `0 9 * * *` | Cron schedule (default: daily 9 AM) |
| `CRON_TIMEZONE` | `Europe/Tallinn` | Timezone for the schedule |
| `CEFR_LEVELS` | `A1,A2` | Enabled word levels |
| `DB_PATH` | `./data/db/progress.db` | SQLite database path |
| `FEATURE_TELEGRAM` | `true` | Enable Telegram delivery |
| `FEATURE_WHATSAPP` | `false` | Enable WhatsApp delivery (not yet implemented) |

## Sentence Sources

Flashcard sentences are resolved with a fallback chain:
1. **Tatoeba API** — community-verified example sentences
2. **Bundled examples** — curated sentences shipped with the word list

## Architecture

```
src/
├── config.ts              # Environment config
├── index.ts               # Entry point + delivery loop
├── db/progress.ts         # SQLite subscriber + progress tracking
├── services/
│   ├── unsplash.ts        # Unsplash image search
│   ├── tatoeba.ts         # Tatoeba sentence API
│   └── sentence.ts        # Fallback chain resolver
├── channels/
│   ├── types.ts           # DeliveryChannel interface
│   ├── telegram.ts        # Telegram implementation
│   └── whatsapp.ts        # WhatsApp stub
├── flashcard/
│   ├── types.ts           # Core types (Word, Flashcard)
│   ├── word-bank.ts       # Load + query word lists
│   ├── builder.ts         # Compose flashcards
│   └── scheduler.ts       # Cron scheduler
└── bot/commands.ts        # Telegram bot commands
```

## License

MIT
