"""증폭 리플레이 — 실측 패턴을 보존한 채 트래픽만 N배로 늘려 재생 (07 §2-4)

"트래픽이 많다고 치고"의 가상 부하 대신, 관측된 트래픽을 보존 복제해 배수만 올린다.
복제: 실제 사용자를 N배 클론. 클론은 원본의 조회 시퀀스를 유지하되
  위치 ±250m 가우시안 지터(같은 동네의 다른 사람), 시각 ±30분 균등 지터.
  → 공간 밀도·시간대 패턴·홈/탐색 비율이 기대값 그대로 보존된다.

방식: 결과셋(geohash p6) vs 셀 버킷. 정책: TTL 600s + 완주 이빅트 (채택 정책).

사용법: python3 scale_replay.py <데이터_디렉토리> [배수,배수,...]  (기본 1,5,20,100)
"""
import json
import math
import os
import random
import sys
from collections import defaultdict
from datetime import datetime

BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
TTL = 600.0
random.seed(42)


def geohash6(lat, lng):
    lat_r, lng_r = [-90.0, 90.0], [-180.0, 180.0]
    bits, ch, out, even = 0, 0, [], True
    while len(out) < 6:
        if even:
            mid = (lng_r[0] + lng_r[1]) / 2
            ch = ch * 2 + (1 if lng > mid else 0)
            lng_r = [mid, lng_r[1]] if lng > mid else [lng_r[0], mid]
        else:
            mid = (lat_r[0] + lat_r[1]) / 2
            ch = ch * 2 + (1 if lat > mid else 0)
            lat_r = [mid, lat_r[1]] if lat > mid else [lat_r[0], mid]
        even = not even
        bits += 1
        if bits == 5:
            out.append(BASE32[ch])
            bits, ch = 0, 0
    return "".join(out)


def dist_km(a1, o1, a2, o2):
    return math.sqrt(((a1 - a2) * 111) ** 2
                     + ((o1 - o2) * 111 * math.cos(math.radians(a1))) ** 2)


def covering(lat, lng):
    dlat = 2.0 / 111.0
    dlng = 2.0 / (111.0 * math.cos(math.radians(lat)))
    out = []
    la = round(lat - dlat, 2)
    while la <= lat + dlat + 1e-9:
        lo = round(lng - dlng, 2)
        while lo <= lng + dlng + 1e-9:
            if dist_km(lat, lng, la, lo) <= 2.8:
                out.append((round(la, 2), round(lo, 2)))
            lo = round(lo + 0.01, 2)
        la = round(la + 0.01, 2)
    return out


def ts(s):
    return datetime.strptime(s[:19], "%Y-%m-%d %H:%M:%S").timestamp()


def coarse(lat, lng):
    return (round(lat * 50) / 50, round(lng * 50) / 50)  # 0.02° 버킷 (이빅트 역산 인덱스)


def jitter_pos(lat, lng):
    return (lat + random.gauss(0, 0.25 / 111.0),
            lng + random.gauss(0, 0.25 / (111.0 * math.cos(math.radians(lat)))))


def amplify(base_events, base_runs, scale):
    evs, rns = [], []
    for c in range(scale):
        for e in base_events:
            la, lo = (e["lat"], e["lng"]) if c == 0 else jitter_pos(e["lat"], e["lng"])
            t = e["ts"] if c == 0 else e["ts"] + random.uniform(-1800, 1800)
            evs.append((t, la, lo, f'{e["uid"]}#{c}'))
        for r in base_runs:
            t = r["ts"] if c == 0 else r["ts"] + random.uniform(-1800, 1800)
            rns.append((t, f'{r["uid"]}#{c}'))
    evs.sort()
    rns.sort()
    return evs, rns


