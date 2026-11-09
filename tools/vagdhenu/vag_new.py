"""Vāgdhenu rendering for Kannada (Harikathamrutha Sara), using the models loaded by SamkruthaTTS/vagdhenu_repo/server.py"""
import os, sys
REPO = "/Users/vivek-2683/AndroidStudioProjects/SamkruthaTTS/vagdhenu_repo"
sys.path.insert(0, REPO); sys.path.insert(0, os.path.join(REPO, "BigVGAN")); sys.path.insert(0, os.path.join(REPO, "src"))
_cwd = os.getcwd(); os.chdir(REPO)
import numpy as np, soundfile as sf, torch
import server as V
from f5_tts.infer.utils_infer import infer_process, preprocess_ref_audio_text
os.chdir(_cwd)
SR = 24000

def n_aksharas(s):
    n = 0
    for i, c in enumerate(s):
        o = ord(c)
        if (0x0905 <= o <= 0x0914) or (0x0C85 <= o <= 0x0C94): n += 1
        elif ((0x0915 <= o <= 0x0939) or (0x0C95 <= o <= 0x0CB9) or o == 0x0CDE) and (s[i+1] if i+1 < len(s) else "") not in ("್", "್"): n += 1
    return n

def vocalic_l(s):
    return s.replace("ೢ", "್ಲೃ").replace("ೣ", "್ಲೄ").replace("ಌ", "ಲೃ").replace("ೡ", "ಲೄ")

_refs = {}
def ref(meter):
    if meter not in _refs:
        e = V.bank[meter]
        ra, rt = preprocess_ref_audio_text(os.path.join(V.bdir, e["wav"]), e.get("ref_text", ""), clip_short=True)
        d, sr = sf.read(ra)
        _refs[meter] = (ra, rt, len(d) / sr, float(e.get("sec_per_syll", 0.26)))
    return _refs[meter]

def synth(text, meter="anuṣṭubh", nfe=32, seed=42, speed=0.9, sps_scale=1.0):
    ra, rt, ref_len, sps = ref(meter)
    p = V.PT.model_text_sandhi(text, echo_final=False)
    p = vocalic_l(V._hna_metathesis(V._danda_fix(V._anusvara_m(p))))
    for att in range(3):
        torch.manual_seed(seed + att)
        w, sr, _ = infer_process(ra, rt, p, V.cfm, V.cap, mel_spec_type="vocos", speed=speed, nfe_step=nfe,
                                 cfg_strength=3.0, device=V.DEVICE, fix_duration=ref_len + n_aksharas(p) * sps * sps_scale)
        y = V.bvgan(V.cap.last); mx = np.abs(y).max(); y = y / mx * 0.97 if mx > 1 else y
        if float(np.sqrt((y ** 2).mean())) > 0.02: break
    return V.gate(y)
