# Add Words to Wordagram DB

Review candidate words discovered by the background Ekilex job, add Turkish translations, and approve them into the main words table.

## Instructions

The backend discovers new Estonian words from Ekilex every 10 minutes and saves them to `candidate_words` table with status `pending`. Your job is to:
1. Check what candidates are available
2. Add Turkish translations (word + sentences)
3. Approve them into the main `words` table

### API Base: https://wordagram.hatip.dev/api
### Auth Header: X-User-Id: 8607749953

### Step 1: Check pipeline status
```
GET /api/admin/candidates/stats
```

### Step 2: Get pending candidates (with sentences)
```
GET /api/admin/candidates?status=pending&level=A1&limit=20
```
Response includes each word's Estonian, English, CEFR level, and example sentences.

### Step 3: Translate a candidate (word + sentences)
```
PATCH /api/admin/candidates/{id}
Body: {
  "turkish": "köpek",
  "sentences": [
    {"estonian": "Koer jookseb.", "turkish": "Köpek koşuyor."}
  ]
}
```

For each candidate:
- Translate the word itself to Turkish
- Translate ALL its example sentences to Turkish
- Use natural, everyday Turkish — not overly formal
- This changes the candidate status from `pending` to `translated`

### Step 4: Approve translated candidates into words table
Approve one:
```
POST /api/admin/candidates/{id}/approve
```
Approve ALL translated at once:
```
POST /api/admin/candidates/approve-all
```

### Step 5: Reject bad candidates (wrong level, duplicate meaning, etc.)
```
DELETE /api/admin/candidates/{id}
```

### Step 6: Verify final stats
```
GET /api/admin/words/stats
```

### Workflow:
1. First check candidate stats to see how many are pending
2. Fetch pending candidates for the requested level (or all levels)
3. For EACH candidate, translate the word AND all its sentences to Turkish using PATCH
4. After translating a batch, call approve-all to move them to the words table
5. Report: how many translated, how many approved, how many rejected, new word totals

### Processing approach:
- Fetch candidates in batches (limit=10 or limit=20)
- Translate each one with a PATCH call — include Turkish for the word AND every sentence
- After each batch of translations, call approve-all once
- Then fetch the next batch
- Continue until all requested candidates are processed or user's limit is reached

### Rules:
- Always check candidate stats first
- Translate BOTH the word AND its example sentences to Turkish
- Use natural everyday Turkish translations
- Reject words that are too obscure, have no clear Turkish equivalent, or are duplicates in meaning of existing words
- If a candidate has no sentences, still translate and approve the word
- After approving, verify with /admin/words/stats to confirm new totals
- When user says "add N words for level X" — translate and approve N pending candidates for that level
- When user says "translate all" — process all pending candidates across all levels

$ARGUMENTS