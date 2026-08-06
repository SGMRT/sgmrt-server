"""실사용 트래픽 트레이스 재생 — 캐시키 전략 비교 (07-cell-bucket-design.md §2)

실제 조회 이벤트(coords.jsonl)를 시간순으로 가상 캐시에 재생해
캐시 설계별 히트율을 같은 트래픽 위에서 공정 비교한다.

방식:
  geohash    — geohash p6 결과셋 캐시 (조회 결과 통째 저장)
  cellbucket — 0.01° 셀 버킷 + 커버리지 합집합 (셀 소속 코스 목록 저장)
  regionId   — 행정동 결과셋 캐시 (geocache.jsonl 필요 — geocode_cells.py로 생성)

정책 재현:
  - TTL 60/600s, 히트가 TTL을 연장하지 않음 (Spring @Cacheable 동일)
  - 완주(Run End ≥0.5km) 시 이빅트: 결과셋 = 완주 지점 ±2km 앵커 팬아웃 / 셀 버킷 = 소속 셀 1개
  - 근사: 완주 위치 = 완주자의 직전 조회 좌표, 홈/탐색 = 최빈 셀 2km 이내 여부

지표:
  full-hit%  — 필요한 키 전부 히트 (DB 조회 0회 응답)
  DB회피%    — 부분 히트 포함, 조회 단위 중 캐시가 흡수한 비율
  self/shared — 채운 본인 재조회 히트 / 타인 히트

사용법: python3 trace_replay.py <데이터_디렉토리> [방식,방식,...]
"""
import json
import math
import os
import sys
from collections import Counter, defaultdict
from datetime import datetime

BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
RADIUS_KM = 2.0


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


def cell_of(lat, lng):
    return (round(lat, 2), round(lng, 2))


def covering_cells(lat, lng):
    dlat = RADIUS_KM / 111.0
    dlng = RADIUS_KM / (111.0 * math.cos(math.radians(lat)))
    out = []
    la = round(lat - dlat, 2)
    while la <= lat + dlat + 1e-9:
        lo = round(lng - dlng, 2)
        while lo <= lng + dlng + 1e-9:
            if dist_km(lat, lng, la, lo) <= RADIUS_KM + 0.8:
                out.append((round(la, 2), round(lo, 2)))
            lo = round(lo + 0.01, 2)
        la = round(la + 0.01, 2)
    return out


def ts(s):
    return datetime.strptime(s[:19], "%Y-%m-%d %H:%M:%S").timestamp()


def load(data_dir):
    events = [json.loads(l) for l in open(os.path.join(data_dir, "coords.jsonl"))]
    events = [e for e in events
              if 33 < e["lat"] < 39 and 124 < e["lng"] < 132 and e["t"]]  # 한국 내
    for e in events:
        e["ts"] = ts(e["t"])
    events.sort(key=lambda e: e["ts"])

    runs = [json.loads(l) for l in open(os.path.join(data_dir, "runends.jsonl"))]
    runs = [r for r in runs if r["dist_km"] >= 0.5 and r["t"]]
    for r in runs:
        r["ts"] = ts(r["t"])
    runs.sort(key=lambda r: r["ts"])

    geo = {}
    geopath = os.path.join(data_dir, "geocache.jsonl")
    if os.path.exists(geopath):
        for l in open(geopath):
            r = json.loads(l)
            if r["region"] and r["region"] != "ERROR":
                geo[(r["lat"], r["lng"])] = r["region"]
    return events, runs, geo


