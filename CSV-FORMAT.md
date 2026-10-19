# CSV format – how each question format is stored

All five question formats live in **one** table with up to five columns. You can mix them freely in a
single file; whatever the type column does not state, the app infers from the content.

```csv
Front;Back;Tags;Type;Options
```

---

## Cheat sheet

| I want to ask … | `Type` | `Back` | `Options` |
|---|---|---|---|
| flip the card, read the answer | `Basic` or empty | the answer | – |
| text with **gaps** | `Cloze` or empty ¹ | a note (optional) | – |
| **type** the answer | `Input` ² | the expected answer | other accepted ones, `\|` separated |
| **quiz** with buttons | `Choice` or empty ¹ | the **correct** answer | the **wrong** ones, `\|` separated |
| **match** pairs | `Match` or empty ¹ | `A => 1 \| B => 2 \| C => 3` | wrong targets, `\|` separated |

¹ Leaving it empty works, the app recognises it from the content – see “Automatic detection”.
² The only type that **cannot** be inferred: without an entry in the type column it turns into a choice
card or a basic card.

**Ready to copy** – one file holding all five formats:

```csv
Front;Back;Tags;Type;Options
What does DNS stand for?;Domain Name System;networking;Basic;
The capital of {{Germany}} is {{Berlin}}.;Capital again since 1990.;geography;Cloze;
What is the capital of France?;Paris;geography;Input;Paris in France|the city of Paris
Which device works on OSI layer 3?;Router;networking;Choice;Switch|Hub|Repeater
Match the protocols to their ports.;HTTP => 80 | HTTPS => 443 | SSH => 22;networking;Match;25|3389
```

Save the file as `.csv` (UTF-8, semicolons), copy it onto the phone and import it in the app. More on
that under “File, encoding, delimiter” and “Several decks at once”.

Having an AI write your cards works too – there is a ready-made prompt in [AI-PROMPT.md](AI-PROMPT.md).

---

| Column | Required | Content |
|---|---|---|
| `Front` | yes | The front. Markdown and images allowed. **The card's identity** – see “Re-import”. |
| `Back` | yes¹ | The back. Depending on the type: answer, note, expected input or the pairs. |
| `Tags` | no | Keywords, separated by spaces or commas (`networking osi`). |
| `Type` | no | Question format (see below). Empty = infer from the content. |
| `Options` | no | Depending on the type: other accepted answers, wrong answers or wrong targets – `\|` separated. |

¹ A row is only dropped when **both front and back** are empty.

**Header row:** it is recognised as soon as a known name for front or back appears:

| Column | recognised names |
|---|---|
| Front | `front` `question` `term` `prompt` `vorderseite` `frage` `begriff` `vorne` |
| Back | `back` `answer` `definition` `solution` `rückseite` `rueckseite` `antwort` `hinten` |
| Tags | `tags` `tag` `category` `topic` `kategorie` `thema` |
| Type | `type` `cardtype` `kind` `typ` `kartentyp` `art` |
| Options | `options` `option` `choices` `distractors` `wrong` `optionen` `auswahl` `alternativen` `distraktoren` `falsch` |

The German names are still accepted so files written for earlier versions keep working. The column order
is up to you. **Without** a header row the layout is fixed: column 1 = front, 2 = back, 3 = tags – there
is no `Type` and no `Options` then, as those are only found through the header. So anyone who wants types
or options needs a header row.

---

## 1. Basic – question, flip, answer

`Type`: `Basic` · `simple` · `normal` · `card` — or simply left empty.

```csv
Front;Back;Tags;Type;Options
What does **DNS** stand for?;Domain Name System;networking;Basic;
What does HTTP stand for?;Hypertext Transfer Protocol;networking;;
```

`Options` stays empty. This is the only type graded on four levels (Again / Hard / Good / Easy), every
other one is graded on three (wrong / partly / correct).

## 2. Cloze – gaps in the front

`Type`: `Cloze` · `gap` · `blank` · `fill-in`

The gaps are written in **double curly braces in the front**, Anki notation included:

| Notation | Closed it shows | Revealed |
|---|---|---|
| `{{Berlin}}` | `[ … ]` | **Berlin** |
| `{{c1::Berlin}}` | `[ … ]` | **Berlin** |
| `{{Berlin::capital}}` | `[ capital ]` | **Berlin** |
| `{{c1::Berlin::capital}}` | `[ capital ]` | **Berlin** |

