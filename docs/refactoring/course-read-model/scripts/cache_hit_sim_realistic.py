#!/usr/bin/env python3
"""
코스 지도 캐시키 전략 시뮬레이션 v3 — 현실 세계 모델 (냉정 버전)

v2(균일 포아송·무개체 트래픽)의 두 가지 거짓말을 걷어낸다:
  ① 트래픽은 하루 균등이 아니다 — 러닝 앱은 아침/저녁 피크에 몰린다 (TTL 60s 캐시는 순간 RPS가 결정)
  ② 요청에는 개체가 있다 — 같은 사용자의 새로고침(60초 내 같은 키)은 어떤 전략이든 공짜 히트다.
     전략 간 차이는 오직 "서로 다른 사용자가 같은 동네"에서만 난다 → 히트를 자기/타인으로 분해 측정

[현실 가정 — 2026-08 사용자 확인]
- DAU ~수백 (성장 스윕: 100~10,000)
- 세션: 사용자당 하루 1~3회 진입(0.6/0.3/0.1), 진입당 새로고침 기하분포 p=0.5 (평균 ~1회, 5~30s 간격)
- 탐색: 세션의 30%에서 팬/줌 1~3회 (집에서 1~8km 지점, 뷰포트 반경 큼)
- 시간대: 아침(05~08)·저녁(18~22) 피크 — 저녁 4시간에 하루 트래픽 ~45%
- 기기: iOS:Android = 5:5. 지역의 30%는 플랫폼 간 명칭이 달라 키가 갈라진다고 가정 (NAME_MISMATCH_REGION_RATIO)
- 사용자 위치: 자기 동네 중심 σ=400m에 고정 (요청마다 흔들리지 않음 — GPS 지터는 키에 무영향 수준)
- FE 완전 침투 가정 (배포 과도기 제외 — 정상 상태 측정)

[측정]
- 전체 히트율 / 타인 공유 히트율(전략의 실력) / 자기 히트율(공짜) / 생성 키 수
- regionId는 탐색 요청 캐시 우회(설계) — 탐색은 히트율 분모에 포함 (보수적 집계)

사용법: python3 cache_hit_sim_realistic.py [DAU ...]   (기본: 100 300 1000 3000 10000)
"""
import math
import random
import sys

random.seed(42)

# ---- 공간 ----
REGIONS = 400
GRID = 20
SPACING_M = 1200
SIGMA_M = 400
BASE_LAT, BASE_LNG = 37.45, 126.90
M_PER_DEG_LAT = 111_320.0
M_PER_DEG_LNG = 111_320.0 * math.cos(math.radians(37.55))

# ---- 행동 ----
TTL_S = 60
DAY_S = 86_400
SESSIONS_DIST = ([1] * 6 + [2] * 3 + [3] * 1)          # 하루 진입 횟수 분포
REFRESH_P = 0.5                                         # 새로고침 기하분포
EXPLORE_SESSION_P = 0.3                                 # 탐색이 있는 세션 비율
NAME_MISMATCH_REGION_RATIO = 0.3                        # 플랫폼 간 명칭이 다른 지역 비율
ZIPF_A = 1.1

# 시간대 가중치 (0시~23시) — 저녁 18~21시에 하루의 ~45%
HOURLY_W = [0.2, 0.1, 0.1, 0.1, 0.4, 1.6, 2.4, 1.8, 1.0, 0.8, 0.7, 0.8,
            0.9, 0.8, 0.7, 0.8, 1.2, 2.0, 4.2, 4.6, 4.0, 3.0, 1.6, 0.8]

GEOHASH_BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"


def geohash(lat, lng, precision):
    lat_rng, lng_rng = [-90.0, 90.0], [-180.0, 180.0]
    bits, even, ch, out = 0, True, 0, []
    while len(out) < precision:
        if even:
            mid = (lng_rng[0] + lng_rng[1]) / 2
            if lng >= mid:
                ch, lng_rng[0] = ch * 2 + 1, mid
            else:
                ch, lng_rng[1] = ch * 2, mid
        else:
            mid = (lat_rng[0] + lat_rng[1]) / 2
            if lat >= mid:
                ch, lat_rng[0] = ch * 2 + 1, mid
            else:
                ch, lat_rng[1] = ch * 2, mid
        even = not even
        bits += 1
        if bits == 5:
            out.append(GEOHASH_BASE32[ch])
            bits, ch = 0, 0
    return "".join(out)


# ---- 세계 생성 ----
region_centers = []
for i in range(REGIONS):
    gx, gy = i % GRID, i // GRID
    lat = BASE_LAT + (gy * SPACING_M + random.uniform(-300, 300)) / M_PER_DEG_LAT
    lng = BASE_LNG + (gx * SPACING_M + random.uniform(-300, 300)) / M_PER_DEG_LNG
    region_centers.append((lat, lng))

