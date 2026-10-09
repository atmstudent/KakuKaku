# KakuKaku 画²

KakuKaku is a Japanese OCR dictionary for Android. It floats over any app, reads the Japanese text in a box you place on the screen, and shows dictionary entries for the words in it: manga, games, web pages, anything you can see. Select text in any app and share it to KakuKaku to look it up without the capture box.

KakuKaku is a **continuation of [Kaku](https://github.com/0xbad1d3a5/Kaku)** by 0xbad1d3a5, published as a fork of it under the same BSD 3-Clause licence. It is not endorsed by Kaku's author.

Yes, it's vibecoded 😞 

## What is new compared with Kaku

- Works on current Android.
- ML Kit Japanese text recognition instead of Tesseract.
- Material 3 design with light and dark mode and dynamic colour.
- Dictionary import in Yomitan format.
- Optional pitch accent from a dictionary you import yourself.
- Optional word frequency from a dictionary you import yourself (JPDB or BCCWJ, for example): the more common word is shown first.
- Furigana is stripped automatically from scanned and from selected text (switch on the home screen).
- Words are found by their kana reading (とても finds 迚も).
- JMdict and KANJIDIC2 were updated.

## Install

Download `KakuKaku-<version>.apk` from the [Releases](../../releases) page and open it (Android asks you to allow installs from your browser or file manager). It needs Android 8.0 or newer on an arm64 phone or tablet. 

If Android will not let you turn on "Display over other apps" (the switch is greyed out), it is restricting an app installed from outside a store. Open **Settings → Apps → KakuKaku**, tap the **⋮** menu in the top-right corner, choose **Allow restricted settings**, then try again. 

Press **Start**, allow drawing over other apps and screen capture, drag the box over Japanese text and double-tap it. Tap a character in the popup to look up from there.

## Privacy

KakuKaku works offline. It asks for no network permission, collects nothing and sends nothing anywhere. The screen capture is only analysed on the device and only when you double-tap the box. It needs "display over other apps" to show the box and popup, and screen capture to read the text under the box.

## Licence and credits

BSD 3-Clause, see [LICENSE](LICENSE). © 2016 0xbad1d3a5, © 2026 atmstudent. The deinflection rules (`app/src/main/assets/deinflect.dat`) come from Rikaichan by Jonathan Zarate. Rikaichan is licensed under the GNU GPL (version 2 or later), and that file is not covered by the BSD licence above: it keeps its original terms. Text recognition is Google ML Kit. The same notices are in the app under *About and licences*.
