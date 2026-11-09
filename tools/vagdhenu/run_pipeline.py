#!/usr/bin/env python3
import os, sys, json, time, subprocess, urllib.request
from huggingface_hub import HfApi

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(os.path.dirname(HERE))
HKS_PATH = os.path.join(PROJECT_ROOT, "app", "src", "main", "assets", "hks.json")
RENDER_DIR = os.path.join(PROJECT_ROOT, "build_audio", "vagdhenu")
PACKS_DIR = os.path.join(PROJECT_ROOT, "build_audio", "packs")
REPO_ID = "ssurya1992/harikathamrutha-vagdhenu"

os.makedirs(RENDER_DIR, exist_ok=True)
os.makedirs(PACKS_DIR, exist_ok=True)

def is_online():
    try:
        urllib.request.urlopen("https://huggingface.co", timeout=5)
        return True
    except Exception:
        return False

def try_upload_pending(api):
    if not is_online():
        return
    for f in sorted(os.listdir(PACKS_DIR)):
        if f.startswith("vag_") and f.endswith(".zip"):
            zpath = os.path.join(PACKS_DIR, f)
            try:
                print(f"[ONLINE] Uploading {f} to Hugging Face...", flush=True)
                api.upload_file(
                    path_or_fileobj=zpath,
                    path_in_repo=f,
                    repo_id=REPO_ID,
                    repo_type="dataset"
                )
                print(f"[ONLINE] Successfully uploaded {f}!", flush=True)

                # NOT published to the current app (hks/): user judged these per-line renders weaker than the server's
                # half-padya takes (2026-11-05); the app's packs come from the server's refine job only.

                # After successful upload, delete the zip and raw wavs to save disk space
                os.remove(zpath)
                sid = f.split('_')[1].split('.')[0]
                raw_dir = os.path.join(RENDER_DIR, sid)
                if os.path.exists(raw_dir):
                    import shutil
                    shutil.rmtree(raw_dir)
                # Create a .done marker so render_lines knows it's fully done and uploaded
                open(os.path.join(PACKS_DIR, f"vag_{sid}.done"), 'w').close()
            except Exception as e:
                print(f"[WARNING] Upload for {f} deferred (network error): {e}", flush=True)

def main():
    print("=== Starting Offline-First Local TTS & Resilient Upload Pipeline ===", flush=True)
    api = HfApi()

    # Step 1: Run render_lines.py locally
    render_cmd = [
        sys.executable,
        os.path.join(HERE, "render.py"),
        HKS_PATH,
        RENDER_DIR
    ]

    proc = subprocess.Popen(render_cmd)

    # Monitor loop
    hks = json.load(open(HKS_PATH, encoding='utf-8'))
    sandhi_ids = [s['id'] for s in hks]
    line_keys = {s['id']: [f"{u['n']:03d}_{li}"
                           for u in ([{'n': 0, 'lines': s['intro']}] if s.get('intro') else []) + s['verses']
                           for li, _ in enumerate(u['lines'])] for s in hks}

    try:
        while True:
            # Check for any sandhis that are fully rendered but not packed yet
            for sid in sandhi_ids:
                zpath = os.path.join(PACKS_DIR, f"vag_{sid:02d}.zip")
                done_path = os.path.join(PACKS_DIR, f"vag_{sid:02d}.done")

                # only start the packer (which loads an alignment model) once every line of the sandhi exists
                if not os.path.exists(zpath) and not os.path.exists(done_path) and all(
                        os.path.exists(os.path.join(RENDER_DIR, f"{sid:02d}", k + ".wav")) for k in line_keys[sid]):
                    # Try packing locally
                    pack_cmd = [
                        sys.executable,
                        os.path.join(HERE, "packer.py"),
                        HKS_PATH,
                        RENDER_DIR,
                        PACKS_DIR,
                        str(sid)
                    ]
                    p_res = subprocess.run(pack_cmd, capture_output=True, text=True)
                    if "lines, " in p_res.stdout:
                        print(f"[LOCAL PACK] {p_res.stdout.strip()}", flush=True)
                    elif p_res.returncode != 0:   # never hide a packing failure again
                        print(f"[PACK FAILED] sandhi {sid}:\n{p_res.stderr.strip()[-1500:]}", flush=True)
                    try_upload_pending(api)   # upload right away, don't wait for the rest of the loop

            # Try uploading any pending zip files if internet is available
            try_upload_pending(api)

            # Check if render_lines finished
            if proc.poll() is not None:
                print("=== Local TTS rendering finished ===", flush=True)
                break

            time.sleep(10)

        # Final upload attempt after rendering completes
        print("Waiting for internet connection for final upload sync...", flush=True)
        while True:
            pending = [f for f in os.listdir(PACKS_DIR) if f.startswith("vag_") and f.endswith(".zip")]
            if not pending:
                print("=== ALL PACKS UPLOADED & SYNCED ===", flush=True)
                break
            try_upload_pending(api)
            time.sleep(15)

    except KeyboardInterrupt:
        print("\nPipeline monitor paused. Local background rendering continues.")

if __name__ == '__main__':
    main()
