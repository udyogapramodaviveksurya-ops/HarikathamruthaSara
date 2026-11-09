#!/bin/zsh
# Re-make the Letters screen sounds (app/src/main/assets/letters) with Google's Kannada voice, on the EMULATOR.
# Needs Google TTS with the Kannada voice installed on emulator-5554. Steps: install app + test APK, run the
# LetterVoiceExport "test" (speaks every sound to WAV), pull, trim + normalise + encode (pack_letters.py).
set -e
cd "$(dirname "$0")/../.."
A=~/Library/Android/sdk/platform-tools/adb; D=$(mktemp -d)
./gradlew -q :app:installDebug :app:installDebugAndroidTest
$A -s emulator-5554 shell rm -rf /sdcard/Android/data/com.prathosh.hks/files/letters_tts
$A -s emulator-5554 shell am instrument -w -e class com.example.harikathamruthasara.LetterVoiceExport -e exportLetters true \
  com.prathosh.hks.test/androidx.test.runner.AndroidJUnitRunner
$A -s emulator-5554 pull /sdcard/Android/data/com.prathosh.hks/files/letters_tts "$D"
rm -rf app/src/main/assets/letters
/opt/anaconda3/bin/python tools/letters/pack_letters.py "$D/letters_tts" app/src/main/assets/letters