def simulate(scheme, evs, rns):
    cache = {}
    anchor_idx = defaultdict(set)
    key_anchor = {}
    last_pos = {}
    ri = 0
    full_hit = hit_units = total_units = self_u = shared_u = dels = 0
    keys = set()
    for t, lat, lng, uid in evs:
        while ri < len(rns) and rns[ri][0] <= t:
            _, ruid = rns[ri]
            ri += 1
            pos = last_pos.get(ruid)
            if not pos:
                continue
            rt = rns[ri - 1][0]
            if scheme == "cellbucket":
                k = (round(pos[0], 2), round(pos[1], 2))
                if k in cache and rt < cache[k][0]:   # 살아있는 엔트리만 DEL로 계수
                    del cache[k]
                    dels += 1
            else:
                cands = set()
                base = coarse(*pos)
                for di in (-2, -1, 0, 1, 2):
                    for dj in (-2, -1, 0, 1, 2):
                        cands |= anchor_idx.get(
                            (round(base[0] + di * 0.02, 2), round(base[1] + dj * 0.02, 2)), set())
                for k in list(cands):
                    if k in cache and rt < cache[k][0] and dist_km(pos[0], pos[1], *key_anchor[k]) <= 2.0:
                        del cache[k]
                        dels += 1
        last_pos[uid] = (lat, lng)
        qkeys = covering(lat, lng) if scheme == "cellbucket" else [geohash6(lat, lng)]
        total_units += len(qkeys)
        h = 0
        for k in qkeys:
            ent = cache.get(k)
            if ent and t < ent[0]:
                h += 1
                if ent[1] == uid:
                    self_u += 1
                else:
                    shared_u += 1
            else:
                cache[k] = (t + TTL, uid)
                keys.add(k)
                if scheme != "cellbucket" and k not in key_anchor:
                    key_anchor[k] = (lat, lng)
                    anchor_idx[coarse(lat, lng)].add(k)
        hit_units += h
        if h == len(qkeys):
            full_hit += 1
    n = len(evs)
    return (full_hit / n * 100, hit_units / total_units * 100,
            shared_u / max(1, hit_units) * 100, len(keys), dels)


def main():
    data_dir = sys.argv[1] if len(sys.argv) > 1 else "."
    scales = [int(s) for s in sys.argv[2].split(",")] if len(sys.argv) > 2 else [1, 5, 20, 100]

    base_events = [json.loads(l) for l in open(os.path.join(data_dir, "coords.jsonl"))]
    base_events = [e for e in base_events
                   if 33 < e["lat"] < 39 and 124 < e["lng"] < 132 and e["t"]]
    for e in base_events:
        e["ts"] = ts(e["t"])
    base_runs = [json.loads(l) for l in open(os.path.join(data_dir, "runends.jsonl"))]
    base_runs = [r for r in base_runs if r["dist_km"] >= 0.5 and r["t"]]
    for r in base_runs:
        r["ts"] = ts(r["t"])

    print(f"기준 트레이스: 조회 {len(base_events)}건, 완주 {len(base_runs)}건")
    print(f"{'배수':>4} {'조회수':>9} | {'— 결과셋(지오해싱) —':^38} | {'— 셀 버킷 —':^38}")
    print(f"{'':>4} {'':>9} | {'full%':>6} {'회피%':>6} {'공유%':>6} {'키':>8} {'중복계수':>7} | "
          f"{'full%':>6} {'회피%':>6} {'공유%':>6} {'키':>8} {'중복계수':>7}")
    for scale in scales:
        evs, rns = amplify(base_events, base_runs, scale)
        g = simulate("geohash", evs, rns)
        c = simulate("cellbucket", evs, rns)
        # 중복 계수 = 변경(완주) 1회당 무효화된 살아있는 엔트리 수 — 그 코스가 캐시에 몇 벌 있었나
        gd = g[4] / len(rns)
        cd = c[4] / len(rns)
        print(f"×{scale:>3} {len(evs):>9,} | {g[0]:>6.1f} {g[1]:>6.1f} {g[2]:>6.1f} {g[3]:>8,} {gd:>7.2f} | "
              f"{c[0]:>6.1f} {c[1]:>6.1f} {c[2]:>6.1f} {c[3]:>8,} {cd:>7.2f}", flush=True)


if __name__ == "__main__":
    main()
