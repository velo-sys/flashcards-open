# Third-party notices

The app's own source code is under the MIT license, see [LICENSE](LICENSE). The parts listed here are
**not** covered by it – each keeps its own license, and each notice is retained where the code sits.

## Bundled in this repository

`reference/flashcards.html` is a self-contained web prototype. It serves as the reference for behaviour
(CSV detection, Markdown, scheduling) and carries four minified libraries inside it:

| Library | Version | License | Project |
|---|---|---|---|
| Papa Parse | 5.7.0 | MIT | https://github.com/mholt/PapaParse |
| marked | 12.0.2 | MIT | https://github.com/markedjs/marked |
| DOMPurify | 3.4.16 | Apache-2.0 **or** MPL-2.0 | https://github.com/cure53/DOMPurify |
| regenerator-runtime | – | MIT | https://github.com/babel/babel |

`gradlew` and `gradlew.bat` are the Gradle wrapper scripts: Apache-2.0, copyright the original authors.
Their SPDX headers are intact.

## Shipped inside the APK

Every runtime dependency is under the **Apache License 2.0**:

- AndroidX: `core-ktx`, `activity-compose`, `lifecycle-*`, `navigation-compose`, `room-*`,
  `paging-compose`, `datastore-preferences`, `documentfile`
- Jetpack Compose (BOM) including Material 3 and the extended Material icons
- Kotlin standard library, `kotlinx-coroutines`, `kotlinx-serialization-json`
- [Coil 3](https://github.com/coil-kt/coil) and
  [multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer)

Apache-2.0 and MIT combine without friction, so the APK carries no copyleft obligations.

## Test-only, never shipped

These are `testImplementation` dependencies. They do not end up in any APK, so their terms do not reach
anyone installing the app: JUnit 4 (EPL-1.0), Robolectric (MIT), Mozilla Rhino (MPL-2.0),
`androidx.test` (Apache-2.0).

## Spaced repetition

The scheduler is an SM-2 variant. SM-2 was published by Piotr Woźniak as an algorithm; the
implementation here is written from scratch against the prototype in `reference/`. No code was taken
from Anki or any other SRS application.
