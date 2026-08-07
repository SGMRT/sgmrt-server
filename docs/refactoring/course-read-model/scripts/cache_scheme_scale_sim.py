#!/usr/bin/env python3
"""
키 전략 규모 비교 v5 — 반올림 vs regionId, 채택 정책(고정 2km + 반경 가드 + 완주 이빅트) 하에서

공정성: 반올림에도 regionId와 같은 정책을 준다.
- 값 = 셀 중심 기준 고정 2km, radiusM>3000 캐시 우회(서버 가드 — FE 협조 불필요)
- 이빅트 = 코스 좌표 ±2km 박스 안 격자 셀 열거 후 DEL (결정적 — DB 조회조차 불필요, regionId보다 단순)
- 차이점만 남긴다: ① 격자선 위치(수학 격자 vs 동네 경계) ② 탐색 요청 — 반올림은 구분 불가라
  반경 가드를 통과한 탐색(뷰포트 반경 ≤3km, EXPLORE_CACHEABLE_P)을 캐시하고, regionId는 전부 우회
  ③ regionId는 OS 명칭 분화(30% 지역)로 키 분열, 반올림은 없음

지표: 전체/타인 공유 히트율, 정확 히트율(스테일 제외 — 이빅트 있어 둘 다 ≈전체), 생성 키 수, 이빅트 DEL
사용법: python3 cache_scheme_scale_sim.py [DAU ...]   (기본: 1000 10000 1000000)
"""
import math
import random
import sys

random.seed(42)

SPACING_M = 1200
SIGMA_M = 400
BASE_LAT, BASE_LNG = 37.45, 126.90
M_PER_DEG_LAT = 111_320.0
M_PER_DEG_LNG = 111_320.0 * math.cos(math.radians(37.55))
BOX_M = 2000
TTL_S = 600
CELL_DEG = 0.01                     # 반올림 격자 (round 2자리)

SESSIONS_DIST = ([1] * 6 + [2] * 3 + [3] * 1)
REFRESH_P = 0.5
EXPLORE_SESSION_P = 0.3
EXPLORE_CACHEABLE_P = 0.3           # 탐색 중 뷰포트 반경 ≤3km라 반올림 캐시를 타는 비율
NAME_MISMATCH_REGION_RATIO = 0.3
ZIPF_A = 1.1
RUN_SESSION_P = 0.4
POST_RUN_CHECK_P = 0.9
HOURLY_W = [0.2, 0.1, 0.1, 0.1, 0.4, 1.6, 2.4, 1.8, 1.0, 0.8, 0.7, 0.8,
            0.9, 0.8, 0.7, 0.8, 1.2, 2.0, 4.2, 4.6, 4.0, 3.0, 1.6, 0.8]

region_centers, zipf_w, mismatch_regions = [], [], set()
_rbucket = {}


