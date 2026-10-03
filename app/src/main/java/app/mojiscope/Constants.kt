@file:JvmName("Constants")

package app.mojiscope

// Thanks to the fact that SqliteOpenHelper.onUpgrade() doesn't work (due to multi-threading and getDao() being called before onUpgrade()),
// we version/upgrade the DBs by changing the name. Lol. Should probably fix this if this becomes an issue in the future.
const val JMDICT_DATABASE_NAME = "DB_MojiDict-2026-10-03.db"
const val SCREENSHOT_FOLDER_NAME = "screenshots"

const val DB_SPLIT_CHAR = "\ufffc"
const val DB_JMDICT_NAME = "JMDICT"
const val DB_KANJIDICT_NAME = "KANJIDICT"
const val DB_ENAMEDICT_NAME = "ENAMEDICT"

const val MOJI_PREF_FILE = "app.mojiscope"
const val MOJI_PREF_SELECTED_DICTIONARY = "SelectedDictionary"
const val MOJI_PREF_SHOW_HIDE = "ShowHide"
const val MOJI_PREF_IMAGE_FILTER = "ImageFilter"
const val MOJI_PREF_TEXT_DIRECTION = "TextDirection"
const val MOJI_PREF_INSTANT_MODE = "InstantMode"

const val EXTRA_PROJECTION_RESULT_CODE = "app.mojiscope.PROJECTION_RESULT_CODE"
const val EXTRA_PROJECTION_RESULT_INTENT = "app.mojiscope.PROJECTION_RESULT_INTENT"

const val WINDOW_CAPTURE = "WINDOW_CAPTURE"
const val WINDOW_INFO = "WINDOW_INFO"
const val WINDOW_EDIT = "WINDOW_EDIT"
const val WINDOW_INSTANT_KANJI = "WINDOW_INSTANT_KANJI"
const val WINDOW_KANJI_CHOICE = "WINDOW_KANJI_CHOICE"
const val WINDOW_HISTORY = "WINDOW_HISTORY"

const val MOJI_CHANNEL_ID = "moji_notification_channel_id"
const val MOJI_CHANNEL_NAME = "Show Mojiscope Notification"

const val REQUEST_SERVICE_TOGGLE_IMAGE_PREVIEW = 300
const val REQUEST_SERVICE_TOGGLE_PAGE_MODE = 400
const val REQUEST_SERVICE_TOGGLE_INSTANT_MODE = 500
const val REQUEST_SERVICE_SHUTDOWN = 600
const val REQUEST_SERVICE_TOGGLE_SHOW_HIDE = 700