zipf_w = [1 / (r + 1) ** ZIPF_A for r in range(REGIONS)]
random.shuffle(zipf_w)
mismatch_regions = set(random.sample(range(REGIONS), int(REGIONS * NAME_MISMATCH_REGION_RATIO)))

SCHEMES = ["반올림(1.1km)", "geohash p6", "regionId"]


def draw_session_start():
    hour = random.choices(range(24), weights=HOURLY_W, k=1)[0]
    return hour * 3600 + random.uniform(0, 3600)


def geometric(p):
    n = 0
    while random.random() > p:
        n += 1
    return n


def build_events(dau):
    """(시각, userId, 종류, lat, lng, homeRegionId|None) 이벤트 목록"""
    events = []
    for uid in range(dau):
        rid = random.choices(range(REGIONS), weights=zipf_w, k=1)[0]
        clat, clng = region_centers[rid]
        home_lat = clat + random.gauss(0, SIGMA_M) / M_PER_DEG_LAT
        home_lng = clng + random.gauss(0, SIGMA_M) / M_PER_DEG_LNG
        platform = "ios" if uid % 2 == 0 else "aos"
        # 명칭 분화 지역이면 플랫폼별로 키가 갈라진다
        region_key = f"{rid}:{platform}" if rid in mismatch_regions else f"{rid}"

        for _ in range(random.choice(SESSIONS_DIST)):
            t = draw_session_start()
            events.append((t, uid, "home", home_lat, home_lng, region_key))
            for _ in range(geometric(REFRESH_P)):                    # 새로고침 — 같은 위치
                t += random.uniform(5, 30)
                events.append((t, uid, "home", home_lat, home_lng, region_key))
            if random.random() < EXPLORE_SESSION_P:                  # 탐색 — 팬/줌
                for _ in range(random.randint(1, 3)):
                    t += random.uniform(20, 90)
                    dist = random.uniform(1000, 8000)
                    ang = random.uniform(0, 2 * math.pi)
                    events.append((t, uid, "explore",
                                   home_lat + dist * math.sin(ang) / M_PER_DEG_LAT,
                                   home_lng + dist * math.cos(ang) / M_PER_DEG_LNG,
                                   None))
    events.sort(key=lambda e: e[0])
    return events


def simulate(dau):
    events = build_events(dau)
    caches = {s: {} for s in SCHEMES}                    # key → (적재 시각, 적재 userId)
    total = len(events)
    hits_self = {s: 0 for s in SCHEMES}
    hits_cross = {s: 0 for s in SCHEMES}
    keys_created = {s: 0 for s in SCHEMES}

    for t, uid, kind, lat, lng, region_key in events:
        keys = {
            "반올림(1.1km)": f"{round(lat, 2)}:{round(lng, 2)}",
            "geohash p6": geohash(lat, lng, 6),
            "regionId": region_key if kind == "home" else None,   # 탐색은 캐시 우회 (설계)
        }
        for scheme, key in keys.items():
            if key is None:
                continue                                  # 우회 — 분모에는 포함(미스 취급)
            cache = caches[scheme]
            entry = cache.get(key)
            if entry and t - entry[0] < TTL_S:
                if entry[1] == uid:
                    hits_self[scheme] += 1
                else:
                    hits_cross[scheme] += 1
            else:
                cache[key] = (t, uid)
                keys_created[scheme] += 1

    return {s: dict(total=total,
                    overall=(hits_self[s] + hits_cross[s]) / total,
                    cross=hits_cross[s] / total,
                    self_=hits_self[s] / total,
                    keys=keys_created[s]) for s in SCHEMES}


if __name__ == "__main__":
    dau_list = [int(x) for x in sys.argv[1:]] or [100, 300, 1000, 3000, 10000]
    print("모델: 저녁 피크 집중, 새로고침 기하(p=0.5), 탐색 세션 30%, "
          f"명칭 분화 지역 {NAME_MISMATCH_REGION_RATIO:.0%} (iOS:AOS=5:5), TTL {TTL_S}s\n")
    for dau in dau_list:
        r = simulate(dau)
        total = r[SCHEMES[0]]["total"]
        print(f"◆ DAU {dau:,} (하루 요청 {total:,}건)")
        print(f"{'전략':<14} {'전체 히트':>7} {'타인 공유':>7} {'자기(새로고침)':>10} {'생성 키':>7}")
        for s in SCHEMES:
            print(f"{s:<14} {r[s]['overall']:>8.1%} {r[s]['cross']:>8.1%} "
                  f"{r[s]['self_']:>11.1%} {r[s]['keys']:>8,}")
        print()
