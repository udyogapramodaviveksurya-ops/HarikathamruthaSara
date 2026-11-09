package com.example.harikathamruthasara.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.TimeZone

data class VerseStat(val listens: Int = 0, val bestScore: Int = 0, val bestFullScore: Int = 0, val recites: Int = 0) {
    val stars: Int get() = when {
        bestFullScore >= 90 -> 3
        bestScore >= 70 -> 2
        listens >= 10 -> 1
        else -> 0
    }
}

data class Level(val minPoints: Int, val emoji: String, val kannada: String, val english: String)

val LEVELS = listOf(
    Level(0, "🌱", "ಶ್ರವಣ", "Listener"),
    Level(100, "🌿", "ಅಭ್ಯಾಸಿ", "Learner"),
    Level(300, "🪴", "ಪಠಣ", "Reciter"),
    Level(700, "🌸", "ಗಾಯನ", "Singer"),
    Level(1500, "🪔", "ಭಕ್ತಿ", "Devotee"),
    Level(3000, "🌕", "ಜ್ಞಾನ", "Knower"),
    Level(6000, "🙏", "ಹರಿದಾಸಿ", "Haridasi"),
)

data class Badge(val id: String, val emoji: String, val title: String, val hint: String)

val BADGES = listOf(
    Badge("first_listen", "👣", "First steps", "Listen to your first verse"),
    Badge("first_recite", "🎤", "First recitation", "Recite a line to the app"),
    Badge("perfect", "💯", "Perfect!", "Score 95% or more in a recitation"),
    Badge("streak3", "🔥", "3-day streak", "Practise 3 days in a row"),
    Badge("streak7", "🪔", "One week", "Practise 7 days in a row"),
    Badge("streak30", "🌕", "One month", "Practise 30 days in a row"),
    Badge("japa108", "📿", "108 repeats", "Listen to 108 repeats in one day"),
    Badge("early", "🌅", "Early bird", "Practise before 7 in the morning"),
    Badge("goal", "🎯", "Goal reached", "Reach your daily goal"),
    Badge("quiz10", "🧠", "Sharp memory", "10 correct answers in a row in Games"),
    Badge("letters", "🔤", "Akshara", "Get 30 letters right in the letter game"),
    Badge("sandhi1", "🌺", "Mangalacharana", "Get 2★ on every verse of Mangalacharana Sandhi"),
    Badge("master10", "🏵️", "10 verses", "Get 3★ on 10 verses"),
    Badge("master50", "👑", "50 verses", "Get 3★ on 50 verses"),
)

sealed class Reward {
    data class BadgeEarned(val badge: Badge) : Reward()
    data class LevelUp(val level: Level) : Reward()
    data class GoalMet(val minutes: Int) : Reward()
}

data class ProgressSnapshot(
    val points: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val todaySeconds: Int = 0,
    val todayRepeats: Int = 0,
    val history: Map<Long, Int> = emptyMap(),
    val badges: Set<String> = emptySet(),
    val verses: Map<String, VerseStat> = emptyMap(),
    val lastSandhi: Int = 1,
    val lastVerse: Int = 0,
    val quizRun: Int = 0,
    val lettersRight: Int = 0,
) {
    val level: Level get() = LEVELS.last { points >= it.minPoints }
    val nextLevel: Level? get() = LEVELS.firstOrNull { it.minPoints > points }
    fun stat(key: String) = verses[key] ?: VerseStat()
    val mastered: Int get() = verses.values.count { it.stars == 3 }
}

fun today(): Long {
    val now = System.currentTimeMillis()
    return (now + TimeZone.getDefault().getOffset(now)) / 86_400_000L
}

