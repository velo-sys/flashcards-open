# Generating cards with an AI

The block below is a ready-made brief for ChatGPT, Claude, Gemini and friends. Copy it, append your own
material, send it off – out comes a CSV this app imports directly.

**How to use it:**

1. Copy the prompt (everything between the rules).
2. At the very bottom, drop in your material: a text, a script, a vocabulary list, bullet points, the
   contents of a PDF, or just a topic.
3. Save the answer as a text file with the extension `.csv`, encoded as **UTF-8**.
4. Copy the file onto your phone and import it in the app. The preview shows which card types were
   recognised before anything is saved – that is where you spot a sloppy answer immediately.

---

## The prompt

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

---

## Variants

Append one of these lines to the prompt:

| Goal | Addition |
|---|---|
| Vocabulary | “Only type Input. Front = the word in my language, Back = the foreign word, Options = other valid translations.” |
| Exam prep | “Only type Choice with four wrong answers each, at the level of a professional certification exam.” |
| Drilling definitions | “Only type Cloze, exactly one gap per card, on the central technical term.” |
| Dates, categories | “Only type Match with five pairs each.” |
| Lots of material | “Create 60 cards and output them in three blocks of 20.” |

## When the result does not fit

- **Columns shifted:** the AI forgot to escape a semicolon inside the text. Ask again: “Wrap every field
  containing a semicolon in quotes.”
- **Type column empty:** no big deal – the app recognises cloze, match and choice from the content as
  well. Only “Input” strictly needs the column.
- **Everything in one column:** the file was saved with commas instead of semicolons. Either ask again or
  add `#separator:Comma` as the first line.
- **Garbled characters instead of accents:** the file was not saved as UTF-8. Pick “UTF-8” in your editor
  and save it again.

The import preview counts how many rows ended up in which type and how many were unknown or incomplete –
a glance at it before saving spares you the rework.

## Rather write them yourself?

Works just as well: a text editor or a spreadsheet, five columns, saved as CSV. Every rule, column name,
special case and pitfall is in **[CSV-FORMAT.md](CSV-FORMAT.md)**. Sample files to crib from live under
[`sample-data/`](sample-data).
