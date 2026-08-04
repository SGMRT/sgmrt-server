#!/usr/bin/env python3
"""
코스 지도 캐시키 전략별 히트율 시뮬레이션 v2 (cache/06-home-locality-analysis.md 근거 스크립트)

[트래픽 모델 — 서비스 특성 반영]
- 러닝 앱 홈 트래픽의 본질: "각자 자기 동네(집 주변)에서 앱을 연다"
- 요청 = 홈 진입/새로고침 (1-EXPLORE_RATIO) + 지도 탐색(스크롤/팬) (EXPLORE_RATIO)
  - 홈 진입: 사용자의 집 동네 중심에서 σ=SIGMA_M 가우시안 위치
  - 탐색: 임의 지점 (절반은 핫플레이스 주변, 절반은 완전 무작위) — 좌표 long-tail
- 동네별 인구 가중치는 Zipf 분포 (핫한 동네에 트래픽 집중)
- 요청은 포아송 도착, 캐시 TTL 60초

[방안별 캐시 동작 — 설계와 동일]
- 반올림/지오해시: 키 = 좌표 유도 → 홈/탐색 요청 구분 불가, 둘 다 캐시 (탐색이 키 공간 오염)
- regionId: 홈 요청만 키 있음(동네 ID), 탐색은 캐시 우회(설계 §7-3 — 항상 DB 직행이자 항상 최신)
  OS 지오코딩 명칭 분화율 NAME_SPLIT 만큼 키 분열 노이즈 반영

[지표]
- 전체 히트율: 전 요청 대비 (탐색 우회는 미스로 집계 — regionId에 불리한 보수적 기준)
- 홈 히트율: 홈 진입 요청만 대비 (캐시가 실제 겨냥하는 트래픽)
- 생성 키 수: TTL 동안 적재된 총 엔트리 수 (메모리·키 공간 오염 지표)
- 스냅 오차: 홈 요청에서 사용자 위치 ↔ 캐시 값 기준점(셀/동네 중심) 평균 거리

사용법: python3 cache_hit_sim.py [초당요청수 ...]
"""
import math
import random
import sys

random.seed(42)

# ---- 파라미터 ----------------------------------------------------------------
REGIONS = 400            # 동네 수 (서울 행정동 ~426개)
GRID = 20                # 동네 배치 격자 (20x20)
SPACING_M = 1200         # 동네 간격 ~1.2km
SIGMA_M = 400            # 집 동네 중심 대비 사용자 위치 표준편차
TTL_S = 60               # 캐시 TTL
SIM_S = 3600             # 시뮬레이션 시간 (1시간)
ZIPF_A = 1.1             # 인구 가중치 왜도
NAME_SPLIT = 0.05        # OS 지오코딩 명칭 분화율 (iOS/Android 다른 이름 → 키 분열)
EXPLORE_RATIO = 0.2      # 탐색(스크롤) 요청 비율 — 홈 80% : 탐색 20%

BASE_LAT, BASE_LNG = 37.45, 126.90
M_PER_DEG_LAT = 111_320.0
M_PER_DEG_LNG = 111_320.0 * math.cos(math.radians(37.55))
AREA_M = GRID * SPACING_M  # 시뮬레이션 영역 한 변 (~24km)

GEOHASH_BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"


def geohash(lat, lng, precision):
    lat_rng, lng_rng = [-90.0, 90.0], [-180.0, 180.0]
    bits, even, ch, out = 0, True, 0, []
    while len(out) < precision:
        if even:
            mid = (lng_rng[0] + lng_rng[1]) / 2
            if lng >= mid:
                ch = ch * 2 + 1
                lng_rng[0] = mid
            else:
                ch *= 2
                lng_rng[1] = mid
        else:
            mid = (lat_rng[0] + lat_rng[1]) / 2
            if lat >= mid:
                ch = ch * 2 + 1
                lat_rng[0] = mid
            else:
                ch *= 2
                lat_rng[1] = mid
        even = not even
        bits += 1
        if bits == 5:
            out.append(GEOHASH_BASE32[ch])
            bits, ch = 0, 0
    return "".join(out)


# ---- 동네 배치 + 인구 가중치 --------------------------------------------------
region_centers = []
for i in range(REGIONS):
    gx, gy = i % GRID, i // GRID
    lat = BASE_LAT + (gy * SPACING_M + random.uniform(-300, 300)) / M_PER_DEG_LAT
    lng = BASE_LNG + (gx * SPACING_M + random.uniform(-300, 300)) / M_PER_DEG_LNG
    region_centers.append((lat, lng))

weights = [1 / (r + 1) ** ZIPF_A for r in range(REGIONS)]
random.shuffle(weights)
total_w = sum(weights)
weights = [w / total_w for w in weights]

