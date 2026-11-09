"""Render every line of Harikathamrutha Sara with Vāgdhenu (anuṣṭubh reference, 32 steps — style "A" chosen by ear).
One WAV per line: <outdir>/<sandhi:02d>/<padya:03d>_<line>.wav. Resumable: existing files are skipped.
"""
import os, sys, json, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
HKS = os.path.abspath(sys.argv[1]); OUT = os.path.abspath(sys.argv[2])
import soundfile as sf
import vag_new as vag

STYLE = dict(meter="anuṣṭubh", nfe=16)
PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

def main():
    hks = json.load(open(HKS, encoding='utf-8'))
    only = [int(x) for x in sys.argv[3].split(',')] if len(sys.argv) > 3 else None
    t_all = time.time(); n = 0; audio = 0.0
    for s in hks:
        if only and s['id'] not in only: continue

        # Skip if already uploaded and marked as done
        pack_done_file = os.path.join(PROJECT_ROOT, "build_audio", "packs", f"vag_{s['id']:02d}.done")
        if os.path.exists(pack_done_file):
            continue

        units = ([{'n': 0, 'lines': s['intro']}] if s.get('intro') else []) + s['verses']
        d = os.path.join(OUT, f"{s['id']:02d}"); os.makedirs(d, exist_ok=True)
        t_s = time.time()
        for u in units:
            for li, l in enumerate(u['lines']):
                f = os.path.join(d, f"{u['n']:03d}_{li}.wav")
                if os.path.exists(f): continue
                y = vag.synth(l['t'], **STYLE)
                sf.write(f + ".tmp.wav", y, vag.SR, subtype='PCM_16'); os.replace(f + ".tmp.wav", f)
                n += 1; audio += len(y) / vag.SR
        print(f"sandhi {s['id']} done in {time.time()-t_s:.0f}s  (total {n} lines, {audio/60:.1f} min audio, {(time.time()-t_all)/3600:.2f} h)", flush=True)
    print("ALL DONE", flush=True)

if __name__ == '__main__':
    main()