```csv
Front;Back;Tags;Type;Options
The capital of {{c1::Germany::country}} is {{Berlin}}.;Berlin has been the capital again since 1990.;geography;Cloze;
Resistance is measured in {{ohms}}.;Unit: Ω;physics;;
```

Several gaps per card are fine; they are revealed **together**. The `Back` is optional and serves as a
note – it appears below the card. `Options` stays empty.

## 3. Input – the answer is typed in

`Type`: `Input` · `typing` · `type-in` · `write`

```csv
Front;Back;Tags;Type;Options
What is the capital of France?;Paris;geography;Input;
Chemical formula of water?;H2O;chemistry;Input;water|dihydrogen monoxide
```

- `Back` = the expected answer.
- `Options` = **other accepted** answers, `|` separated.
- When checking, case, punctuation and repeated whitespace do not matter. One typo per eight characters
  (swapped neighbouring letters included) and an answer contained in the expected one count as “partly”.
  The result is a **suggestion** – you can always grade it yourself.

## 4. Choice (multiple choice) – answer by button

`Type`: `Choice` · `mc` · `Multiple Choice` · `buttons`

```csv
Front;Back;Tags;Type;Options
Which device works on OSI layer 3?;Router;networking;Choice;Switch|Hub|Repeater
What color is the sky?;Blue;nature;;Red|Green|Yellow
```

- `Back` = the **correct** answer.
- `Options` = the **wrong** answers, `|` separated. As many as you like; the first nine buttons can also
  be reached with the keys 1–9.
- Empty entries, duplicates and a correct answer accidentally listed among them are dropped.
- The order is shuffled, but stays the same for a given card.

## 5. Match – connect pairs

`Type`: `Match` · `matching` · `pairs` · `connect`

The pairs live **in the back column**, term and target separated by `=>`, the pairs by `|`:

```csv
Front;Back;Tags;Type;Options
Match the protocols to their ports.;HTTP => 80 | HTTPS => 443 | SSH => 22;networking;Match;25|3389
Which capital belongs to which country?;Berlin -> Germany | Paris -> France;geography;;
```

- `Front` = the task (“Match … to …”).
- `Back` = the pairs. `->`, `→` and `=` work instead of `=>`. If the field is quoted, **each line** may
  hold one pair:
  ```csv
  "Match them";"Red => warm
  Blue => cold
  Green => neutral";colors;Match;
  ```
- `Options` = additional **wrong targets** (distractors), `|` separated. Optional.
- While studying, the terms are on top and the shuffled targets below: tap a term, pick a target – or use
  the digits 1–9 directly. The target's **text** is checked (as tolerantly as for “Input”), not its
  position, so two terms may share one target.
- At least two pairs make sense. If the back holds **no** pair at all, the card becomes a basic card – the
  import preview tells you how often that happened.

---

## Automatic detection (type column empty or unknown)

With no type given, the content decides – in this order:

| Check | Result |
|---|---|
| gaps `{{…}}` in the front | **Cloze** |
| two or more pairs with `=>`, `->` or `→` in the back | **Match** |
| `Options` is filled | **Choice** |
| otherwise | **Basic** |

Two things are deliberately **not** detected:

- **A lone `=`** does not make a match card – `E = mc²` is an ordinary answer. If you want `=` as your
  arrow, write `Match` into the type column.
- **Input cards** cannot be inferred (a question with alternatives looks exactly like a choice card).
  That one needs the type column.

Spelling in the type column is forgiving: case, spaces, `-`, `_` and `.` do not matter, so
`Multiple Choice`, `multiple-choice` and `multiplechoice` are the same thing. An **unknown** value
(`Quizshow`) is not swallowed silently: the card is inferred from its content and the import preview
counts the row.

## Writing cards by hand

**Text editor** (simplest and least error-prone): one line per card, fields separated by `;`, saved as
`.csv` in UTF-8. Any plain-text editor can do this.

**Excel or LibreOffice Calc:** create five columns, header in row 1, then
*Save as → CSV UTF-8 (comma delimited)*. Two pitfalls:

- A cell that **starts** with `=` is treated as a formula by Excel. The pairs of a match card
  (`HTTP => 80`) are safe, as they start with text. If it does happen: put a `'` in front, the apostrophe
  does not end up in the file.
- Line breaks inside a cell (Alt+Enter) are allowed but only export correctly when quoted – Excel does
  that by itself. Alternatively type `\n`, which the app converts.

