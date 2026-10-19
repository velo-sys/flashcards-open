# Flashcards Open – Android app (specification for Claude Code)

## Goal
A native Android app for studying flashcards with spaced repetition. **Fully offline**, no account, no server.
Target devices: Android phones and Android handhelds with a controller (Retroid, AYN Odin and the like), portrait and landscape.

The file `reference/flashcards.html` is a working web prototype. It is the **reference for behaviour**
(CSV detection, Markdown/images, scheduling, key bindings). Do not embed it in a WebView; rebuild it natively.

## Hard requirements
1. **Hierarchy: topics → decks → cards.**
   - At least **10 topics** with **at least 20 decks** each. There are **no artificial limits**.
   - The data model and the UI have to stay fluid with **200+ decks and 20,000+ cards** (paging/flows, indices).
   - Topics and decks can be created, renamed, deleted, sorted and moved between topics.
   - Every topic has a name, a color and optionally an emoji/icon.
2. **No `INTERNET` permission** in the manifest. Every library has to work without a network.
3. **Special characters must work reliably** (ä ö ü ß € „“ – as well as emoji). See the CSV import.
4. **Markdown and images** on both sides of a card.
5. Study progress survives when a CSV is imported again.
6. **Studying several decks at once (required).** Any decks, including ones from different topics, can be selected and studied together in one mixed session. See “Combined study”.

## Tech stack
- Kotlin, Jetpack Compose, Material 3, single activity, Navigation Compose
- Room (with KSP), Kotlin Coroutines/Flow, architecture: MVVM plus repositories; DI manual or Hilt
- Markdown: `com.mikepenz:multiplatform-markdown-renderer-m3` plus `-coil3` (images from local files)
- CSV: a hand-written RFC 4180 parser (quotes, `""` escapes, line breaks inside fields), with unit tests
- Files: Storage Access Framework (`ACTION_OPEN_DOCUMENT`, `ACTION_OPEN_DOCUMENT_TREE`, `ACTION_CREATE_DOCUMENT`)
- minSdk 26, targetSdk = the latest stable release. **Check the current stable version before adding any dependency** and use the version catalog (`gradle/libs.versions.toml`).

## Data model (Room)
- `Topic(id, name, colorArgb, emoji?, sortIndex, createdAt)`
- `Deck(id, topicId → Topic, name, sourceFileName?, sortIndex, createdAt, updatedAt, newPerDay? /* null = global */)`
- `Card(id, deckId → Deck, frontMd, backMd, tags, orderIndex, contentKey /* hash of the front */,
        type[BASIC|CLOZE|INPUT|CHOICE|MATCH], options /* `|` separated */,
        state[NEW|LEARN|REVIEW|RELEARN], dueAt, intervalDays, ease, reps, lapses, step)`
- `ReviewLog(id, cardId, reviewedAt, rating, prevState, newIntervalDays)`
- `Media(fileName /* normalised: NFC, lower case */, relativePath, sizeBytes)`. The images live in `filesDir/media/`.
- Indices: `Card(deckId, state, dueAt)`, `Deck(topicId)`. Foreign keys with `CASCADE` on delete.

## CSV import (as in the reference)
- **Encoding:** BOM detection (UTF-8, UTF-16 LE/BE). Otherwise strict UTF-8 first (`CharsetDecoder` with `CodingErrorAction.REPORT`), falling back to `windows-1252` (Excel ANSI) on error. Then normalise to Unicode NFC.
- **Delimiter:** auto-detected among `;` `,` tab `|` from the first rows (consistent column count, quoting honoured). An Anki header such as `#separator:Semicolon` is respected. Further `#key:value` headers are ignored.
- **Header row:** recognised when column names appear in {front, question, term, prompt, vorderseite, frage, begriff, vorne} / {back, answer, definition, solution, rückseite, antwort, hinten} / {tags, tag, category, topic, kategorie, thema}. Without a header: column 1 = front, 2 = back, 3 = tags.
- **Card type:** the optional columns {type, cardtype, kind, typ, kartentyp, art} and {options, option, choices, distractors, wrong, optionen, auswahl, alternativen, falsch}. All types may be mixed within one file, even without a type column (see “Inference”). They only exist through the header row, never by position – otherwise existing four-column files would be read wrongly. See “Card types”.
- A literal `\n` in the text becomes a line break.
- **Re-import:** matched through `contentKey` (hash of the front). Existing cards keep their progress, new ones are added, ones that are gone are removed (with a preview dialog first: “x new, y changed, z removed”).
- **Import preview** before saving: the detected encoding, delimiter and header, the first 5 cards rendered, and the target topic selectable.