class ProgressStore(private val context: Context, private val settings: SettingsStore) {
    private val file = File(context.filesDir, "progress.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var saveJob: Job? = null

    private val _state = MutableStateFlow(load())
    val state: StateFlow<ProgressSnapshot> = _state.asStateFlow()
    private val _rewards = MutableSharedFlow<Reward>(extraBufferCapacity = 16)
    val rewards: SharedFlow<Reward> = _rewards

    private var lastActiveDay: Long = loadLastDay()
    private var goalMetDay: Long = -1

    init { rollDay() }

    // ---------- events ----------

    fun addListen(verseKey: String, seconds: Int) = mutate { s ->
        val st = s.stat(verseKey)
        val v = s.verses + (verseKey to st.copy(listens = st.listens + 1))
        s.copy(points = s.points + 1, verses = v, todayRepeats = s.todayRepeats + 1)
            .withSeconds(seconds).also { award("first_listen") }
            .also { if (it.todayRepeats >= 108) award("japa108") }
    }

    fun addSeconds(seconds: Int) = mutate { it.withSeconds(seconds) }

    fun recordRecite(verseKey: String, score: Int, fullVerse: Boolean) = mutate { s ->
        val st = s.stat(verseKey)
        val nst = st.copy(
            recites = st.recites + 1,
            bestScore = maxOf(st.bestScore, score),
            bestFullScore = if (fullVerse) maxOf(st.bestFullScore, score) else st.bestFullScore,
        )
        val pts = when { score >= 90 -> 10; score >= 70 -> 5; score >= 40 -> 2; else -> 1 }
        award("first_recite")
        if (score >= 95) award("perfect")
        s.copy(points = s.points + pts, verses = s.verses + (verseKey to nst)).withSeconds(5)
    }

    fun recordQuiz(correct: Boolean) = mutate { s ->
        val run = if (correct) s.quizRun + 1 else 0
        if (run >= 10) award("quiz10")
        s.copy(points = s.points + if (correct) 3 else 0, quizRun = run)
    }

    fun recordLetter(correct: Boolean) = mutate { s ->
        val n = s.lettersRight + if (correct) 1 else 0
        if (n >= 30) award("letters")
        s.copy(points = s.points + if (correct) 1 else 0, lettersRight = n)
    }

    fun setPosition(sandhi: Int, verse: Int) = synchronized(lock) {
        if (_state.value.lastSandhi != sandhi || _state.value.lastVerse != verse) posAt = System.currentTimeMillis()
        _state.value = _state.value.copy(lastSandhi = sandhi, lastVerse = verse)
        scheduleSave()
    }

    fun reset() {
        synchronized(lock) {
            _state.value = ProgressSnapshot()
            lastActiveDay = -1
            scheduleSave()
        }
    }

    // ---------- internals ----------

    private val pendingAwards = mutableListOf<String>()
    private fun award(id: String) { pendingAwards += id }

    private fun ProgressSnapshot.withSeconds(sec: Int): ProgressSnapshot {
        val d = today()
        val h = history + (d to ((history[d] ?: 0) + sec))
        return copy(history = h.filterKeys { it > d - 90 }, todaySeconds = todaySeconds + sec)
    }

    private fun mutate(f: (ProgressSnapshot) -> ProgressSnapshot) {
        val rewards = mutableListOf<Reward>()
        synchronized(lock) {
            rollDay()
            val before = _state.value
            pendingAwards.clear()
            var s = f(before)
            s = touchStreak(s)
            // derived badges
            if (s.streak >= 3) award("streak3")
            if (s.streak >= 7) award("streak7")
            if (s.streak >= 30) award("streak30")
            if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < 7 && s.todaySeconds > 60) award("early")
            if (s.mastered >= 10) award("master10")
            if (s.mastered >= 50) award("master50")
            val goal = settings.value.dailyGoalMinutes * 60
            if (s.todaySeconds >= goal && goalMetDay != today()) {
                goalMetDay = today()
                if (before.todaySeconds < goal) {
                    s = s.copy(points = s.points + 20)
                    rewards += Reward.GoalMet(settings.value.dailyGoalMinutes)
                    award("goal")
                }
            }
            val newBadges = pendingAwards.filter { it !in s.badges }.toSet()
            if (newBadges.isNotEmpty()) {
                s = s.copy(badges = s.badges + newBadges)
                BADGES.filter { it.id in newBadges }.forEach { rewards += Reward.BadgeEarned(it) }
            }
            if (s.level != before.level && s.points > before.points) rewards += Reward.LevelUp(s.level)
            _state.value = s
            scheduleSave()
        }
        rewards.forEach { _rewards.tryEmit(it) }
    }

    fun checkSandhiBadge(sandhi: Sandhi) {
        if (sandhi.id != 1) return
        val s = _state.value
        if (sandhi.verses.filter { !it.isRefrain }.all { s.stat(it.key).stars >= 2 }) mutate { award("sandhi1"); it }
    }

    /** New calendar day: reset today's counters and break the streak if a day was skipped. */
    private fun rollDay() {
        val d = today()
        val s = _state.value
        if (lastActiveDay in 0 until d - 1 && s.streak > 0) _state.value = s.copy(streak = 0)
        val todaySec = s.history[d] ?: 0
        if (s.todaySeconds != todaySec) _state.value = _state.value.copy(todaySeconds = todaySec, todayRepeats = if (todaySec == 0) 0 else s.todayRepeats)
    }

    private fun touchStreak(s: ProgressSnapshot): ProgressSnapshot {
        val d = today()
        if (lastActiveDay == d) return s
        val streak = if (lastActiveDay == d - 1) s.streak + 1 else 1
        lastActiveDay = d
        return s.copy(streak = streak, bestStreak = maxOf(s.bestStreak, streak), todayRepeats = if (s.history[d] == null) 0 else s.todayRepeats)
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch { delay(1500); save() }
    }

