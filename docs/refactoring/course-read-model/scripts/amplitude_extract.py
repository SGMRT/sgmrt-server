"""Amplitude Export API에서 지도 조회 트레이스를 추출한다.

리플레이 시뮬레이션(trace_replay.py, scale_replay.py)의 입력 데이터를 만든다.

사용법:
    export AMPLITUDE_API_KEY=<APP 프로젝트 API Key>
    export AMPLITUDE_SECRET_KEY=<APP 프로젝트 Secret Key>
    python3 amplitude_extract.py <출력_디렉토리> [시작일 YYYYMMDD] [종료일 YYYYMMDD]

출력:
    coords.jsonl   — gotten_courses_info 이벤트 (lat, lng, uid, t)
    runends.jsonl  — Run End 이벤트 (uid, t, dist_km) — 이빅트 모델링용

주의: 키는 반드시 환경변수로 주입한다 (코드·커밋에 남기지 말 것).
Export API는 주 단위로 청크해 받는다 (긴 범위는 502/타임아웃).
"""
import gzip
import json
import os
import sys
import time
import urllib.error
import urllib.request
import zipfile
from base64 import b64encode
from datetime import date, timedelta


def download(out_dir, start, end):
    api_key = os.environ["AMPLITUDE_API_KEY"]
    secret = os.environ["AMPLITUDE_SECRET_KEY"]
    auth = b64encode(f"{api_key}:{secret}".encode()).decode()

    chunks = []
    cur = start
    while cur <= end:
        chunk_end = min(cur + timedelta(days=6), end)
        chunks.append((cur, chunk_end))
        cur = chunk_end + timedelta(days=1)

    for s, e in chunks:
        dest = os.path.join(out_dir, f"w{s.strftime('%Y%m%d')}.zip")
        if os.path.exists(dest) and os.path.getsize(dest) > 0:
            print(f"{dest}: skip (exists)", flush=True)
            continue
        url = (f"https://amplitude.com/api/2/export"
               f"?start={s.strftime('%Y%m%d')}T00&end={e.strftime('%Y%m%d')}T23")
        for attempt in range(3):
            req = urllib.request.Request(url, headers={"Authorization": f"Basic {auth}"})
            try:
                with urllib.request.urlopen(req, timeout=300) as resp:
                    data = resp.read()
                with open(dest, "wb") as f:
                    f.write(data)
                print(f"{os.path.basename(dest)}: {len(data)} bytes", flush=True)
                break
            except urllib.error.HTTPError as err:
                if err.code == 404:  # 해당 기간 데이터 없음
                    break
                print(f"{os.path.basename(dest)}: HTTP {err.code} (attempt {attempt + 1})", flush=True)
                time.sleep(5)
            except Exception as err:
                print(f"{os.path.basename(dest)}: {type(err).__name__} (attempt {attempt + 1})", flush=True)
                time.sleep(5)


def extract(out_dir):
    coords, runs = [], []
    for name in sorted(os.listdir(out_dir)):
        if not (name.startswith("w") and name.endswith(".zip")):
            continue
        try:
            zf = zipfile.ZipFile(os.path.join(out_dir, name))
        except zipfile.BadZipFile:
            print(f"skip bad zip: {name}")
            continue
        for info in zf.namelist():
            if not info.endswith(".gz"):
                continue
            raw = gzip.decompress(zf.open(info).read())
            for line in raw.split(b"\n"):
                if not line.strip():
                    continue
                try:
                    ev = json.loads(line)
                except json.JSONDecodeError:
                    continue
                etype = ev.get("event_type")
                props = ev.get("event_properties") or {}
                uid = ev.get("user_id") or ev.get("device_id") or "?"
                if etype == "gotten_courses_info":
                    lat, lng = props.get("lat"), props.get("lng")
                    if lat is None or lng is None:
                        continue
                    coords.append({"lat": float(lat), "lng": float(lng),
                                   "uid": uid, "t": ev.get("event_time", "")})
                elif etype == "Run End":
                    try:
                        dist = float(props.get("distance_km") or 0)
                    except (TypeError, ValueError):
                        dist = 0.0
                    runs.append({"uid": uid, "t": ev.get("event_time", ""), "dist_km": dist})

    with open(os.path.join(out_dir, "coords.jsonl"), "w") as f:
        for c in coords:
            f.write(json.dumps(c) + "\n")
    runs.sort(key=lambda r: r["t"])
    with open(os.path.join(out_dir, "runends.jsonl"), "w") as f:
        for r in runs:
            f.write(json.dumps(r) + "\n")
    print(f"coords.jsonl {len(coords)}건, runends.jsonl {len(runs)}건")


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    start = date.fromisoformat(sys.argv[2]) if len(sys.argv) > 2 else date(2025, 10, 22)
    end = date.fromisoformat(sys.argv[3]) if len(sys.argv) > 3 else date.today()
    os.makedirs(out, exist_ok=True)
    download(out, start, end)
    extract(out)