**In the app:** the card editor behind “+” covers **all five types**. Pick the type at the top and the
fields adapt: cloze gets an “Insert gap” button, input and choice get a list for `Options`, match gets a
list of term and target. What it saves is exactly the format a CSV would deliver, so export and re-import
stay compatible. One thing to keep in mind: if you import the same CSV again later, it overwrites those
edits – whoever corrects cards in the app should carry the change over into the CSV (or stop re-importing
that deck).

## File, encoding, delimiter

- **Recommended: UTF-8 with BOM and semicolons** – Excel opens that correctly and the app detects it
  reliably. In Excel: *Save as → CSV UTF-8 (comma delimited)*.
- UTF-16 (LE/BE with BOM) is detected as well and, as a fallback, Windows-1252 (Excel ANSI). The app then
  normalises to Unicode NFC – `ä ö ü ß € „ “ – Ω` and emoji all work.
- The **delimiter** is detected automatically; `;`, tab, `,` and `|` are tried and whichever yields the
  most consistent column count across the first rows wins (on a tie, the one with more columns). Because
  `|` is among the candidates, a file **without** a header row in which every answer contains the same
  number of `|` can be split wrongly. A header row, quotes around the field or the `#separator` line all
  prevent that.
- You can force it with an Anki header as the **first** line; further `#key:value` lines are ignored:
  ```csv
  #separator:Semicolon
  Front;Back;Tags;Type;Options
  ```
- **Quoting** follows RFC 4180: put fields containing a delimiter, a line break or a quote into `"…"`,
  and double a `"` inside the text to `""`:
  ```csv
  "Größe; „x“";"line 1
  line ""2""";t;;
  ```
- Instead of real line breaks a **literal `\n`** works inside a field – which keeps Excel exports
  readable: `answer 1\nanswer 2`.
- Empty rows are skipped, whitespace around a field is trimmed.

## Markdown and images

On both sides of a card: bold, italics, lists, tables, code, quotes, links. Images are resolved by
**file name** (no path, case insensitive) – the image files reach the app through the folder or ZIP
import, a single CSV carries no images:

```csv
Front;Back;Tags;Type;Options
What does ![](osi.png) show?;The **OSI model** with its 7 layers;networking;;
```

Recognised are `![](image.png)`, `![](<my image.png>)`, `[[image.png]]`, `![[image.png]]` and
`<img src="image.png">`. If the file is missing, the card reads “Missing image: name”.

## Re-import – what keeps your progress

Cards are recognised by their **front** (hashed, NFC-normalised, trimmed). Which means:

- Back, tags, type, options and order can be changed freely – the card keeps its study progress and the
  import reports it as “changed”.
- Change the **front** and it is a new card: the old one is removed (along with its progress) and the new
  one starts from zero. So fix typos in a front inside the app rather than in the CSV.
- Cards missing from the file are deleted on re-import – a dialog asks first (“x new, y changed,
  z removed”).
- Two identical fronts in one file are allowed, they are told apart internally.

## Several decks at once

A folder (or ZIP) with this structure, then **Import folder** in the app: subfolders become topics, CSV
files become decks, and images from any subfolder are picked up.

```
Flashcards/
  Networking/             ← topic
    OSI-model.csv         ← deck
    Subnetting.csv
    images/osi.png        ← images
  Math/
    Derivatives.csv
```

## Export

The app writes exactly this format back: `Front;Back;Tags;Type;Options` (UTF-8 with BOM, semicolons), and
when exporting a whole topic it adds `Deck` as a sixth column. So an export can be edited and imported
again straight away.

## Example holding all five formats

Shipped as `sample-data/Flashcards/Card-types/All-types.csv`:

```csv
Front;Back;Tags;Type;Options
The capital of {{c1::Germany::country}} is {{Berlin}}.;Berlin has been the capital again since 1990.;geography;Cloze;
What is the capital of France?;Paris;geography;Input;Paris, France
Which device works on OSI layer 3?;Router;networking;Choice;Switch|Hub|Repeater
What does **DNS** stand for?;Domain Name System;networking;Basic;
Resistance is measured in {{ohms}}.;Unit: Ω;physics;;
Match the protocols to their ports.;HTTP => 80 | HTTPS => 443 | SSH => 22;networking;Match;25|3389
Which capital belongs to which country?;Germany => Berlin | France => Paris | Italy => Rome;geography;;
```

The last two rows show both: a match card with a type column and distractors, and a match card
recognised from its content alone.