def make_world(n_regions, grid):
    global region_centers, zipf_w, mismatch_regions, _rbucket
    region_centers, _rbucket = [], {}
    for i in range(n_regions):
        gx, gy = i % grid, i // grid
        lat = BASE_LAT + (gy * SPACING_M + random.uniform(-300, 300)) / M_PER_DEG_LAT
        lng = BASE_LNG + (gx * SPACING_M + random.uniform(-300, 300)) / M_PER_DEG_LNG
        region_centers.append((lat, lng))
        _rbucket.setdefault((int(lat * M_PER_DEG_LAT // BOX_M), int(lng * M_PER_DEG_LNG // BOX_M)), []).append(i)
    zipf_w = [1 / (r + 1) ** ZIPF_A for r in range(n_regions)]
    random.shuffle(zipf_w)
    mismatch_regions = set(random.sample(range(n_regions), int(n_regions * NAME_MISMATCH_REGION_RATIO)))


def draw_session_start():
    h = random.choices(range(24), weights=HOURLY_W, k=1)[0]
    return h * 3600 + random.uniform(0, 3600)


def geometric(p):
    n = 0
    while random.random() > p:
        n += 1
    return n


def grid_key(lat, lng):
    return (round(lat, 2), round(lng, 2))


def grid_cells_covering(lat, lng):
    """코스 ±2km 박스와 겹치는 반올림 셀 열거 — 결정적 이빅트 (DB 불필요)"""
    dlat = BOX_M / M_PER_DEG_LAT
    dlng = BOX_M / M_PER_DEG_LNG
    cells = []
    la = round(lat - dlat, 2)
    while la <= lat + dlat + 1e-9:
        lo = round(lng - dlng, 2)
        while lo <= lng + dlng + 1e-9:
            cells.append((round(la, 2), round(lo, 2)))
            lo = round(lo + CELL_DEG, 2)
        la = round(la + CELL_DEG, 2)
    return cells


def build_events(dau):
    events = []
    n_regions = len(region_centers)
    for uid in range(dau):
        rid = random.choices(range(n_regions), weights=zipf_w, k=1)[0]
        clat, clng = region_centers[rid]
        hlat = clat + random.gauss(0, SIGMA_M) / M_PER_DEG_LAT
        hlng = clng + random.gauss(0, SIGMA_M) / M_PER_DEG_LNG
        rkey = f"{rid}:{'ios' if uid % 2 == 0 else 'aos'}" if rid in mismatch_regions else f"{rid}"
        for _ in range(random.choice(SESSIONS_DIST)):
            t = draw_session_start()
            events.append((t, uid, "q", hlat, hlng, rkey, rid))
            for _ in range(geometric(REFRESH_P)):
                t += random.uniform(5, 30)
                events.append((t, uid, "q", hlat, hlng, rkey, rid))
            if random.random() < EXPLORE_SESSION_P:
                for _ in range(random.randint(1, 3)):
                    t += random.uniform(20, 90)
                    d, a = random.uniform(1000, 8000), random.uniform(0, 2 * math.pi)
                    events.append((t, uid, "e",
                                   hlat + d * math.sin(a) / M_PER_DEG_LAT,
                                   hlng + d * math.cos(a) / M_PER_DEG_LNG, None, None))
            if random.random() < RUN_SESSION_P:
                ft = t + random.uniform(1200, 3600)
                events.append((ft, uid, "run",
                               hlat + random.gauss(0, 300) / M_PER_DEG_LAT,
                               hlng + random.gauss(0, 300) / M_PER_DEG_LNG, None, None))
                if random.random() < POST_RUN_CHECK_P:
                    events.append((ft + random.uniform(10, 120), uid, "q", hlat, hlng, rkey, rid))
    events.sort(key=lambda e: e[0])
    return events


def simulate(dau):
    events = build_events(dau)
    stats = {}
    for scheme in ("반올림+이빅트", "regionId+이빅트"):
        cache, n_q, hits, cross, keys, dels = {}, 0, 0, 0, 0, 0
        for ev in events:
            t, uid, kind, lat, lng, rkey, rid = ev
            if kind == "run":
                targets = ([k for k in grid_cells_covering(lat, lng)] if scheme.startswith("반올림")
                           else None)
                if targets is None:
                    # regionId: ±2km 박스 안 대표좌표 region (변형 키 포함, 공간 버킷)
                    targets = []
                    cx, cy = int(lat * M_PER_DEG_LAT // BOX_M), int(lng * M_PER_DEG_LNG // BOX_M)
                    for dx in (-1, 0, 1):
                        for dy in (-1, 0, 1):
                            for r in _rbucket.get((cx + dx, cy + dy), ()):
                                cl, cg = region_centers[r]
                                if abs(lat - cl) * M_PER_DEG_LAT <= BOX_M and abs(lng - cg) * M_PER_DEG_LNG <= BOX_M:
                                    targets += [f"{r}", f"{r}:ios", f"{r}:aos"]
                for k in targets:
                    if k in cache:
                        del cache[k]
                        dels += 1
                continue

            n_q += 1
            if scheme.startswith("반올림"):
                if kind == "e" and random.random() >= EXPLORE_CACHEABLE_P:
                    continue                      # 광역 뷰포트 — 반경 가드로 우회
                key = grid_key(lat, lng)
            else:
                if kind == "e" or rkey is None:
                    continue                      # regionId: 탐색 전부 우회
                key = rkey
            entry = cache.get(key)
            if entry and t - entry[0] < TTL_S:
                hits += 1
                if entry[1] != uid:
                    cross += 1
            else:
                cache[key] = (t, uid)
                keys += 1
        stats[scheme] = dict(hit=hits / n_q, cross=cross / n_q, keys=keys, dels=dels, n_q=n_q)
    return stats


if __name__ == "__main__":
    dau_list = [int(x) for x in sys.argv[1:]] or [1000, 10000, 1000000]
    print(f"모델: 채택 정책 공통 적용(고정 2km, 반경 가드, 완주 이빅트, TTL {TTL_S}s)\n")
    for dau in dau_list:
        if dau >= 100_000:
            make_world(3600, 60)
        else:
            make_world(400, 20)
        r = simulate(dau)
        print(f"◆ DAU {dau:,}")
        print(f"{'전략':<16} {'전체 히트':>7} {'타인 공유':>7} {'생성 키':>9} {'이빅트 DEL':>9}")
        for s2, m in r.items():
            print(f"{s2:<16} {m['hit']:>8.1%} {m['cross']:>8.1%} {m['keys']:>10,} {m['dels']:>10,}")
        print()
