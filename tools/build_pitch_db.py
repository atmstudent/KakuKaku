#!/usr/bin/env python3
"""Builds the bundled pitch accent database from a Yomitan pitch dictionary zip.

    tools/build_pitch_db.py <dictionary.zip> app/src/main/assets/DB_MojiPitch-YYYY-MM-DD.db

Readings are stored in hiragana so katakana and hiragana spellings match. A word with several
accents keeps them all, in the dictionary's order, as a comma separated list of downstep positions.
"""
import json
import sqlite3
import sys
import zipfile
from html import unescape


def to_hiragana(text):
    return "".join(chr(ord(c) - 0x60) if "ァ" <= c <= "ヶ" else c for c in text)


def main(source, target):
    pitches = {}
    with zipfile.ZipFile(source) as z:
        for name in sorted(z.namelist()):
            if "term_meta_bank" not in name:
                continue
            for term, mode, data in json.loads(z.read(name)):
                if mode != "pitch":
                    continue
                term = unescape(term)
                # A few rows are broken by markup that leaked into the term
                if not term or '"' in term:
                    continue
                reading = to_hiragana(data.get("reading") or term)
                positions = pitches.setdefault((term, reading), [])
                for p in data.get("pitches", []):
                    pos = p.get("position")
                    if isinstance(pos, int) and pos not in positions:
                        positions.append(pos)

    db = sqlite3.connect(target)
    db.execute("CREATE TABLE pitch (term TEXT NOT NULL, reading TEXT NOT NULL, positions TEXT NOT NULL)")
    db.executemany("INSERT INTO pitch VALUES (?, ?, ?)",
                   ((t, r, ",".join(map(str, p))) for (t, r), p in pitches.items() if p))
    db.execute("CREATE INDEX pitch_term ON pitch (term)")
    db.commit()
    db.execute("VACUUM")
    db.close()
    print(len(pitches), "entries")


main(*sys.argv[1:3])
