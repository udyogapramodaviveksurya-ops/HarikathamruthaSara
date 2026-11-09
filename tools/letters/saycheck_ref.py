"""Reference (Python) version of speech/SayCheck.kt — the Kotlin must give the same verdicts (SayCheckTest fixtures).

  python saycheck_ref.py calibrate <refs.bin>      tune K / SLACK: templates = voice 0, learner = voice 1
"""
import sys, struct, collections
import numpy as np

CLIP = 15.0
LENGTH, CONS = "LENGTH", "CONS"

# usual mistakes: (letter, mistake, kind) — same table as SayCheck.kt
PAIRS = [("ಅ", "ಆ", LENGTH), ("ಇ", "ಈ", LENGTH), ("ಉ", "ಊ", LENGTH), ("ಎ", "ಏ", LENGTH), ("ಒ", "ಓ", LENGTH),
         ("ಐ", "ಏ", LENGTH), ("ಔ", "ಓ", LENGTH),
         ("ಿ", "ೀ", LENGTH), ("ು", "ೂ", LENGTH), ("ೆ", "ೇ", LENGTH), ("ೊ", "ೋ", LENGTH), ("ೈ", "ೇ", LENGTH), ("ೌ", "ೋ", LENGTH)]
PAIRS += [(a, b, CONS) for a, b in ["ಕಖ", "ಗಘ", "ಚಛ", "ಜಝ", "ಟಠ", "ಡಢ", "ತಥ", "ದಧ", "ಪಫ", "ಬಭ",
                                    "ಟತ", "ಠಥ", "ಡದ", "ಢಧ", "ಣನ", "ಳಲ", "ಶಸ", "ಷಸ", "ಶಷ", "ಙನ", "ಞನ", "ವಬ"]]
SWAP = collections.defaultdict(list)
for a, b, k in PAIRS: SWAP[a].append((b, k)); SWAP[b].append((a, k))
CONSONANT = {chr(c) for c in range(0x0C95, 0x0CBA)}


def mistakes(t):
    """[(mistake text, kind)] for a letter/syllable: each letter swapped for its usual mistake, short a said long."""
    out = []
    for i, c in enumerate(t):
        for b, k in SWAP.get(c, []): out.append((t[:i] + b + t[i + 1:], k))
    if t[-1] in CONSONANT: out.append((t + "ಾ", LENGTH))
    if t.endswith("ಾ"): out.append((t[:-1], LENGTH))
    return list(dict.fromkeys(out))


def load(path):
    """{text: [fingerprint per voice]} with log-probs as float32 [frames, 66]"""
    b = open(path, "rb").read(); assert b[:4] == b"SAY1"
    n, rl = struct.unpack_from("<IH", b, 4); o = 10 + rl
    refs = collections.defaultdict(dict)
    for _ in range(n):
        (tl,) = struct.unpack_from("<H", b, o); o += 2
        t = b[o:o + tl].decode(); o += tl
        v, fr = struct.unpack_from("<BH", b, o); o += 3
        refs[t][v] = -np.frombuffer(b, np.uint8, fr * 66, o).reshape(fr, 66).astype(np.float32) * CLIP / 255; o += fr * 66
    return refs


def fingerprint(lp):
    keep = np.exp(lp[:, 1]) < 0.95
    x = lp[keep] if keep.sum() >= 3 else lp
    return np.maximum(x, -CLIP)


def dtw(a, b):
    """mean symmetric-KL frame cost along the best alignment + a length-ratio penalty"""
    pa, pb = np.exp(a), np.exp(b)
    C = 0.5 * ((pa[:, None, :] - pb[None, :, :]) * (a[:, None, :] - b[None, :, :])).sum(-1)
    n, m = C.shape
    D = np.full((n + 1, m + 1), np.inf); D[0, 0] = 0
    for i in range(1, n + 1):
        for j in range(1, m + 1):
            D[i, j] = C[i - 1, j - 1] + min(D[i - 1, j], D[i, j - 1], D[i - 1, j - 1])
    return D[n, m] / (n + m) + 0.5 * abs(np.log(n / m))


def judge(her, target, cands, refs, kinds, K, SLACK, voices=None):
    """('ok'|'mistake'|'wrong', text it sounded like, kind)"""
    def d(x): return min(dtw(her, f) for v, f in refs[x].items() if voices is None or v in voices)
    dist = {x: d(x) for x in set(cands) | {target} if x in refs}
    dt = dist[target]
    closer = sorted((v, x) for x, v in dist.items() if x != target and v < dt)
    if len(closer) >= K: return "wrong", closer[0][1], None
    for m, k in mistakes(target):
        if k in kinds and m in dist and dist[m] * (1 + SLACK) < dt: return "mistake", m, k
    return "ok", None, None


