"""Reference "fingerprints" for Read Kannada → Say it (speech/SayCheck.kt).

For every letter/word clip, the ear's per-frame log-probabilities ([frames][66], the SAME int8 model the app downloads)
with the silent (blank) frames dropped, clipped at -15 and stored as one byte each. The app runs the ear on the learner's
attempt and compares it with these by DTW: is the voice closest to the right letter, or to a usual mistake / another
letter? Two voices per item (Google kn-in-x-knd = the one the app plays, kn-in-x-knc) so one voice's habits don't decide.

usage: python make_saycheck.py <ear.onnx> <out .bin> <voice name>=<letters m4a dir>,<words m4a dir> [...]
format (little endian): "SAY1" u32 nEntries, u16 len + utf8 ear rev, then per entry:
  u16 len + utf8 text, u8 voice index, u16 frames, frames*66 bytes  (logprob = -byte * 15 / 255)
"""
import os, sys, struct, hashlib, subprocess
import numpy as np

CLIP = 15.0
BLANK = 1


def load16k(path):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-ar", "16000", "-f", "f32le", "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32).copy()


class Ear:
    def __init__(self, path):
        import onnxruntime as ort
        self.s = ort.InferenceSession(path, providers=["CPUExecutionProvider"])

    def logprobs(self, y):
        return self.s.run(None, {"audio": y[None, :].astype(np.float32)})[0][0]


def fingerprint(lp):
    """drop frames that are (almost) only blank = silence; keep at least 3 frames"""
    keep = np.exp(lp[:, BLANK]) < 0.95
    x = lp[keep] if keep.sum() >= 3 else lp
    return np.clip(np.round(-np.maximum(x, -CLIP) / CLIP * 255), 0, 255).astype(np.uint8)


def name2text(n): return "".join(chr(int(h, 16)) for h in n.split("_"))


def main():
    ear_path, out = sys.argv[1], sys.argv[2]
    rev = hashlib.sha256(open(ear_path, "rb").read()).hexdigest()
    ear = Ear(ear_path)
    entries = []
    for vi, spec in enumerate(sys.argv[3:]):
        voice, dirs = spec.split("=", 1)
        for d in dirs.split(","):
            for f in sorted(os.listdir(d)):
                if not f.endswith(".m4a"): continue
                fp = fingerprint(ear.logprobs(load16k(os.path.join(d, f))))
                entries.append((name2text(f[:-4]), vi, fp))
        print(f"voice {vi} {voice}: {sum(e[1] == vi for e in entries)} clips", flush=True)
    with open(out, "wb") as o:
        r = rev.encode()
        o.write(b"SAY1" + struct.pack("<IH", len(entries), len(r)) + r)
        for t, vi, fp in entries:
            b = t.encode("utf-8")
            o.write(struct.pack("<H", len(b)) + b + struct.pack("<BH", vi, fp.shape[0]) + fp.tobytes())
    print(f"{len(entries)} fingerprints, {os.path.getsize(out) / 1e6:.1f} MB → {out}")


if __name__ == "__main__":
    main()