def simulate(scheme, ttl, evict, events, runs, geo, home, region_for, region_anchor):
    cache = {}
    anchor = {}
    last_pos = {}
    ri = 0
    full_hit = hit_units = total_units = self_u = shared_u = bypass = dels = 0
    keys = set()
    for e in events:
        if evict:
            while ri < len(runs) and runs[ri]["ts"] <= e["ts"]:
                r = runs[ri]
                ri += 1
                pos = last_pos.get(r["uid"])
                if not pos:
                    continue
                rt = r["ts"]
                if scheme == "cellbucket":
                    k = cell_of(*pos)
                    if k in cache and rt < cache[k][0]:   # 살아있는 엔트리만 DEL로 계수
                        del cache[k]
                        dels += 1
                else:
                    dead = [k for k, a in anchor.items()
                            if k in cache and rt < cache[k][0]
                            and dist_km(pos[0], pos[1], a[0], a[1]) <= RADIUS_KM]
                    for k in dead:
                        del cache[k]
                        dels += 1
        lat, lng, uid, now = e["lat"], e["lng"], e["uid"], e["ts"]
        last_pos[uid] = (lat, lng)

        if scheme == "regionId":
            h = home[uid]
            if dist_km(lat, lng, h[0], h[1]) > 2.0:  # 탐색은 캐시 불가 (regionId 미첨부)
                bypass += 1
                continue
            qkeys = [region_for(lat, lng)]
        elif scheme == "geohash":
            qkeys = [geohash6(lat, lng)]
        else:
            qkeys = covering_cells(lat, lng)

        total_units += len(qkeys)
        h = 0
        for k in qkeys:
            ent = cache.get(k)
            if ent and now < ent[0]:
                h += 1
                if ent[1] == uid:
                    self_u += 1
                else:
                    shared_u += 1
            else:
                cache[k] = (now + ttl, uid)
                keys.add(k)
                if scheme == "cellbucket":
                    anchor[k] = k
                elif scheme == "geohash":
                    anchor[k] = (lat, lng)
                else:
                    anchor[k] = region_anchor.get(k, (lat, lng))
        hit_units += h
        if h == len(qkeys):
            full_hit += 1
    n = len(events)
    return {"full_hit": full_hit / n * 100,
            "db_avoid": hit_units / total_units * 100 if total_units else 0,
            "self": self_u, "shared": shared_u, "bypass": bypass,
            "keys": len(keys), "dels": dels}


def main():
    data_dir = sys.argv[1] if len(sys.argv) > 1 else "."
    schemes = sys.argv[2].split(",") if len(sys.argv) > 2 else ["geohash", "cellbucket"]
    events, runs, geo = load(data_dir)

    uc = defaultdict(Counter)
    for e in events:
        uc[e["uid"]][cell_of(e["lat"], e["lng"])] += 1
    home = {u: c.most_common(1)[0][0] for u, c in uc.items()}

    geo_cells = list(geo.keys())
    region_cache = {}

    def region_for(lat, lng):
        cell = cell_of(lat, lng)
        if cell in region_cache:
            return region_cache[cell]
        if cell in geo:
            rg = geo[cell]
        else:
            best, bd = None, 1.5
            for gc in geo_cells:
                d = dist_km(cell[0], cell[1], gc[0], gc[1])
                if d < bd:
                    best, bd = gc, d
            rg = geo[best] if best else f"pseudo:{cell[0]}:{cell[1]}"
        region_cache[cell] = rg
        return rg

    region_anchor = {}
    if geo:
        rc = defaultdict(list)
        for c, rg in geo.items():
            rc[rg].append(c)
        region_anchor = {rg: (sum(x[0] for x in cs) / len(cs), sum(x[1] for x in cs) / len(cs))
                         for rg, cs in rc.items()}

    print(f"이벤트 {len(events)}건, 완주 {len(runs)}건"
          + (f", region {len(set(geo.values()))}개" if geo else " (geocache 없음 — regionId 방식 제외)"))
    print(f"{'방식':<11} {'TTL':>4} {'이빅트':<5} {'full-hit%':>9} {'DB회피%':>7} "
          f"{'self':>7} {'shared':>7} {'우회':>5} {'키수':>7} {'DEL':>6}")
    for scheme in schemes:
        if scheme == "regionId" and not geo:
            continue
        for ttl in (60, 600):
            for evict in (False, True):
                r = simulate(scheme, ttl, evict, events, runs, geo, home, region_for, region_anchor)
                print(f"{scheme:<11} {ttl:>4} {str(evict):<5} {r['full_hit']:>9.1f} {r['db_avoid']:>7.1f} "
                      f"{r['self']:>7} {r['shared']:>7} {r['bypass']:>5} {r['keys']:>7} {r['dels']:>6}")


if __name__ == "__main__":
    main()
