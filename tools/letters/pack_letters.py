"""Google Kannada voice WAVs -> app assets/letters/*.m4a.

  trim silence, 40 ms lead-in, normalise loudness (peak 0.95) — and SHORTEN SHORT VOWELS:
  spoken alone, Google stretches a short vowel almost to a long one (ಕ 0.66 s vs ಕಾ 0.70 s; long/short voiced length
  only 1.16–1.30×, measured 2026-11-08), so ಕ sounded like ಕಾ. A short-vowel sound is cut (with a soft fade) so its
  voiced part is SHORT_RATIO of its long partner's (ಕ←ಕಾ, ಕಿ←ಕೀ, ಅ←ಆ …); without a partner clip (ತ್ತ, ಪ್ರ …) it
  keeps NO_PARTNER of its own length.

usage: python pack_letters.py <wav dir> <assets/letters dir> [--words]   (--words: no vowel shortening)
"""
import os, sys, subprocess, numpy as np, soundfile as sf

SHORT_RATIO = 0.55
NO_PARTNER = 0.65
CONS = {chr(c) for c in range(0x0C95, 0x0CBA)} | {"ೞ"}
LONG_OF = {"ಅ": "ಆ", "ಇ": "ಈ", "ಉ": "ಊ", "ಎ": "ಏ", "ಒ": "ಓ", "ಿ": "ೀ", "ು": "ೂ", "ೆ": "ೇ", "ೊ": "ೋ"}


def name2text(n): return "".join(chr(int(h, 16)) for h in n.split("_"))
def text2name(t): return "_".join("%04x" % ord(c) for c in t)


def long_partner(t):
    """(is it a short-vowel sound?, its long partner's text or None)"""
    if t in LONG_OF: return True, LONG_OF[t]                       # ಅ → ಆ
    if t[-1] in LONG_OF: return True, t[:-1] + LONG_OF[t[-1]]       # ಕಿ → ಕೀ
    if t[-1] in CONS: return True, t + "ಾ"                         # ಕ → ಕಾ, ತ್ತ → ತ್ತಾ (may not exist)
    if t in ("ಋ",) or t.endswith("ೃ"): return True, None
    return False, None


def frames(y, sr, hop=0.01):
    win = int(hop * sr)
    return win, np.array([np.sqrt(np.mean(y[i:i + win] ** 2)) for i in range(0, max(1, len(y) - win), win)])


def trim(y, sr):
    win, r = frames(y, sr)
    on = np.nonzero(r > max(0.003, 0.02 * r.max()))[0]
    return y[max(0, (on[0] - 2) * win):(on[-1] + 4) * win]


def voiced_span(y, sr):
    """(start, end) samples of the loud (voiced) part"""
    win, r = frames(y, sr)
    on = np.nonzero(r > 0.1 * r.max())[0]
    return on[0] * win, (on[-1] + 1) * win


def shorten(y, sr, target):
    """cut the end of the vowel so the voiced part lasts [target] samples, with a 40 ms fade"""
    a, b = voiced_span(y, sr)
    if b - a <= target: return y
    end = a + target; fo = int(0.04 * sr)
    out = y[:end + fo].copy(); out[-fo:] *= np.linspace(1, 0, fo) ** 2
    return out


def main():
    src, dst = sys.argv[1], sys.argv[2]; words = "--words" in sys.argv[3:]
    os.makedirs(dst, exist_ok=True)
    raw = {}
    for f in sorted(os.listdir(src)):
        if not f.endswith(".wav"): continue
        y, sr = sf.read(os.path.join(src, f), dtype="float32")
        raw[name2text(f[:-4])] = (trim(y.mean(1) if y.ndim > 1 else y, sr), sr)
    cut = 0
    for t, (y, sr) in raw.items():
        short, partner = (False, None) if words else long_partner(t)
        if short:
            a, b = voiced_span(y, sr)
            if partner in raw:
                pa, pb = voiced_span(*raw[partner]); target = int(SHORT_RATIO * (pb - pa))
            else:
                target = int(NO_PARTNER * (b - a))
            if b - a > target: y = shorten(y, sr, target); cut += 1
        y = y / max(1e-6, np.abs(y).max()) * 0.95
        fo = int(0.02 * sr); y[-fo:] *= np.linspace(1, 0, fo)
        y = np.concatenate([np.zeros(int(0.04 * sr), np.float32), y, np.zeros(int(0.03 * sr), np.float32)])
        p = subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(sr), "-ac", "1", "-i", "-",
                            "-c:a", "aac", "-b:a", "48k", os.path.join(dst, text2name(t) + ".m4a")],
                           input=y.astype("<f4").tobytes())
        assert p.returncode == 0, t
    print(f"{len(raw)} clips, {cut} short vowels shortened")


if __name__ == "__main__":
    main()
