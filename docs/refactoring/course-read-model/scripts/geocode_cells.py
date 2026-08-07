"""활성 격자 셀 → 행정동 역지오코딩 (Nominatim) — regionId 방식 리플레이의 보조 데이터

trace_replay.py의 regionId 방식과 경계 누락률 측정에만 필요하다 (셀 버킷·지오해싱은 불필요).
Nominatim 사용 정책(1 req/s)을 지키기 위해 1.1초 간격, 캐시 파일에 이어받기 지원.

사용법:
    python3 geocode_cells.py <데이터_디렉토리>
    (사전에 coords.jsonl이 있어야 하며, 이벤트 2건 이상인 셀만 대상 — 약 2,400셀 ≈ 45분)

출력: geocache.jsonl — {"lat", "lng", "n", "region": "시|구|동"}
"""
import json
import os
import sys
import time
import urllib.request
from collections import Counter


def main():
    data_dir = sys.argv[1] if len(sys.argv) > 1 else "."
    cache_path = os.path.join(data_dir, "geocache.jsonl")

    events = [json.loads(l) for l in open(os.path.join(data_dir, "coords.jsonl"))]
    events = [e for e in events if 33 < e["lat"] < 39 and 124 < e["lng"] < 132]
    cell_cnt = Counter((round(e["lat"], 2), round(e["lng"], 2)) for e in events)
    targets = sorted(((c, n) for c, n in cell_cnt.items() if n >= 2), key=lambda x: -x[1])

    done = set()
    if os.path.exists(cache_path):
        for line in open(cache_path):
            r = json.loads(line)
            done.add((r["lat"], r["lng"]))
    todo = [(c, n) for c, n in targets if c not in done]
    print(f"전체 {len(targets)}개 중 남은 셀: {len(todo)}개", flush=True)

    with open(cache_path, "a") as out:
        for i, ((lat, lng), n) in enumerate(todo):
            url = (f"https://nominatim.openstreetmap.org/reverse"
                   f"?lat={lat}&lon={lng}&format=json&zoom=14&accept-language=ko")
            req = urllib.request.Request(url, headers={"User-Agent": "ghostrunner-cache-analysis/1.0"})
            region = "ERROR"
            for attempt in range(2):
                try:
                    with urllib.request.urlopen(req, timeout=20) as r:
                        d = json.loads(r.read())
                    addr = d.get("address", {})
                    city = addr.get("city") or addr.get("town") or addr.get("county") or ""
                    gu = addr.get("borough") or addr.get("city_district") or ""
                    dong = (addr.get("suburb") or addr.get("quarter")
                            or addr.get("neighbourhood") or addr.get("village") or "")
                    region = f"{city}|{gu}|{dong}".strip("|")
                    break
                except Exception:
                    time.sleep(3)
            out.write(json.dumps({"lat": lat, "lng": lng, "n": n, "region": region},
                                 ensure_ascii=False) + "\n")
            out.flush()
            if (i + 1) % 100 == 0:
                print(f"{i + 1}/{len(todo)} 완료", flush=True)
            time.sleep(1.1)
    print("지오코딩 완료", flush=True)


if __name__ == "__main__":
    main()
