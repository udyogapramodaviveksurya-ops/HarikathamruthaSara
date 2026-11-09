"""Package rendered Vāgdhenu line clips into one download per sandhi.
For each sandhi: every line -> AAC .m4a (mono 48 kbps) + word timings by forced alignment on the clean audio
(torchaudio MMS_FA), and a manifest.json. Output: <packs>/vag_<NN>.zip

usage: /opt/anaconda3/bin/python tools/vagdhenu/packer.py <hks.json> <render_dir> <packs_dir> [sandhi ids]
"""
import os, sys, json, subprocess, zipfile, tempfile
HERE = os.path.dirname(os.path.abspath(__file__)); sys.path.insert(0, os.path.dirname(HERE)); sys.path.insert(0, HERE)
import soundfile as sf, torch, torchaudio
import roman

bundle = torchaudio.pipelines.MMS_FA
model = bundle.get_model(with_star=False).eval(); tok = bundle.get_tokenizer(); aligner = bundle.get_aligner()

def word_times(wav_path, words):
    y, sr = sf.read(wav_path)
    x = torchaudio.functional.resample(torch.tensor(y, dtype=torch.float32), sr, 16000)[None]
    with torch.inference_mode(): em, _ = model(x)
    dur = len(y) / sr
    rw = [roman.roman(w) or "a" for w in words]
    try:
        spans = aligner(em[0], tok(rw))
        if len(spans) != len(words):
            raise ValueError("Span count mismatch")
    except Exception:
        # too short for CTC or span count mismatch: spread evenly by length
        tot = sum(len(w) for w in words); acc = 0; out = []
        for w in words:
            out.append([round(dur * acc / tot, 3), round(dur * (acc + len(w)) / tot, 3)]); acc += len(w)
        return out, 0.0
    ratio = dur / em.shape[1]
    out = [[round(sp[0].start * ratio, 3), round(sp[-1].end * ratio, 3)] for sp in spans]
    for i in range(len(out) - 1): out[i][1] = out[i + 1][0]
    out[0][0] = 0.0; out[-1][1] = round(dur, 3)
    score = sum(s.score * len(s) for sp in spans for s in sp) / max(1, sum(len(s) for sp in spans for s in sp))
    return out, float(score)

def main():
    hks = json.load(open(sys.argv[1])); rdir = sys.argv[2]; pdir = sys.argv[3]; os.makedirs(pdir, exist_ok=True)
    only = [int(x) for x in sys.argv[4].split(',')] if len(sys.argv) > 4 else None
    for s in hks:
        sid = s['id']
        if only and sid not in only: continue
        units = ([{'n': 0, 'lines': s['intro']}] if s.get('intro') else []) + s['verses']
        src = os.path.join(rdir, f"{sid:02d}")
        need = [(u['n'], li, l['t']) for u in units for li, l in enumerate(u['lines'])]
        missing = [k for k in need if not os.path.exists(os.path.join(src, f"{k[0]:03d}_{k[1]}.wav"))]
        if missing:
            print(f"sandhi {sid}: {len(missing)} lines not rendered yet, skipping"); continue
        manifest = {'sandhi': sid, 'voice': 'Vāgdhenu (anuṣṭubh reference)', 'lines': {}}
        zpath = os.path.join(pdir, f"vag_{sid:02d}.zip")
        with tempfile.TemporaryDirectory() as tmp, zipfile.ZipFile(zpath + ".tmp", 'w', zipfile.ZIP_STORED) as z:
            for n, li, text in need:
                key = f"{n:03d}_{li}"; wav = os.path.join(src, key + ".wav")
                words = text.split()
                wt, sc = word_times(wav, words)
                m4a = os.path.join(tmp, key + ".m4a")
                subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-ac', '1', '-c:a', 'aac', '-b:a', '48k', m4a], check=True)
                z.write(m4a, key + ".m4a")
                manifest['lines'][key] = {'dur': round(sf.info(wav).duration, 3), 'words': wt, 'score': round(sc, 3)}
            z.writestr('manifest.json', json.dumps(manifest, ensure_ascii=False, separators=(',', ':')))
        os.replace(zpath + ".tmp", zpath)
        sc = [v['score'] for v in manifest['lines'].values()]
        print(f"sandhi {sid}: {len(need)} lines, {os.path.getsize(zpath)/1e6:.1f} MB, align score avg {sum(sc)/len(sc):.2f}", flush=True)

if __name__ == '__main__':
    main()
