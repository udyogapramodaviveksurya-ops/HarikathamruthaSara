#!/usr/bin/env python3
import json, os, time, sys

def main():
    hks_path = 'app/src/main/assets/hks.json'
    if not os.path.exists(hks_path):
        print("Error: app/src/main/assets/hks.json not found. Run from project root.")
        sys.exit(1)

    try:
        while True:
            hks = json.load(open(hks_path, encoding='utf-8'))
            total_lines = 0
            done_lines = 0
            sandhi_stats = []

            for s in hks:
                sid = s['id']
                units = ([{'n': 0, 'lines': s['intro']}] if s.get('intro') else []) + s['verses']
                sandhi_total = sum(len(u['lines']) for u in units)
                total_lines += sandhi_total

                src = f'build_audio/vagdhenu/{sid:02d}'
                rendered = 0
                if os.path.exists(src):
                    rendered = len([f for f in os.listdir(src) if f.endswith('.wav') and not f.endswith('.tmp.wav')])

                done_marker = f'build_audio/packs/vag_{sid:02d}.done'
                zip_marker = f'build_audio/packs/vag_{sid:02d}.zip'

                status = ""
                if os.path.exists(done_marker):
                    rendered = sandhi_total
                    status = " [HOSTED ON HF]"
                elif os.path.exists(zip_marker):
                    rendered = sandhi_total
                    status = " [PACKED]"
                elif rendered == sandhi_total and sandhi_total > 0:
                    status = " [DONE]"
                elif rendered > 0:
                    status = " [RENDERING...]"

                done_lines += rendered
                if rendered > 0 or sid == 1:
                    sandhi_stats.append((sid, rendered, sandhi_total, status))

            pct = (done_lines / total_lines * 100) if total_lines > 0 else 0
            bar_len = 30
            filled = int(bar_len * done_lines / total_lines) if total_lines > 0 else 0
            bar = '█' * filled + '░' * (bar_len - filled)

            print("\033[H\033[J", end="", flush=True)
            print("==================================================")
            print("       VĀGDHENU TTS RENDERING PROGRESS            ")
            print("==================================================")
            print(f"Overall Progress: [{bar}] {done_lines}/{total_lines} lines ({pct:.1f}%)\n")

            for sid, r, t, st in sandhi_stats:
                spct = (r / t * 100) if t > 0 else 0
                sfilled = int(15 * r / t) if t > 0 else 0
                sbar = '█' * sfilled + '░' * (15 - sfilled)
                print(f"Sandhi {sid:02d}: [{sbar}] {r:3d}/{t:3d} lines ({spct:5.1f}%){st}")

            print("\nPress Ctrl+C to exit dashboard.\n")
            time.sleep(2)
    except KeyboardInterrupt:
        print("\nExited progress monitor.")

if __name__ == '__main__':
    main()