    private fun save() {
        val tmp = File(file.parentFile, "progress.json.tmp")
        tmp.writeText(toJson(_state.value, lastActiveDay).toString())
        tmp.renameTo(file)
    }

    /** All progress as JSON — what is stored on the phone and in the cloud. */
    fun exportJson(): String = synchronized(lock) { toJson(_state.value, lastActiveDay).toString() }

    /**
     * Merges progress from another device (the cloud copy) into this one, never losing anything: the higher of
     * points, stars, scores and counts, every badge, every day's minutes, the more recent streak and position.
     * Merging the same data twice changes nothing, so sync can't loop.
     */
    fun mergeFrom(json: String) {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return
        val r = parse(o)
        val rDay = o.optLong("lastDay", -1)
        val rPos = o.optLong("posAt", 0)
        synchronized(lock) {
            val l = _state.value
            val hist = (l.history.keys + r.history.keys).associateWith { maxOf(l.history[it] ?: 0, r.history[it] ?: 0) }
            val verses = (l.verses.keys + r.verses.keys).associateWith { k ->
                val a = l.stat(k); val b = r.stat(k)
                VerseStat(maxOf(a.listens, b.listens), maxOf(a.bestScore, b.bestScore), maxOf(a.bestFullScore, b.bestFullScore), maxOf(a.recites, b.recites))
            }
            val (streak, day) = when {
                rDay > lastActiveDay -> r.streak to rDay
                rDay == lastActiveDay -> maxOf(l.streak, r.streak) to lastActiveDay
                else -> l.streak to lastActiveDay
            }
            val remotePos = rPos > posAt
            val m = l.copy(
                points = maxOf(l.points, r.points), streak = streak, bestStreak = maxOf(l.bestStreak, r.bestStreak, streak),
                history = hist, todaySeconds = hist[today()] ?: 0, todayRepeats = maxOf(l.todayRepeats, r.todayRepeats),
                badges = l.badges + r.badges, verses = verses,
                lastSandhi = if (remotePos) r.lastSandhi else l.lastSandhi, lastVerse = if (remotePos) r.lastVerse else l.lastVerse,
                lettersRight = maxOf(l.lettersRight, r.lettersRight),
            )
            lastActiveDay = day
            if (remotePos) posAt = rPos
            if (m != l) { _state.value = m; scheduleSave() }
        }
    }

    /** when the "continue" position last changed (the newer device's position wins in a merge) */
    private var posAt: Long = runCatching { JSONObject(file.readText()).optLong("posAt", 0) }.getOrDefault(0)

    private fun toJson(s: ProgressSnapshot, lastDay: Long): JSONObject {
        val o = JSONObject()
        o.put("points", s.points).put("streak", s.streak).put("best", s.bestStreak)
            .put("todayRepeats", s.todayRepeats).put("lastDay", lastDay).put("posAt", posAt)
            .put("lastSandhi", s.lastSandhi).put("lastVerse", s.lastVerse)
            .put("quizRun", s.quizRun).put("letters", s.lettersRight)
        o.put("badges", JSONArray(s.badges.toList()))
        o.put("history", JSONObject().apply { s.history.forEach { (k, v) -> put(k.toString(), v) } })
        o.put("verses", JSONObject().apply {
            s.verses.forEach { (k, v) -> put(k, JSONArray(listOf(v.listens, v.bestScore, v.bestFullScore, v.recites))) }
        })
        return o
    }

    private fun loadLastDay(): Long = runCatching { JSONObject(file.readText()).optLong("lastDay", -1) }.getOrDefault(-1)

    private fun load(): ProgressSnapshot = runCatching { parse(JSONObject(file.readText())) }.getOrDefault(ProgressSnapshot())

    private fun parse(o: JSONObject): ProgressSnapshot = run {
        val b = o.optJSONArray("badges") ?: JSONArray()
        val h = o.optJSONObject("history") ?: JSONObject()
        val vs = o.optJSONObject("verses") ?: JSONObject()
        val hist = h.keys().asSequence().associate { it.toLong() to h.getInt(it) }
        ProgressSnapshot(
            points = o.optInt("points"), streak = o.optInt("streak"), bestStreak = o.optInt("best"),
            todaySeconds = hist[today()] ?: 0, todayRepeats = o.optInt("todayRepeats"),
            history = hist,
            badges = (0 until b.length()).map { b.getString(it) }.toSet(),
            verses = vs.keys().asSequence().associateWith { k ->
                val a = vs.getJSONArray(k); VerseStat(a.getInt(0), a.getInt(1), a.getInt(2), a.getInt(3))
            },
            lastSandhi = o.optInt("lastSandhi", 1), lastVerse = o.optInt("lastVerse", 0),
            quizRun = o.optInt("quizRun"), lettersRight = o.optInt("letters"),
        )
    }
}
