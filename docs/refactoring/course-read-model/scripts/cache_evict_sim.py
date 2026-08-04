#!/usr/bin/env python3
"""
코스 지도 캐시 정책 시뮬레이션 v4 — 성장 가정 + TTL×이빅트 정책 비교

전제: 키 전략은 regionId로 확정 (v3). 이번엔 성장한 규모(DAU 1만~3만)에서
"TTL 연장 × 완주 시 이빅트" 정책 조합을 비교한다.

[정책]
  A: TTL 60s,  이빅트 없음 (현행)
  B: TTL 600s, 이빅트 없음 (TTL만 연장)
  C: TTL 600s, 완주 시 이빅트 (제안 — AFTER_COMMIT 리스너)
  D: TTL 60s,  완주 시 이빅트

[러닝 행동 (v3 세계 + 추가)]
- 세션의 40%가 실제 러닝으로 이어짐 (20~60분), 코스 위치 = 집 근처(σ300m)
- 완주는 코스 ±2km 박스 안 모든 region의 리드모델 데이터를 바꿈 (TOP4/러너수)
- 완주 후 90%는 10~120초 내 홈을 다시 열어 자기 등수를 확인한다 ("끝나자마자 무조건")
- 이빅트 = 완주 시각에 해당 region들의 캐시 키 DEL (실서버: 코스 좌표 ±2km 박스로 region 역산)

[지표]
- 본인 미반영율: 완주 확인 조회가 "내 완주 이전에 적재된 캐시"를 받은 비율 ← 요구사항 직접 측정
- 스테일 서빙율: 전체 히트 중, 적재 이후 그 region 데이터가 바뀐 엔트리를 준 비율
- 전체/타인 공유 히트율, 이빅트 DEL 수

사용법: python3 cache_evict_sim.py [DAU ...]   (기본: 10000 30000)
"""
import math
import random
import sys

random.seed(42)

REGIONS = 400            # 기본: 서울 유사 (국민앱 스케일에서는 make_world가 전국으로 확장)
GRID = 20
SPACING_M = 1200
SIGMA_M = 400
BASE_LAT, BASE_LNG = 37.45, 126.90
M_PER_DEG_LAT = 111_320.0
M_PER_DEG_LNG = 111_320.0 * math.cos(math.radians(37.55))
BOX_M = 2000                       # 캐시 값의 고정 반경 = region bbox 반변

SESSIONS_DIST = ([1] * 6 + [2] * 3 + [3] * 1)
REFRESH_P = 0.5
EXPLORE_SESSION_P = 0.3
NAME_MISMATCH_REGION_RATIO = 0.3
ZIPF_A = 1.1
RUN_SESSION_P = 0.4                # 세션이 실제 러닝으로 이어질 확률
POST_RUN_CHECK_P = 0.9             # 완주 직후 홈에서 등수 확인할 확률
HOURLY_W = [0.2, 0.1, 0.1, 0.1, 0.4, 1.6, 2.4, 1.8, 1.0, 0.8, 0.7, 0.8,
            0.9, 0.8, 0.7, 0.8, 1.2, 2.0, 4.2, 4.6, 4.0, 3.0, 1.6, 0.8]

POLICIES = {
    "A: TTL60":        dict(ttl=60, evict=False),
    "B: TTL600":       dict(ttl=600, evict=False),
    "C: TTL600+evict": dict(ttl=600, evict=True),
    "D: TTL60+evict":  dict(ttl=60, evict=True),
}

# ---- 세계 (규모별 생성) ----
region_centers = []
zipf_w = []
mismatch_regions = set()
_bucket = {}                       # 공간 버킷(2km 셀) → region id 목록 — 역산 O(1)화


def make_world(n_regions, grid):
    """국민앱 스케일(DAU 10만+)은 전국 행정동 ~3,500개로 세계를 넓혀 밀도 왜곡을 막는다."""
    global region_centers, zipf_w, mismatch_regions, _bucket
    region_centers, _bucket = [], {}
    for i in range(n_regions):
        gx, gy = i % grid, i // grid
        lat = BASE_LAT + (gy * SPACING_M + random.uniform(-300, 300)) / M_PER_DEG_LAT
        lng = BASE_LNG + (gx * SPACING_M + random.uniform(-300, 300)) / M_PER_DEG_LNG
        region_centers.append((lat, lng))
        _bucket.setdefault(_cell(lat, lng), []).append(i)
    zipf_w = [1 / (r + 1) ** ZIPF_A for r in range(n_regions)]
    random.shuffle(zipf_w)
    mismatch_regions = set(random.sample(range(n_regions), int(n_regions * NAME_MISMATCH_REGION_RATIO)))


