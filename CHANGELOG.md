# Changelog

## 3.0.0

- Send terms to AnkiDroid: an icon next to each definition adds a card. In the Anki menu you choose the deck and note type, and write what goes into each field as text and markers (like Yomitan): {sentence}, {expression}, {reading}, {furigana}, {furigana-plain}, {cloze-body}, {glossary}, {pitch-accent-positions}, {writing}, {writing-furigana}, {writing-furigana-plain}. The info button explains each marker; a dropdown in every field adds them. Duplicates (same first field) are not added twice. Needs AnkiDroid and your permission to access it.
- Word list: a second icon next to each definition saves the term with all its information. The Word list screen (menu) shows and deletes saved terms (tap one to see everything saved), lets you choose which information is shown and exported, exports TSV or CSV, and copies all text.
- Tesseract, the text recognition of the old Kaku, can be chosen in Settings instead of ML Kit. It brings the Japanese language data with it, so the app grows to about 64 MB. It shows several choices per character; furigana is not stripped from its scans.
- Vertical text is read from the rightmost column to the left.
- A "Strip furigana" chip in the popup turns furigana stripping on or off at once, for the last scan or the shared text. It is the same setting as in Settings.
- Settings: text recognition engine, and switches for the Anki and word list icons.
- Fixed Pause sometimes leaving the notification saying "KakuKaku is running" with a Pause button.

## 2.0.0

- Notification with buttons: Instant mode, Filter and Pause. Pause leaves the notification with a Start button, so you can continue from there. The buttons use the system colours on Android 12 and newer.
- Instant mode scans whenever the capture box is released, for any box size.
- Furigana is stripped from scanned and from selected or shared text.
- Settings screen: strip furigana, strip line breaks (keep each break, which ends a word lookup), popup text size, popup at the top or bottom of the screen, and a light or dark popup.
- Dictionaries screen reworked: Dictionary, Word frequency and Pitch accent work the same way. Import several of each, choose one (or None for frequency and pitch accent), delete them. The explanations are behind the (i) buttons. A pitch accent or frequency dictionary imported earlier is kept and stays selected.
- Tutorial with annotated screenshots (menu: Tutorial).
- The definitions slide in only for the first selection.
- Fixed a crash when the edit window was opened for a character at the edge of the scanned image.

## 1.1.0

- Words are found by their reading too: とても finds 迚も, ネコ finds 猫. This also works in imported dictionaries.
- Much faster lookups (a few milliseconds instead of a few hundred). The first start after the update takes a few seconds longer while the search index is built.
- Word frequency: import a Yomitan frequency dictionary (JPDB or BCCWJ, for example) on the Dictionaries screen, and the more common word is shown first (行く before 生きる for いきました). Not bundled; the recommended file is JPDB v2.2 Frequency Kana from Kuuuube/yomitan-dictionaries.
- A hint explains "Allow restricted settings" when the overlay permission cannot be granted on a sideloaded install.
- The licence of `deinflect.dat` (Rikaichan, GPL v2+) is stated in the README and the About screen.

## 1.0.0

First release as KakuKaku, a continuation of Kaku (formerly Mojiscope and Kaku2).

- New name and app ID `io.github.atmstudent.kakukaku`: it installs as a separate app, and settings and imported dictionaries of earlier versions do not carry over.
- Pitch accent is no longer bundled. Import a Yomitan pitch accent dictionary on the Dictionaries screen.
- Tutorial removed (its videos showed the old app).
- About and licences screen with the version.
- No network permission.
- Signed with a new release key.