## Bulk import (important for many decks)
- **Folder import** through `ACTION_OPEN_DOCUMENT_TREE`, with this structure:
  ```
  Flashcards/
    Networking/           ← topic
      OSI-model.csv       ← deck
      Subnetting.csv
      images/osi.png      ← images (any subfolder)
    Math/
      Derivatives.csv
  ```
  Subfolders become topics, CSV files become decks, every image ends up in `Media`. Whatever already exists is updated through a re-import.
- **ZIP import** with the same structure.
- All of it runs with a progress indicator and can be cancelled (a coroutine in the ViewModel, or WorkManager).

## Card types
Every card has a type. It decides how the front is asked, not how it is scheduled: the scheduler stays untouched.

| Type | CSV value (examples) | Front | Back | `options` |
|---|---|---|---|---|
| `BASIC` | Basic, simple, normal | question | answer | – |
| `CLOZE` | Cloze, gap, blank | text with gaps `{{…}}` | optional note | – |
| `INPUT` | Input, typing, write | question | expected answer | other accepted answers |
| `CHOICE` | Choice, mc, buttons | question | the **correct** answer | the wrong answers |
| `MATCH` | Match, matching, pairs | the task | the pairs `A => 1 \| B => 2` | additional wrong targets |

The German names (Standard, Lückentext, Eingabe, Auswahl, Zuordnung …) are kept as aliases so files written for earlier versions still import.

- **Gaps:** `{{Berlin}}`, `{{c1::Berlin}}`, `{{c1::Berlin::hint}}` or `{{Berlin::hint}}` (Anki compatible).
  Closed they show `[ … ]` or `[ hint ]`, revealed the answer in bold.
- **Pairs:** `HTTP => 80 | HTTPS => 443`, one pair per `|` section or per line. `->`, `→` and `=` work instead
  of `=>`. The target's **text** is compared (normalised as for `INPUT`), not its position – two terms may
  share a target. Only the targets are shuffled, stably through the card ID.
- **Inference:** with no type column, or an unknown value, the content decides – gaps in the front give
  `CLOZE`, two or more pairs with an unambiguous arrow (`=>`, `->`, `→`) in the back give `MATCH`, filled
  `options` give `CHOICE`, everything else `BASIC`. A lone `=` infers **no** match card, it shows up far too
  often in ordinary answers (`E = mc²`). That is what lets all types be mixed within one file. Unknown values
  and match cards without pairs (which become `BASIC`) are counted and shown in the import preview rather
  than being swallowed silently.
- **Checking `INPUT`:** case, punctuation and repeated whitespace do not matter; swapped neighbouring
  characters and one typo per eight characters count as “partly”, as does an answer contained in the expected
  one. The result is a **suggestion**: the user always does the grading and can overrule it.
- **Grading:** `BASIC` keeps its four levels. Every type that takes an answer is graded on three levels –
  correct / partly / wrong – and mapped onto the scheduler (Good / Hard / Again). The suggested grade gets an
  outline, Ⓐ confirms it.
- **CSV export** writes `Front;Back;Tags;Type;Options`, and the import reads it back.

## Markdown and images
- GFM: bold/italics, lists, tables, code blocks, quotes, links.
- Image syntax: `![](image.png)`, `![](<my image.png>)`, `![](my image.png)` (tolerate spaces), `[[image.png]]` / `![[image.png]]` (Obsidian), `<img src="image.png">` (Anki).
- An image is resolved by its file name (no path, NFC, lower case) and looked up in `Media`. Missing images are shown as a red placeholder reading “Missing image: name”.
- Tapping an image opens a full screen view with pinch zoom.

## Spaced repetition
- Encapsulate the scheduler behind a `Scheduler` interface so FSRS becomes possible later.
- Version 1 is an SM-2 variant **identical to the reference** (`schedule()` in the HTML):
  learning steps 1 min / 10 min, ease starting at 2.5, minimum 1.3, ratings 1 Again / 2 Hard / 3 Good / 4 Easy.
- The grading buttons show the next interval (“10 min”, “4 d”).
- Cards that take an answer are graded on three levels (see “Card types”); the scheduler itself stays four-level.
- There is a limit for new cards per day, globally (20 by default) and optionally per deck.
- **Studying on four levels:** one deck, a **free selection of several decks** (combined study), a whole topic (all its decks mixed) and “everything due”.
- **Cram mode** without grading: shuffled, filterable by tag. Progress stays untouched. Works with a multi-selection as well.

