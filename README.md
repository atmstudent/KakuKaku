# KakuKaku 画²

KakuKaku is a Japanese OCR dictionary for Android. It floats over any app, reads the Japanese text in a box you place on the screen (ML Kit), and shows dictionary entries for the words in it: manga, games, web pages, anything you can see. Select text in any app and share it to KakuKaku to look it up without the capture box.

KakuKaku is a **continuation of [Kaku](https://github.com/0xbad1d3a5/Kaku)** by 0xbad1d3a5, published as a fork of it under the same BSD 3-Clause licence. It is not endorsed by Kaku's author. It used to be called Mojiscope and Kaku2 on the way here.

## What is new compared with Kaku

- Works on current Android (minSdk 26, targetSdk 36): MediaProjection consent, edge-to-edge, rotation, camera notch.
- ML Kit Japanese text recognition instead of Tesseract, including vertical text.
- Material 3 design with light and dark mode and dynamic colour.
- Dictionary import in Yomitan format (tested with JMdict; others such as Jitendex, JMnedict, KANJIDIC and Wiktionary should work), with background import and progress.
- Optional pitch accent from a dictionary you import yourself.
- Words are also found by their reading (とても finds 迚も).
- Popups from Share and "process text" without flashing a white window, and a landscape layout for the popup.
- JMdict and KANJIDIC2 bundled from October 2026.
- Much smaller download: the unused Kuromoji dictionary and the extra CPU libraries are gone (the release APK is arm64 only).
- No ads, rating prompts or store links. Instant mode and the image filter are off by default.

## Install

KakuKaku is not on any app store. Download `KakuKaku-<version>.apk` from the [Releases](../../releases) page and open it (Android asks you to allow installs from your browser or file manager). It needs Android 8.0 or newer on an arm64 phone or tablet. KakuKaku has its own app ID, so it installs next to Kaku, Mojiscope or Kaku2 and does not take over their settings or imported dictionaries.

Press **Start**, allow drawing over other apps and screen capture, drag the box over Japanese text and double-tap it. Tap a character in the popup to look up from there.

## Privacy

KakuKaku works offline. It asks for no network permission, collects nothing and sends nothing anywhere. The screen capture is only analysed on the device and only when you double-tap the box. It needs "display over other apps" to show the box and popup, and screen capture to read the text under the box.

## Building

```bash
export JAVA_HOME=/path/to/android-studio/jbr   # the system Java is not enough
./gradlew assembleDebug lintDebug testDebugUnitTest
```

A release build is signed with the key from `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`; not in the repository). Without that file `./gradlew assembleRelease` produces an unsigned APK. The bundled dictionary is a gitignored asset that was added with `git add -f`, see below.

## Licence and credits

BSD 3-Clause, see [LICENSE](LICENSE). © 2016 0xbad1d3a5, © 2026 atmstudent. The deinflection rules (`deinflect.dat`) come from Rikaichan by Jonathan Zarate. Text recognition is Google ML Kit. The same notices are in the app under *About and licences*.

## Dictionaries

KakuKaku ships with JMdict and KANJIDIC data from **October 2026**. You can import newer or additional dictionaries from the three-dot menu on the home screen, under **Dictionaries**. There you can also choose which dictionary is used for word lookups. Kanji information always comes from the built-in dictionary.

### Format

KakuKaku reads dictionaries in the **Yomitan (formerly Yomichan) format**: a single `.zip` file, which you import as it is (do not unpack it). The zip must contain, at its top level:

| File | Required | Used for |
|------|----------|----------|
| `index.json` | yes | Title and revision. Importing a dictionary with the same `title` as an installed one **replaces** it, which is how you update a dictionary. A date in square brackets at the end of the title, as in `JMdict [2026-10-03]`, is ignored for this comparison. |
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

KakuKaku itself works offline and never downloads anything: get the `.zip` with a browser, then import it from the Dictionaries screen.

### Rebuilding the bundled dictionary

The bundled database is generated from the official JMdict and KANJIDIC2 files. To update it:

1. Download and unpack `JMdict_e.gz` (<http://ftp.edrdg.org/pub/Nihongo/JMdict_e.gz>) and `kanjidic2.xml.gz` (<http://www.edrdg.org/kanjidic/kanjidic2.xml.gz>).
2. Run the generator, which takes about ten seconds and about 3 GB of memory (`-PgenerateDictionary` is what switches it on; the normal test run skips it):

   ```bash
   KAKUKAKU_JMDICT_XML=/path/to/JMdict_e \
   KAKUKAKU_KANJIDIC_XML=/path/to/kanjidic2.xml \
   KAKUKAKU_DB_OUT=/path/to/DB_KakuKakuDict-YYYY-MM-DD.db \
   ./gradlew testDebugUnitTest -PgenerateDictionary --tests io.github.atmstudent.kakukaku.GenerateDictionary
   ```

3. Put the result in `app/src/main/assets/`, replacing the old file, and set `JMDICT_DATABASE_NAME` in `Constants.kt` to the new file name. The name is the database's version: a changed name makes the app copy the new database and delete the old one.

## Pitch accent

KakuKaku does not include pitch accent data: the common pitch accent dictionaries are built from commercial dictionaries that cannot be redistributed. On the Dictionaries screen you can import a pitch accent dictionary in Yomitan format (a `.zip` with `term_meta_bank_N.json` files; see the [Yomitan wiki](https://yomitan.wiki/dictionaries/)). The accent is then shown after the reading, like `猫 (ねこ) [1]`. Check the licence of the dictionary you import.

JMdict and KANJIDIC are the property of the Electronic Dictionary Research and Development Group and are used under their [licence](https://www.edrdg.org/edrdg/licence.html).
