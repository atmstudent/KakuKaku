# Changelog

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