## Combined study (several decks at once) – required
- **Multi-selection:**
  - In every deck list, a long press turns on selection mode. It offers checkboxes and “All in topic”.
  - On top of that there is a dedicated **“Combined study”** screen: a tree of topics with their decks. A whole topic is ticked with one tap, individual decks likewise. There is a search field too.
  - A bar at the bottom shows the totals “x decks · new / learning / due” and the buttons “Study” / “Cram”.
- **Session:**
  - All due cards of the selection are mixed into **one** queue. Order: learning cards first, then reviews and new cards interleaved.
  - The mix mode is selectable: “fully shuffled” or “one deck after another”.
  - The new-card limit applies per deck (or globally). Optionally an overall session cap for new cards can be set as well.
  - In the study view, a small chip above each card says which deck and topic it comes from (in the topic's color).
- **Saved selections (“presets”):**
  - A selection can be saved under a name, e.g. “Webdev exam” = 6 decks from 2 topics. Presets appear on the home screen with their own counters and start with one tap.
  - Data model: `StudyPreset(id, name, createdAt, shuffleMode, newLimit?)` plus `StudyPresetDeck(presetId, deckId)` (n:m, CASCADE). A deleted deck disappears from the preset automatically.
- **Performance:** the queue for 50+ decks and 10,000+ cards is built in SQL (`WHERE deckId IN (...)`, `dueAt` index), not filtered in memory. Target: start in under 300 ms.
- Tests: a unit test for the queue logic (limits, mix modes, requeueing of learning cards) and a Room test with 20 selected decks from 10 topics.

## UI
- **Home:** the list of topics (color, emoji, totals new/learning/due), with the saved presets and the buttons “Combined study” and “Study everything due” above it.
- **Topic:** the list of decks with counters, a search field and sorting (name / due / recent). A long press opens a menu for renaming, moving and deleting.
- **Deck:** statistics, “Study”, “Cram”, the card list with search, reset progress.
- **Study view:** the card, a tap flips it, below it 4 grading buttons and a progress bar. In landscape the front and back sit side by side with the buttons on the right.
  Depending on the card type, the answer goes between the card and the grading: buttons for `CHOICE`, a text field with “Check” for `INPUT`. For `CLOZE` the gaps fill in on flipping.
  For `MATCH` the terms with their target are on top and the targets below: tap a term, pick a target (or use the digits 1–9); “Check” grades every pair and shows the solution next to each wrong one.
- **Statistics:** reviews per day (from `ReviewLog`) and a forecast of the cards due over the next 7 days.
- **Settings:** new cards per day, dark/light/system, card font size, backup.
- Dark mode following the system, dynamic color optional.

## Controller / keyboard (handheld)
Intercepted through `onPreviewKeyEvent` or `dispatchKeyEvent`:
| Key | Before revealing | After revealing |
|---|---|---|
| A / Enter / space | reveal | Good |
| B | – | Again |
| X | – | Hard |
| Y | – | Easy |
| D-pad | – | move focus between buttons |
| Select / Back / Esc | back | back |
| Keys 1–4 | – | grade |
In lists, D-pad navigation works with a visible focus outline.

## Backup
- Export as a ZIP through SAF: `backup.json` (every table) plus `media/`. An import replaces everything, with a confirmation dialog first.
- On top of that: export a single topic or deck back out as CSV (UTF-8 with BOM, semicolons).
- Leave `android:allowBackup` on, and include `media/` and the database in `dataExtractionRules`.

## Quality and tests
- Unit tests: the CSV parser (quotes, line breaks, every delimiter, UTF-8 / UTF-8 BOM / Windows-1252 with „Größe – € „Test““), header detection, the scheduler (every state transition), image name resolution.
- Instrumented/Room test: **a seed of 10 topics × 20 decks × 100 cards**. The home list and a topic have to load in under 300 ms.
- Debug menu (debug build only): “Generate test data (10×20)”.
- Test data under `sample-data/` with a folder structure, special characters and images.

## How to work
- Work in small phases. After each phase, `./gradlew assembleDebug testDebugUnitTest` has to pass, then a short summary and a git commit.
- No placeholder implementations. Errors are shown to the user as a snackbar; nothing is swallowed silently.
- All interface text in English, in `strings.xml`.
