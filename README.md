# Harikathamrutha Sara — Android app (client)

The **client** half. The text and the Vāgdhenu chant are made and corrected in the **server** project,
`../HarikathamruthaSaraServer` (see its README) — that is the source of truth.

What the app does with it (`data/Source.kt`):
- keeps local copies: the text (`files/text/hks.json`, falling back to the bundled `assets/hks.json`) and each
  downloaded sandhi's chant pack (`files/vag/NN/`, with its fingerprint in `rev.txt`);
- reads the server's catalog at start / when it comes back to the front (≤ every 15 min) or Settings → Check now;
- shows "✨ Improvements are ready" when the server has corrected text or better chant, and downloads it when the
  user taps Update (or by itself if Settings → Download improvements automatically is on); a playing sandhi is
  swapped only when playback stops;
- sends problem reports from the Learn screen (Options → ⚠️ Report a problem here) to the server.

Content address: `BuildConfig.SERVER_URL` = the Hugging Face dataset folder the server publishes to
(`…/harikathamrutha-vagdhenu/resolve/main/hks`, holding `catalog.json`); reports go to the address in the catalog.
For local testing set Settings → Source of truth to `http://10.0.2.2:8765/files` (this Mac, from the emulator).
Local/LAN addresses are reached without a proxy (`data/Net.kt`).

Testing happens on the **emulator** only.
