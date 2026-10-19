# Flashcards Open

<div align="center">

## 🤖 Vibecoded with Claude Code

[![Built with Claude Code](https://img.shields.io/badge/built%20with-Claude%20Code-D97757?style=for-the-badge)](https://claude.com/claude-code)

**This entire app was written by [Claude Code](https://claude.com/claude-code)** — the Kotlin, the Compose
interface, the Room layer, the hand-written CSV parser and all 118 unit tests — working from the
specification in **[CLAUDE.md](CLAUDE.md)**.

**[WALKTHROUGH.md](WALKTHROUGH.md)** retraces the whole build prompt by prompt, so you can do the same.

</div>

---

An Android app for studying with flashcards and spaced repetition – **fully offline**, no account, no
server, no `INTERNET` permission in the manifest. Cards arrive as CSV files, and your study progress
survives a re-import.

## Features

**Five question formats**, freely mixable within one CSV:

| Format | How it is asked |
|---|---|
| Basic | Flip the card, read the answer, grade on four levels |
| Cloze | `{{Berlin}}`, `{{c1::Berlin::hint}}` – Anki compatible |
| Input | Type the answer, matched leniently (typos, punctuation, case) |
| Choice | Multiple choice by button, order shuffled but stable |
| Match | Connect pairs, with optional distractors |

Everything but “Basic” is graded on three levels (correct / partly / wrong) and mapped onto the
scheduler. The app recognises the type from the content when the CSV has no type column.

**On top of that:**

- Hierarchy of **topics → decks → cards**, built for 200+ decks and 20,000+ cards (paging, flows, indices)
- **CSV import** with encoding and delimiter detection (UTF-8/UTF-16/Windows-1252, `;` `,` tab `|`),
  Anki header lines, and a preview before anything is saved
- **Folder and ZIP import** for many decks at once, images included
- **Re-import** matches cards through a hash of the front: changed cards keep their progress
- **Card editor** inside the app for all five types
- **Combined study**: any decks across topics in one mixed session, saveable as a “preset”
- **Spaced repetition** following SM-2, encapsulated behind a `Scheduler` interface
- **Markdown and images** on both sides of a card, Obsidian and Anki image syntax
- **Controller and keyboard support** (aimed at handhelds such as the Retroid or AYN Odin), portrait and
  landscape
- **Backup** as a ZIP through the Storage Access Framework, CSV export per deck or topic
- Dark mode, dynamic color, adjustable font size

## Install

A signed APK is attached to each [release](../../releases). Download it, open it on the phone and allow
installation from unknown sources once. Updating over an existing install keeps all your data, as long as
the APK comes from the same release line.

## Build

Requirements: Android Studio (current stable release) or the Android SDK plus JDK 17.

```bash
git clone <this-repo>
cd flashcards-open
./gradlew assembleDebug          # APK under app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # unit tests
./gradlew lintDebug              # Android Lint
```

If Gradle cannot find a JDK, the one shipped with Android Studio does the job:

```bash
export JAVA_HOME=/opt/android-studio/jbr          # Linux
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # macOS
```

Android Studio writes `local.properties` with the SDK path itself; that file does not belong in the repo.

### Signing a release (optional)

Create a `keystore.properties` in the project root (it is listed in `.gitignore`):

```properties
storeFile=/path/to/keystore.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Alternatively point the `KEYSTORE_PROPERTIES` environment variable at it. Without that file
`assembleRelease` builds unsigned – for development `assembleDebug` is enough.

### Your own application ID

If you publish the app under your own name, change `namespace` and `applicationId` in
`app/build.gradle.kts` (currently `de.flashcards.open`) and rename the package directories under
`app/src/*/java/` to match.

## Creating cards

Either straight in the app through “+” (all five types) or as a CSV:

```csv
Front;Back;Tags;Type;Options
What does DNS stand for?;Domain Name System;networking;Basic;
The capital of {{Germany}} is {{Berlin}}.;Capital again since 1990.;geography;Cloze;
What is the capital of France?;Paris;geography;Input;
Which device works on OSI layer 3?;Router;networking;Choice;Switch|Hub|Repeater
Match the protocols to their ports.;HTTP => 80 | HTTPS => 443 | SSH => 22;networking;Match;25|3389
```

Every detail – column names, automatic type detection, encoding, images, re-import – is in
**[CSV-FORMAT.md](CSV-FORMAT.md)**. Sample files live under [`sample-data/`](sample-data).

### Let an AI write your cards

Copy the block below into ChatGPT, Claude or Gemini, append your own material at the bottom, and you get
a CSV this app imports as it is.

**How to use it:**

1. Copy the prompt — the whole block, from the first line to the last.
2. At the very bottom, replace the placeholder with your material: a text, a script, a vocabulary list,
   bullet points, the contents of a PDF, or just a topic.
3. Save the answer as a text file with the extension `.csv`, encoded as **UTF-8**.
4. Copy the file onto your phone and import it. The preview shows which card types were recognised
   before anything is saved — that is where you spot a sloppy answer immediately.

```text
You create flashcards as a CSV file for a learning app. Follow this format exactly.

OUTPUT
- Reply with the CSV in a single code block and nothing else. No preamble, no explanation afterwards.
- The first line is always exactly:
  Front;Back;Tags;Type;Options
- After that, one line per card: five fields separated by semicolons.
- Every line has exactly four semicolons, even when the trailing fields stay empty.
- Encode as UTF-8 and spell out special characters as they are (é, ü, ß, €, –, “ ”).

FIELDS
- Front:   the front of the card. It must be unique; no two cards may share a front.
- Back:    the back, depending on the type (see below).
- Tags:    one to three keywords, separated by spaces, lower case.
- Type:    one of Basic, Cloze, Input, Choice, Match. Always fill this in.
- Options: depends on the type, entries separated by |. Otherwise leave it empty.

THE FIVE TYPES
1. Basic  – question and answer. Options empty.
   Example: What does DNS stand for?;Domain Name System;networking;Basic;

2. Cloze  – the gaps go into the FRONT, in double curly braces.
   One to three gaps per card. Optionally a hint after a second pair of colons.
   The Back column holds a short note or stays empty. Options empty.
   Example: The capital of {{Germany::country}} is {{Berlin}}.;Capital again since 1990.;geography;Cloze;

3. Input  – the answer is typed in. Back = the expected solution, as short as possible
   (one word or a number). Options holds further accepted spellings, separated by |.
   Example: Chemical formula of water?;H2O;chemistry;Input;water|dihydrogen monoxide

4. Choice – multiple choice. Back = the CORRECT answer,
   Options = three to five WRONG answers separated by |.
   The wrong ones must be plausible and come from the same category.
   Example: Which device works on OSI layer 3?;Router;networking;Choice;Switch|Hub|Repeater

5. Match  – connect pairs. Back = three to six pairs in the form
   term => target, the pairs separated by |. Options = optionally one or two
   additional wrong targets.
   Example: Match the protocols to their ports.;HTTP => 80 | HTTPS => 443 | SSH => 22;networking;Match;25|3389

SYNTAX RULES
- If a field contains a semicolon, a line break or a quotation mark,
  wrap the whole field in "double quotes" and double any inner " to "".
- No semicolons inside the pairs of a match card and none inside Options.
- Markdown is allowed: **bold**, *italics*, `code`, lists with - at the start of a line.
- For a line break inside a field, write \n.
- Do not include images.

CONTENT RULES
- One card tests exactly one thing. Two short cards beat one long one.
- Phrase questions unambiguously, without "which of the following" and without referring to a page,
  a chapter or a figure.
- No yes/no questions, and no cards whose answer is already contained in the question.
- Invent nothing: use only the material given to you.
- Mix the types: definitions as Basic or Input, enumerations and categories as Match,
  distinctions as Choice, mnemonics and formulas as Cloze.
  If I do not give you a number: create 20 cards, at least three of each type.

Here is the material:
<<< YOUR TEXT, YOUR BULLET POINTS OR YOUR TOPIC >>>
```

**Variants** — append one of these lines to the prompt:

| Goal | Addition |
|---|---|
| Vocabulary | “Only type Input. Front = the word in my language, Back = the foreign word, Options = other valid translations.” |
| Exam prep | “Only type Choice with four wrong answers each, at the level of a professional certification exam.” |
| Drilling definitions | “Only type Cloze, exactly one gap per card, on the central technical term.” |
| Dates, categories | “Only type Match with five pairs each.” |
| Lots of material | “Create 60 cards and output them in three blocks of 20.” |

**When the result does not fit:**

- **Columns shifted** — the AI forgot to escape a semicolon inside the text. Ask again: “Wrap every field
  containing a semicolon in quotes.”
- **Type column empty** — no big deal, the app recognises cloze, match and choice from the content as
  well. Only “Input” strictly needs the column.
- **Everything in one column** — the file was saved with commas instead of semicolons. Either ask again
  or add `#separator:Comma` as the very first line.
- **Garbled characters instead of accents** — the file was not saved as UTF-8. Pick “UTF-8” in your
  editor and save it again.

The import preview counts how many rows ended up in which type, and how many were unknown or incomplete.
A glance at it before saving spares you the rework.

The same brief as a standalone document lives in **[AI-PROMPT.md](AI-PROMPT.md)**.

## Under the hood

Kotlin · Jetpack Compose · Material 3 · Room · Coroutines/Flow · MVVM with repositories ·
Navigation Compose · a hand-written RFC 4180 CSV parser · minSdk 26

```
app/src/main/java/de/flashcards/open/
  data/       Room entities, DAOs, repositories, import/export, backup
  domain/     card types, CSV, scheduler, study queue, Markdown  ← pure logic, fully tested
  ui/         Compose screens, one package per area
```

The project's ground rules are in [CLAUDE.md](CLAUDE.md) – data model, CSV behaviour, scheduler, key
bindings. [WALKTHROUGH.md](WALKTHROUGH.md) describes how the project came about.

## Contributing

Pull requests are welcome. Two requests:

- `./gradlew assembleDebug testDebugUnitTest lintDebug` has to pass.
- Interface text belongs in `strings.xml`, not in the code.

## License

MIT, see [LICENSE](LICENSE).

The prototype under `reference/` bundles four third-party libraries, and the APK ships Apache-2.0
dependencies. What belongs to whom, and under which terms, is listed in
**[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)**.
