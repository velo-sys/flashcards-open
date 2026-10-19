# Walkthrough: building this flashcard app with Claude Code + Android Studio

## 0. What you need
- **Android Studio** (current stable release) including the SDK and a JDK – Studio ships both
- **Claude Code** installed locally
- **git**
- An Android device with **USB debugging** enabled (or the emulator)
- From this repo: `CLAUDE.md` (the specification) and `reference/flashcards.html` (the reference prototype)

## 1. Install Claude Code
- macOS / Linux / WSL:
  ```bash
  curl -fsSL https://claude.ai/install.sh | bash
  ```
- Windows (PowerShell):
  ```powershell
  irm https://claude.ai/install.ps1 | iex
  ```
- Check with `claude --version`; on the first run of `claude`, sign in with your Claude account

## 2. Create an empty project in Android Studio
*Why Studio first: this way the Gradle wrapper, the SDK paths and `local.properties` are right from the start.*
- **New Project → Empty Activity** (that is the Compose template)
- Name: `Flashcards Open`, package: e.g. `de.flashcards.open`
- Language: Kotlin, Minimum SDK: **API 26**, build configuration language: **Kotlin DSL**
- Run **Sync** and **Run** on the device once, so you know the toolchain works
- In the project folder:
  ```bash
  git init && git add -A && git commit -m "Empty Compose project"
  mkdir reference
  # CLAUDE.md into the project root, flashcards.html into reference/
  ```

## 3. Start Claude Code
```bash
cd ~/AndroidStudioProjects/flashcards-open
claude
```
- Claude Code picks up `CLAUDE.md` on its own
- Android Studio can stay open. After changes to Gradle files Studio offers “Sync Now” – click it.
- Gradle needs `JAVA_HOME`. If `./gradlew` complains, point it at the JBR shipped with Android Studio:
  - Linux: `export JAVA_HOME=/opt/android-studio/jbr` (adjust the path if needed)
  - macOS: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`

## 4. Prompts – phase by phase
Enter one phase at a time, check the result briefly (run it on the device from Studio), then move on.

**Phase 1 – plan**
> Read CLAUDE.md and reference/flashcards.html. Draft an implementation plan in phases, with the package structure and a list of dependencies including their current stable versions. Do not implement anything yet.

**Phase 2 – foundation**
> Implement the “data model” phase: version catalog, Room entities (Topic, Deck, Card, ReviewLog, Media), DAOs, repository, the navigation skeleton. A Room test seeding 10 topics × 20 decks × 100 cards. The build and the tests have to pass, then commit.

**Phase 3 – CSV import**
> Implement the CSV parser and the single-file import with a preview as described in CLAUDE.md. Unit tests for every encoding and delimiter. Create sample-data/ with accented characters, multi-line fields and images.

**Phase 4 – managing topics and decks**
> Build the home (topics), topic (decks) and deck screens including create/rename/move/delete/sort and the debug menu entry “Generate test data (10×20)”.

**Phase 5 – studying**
> Implement the scheduler interface plus SM-2 identical to the reference, the study view with Markdown and images, studying per deck/topic/“everything due”, and cram mode. Unit tests for the scheduler.

**Phase 5b – combined study (required)**
> Implement the “combined study” section of CLAUDE.md in full: selecting several decks across topics, a mixed session with an origin chip, mix modes, and saved presets on the home screen. Tests for the queue logic and a Room test with 20 decks from 10 topics.

**Phase 6 – bulk import**
> Folder import (subfolder = topic, CSV = deck, images → Media) and ZIP import with progress and cancellation.

**Phase 7 – handheld**
> Controller and keyboard bindings as per the table, D-pad focus in every list, the landscape layout of the study view.

**Phase 8 – backup and polish**
> ZIP backup export/import, CSV export per topic and deck, the statistics screen, settings, dark mode. Verify at the end: no INTERNET permission in the manifest.

Tips:
- When something is off, describe it concretely: “in landscape at 1920×1080 the buttons overlap”, ideally with a screenshot (drag and drop the image into Claude Code)
- `/clear` between large phases keeps the context lean; CLAUDE.md is loaded again anyway
- Error messages from Studio (the build window or Logcat) can simply be pasted in

## 5. Building it and getting it onto the device
- **Testing:** click **Run ▶** in Studio with the device selected at the top
- **Building an APK for the handheld:**
  - **Build → Generate Signed App Bundle / APK → APK**
  - Create a new keystore and **keep it safe**: later updates have to be signed with the same key, otherwise a reinstall loses the study progress
  - Variant: `release`
- **Installing**, either way:
  - over USB: `adb install -r app-release.apk`
  - or copy the APK onto the device and open it there (allow “install unknown apps” for your file manager)
- **Updating:** the same steps with a higher `versionCode`. The data is kept.

## 6. Organising your cards
```
Flashcards/
  Networking/
    OSI-model.csv
    Subnetting.csv
    images/osi.png
  Webdev/
    HTML-basics.csv
  ...
```
- Copy the folder onto the device, then pick **Import folder** in the app. Topics and decks are created automatically.
- CSV: `Front;Back;Tags`. Markdown is allowed, images through `![](osi.png)`.
- Card types through two more columns: `Front;Back;Tags;Type;Options`
  - `Cloze` – gaps in the front as `{{Berlin}}`, with a hint as `{{c1::Berlin::capital}}`
  - `Input` – the answer is typed in and checked; `Options` holds further accepted answers, `|` separated
  - `Choice` – answer by button; the correct one goes into `Back`, the wrong ones into `Options`, `|` separated
  - `Match` – pairs in `Back` as `HTTP => 80 | HTTPS => 443 | SSH => 22`; `Options` holds additional
    wrong targets. `->`, `→` and `=` work instead of `=>`, and each line may hold one pair.
  - `Basic` or empty – an ordinary card. Without a type column the app recognises gaps, pairs and choices
    from the content – so all types may be mixed in **one** file.
  - Those four types are graded on three levels: correct / partly / wrong.
  - **Every format in detail, with examples and pitfalls: [CSV-FORMAT.md](CSV-FORMAT.md)**
- To change cards, edit the CSV on the computer and import it again. The progress is kept.

## Sources
- Claude Code – quickstart: https://code.claude.com/docs/en/quickstart
- Claude Code – setup/installation: https://code.claude.com/docs/en/setup
- Claude Code – installation troubleshooting: https://code.claude.com/docs/en/troubleshoot-install
- Android Studio download: https://developer.android.com/studio
- Creating a Compose project: https://developer.android.com/develop/ui/compose/setup
- Running an app on a device / USB debugging: https://developer.android.com/studio/run/device
- Signing an app (keystore): https://developer.android.com/studio/publish/app-signing
- Room: https://developer.android.com/training/data-storage/room
- Storage Access Framework: https://developer.android.com/training/data-storage/shared/documents-files
- Controller input: https://developer.android.com/develop/ui/views/touch-and-input/game-controllers/controller-input
- Markdown renderer (Compose): https://github.com/mikepenz/multiplatform-markdown-renderer
