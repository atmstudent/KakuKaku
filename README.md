# KakuKaku 画²

KakuKaku is a Japanese OCR dictionary for Android. It floats over any app, reads the Japanese text in a box you place on the screen, and shows dictionary entries for the words in it: manga, games, web pages, anything you can see. Select text in any app and share it to KakuKaku to look it up without the capture box.

KakuKaku is a **continuation of [Kaku](https://github.com/0xbad1d3a5/Kaku)** by 0xbad1d3a5, published as a fork of it under the same BSD 3-Clause licence. It is not endorsed by Kaku's author.

## What is new compared with Kaku

- Works on current Android (minSdk 26, targetSdk 36): MediaProjection consent, edge-to-edge, rotation, camera notch.
- ML Kit Japanese text recognition instead of Tesseract, including vertical text.
- Material 3 design with light and dark mode and dynamic colour.
- Dictionary import in Yomitan format (tested with JMdict; others such as Jitendex, JMnedict, KANJIDIC and Wiktionary should work), with background import and progress.
- Optional pitch accent from a dictionary you import yourself.
- Words are also found by their kana reading (とても finds 迚も).
- JMdict and KANJIDIC2 bundled from October 2026.
- Much smaller download: the unused Kuromoji dictionary and the extra CPU libraries are gone (the release APK is arm64 only).

## Install

Download `KakuKaku-<version>.apk` from the [Releases](../../releases) page and open it (Android asks you to allow installs from your browser or file manager). It needs Android 8.0 or newer on an arm64 phone or tablet. 

Press **Start**, allow drawing over other apps and screen capture, drag the box over Japanese text and double-tap it. Tap a character in the popup to look up from there.

## Privacy

KakuKaku works offline. It asks for no network permission, collects nothing and sends nothing anywhere. The screen capture is only analysed on the device and only when you double-tap the box. It needs "display over other apps" to show the box and popup, and screen capture to read the text under the box.

## Licence and credits

BSD 3-Clause, see [LICENSE](LICENSE). © 2016 0xbad1d3a5, © 2026 atmstudent. The deinflection rules (`app/src/main/assets/deinflect.dat`) come from Rikaichan by Jonathan Zarate. Rikaichan is licensed under the GNU GPL (version 2 or later), and that file is not covered by the BSD licence above: it keeps its original terms. Text recognition is Google ML Kit. The same notices are in the app under *About and licences*.

## Dictionaries

KakuKaku ships with JMdict and KANJIDIC data from **October 2026**. You can import newer or additional dictionaries from the three-dot menu on the home screen, under **Dictionaries**. There you can also choose which dictionary is used for word lookups. Kanji information always comes from the built-in dictionary.