def calibrate(path):
    import random
    refs = load(path); random.seed(5)
    words = [t for t in refs if len(t) >= 3 and t not in ("ಕ್ಷ", "ಜ್ಞ") and not any(t == c + s for c in CONSONANT for s in ["್ಷ"]) and len(t.replace("್", "")) >= 3]
    vowels = "ಅ ಆ ಇ ಈ ಉ ಊ ಋ ಎ ಏ ಐ ಒ ಓ ಔ ಅಂ ಅಃ".split()
    cons = "ಕ ಖ ಗ ಘ ಙ ಚ ಛ ಜ ಝ ಞ ಟ ಠ ಡ ಢ ಣ ತ ಥ ದ ಧ ನ ಪ ಫ ಬ ಭ ಮ ಯ ರ ಲ ವ ಶ ಷ ಸ ಹ ಳ ಕ್ಷ ಜ್ಞ".split()
    game = "ಕಾ ಗಿ ದು ಭೂ ಮೆ ನೇ ಹೊ ಶ್ರೀ ಕೃ ತ್ತ ಮಂ ವೈ ಧ್ಯ ಪ್ರ ಲ್ಲ ಳು ಜ್ಞಾ ಷ್ಣು ರ್ಮ".split()
    core = vowels + cons + game
    trials = []
    for t in core:
        trials.append(("correct", t, t))
        for m, k in mistakes(t):
            if m in refs: trials.append(("mistake:" + k, t, m))
        trials.append(("wrong", t, random.choice([x for x in core if x[0] != t[0]])))
    cache = {}
    for K in (3, 5, 7):
        for SLACK in (0.0, 0.1, 0.2):
            for name, kinds in (("GENTLE", ()), ("NORMAL", (LENGTH,)), ("STRICT", (LENGTH, CONS))):
                if not kinds and SLACK > 0: continue
                c = collections.defaultdict(lambda: [0, 0])
                for typ, t, said in trials:
                    cands = core + [m for m, _ in mistakes(t)]
                    key = (t, said)
                    if key not in cache:
                        her = refs[said][1]
                        cache[key] = {x: min(dtw(her, f) for v, f in refs[x].items() if v == 0) for x in set(cands) | {t} if x in refs}
                    dist = cache[key]; dt = dist[t]
                    closer = sum(1 for x, v in dist.items() if x != t and v < dt)
                    v = "wrong" if closer >= K else ("mistake" if any(k in kinds and m in dist and dist[m] * (1 + SLACK) < dt for m, k in mistakes(t)) else "ok")
                    c[typ][0] += v == "ok"; c[typ][1] += 1
                print(f"K={K} SLACK={SLACK:.1f} {name:6s} | " + "  ".join(f"{k}: {'pass' if k == 'correct' else 'caught'} {(a if k == 'correct' else n - a)}/{n}" for k, (a, n) in sorted(c.items())), flush=True)


CORE = ("ಅ ಆ ಇ ಈ ಉ ಊ ಋ ಎ ಏ ಐ ಒ ಓ ಔ ಅಂ ಅಃ ಕ ಖ ಗ ಘ ಙ ಚ ಛ ಜ ಝ ಞ ಟ ಠ ಡ ಢ ಣ ತ ಥ ದ ಧ ನ ಪ ಫ ಬ ಭ ಮ ಯ ರ ಲ ವ ಶ ಷ ಸ ಹ ಳ ಕ್ಷ ಜ್ಞ "
        "ಕಾ ಗಿ ದು ಭೂ ಮೆ ನೇ ಹೊ ಶ್ರೀ ಕೃ ತ್ತ ಮಂ ವೈ ಧ್ಯ ಪ್ರ ಲ್ಲ ಳು ಜ್ಞಾ ಷ್ಣು ರ್ಮ").split()   # = SAY_LETTERS in SayItTab.kt


def fixtures(path, words_file, out):
    """SayCheckTest fixtures: DTW numbers and verdicts, learner = voice 1, references = voice 0 only."""
    import re, random
    refs = load(path); random.seed(7)
    src = open(words_file).read()
    words = re.findall(r'"([^"]+)"', src.split("PRACTICE_WORDS = listOf(")[1].split(")")[0])
    v0 = {t: {0: f[0]} for t, f in refs.items()}
    lines = []
    for a, b in [("ಕ", "ಕ"), ("ಕ", "ಕಾ"), ("ಳ", "ಲ"), ("ಮಳೆ", "ಮಳೆ"), ("ನೀರು", "ಹಾಲು"), ("ಅ", "ಔ")]:
        lines.append(f"dtw\t{a}\t{b}\t{dtw(refs[a][1], refs[b][0]):.6f}")
    st = {"GENTLE": (), "NORMAL": (LENGTH,), "STRICT": (LENGTH, CONS)}
    trials = [(t, t) for t in random.sample(CORE, 12)] + [(t, m) for t in random.sample(CORE, 14) for m, _ in mistakes(t)[:1] if m in refs]
    for t, said in trials:
        her = fingerprint(refs[said][1])
        for name, kinds in st.items():
            v, x, k = judge(her, t, CORE + [m for m, _ in mistakes(t)], v0, kinds, 7, 0.1)
            lines.append(f"letter\t{t}\t{said}\t{name}\t{v}\t{x or ''}\t{k or ''}")
    for w in random.sample(words, 8):
        for said in (w, random.choice([o for o in words if o != w])):
            her = fingerprint(refs[said][1]); d = {x: dtw(her, v0[x][0]) for x in words}
            best = min(d, key=d.get)
            lines.append(f"word\t{w}\t{said}\t{'ok' if best == w else 'wrong'}\t{'' if best == w else best}")
    open(out, "w").write("\n".join(lines) + "\n"); print(len(lines), "fixtures →", out)


if __name__ == "__main__":
    if sys.argv[1] == "calibrate": calibrate(sys.argv[2])
    if sys.argv[1] == "fixtures": fixtures(sys.argv[2], sys.argv[3], sys.argv[4])
