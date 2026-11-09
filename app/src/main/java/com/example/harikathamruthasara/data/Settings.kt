package com.example.harikathamruthasara.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What one repeat covers. SENTENCE (half padya, up to the ||) and VERSE (full padya) are the recommended units for
 * correct recitation; LINE and BUILDUP play single lines and are only for basic training. (A word-by-word unit was
 * removed: words are still heard by tapping them.)
 */
enum class StudyUnit { SENTENCE, VERSE, LINE, BUILDUP }

enum class GapMode { NONE, FIXED, ECHO }
/** BOTH = Kannada with the reader's script below; MINE = only the reader's own script. */
enum class ScriptMode { BOTH, KANNADA, MINE }
/** FESTIVAL = the app's own bright colours; WALLPAPER = Material You colours from the phone's wallpaper (Android 12+). */
enum class ColorStyle { FESTIVAL, WALLPAPER }
enum class DarkMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val unit: StudyUnit = StudyUnit.SENTENCE,
    /** 0 = repeat forever until stopped */
    val repeatCount: Int = 3,
    val gapMode: GapMode = GapMode.ECHO,
    val gapSeconds: Int = 3,
    val autoAdvance: Boolean = true,
    val scriptMode: ScriptMode = ScriptMode.BOTH,
    val tamilSuperscripts: Boolean = true,
    /** more reading scripts shown under every line, besides the language's own (e.g. Devanagari for a Tamil reader) */
    val alsoScripts: Set<Script> = emptySet(),
    val peekTamil: Boolean = false,
    val fontScale: Float = 1.0f,
    val dailyGoalMinutes: Int = 15,
    val reminderOn: Boolean = false,
    val reminderHour: Int = 14,
    val reminderMinute: Int = 0,
    val strictness: com.example.harikathamruthasara.speech.Strictness = com.example.harikathamruthasara.speech.Strictness.NORMAL,
    val speakCorrections: Boolean = true,
    val keepScreenOn: Boolean = true,
    /** Vāgdhenu chant playback speed */
    val speed: Float = 1.0f,
    /** the source-of-truth server (text + chant packs); "" = the build's default [com.example.harikathamruthasara.BuildConfig.SERVER_URL] */
    val serverUrl: String = "",
    /** download improved text/chant from the server by itself (otherwise the app shows a notice and waits) */
    val autoUpdate: Boolean = false,
    /** null until the user picks a language on first launch */
    val lang: com.example.harikathamruthasara.i18n.Lang? = null,
    val colorStyle: ColorStyle = ColorStyle.FESTIVAL,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    // notifications, by kind (the daily reminder is [reminderOn])
    val notifyStreak: Boolean = true,
    val notifyAchievements: Boolean = true,
    val notifyDownloads: Boolean = true,
    val notifyUpdates: Boolean = true,
    /** the sign-in / guest choice was offered once */
)