SCHEMES = ["반올림(1.1km)", "geohash p6", "geohash p5", "regionId"]


def dist_m(lat1, lng1, lat2, lng2):
    return math.hypot((lat1 - lat2) * M_PER_DEG_LAT, (lng1 - lng2) * M_PER_DEG_LNG)


def simulate(rps):
    n_requests = int(SIM_S * rps)
    times = sorted(random.uniform(0, SIM_S) for _ in range(n_requests))

    caches = {s: {} for s in SCHEMES}
    hits = {s: 0 for s in SCHEMES}
    home_hits = {s: 0 for s in SCHEMES}
    keys_created = {s: 0 for s in SCHEMES}
    snap_sum = {s: 0.0 for s in SCHEMES}
    n_home = 0

    for t in times:
        is_home = random.random() >= EXPLORE_RATIO

        if is_home:
            n_home += 1
            rid = random.choices(range(REGIONS), weights=weights, k=1)[0]
            clat, clng = region_centers[rid]
            lat = clat + random.gauss(0, SIGMA_M) / M_PER_DEG_LAT
            lng = clng + random.gauss(0, SIGMA_M) / M_PER_DEG_LNG
        else:
            # 탐색: 절반은 핫플레이스(인기 동네) 주변 넓은 산포, 절반은 완전 무작위 지점
            if random.random() < 0.5:
                rid = random.choices(range(REGIONS), weights=weights, k=1)[0]
                clat, clng = region_centers[rid]
                lat = clat + random.gauss(0, 2000) / M_PER_DEG_LAT
                lng = clng + random.gauss(0, 2000) / M_PER_DEG_LNG
            else:
                lat = BASE_LAT + random.uniform(0, AREA_M) / M_PER_DEG_LAT
                lng = BASE_LNG + random.uniform(0, AREA_M) / M_PER_DEG_LNG

        # (키, 값 기준점 anchor). 탐색 요청은 regionId 방식에서 캐시 우회(None)
        name_variant = 1 if random.random() < NAME_SPLIT else 0
        entries = {
            "반올림(1.1km)": (f"{round(lat, 2)}:{round(lng, 2)}", round(lat, 2), round(lng, 2)),
            "geohash p6": (geohash(lat, lng, 6),
                           (math.floor(lat / (180 / 2**15)) + 0.5) * (180 / 2**15),
                           (math.floor(lng / (360 / 2**15)) + 0.5) * (360 / 2**15)),
            "geohash p5": (geohash(lat, lng, 5),
                           (math.floor(lat / (180 / 2**12)) + 0.5) * (180 / 2**12),
                           (math.floor(lng / (360 / 2**13)) + 0.5) * (360 / 2**13)),
            "regionId": (f"{rid}:{name_variant}", *region_centers[rid]) if is_home else None,
        }

        for scheme, entry in entries.items():
            if entry is None:
                continue  # 캐시 우회 — 미스로도 적재로도 집계하지 않고 DB 직행 (히트율 분모에는 포함)
            key, alat, alng = entry
            if is_home:
                snap_sum[scheme] += dist_m(lat, lng, alat, alng)
            cache = caches[scheme]
            if key in cache and t - cache[key] < TTL_S:
                hits[scheme] += 1
                if is_home:
                    home_hits[scheme] += 1
                # 히트는 TTL을 연장하지 않음 (Spring @Cacheable 동작과 동일)
            else:
                cache[key] = t
                keys_created[scheme] += 1

    return {
        "overall": {s: hits[s] / n_requests for s in SCHEMES},
        "home": {s: home_hits[s] / n_home for s in SCHEMES},
        "keys": dict(keys_created),
        "snap": {s: snap_sum[s] / n_home for s in SCHEMES},
        "n": n_requests,
        "n_home": n_home,
    }


if __name__ == "__main__":
    rps_list = [float(x) for x in sys.argv[1:]] or [0.5, 2, 10]
    print(f"모델: 동네 {REGIONS}개, σ={SIGMA_M}m, TTL={TTL_S}s, Zipf(a={ZIPF_A}), "
          f"홈 {1-EXPLORE_RATIO:.0%} : 탐색 {EXPLORE_RATIO:.0%}, 명칭 분화 {NAME_SPLIT:.0%}\n")
    for rps in rps_list:
        r = simulate(rps)
        print(f"◆ 초당 {rps:g}건 (총 {r['n']:,}건, 홈 {r['n_home']:,}건)")
        print(f"{'방안':<14} {'전체 히트율':>8} {'홈 히트율':>8} {'생성 키 수':>9} {'스냅 오차':>8}")
        for s in SCHEMES:
            print(f"{s:<14} {r['overall'][s]:>9.1%} {r['home'][s]:>9.1%} "
                  f"{r['keys'][s]:>10,} {r['snap'][s]:>7.0f}m")
        print()
