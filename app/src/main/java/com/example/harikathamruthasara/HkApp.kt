package com.example.harikathamruthasara

import android.app.Application
import com.example.harikathamruthasara.audio.Player
import com.example.harikathamruthasara.data.Library
import com.example.harikathamruthasara.data.ProgressStore
import com.example.harikathamruthasara.data.Sandhi
import com.example.harikathamruthasara.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HkApp : Application() {
    lateinit var settings: SettingsStore; private set
    lateinit var progress: ProgressStore; private set
    lateinit var player: Player; private set
    /** the on-device listening model for Recite & check */
    lateinit var ear: com.example.harikathamruthasara.speech.EarStore; private set
    lateinit var vag: com.example.harikathamruthasara.audio.VagLibrary; private set
    /** the source-of-truth server: text + chant updates, problem reports */
    lateinit var source: com.example.harikathamruthasara.data.Source; private set
    /** sign-in (Google / email / phone / guest) and the cloud copy of progress + settings */
    lateinit var account: com.example.harikathamruthasara.account.Account; private set
    lateinit var sync: com.example.harikathamruthasara.account.CloudSync; private set

    private val _library = MutableStateFlow<List<Sandhi>?>(null)
    val library: StateFlow<List<Sandhi>?> = _library

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        progress = ProgressStore(this, settings)
        vag = com.example.harikathamruthasara.audio.VagLibrary(this)
        player = Player(this, settings, progress, vag)
        // updated chant packs: never swap one in while it is being played, and drop the old files afterwards
        vag.isBusy = { id -> player.state.value.let { it.playing && it.sandhi?.id == id } }
        vag.onReplaced = { if (!player.state.value.playing) player.clip.release() }
        source = com.example.harikathamruthasara.data.Source(this, settings, vag) { _library.value = Library.load(this) }
        ear = com.example.harikathamruthasara.speech.EarStore(this).also { it.source = { source.earLocation() } }
        source.check()
        val bg = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        bg.launch { _library.value = Library.load(this@HkApp) }

        account = com.example.harikathamruthasara.account.Account(this)
        sync = com.example.harikathamruthasara.account.CloudSync(this).also { it.attach() }

        // ---- notifications: say it in the shade only when the app isn't on screen
        val N = com.example.harikathamruthasara.notify.Notifier
        N.createChannels(this)
        N.schedule(this)
        runCatching { com.example.harikathamruthasara.notify.PushService.syncTopics(this) }
        bg.launch { progress.rewards.collect { r -> if (!N.inForeground()) N.reward(this@HkApp, r) } }
        bg.launch {
            var before = emptyMap<Int, com.example.harikathamruthasara.audio.DownloadState>()
            vag.downloads.collect { now ->
                // a new download started: keep it alive in the background
                if (now.any { (id, st) -> st is com.example.harikathamruthasara.audio.DownloadState.Running && before[id] !is com.example.harikathamruthasara.audio.DownloadState.Running })
                    kotlinx.coroutines.withContext(Dispatchers.Main) { com.example.harikathamruthasara.notify.DownloadService.start(this@HkApp) }
                for ((id, st) in before) {
                    if (st !is com.example.harikathamruthasara.audio.DownloadState.Running || N.inForeground()) continue
                    when {
                        now[id] is com.example.harikathamruthasara.audio.DownloadState.Failed -> N.downloadFailed(this@HkApp)
                        id !in now -> bg.launch {
                            // the pack is installed just after the download ends: wait for it
                            repeat(40) {
                                if (vag.isDownloaded(id)) {
                                    val title = _library.value?.firstOrNull { it.id == id }?.let { "$id. " + com.example.harikathamruthasara.data.Scripts.render(it.title, (settings.value.lang ?: com.example.harikathamruthasara.i18n.Lang.EN).script) } ?: "$id"
                                    N.downloadDone(this@HkApp, title); return@launch
                                }
                                kotlinx.coroutines.delay(250)
                            }
                        }
                    }
                }
                before = now
            }
        }
        bg.launch {
            var was: com.example.harikathamruthasara.speech.EarState = ear.state.value
            ear.state.collect { now ->
                val E = com.example.harikathamruthasara.speech.EarState.Downloading::class
                if (E.isInstance(now) && !E.isInstance(was))
                    kotlinx.coroutines.withContext(Dispatchers.Main) { com.example.harikathamruthasara.notify.DownloadService.start(this@HkApp) }
                if (E.isInstance(was) && !N.inForeground()) when (now) {
                    is com.example.harikathamruthasara.speech.EarState.Failed -> N.downloadFailed(this@HkApp)
                    com.example.harikathamruthasara.speech.EarState.Ready -> N.downloadDone(this@HkApp, com.example.harikathamruthasara.i18n.Strs(settings.value.lang ?: com.example.harikathamruthasara.i18n.Lang.EN).earName)
                    else -> {}
                }
                was = now
            }
        }
        bg.launch {
            var had = source.state.value.hasUpdates
            source.state.collect { st ->
                if (st.hasUpdates && !had && !N.inForeground()) N.updatesReady(this@HkApp, st.textUpdate, st.packUpdates)
                had = st.hasUpdates
            }
        }
        // topics follow the "Improvements & news" switch
        bg.launch { settings.flow.collect { runCatching { com.example.harikathamruthasara.notify.PushService.syncTopics(this@HkApp) } } }
    }
}