def _cell(lat, lng):
    return (int(lat * M_PER_DEG_LAT // BOX_M), int(lng * M_PER_DEG_LNG // BOX_M))


def regions_covering(lat, lng):
    """이 좌표의 데이터 변경이 영향을 주는 region 목록 (±2km 박스 — 실서버 역산 쿼리와 동일)"""
    cx, cy = _cell(lat, lng)
    out = []
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for rid in _bucket.get((cx + dx, cy + dy), ()):
                clat, clng = region_centers[rid]
                if abs(lat - clat) * M_PER_DEG_LAT <= BOX_M and abs(lng - clng) * M_PER_DEG_LNG <= BOX_M:
                    out.append(rid)
    return out


def draw_session_start():
    hour = random.choices(range(24), weights=HOURLY_W, k=1)[0]
    return hour * 3600 + random.uniform(0, 3600)


def geometric(p):
    n = 0
    while random.random() > p:
        n += 1
    return n


def build_events(dau):
    """이벤트: (t, uid, kind, ...) — kind: query(홈/탐색) | run_finish | post_run_check"""
    events = []
    for uid in range(dau):
        rid = random.choices(range(len(region_centers)), weights=zipf_w, k=1)[0]
        clat, clng = region_centers[rid]
        home_lat = clat + random.gauss(0, SIGMA_M) / M_PER_DEG_LAT
        home_lng = clng + random.gauss(0, SIGMA_M) / M_PER_DEG_LNG
        platform = "ios" if uid % 2 == 0 else "aos"
        region_key = f"{rid}:{platform}" if rid in mismatch_regions else f"{rid}"

        for _ in range(random.choice(SESSIONS_DIST)):
            t = draw_session_start()
            events.append((t, uid, "query", home_lat, home_lng, region_key, False))
            for _ in range(geometric(REFRESH_P)):
                t += random.uniform(5, 30)
                events.append((t, uid, "query", home_lat, home_lng, region_key, False))
            if random.random() < EXPLORE_SESSION_P:
                for _ in range(random.randint(1, 3)):
                    t += random.uniform(20, 90)
                    d, a = random.uniform(1000, 8000), random.uniform(0, 2 * math.pi)
                    events.append((t, uid, "query",
                                   home_lat + d * math.sin(a) / M_PER_DEG_LAT,
                                   home_lng + d * math.cos(a) / M_PER_DEG_LNG, None, False))
            if random.random() < RUN_SESSION_P:
                course_lat = home_lat + random.gauss(0, 300) / M_PER_DEG_LAT
                course_lng = home_lng + random.gauss(0, 300) / M_PER_DEG_LNG
                finish_t = t + random.uniform(20 * 60, 60 * 60)
                events.append((finish_t, uid, "run_finish", course_lat, course_lng, None, False))
                if random.random() < POST_RUN_CHECK_P:
                    check_t = finish_t + random.uniform(10, 120)
                    events.append((check_t, uid, "query", home_lat, home_lng, region_key, True))
    events.sort(key=lambda e: e[0])
    return events


def simulate(dau):
    events = build_events(dau)
    results = {}
    for pname, policy in POLICIES.items():
        cache = {}                      # key → (적재시각, 적재자, 적재 시점의 region 데이터 버전 시각)
        last_change = {}                # rid → 마지막 데이터 변경 시각
        n_q = hits = cross = stale_hits = 0
        n_check = own_stale = 0
        evict_dels = 0

        for ev in events:
            t, uid, kind = ev[0], ev[1], ev[2]
            if kind == "run_finish":
                affected = regions_covering(ev[3], ev[4])
                for rid in affected:
                    last_change[rid] = t
                    if policy["evict"]:
                        for variant in (f"{rid}", f"{rid}:ios", f"{rid}:aos"):
                            if variant in cache:
                                del cache[variant]
                                evict_dels += 1
                continue

            # query
            lat, lng, region_key, is_post_run = ev[3], ev[4], ev[5], ev[6]
            n_q += 1
            if is_post_run:
                n_check += 1
            if region_key is None:      # 탐색 — 캐시 우회 (항상 최신)
                continue
            rid = int(region_key.split(":")[0])
            entry = cache.get(region_key)
            if entry and t - entry[0] < policy["ttl"]:
                hits += 1
                if entry[1] != uid:
                    cross += 1
                if entry[2] < last_change.get(rid, -1):
                    stale_hits += 1     # 적재 이후 데이터가 바뀐 엔트리를 서빙
                if is_post_run and entry[0] < last_change.get(rid, -1):
                    own_stale += 1      # 완주 확인인데 내 완주 이전 적재분을 받음
            else:
                cache[region_key] = (t, uid, last_change.get(rid, -1))

        results[pname] = dict(
            hit=hits / n_q, cross=cross / n_q,
            stale=stale_hits / max(hits, 1),
            own_stale=own_stale / max(n_check, 1),
            evicts=evict_dels, n_q=n_q, n_check=n_check)
    return results


if __name__ == "__main__":
    dau_list = [int(x) for x in sys.argv[1:]] or [10000, 30000]
    print("모델: v3 세계 + 러닝(세션 40%, 20~60분) + 완주 후 90%가 10~120s 내 등수 확인")
    print("(DAU 10만 미만: 동네 400개(서울 유사) / 이상: 전국 3,600개 — 국민앱 가정)\n")
    for dau in dau_list:
        if dau >= 100_000:
            make_world(3600, 60)
        else:
            make_world(REGIONS, GRID)
        r = simulate(dau)
        any_p = next(iter(r.values()))
        print(f"◆ DAU {dau:,} (조회 {any_p['n_q']:,}건, 완주 확인 {any_p['n_check']:,}건)")
        print(f"{'정책':<17} {'전체 히트':>7} {'타인 공유':>7} {'스테일 서빙':>8} {'본인 미반영':>8} {'이빅트 DEL':>8}")
        for pname, m in r.items():
            print(f"{pname:<17} {m['hit']:>8.1%} {m['cross']:>8.1%} "
                  f"{m['stale']:>9.1%} {m['own_stale']:>9.1%} {m['evicts']:>9,}")
        print()