/** The server to use: the user's choice, else the build default. No trailing slash. */
val Settings.server: String get() = (serverUrl.ifBlank { com.example.harikathamruthasara.BuildConfig.SERVER_URL }).trimEnd('/')

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(read())
    val flow: StateFlow<Settings> = _flow.asStateFlow()
    val value: Settings get() = _flow.value

    fun update(f: (Settings) -> Settings) {
        val s = f(_flow.value)
        if (s == _flow.value) return
        _flow.value = s
        write(s)
        prefs.edit().putLong(CHANGED_AT, System.currentTimeMillis()).apply()
    }

    /** When the settings were last changed on this device (for cloud sync: the newer side wins). */
    val changedAt: Long get() = prefs.getLong(CHANGED_AT, 0)

    /** Every synced setting as JSON (typed). Device-only things — the server address — stay out. */
    fun exportJson(): String {
        val o = org.json.JSONObject()
        prefs.all.forEach { (k, v) ->
            if (k in LOCAL_ONLY) return@forEach
            when (v) {
                is Boolean -> o.put(k, org.json.JSONObject().put("b", v))
                is Int -> o.put(k, org.json.JSONObject().put("i", v))
                is Long -> o.put(k, org.json.JSONObject().put("l", v))
                is Float -> o.put(k, org.json.JSONObject().put("f", v.toDouble()))
                is String -> o.put(k, org.json.JSONObject().put("s", v))
            }
        }
        return o.toString()
    }

    /** Takes settings from the cloud (they are newer than this device's). */
    fun importJson(json: String, at: Long) {
        val o = runCatching { org.json.JSONObject(json) }.getOrNull() ?: return
        val e = prefs.edit()
        o.keys().forEach { k ->
            if (k in LOCAL_ONLY) return@forEach
            val v = o.optJSONObject(k) ?: return@forEach
            when {
                v.has("b") -> e.putBoolean(k, v.getBoolean("b"))
                v.has("i") -> e.putInt(k, v.getInt("i"))
                v.has("l") -> e.putLong(k, v.getLong("l"))
                v.has("f") -> e.putFloat(k, v.getDouble("f").toFloat())
                v.has("s") -> e.putString(k, v.getString("s"))
            }
        }
        e.putLong(CHANGED_AT, at).commit()
        _flow.value = read()
    }

    /** The account this device last synced settings with ("" = none): a new device takes the cloud's settings. */
    var syncedUid: String
        get() = prefs.getString(SYNCED_UID, "") ?: ""
        set(v) { prefs.edit().putString(SYNCED_UID, v).apply() }

    private companion object {
        const val CHANGED_AT = "changedAt"
        const val SYNCED_UID = "syncedUid"
        val LOCAL_ONLY = setOf(CHANGED_AT, SYNCED_UID, "serverUrl", "autoUpdate")
    }

    private fun read(): Settings {
        val d = Settings()
        return Settings(
            unit = enumOr(prefs.getString("unit", null), d.unit),
            repeatCount = prefs.getInt("repeat", d.repeatCount),
            gapMode = enumOr(prefs.getString("gapMode", null), d.gapMode),
            gapSeconds = prefs.getInt("gapSec", d.gapSeconds),
            autoAdvance = prefs.getBoolean("autoAdvance", d.autoAdvance),
            scriptMode = enumOr(prefs.getString("script", null), d.scriptMode),
            tamilSuperscripts = prefs.getBoolean("sup", d.tamilSuperscripts),
            alsoScripts = prefs.getString("alsoScripts", "").orEmpty().split(',').mapNotNull { n -> Script.entries.firstOrNull { it.name == n } }.toSet(),
            peekTamil = prefs.getBoolean("peek", d.peekTamil),
            fontScale = prefs.getFloat("font", d.fontScale),
            dailyGoalMinutes = prefs.getInt("goal", d.dailyGoalMinutes),
            reminderOn = prefs.getBoolean("remOn", d.reminderOn),
            reminderHour = prefs.getInt("remH", d.reminderHour),
            reminderMinute = prefs.getInt("remM", d.reminderMinute),
            // older builds had an on/off "lenient" switch: off = strict
            strictness = enumOr(prefs.getString("strictness", null),
                if (prefs.contains("lenient") && !prefs.getBoolean("lenient", true)) com.example.harikathamruthasara.speech.Strictness.STRICT else d.strictness),
            speakCorrections = prefs.getBoolean("speakFix", d.speakCorrections),
            keepScreenOn = prefs.getBoolean("screenOn", d.keepScreenOn),
            speed = prefs.getFloat("guruSpeed", d.speed),
            serverUrl = prefs.getString("serverUrl", d.serverUrl) ?: "",
            autoUpdate = prefs.getBoolean("autoUpdate", d.autoUpdate),
            lang = prefs.getString("lang", null)?.let { n -> com.example.harikathamruthasara.i18n.Lang.entries.firstOrNull { it.name == n } },
            colorStyle = enumOr(prefs.getString("colorStyle", null), d.colorStyle),
            darkMode = enumOr(prefs.getString("darkMode", null), d.darkMode),
            notifyStreak = prefs.getBoolean("nStreak", d.notifyStreak),
            notifyAchievements = prefs.getBoolean("nAchieve", d.notifyAchievements),
            notifyDownloads = prefs.getBoolean("nDownloads", d.notifyDownloads),
            notifyUpdates = prefs.getBoolean("nUpdates", d.notifyUpdates),
        )
    }

    private fun write(s: Settings) {
        prefs.edit()
            .putString("unit", s.unit.name).putInt("repeat", s.repeatCount)
            .putString("gapMode", s.gapMode.name).putInt("gapSec", s.gapSeconds)
            .putBoolean("autoAdvance", s.autoAdvance).putString("script", s.scriptMode.name)
            .putBoolean("sup", s.tamilSuperscripts).putString("alsoScripts", s.alsoScripts.joinToString(",") { it.name }).putBoolean("peek", s.peekTamil)
            .putFloat("font", s.fontScale).putInt("goal", s.dailyGoalMinutes)
            .putBoolean("remOn", s.reminderOn).putInt("remH", s.reminderHour)
            .putInt("remM", s.reminderMinute).putString("strictness", s.strictness.name)
            .putBoolean("speakFix", s.speakCorrections).putBoolean("screenOn", s.keepScreenOn)
            .putFloat("guruSpeed", s.speed)
            .putString("serverUrl", s.serverUrl).putBoolean("autoUpdate", s.autoUpdate)
            .putString("lang", s.lang?.name)
            .putString("colorStyle", s.colorStyle.name).putString("darkMode", s.darkMode.name)
            .putBoolean("nStreak", s.notifyStreak).putBoolean("nAchieve", s.notifyAchievements)
            .putBoolean("nDownloads", s.notifyDownloads).putBoolean("nUpdates", s.notifyUpdates)
            .apply()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, d: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: d
}
