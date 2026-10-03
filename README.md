Mojiscope (formerly Kaku): 文字 (もじ) - character, letter

Fork of [Kaku](https://github.com/0xbad1d3a5/Kaku), modernized for current Android (ML Kit OCR).

Mojiscope is a fast, powerful Japanese dictionary that stays on top of all your apps. It uses optical character recognition (OCR) technology to recognize kanji on the device screen for you (rather than the slowww tedious process of looking up individual characters manually), making it perfect for Japanese learners who want to study by reading raw manga, play untranslated games, and so on without the hassle of switching apps.

## Dictionaries

Mojiscope ships with JMdict and KANJIDIC data from **February 2019**. You can import newer or additional dictionaries from the three-dot menu on the home screen, under **Dictionaries**. There you can also choose which dictionary is used for word lookups. Kanji information always comes from the built-in dictionary.

### Format

Mojiscope reads dictionaries in the **Yomitan (formerly Yomichan) format**: a single `.zip` file, which you import as it is (do not unpack it). The zip must contain, at its top level:

| File | Required | Used for |
|------|----------|----------|
| `index.json` | yes | Title and revision. Importing a dictionary with the same `title` as an installed one **replaces** it, which is how you update a dictionary. |
| `term_bank_1.json`, `term_bank_2.json`, … | one of these two | Words: written form, reading, tags and definitions. Formats 1 to 3 are supported. Definitions given as text or as structured content are converted to plain text; images are ignored. |
| `kanji_bank_1.json`, … | one of these two | Single kanji with their on/kun readings and meanings. |

Everything else (tag banks, frequency and pitch-accent banks, media) is ignored. A dictionary that has only frequency or pitch-accent data has nothing to show and is rejected on import.

### How lookups use an imported dictionary

- Words are matched on their written form (the term). A kana-only word is found by its kana, but a word written with kanji is not found by typing only its reading.
- Conjugated forms are matched through the same deinflection rules as the built-in dictionary. This uses the `rules` field of the term banks (`v1`, `v5`, `adj-i`, `vk`, `vs`), so dictionaries that fill it in, such as JMdict, give the best results.
- Only one dictionary is used for word lookups at a time.

### Where to get dictionaries

The dictionaries listed on the [Yomitan wiki](https://yomitan.wiki/dictionaries/) work. Among them:

- **JMdict** (Japanese to English and other languages, rebuilt daily): <https://github.com/yomidevs/jmdict-yomitan/releases/latest>. For English, download `JMdict_english.zip`.
- **Jitendex** (Japanese to English, an improved JMdict): <https://github.com/stephenmk/Jitendex>, downloads at <https://jitendex.org/pages/downloads.html>.
- **JMnedict** (names and places) and **KANJIDIC** (kanji): <https://github.com/yomidevs/jmdict-yomitan>.
- **Wiktionary** in many languages: <https://yomidevs.github.io/wiktionary-to-yomitan/download/>.
- **CC-CEDICT** (Chinese to English): <https://github.com/MarvNC/cc-cedict-yomitan>.

Mojiscope itself works offline and never downloads anything: get the `.zip` with a browser, then import it from the Dictionaries screen.
