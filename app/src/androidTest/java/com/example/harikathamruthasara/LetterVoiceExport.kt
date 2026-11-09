package com.example.harikathamruthasara

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.harikathamruthasara.ui.ALL_LETTER_SOUNDS
import com.example.harikathamruthasara.ui.PRACTICE_WORDS
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * NOT a real test: a tool. Speaks every Letters-screen sound with Google's Kannada voice into WAV files, which
 * tools/letters/make_letters.sh pulls, trims, normalises and bundles as app/src/main/assets/letters (the app itself
 * uses no phone voice). Skipped unless run with -e exportLetters true. Run it through make_letters.sh, not
 * connectedAndroidTest (that uninstalls the app afterwards and the WAVs go with it).
 * Output: /sdcard/Android/data/com.prathosh.hks/files/{letters_tts,words_tts}/<code points>.wav
 */
@RunWith(AndroidJUnit4::class)
class LetterVoiceExport {
    @Test
    fun export() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("exportLetters") == "true")
        // optional: -e voice <name> (another voice, e.g. to test the pronunciation check), -e out <dir prefix>
        val wantVoice = args.getString("voice"); val prefix = args.getString("out") ?: ""
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val base = ctx.getExternalFilesDir(null)
        val ready = CountDownLatch(1); var status = TextToSpeech.ERROR
        lateinit var tts: TextToSpeech
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            tts = TextToSpeech(ctx, { status = it; ready.countDown() }, "com.google.android.tts")
        }
        check(ready.await(30, TimeUnit.SECONDS) && status == TextToSpeech.SUCCESS) { "Google TTS did not start" }
        val kn = Locale("kn", "IN")
        val avail = tts.setLanguage(kn)
        check(avail >= TextToSpeech.LANG_AVAILABLE) { "Kannada voice not installed (setLanguage=$avail)" }
        val kv = tts.voices.orEmpty().filter { it.locale.language == "kn" }
        kv.forEach { Log.i("LetterVoiceExport", "available: ${it.name} network=${it.isNetworkConnectionRequired} q=${it.quality}") }
        // prefer an offline (local) Kannada voice
        (kv.firstOrNull { it.name == wantVoice } ?: kv.filter { !it.isNetworkConnectionRequired }.maxByOrNull { it.quality })
            ?.let { tts.voice = it; Log.i("LetterVoiceExport", "voice ${it.name}") }
        tts.setSpeechRate(0.8f)
        val done = mutableMapOf<String, CountDownLatch>()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) {}
            override fun onDone(id: String) { done[id]?.countDown() }
            @Deprecated("old") override fun onError(id: String) { done[id]?.countDown() }
            override fun onError(id: String, code: Int) { Log.w("LetterVoiceExport", "error $code for $id"); done[id]?.countDown() }
        })
        for ((dir, items) in listOf("letters_tts" to ALL_LETTER_SOUNDS, "words_tts" to PRACTICE_WORDS) +
                                  // -e extra a,b,c : extra texts (e.g. mispronounced words to test the check)
                                  listOfNotNull(args.getString("extra")?.let { "extra_tts" to it.split(",") })) {
          val out = File(base, prefix + dir).apply { mkdirs() }
          for (s in items) {
            val name = s.map { "%04x".format(it.code) }.joinToString("_")
            val f = File(out, "$name.wav")
            if (f.exists() && f.length() > 2000) continue
            val latch = CountDownLatch(1); done[name] = latch
            tts.synthesizeToFile(s, Bundle(), f, name)
            latch.await(20, TimeUnit.SECONDS)
          }
          Log.i("LetterVoiceExport", "wrote ${out.listFiles()?.size} files to $out")
        }
        tts.shutdown()
    }
}
