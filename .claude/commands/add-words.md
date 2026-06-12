# Add Words to Wordagram DB

Add new Estonian words to the database. Uses the Ekilex API to discover words with CEFR levels, English translations, and example sentences.

## Instructions

The user will specify what words to add. Interpret their request and use the appropriate API:

### API Base: https://wordagram.hatip.dev/api
### Auth Header: X-User-Id: 8607749953

### Available Endpoints:

1. **Discover by search term** — finds a specific word in Ekilex:
   ```
   POST /api/admin/words/from-ekilex
   Body: {"search": "koer"}
   ```

2. **Discover random words by level** — pulls N new words at a given CEFR level:
   ```
   POST /api/admin/words/from-ekilex
   Body: {"level": "A1", "count": 10}
   ```

3. **Add manually** — when Ekilex doesn't have the word:
   ```
   POST /api/admin/words
   Body: {"estonian": "koer", "english": "dog", "turkish": "köpek", "cefrLevel": "A1", "sentences": [{"estonian": "Koer jookseb.", "english": "The dog runs.", "turkish": "Köpek koşuyor."}]}
   ```

4. **Check current stats**:
   ```
   GET /api/admin/words/stats
   ```

5. **Get words needing Turkish translation**:
   ```
   GET /api/admin/words/untranslated-full?level=A1&limit=10
   ```

6. **Add Turkish translations** (word + sentences):
   ```
   PATCH /api/admin/words/{id}/translate
   Body: {"turkish": "köpek", "sentences": [{"estonian": "Koer jookseb.", "turkish": "Köpek koşuyor."}]}
   ```

7. **Bulk translate**:
   ```
   POST /api/admin/words/bulk-translate
   Body: {"translations": [{"id": "a1-koer", "turkish": "köpek", "sentences": [{"estonian": "...", "turkish": "..."}]}]}
   ```

### Workflow:

1. First check stats to see current word counts per level
2. Use from-ekilex to discover new words (they come with Estonian + English + sentences)
3. After adding, fetch untranslated words and add Turkish translations
4. Report what was added and what still needs Turkish

### Rules:
- Always check stats first to understand current state
- Duplicate words are rejected by the API (unique constraint on estonian column)
- When adding Turkish translations, translate BOTH the word AND its example sentences
- Report results clearly: how many added, how many skipped (duplicates), how many need Turkish

$ARGUMENTS