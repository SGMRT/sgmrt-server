-- ============================================================================
-- CourseReadModel 마이그레이션 + 백필 SQL (MySQL 8.0)
--
-- 목적: 리드모델 테이블을 만들고(신규 환경) 기존 course/running_record 데이터로
--       리드모델을 채운다(백필). PR-2의 Java 백필 러너를 대체할 수 있는 일회성 SQL.
--
-- [실행 순서]
--   ① 기존 환경: pr1.sql(ALTER) 선적용  /  신규 환경: 아래 §1 CREATE TABLE
--   ② PR-1 코드 배포
--   ③ §2 백필 실행 (배포 직후 1회 — 새 쓰기는 코드가 실시간 반영하므로 순서 안전)
--   ④ §3 검증 쿼리로 확인
--
-- [멱등성] §2-1 INSERT는 NOT EXISTS 가드로, §2-2 UPDATE는 전체 덮어쓰기라 재실행 안전.
-- [정합성 기준] 집계 모집단 = is_public ∧ ¬deleted ∧ ¬has_paused (Q1·Q2, 코드의
--   CourseReadModelRepository 재계산 쿼리와 동일). 탈퇴 러너는 제외하지 않는다(요구사항).
-- ============================================================================


-- ============================================================================
-- §1. 테이블 생성 (신규 환경용 — Hibernate 생성 스키마와 동일 구조)
--     기존 환경(dev/prod)은 테이블이 이미 있으므로 §1을 건너뛰고 pr1.sql만 적용.
-- ============================================================================
CREATE TABLE IF NOT EXISTS course_read_model (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    course_id            BIGINT       NOT NULL,
    name                 VARCHAR(255) NOT NULL,
    owner_uuid           VARCHAR(36)  NULL,                -- OFFICIAL/더미 코스는 소유자 없음
    route_url            TEXT         NOT NULL,
    thumbnail_url        TEXT         NULL,
    distance_km          DOUBLE       NOT NULL,
    elevation_average_m  DOUBLE       NULL,
    elevation_gain_m     DOUBLE       NULL,
    elevation_loss_m     DOUBLE       NULL,
    start_lat            DOUBLE       NOT NULL,
    start_lng            DOUBLE       NOT NULL,
    is_public            BIT(1)       NOT NULL,
    source               ENUM('OFFICIAL','RECOMMENDED','USER') NOT NULL,
    top1_member_id       BIGINT       NULL,
    top1_time_seconds    INT          NULL,
    top2_member_id       BIGINT       NULL,
    top2_time_seconds    INT          NULL,
    top3_member_id       BIGINT       NULL,
    top3_time_seconds    INT          NULL,
    top4_member_id       BIGINT       NULL,
    top4_time_seconds    INT          NULL,
    runners_count        BIGINT       NOT NULL DEFAULT 0,
    created_at           DATETIME(6)  NULL,
    updated_at           DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_course_id (course_id),                  -- X락이 레코드락으로 잡히는 근거
    KEY idx_is_public_location (is_public, start_lat, start_lng)  -- 지도 바운딩박스 조회
);


-- ============================================================================
-- §2. 백필
-- ============================================================================

-- §2-1. 리드모델 행 생성 — 공개·미삭제 코스 중 리드모델이 없는 것만 (멱등)
--   공개 코스는 등록 시 이름이 강제되므로 name NOT NULL 제약과 충돌하지 않는다.
--   집계 컬럼(TOP4/runners_count)은 §2-2가 채우므로 여기선 초기값만.
INSERT INTO course_read_model
    (course_id, name, owner_uuid, route_url, thumbnail_url,
     distance_km, elevation_average_m, elevation_gain_m, elevation_loss_m,
     start_lat, start_lng, is_public, source, runners_count,
     created_at, updated_at)
SELECT
    c.id, c.name, m.uuid, c.route_url, c.thumbnail_url,
    c.distance_km, c.elevation_average_m, c.elevation_gain_m, c.elevation_loss_m,
    c.start_latitude, c.start_longtitude,                 -- 원본 컬럼명 오타 그대로 (start_longtitude)
    c.is_public, c.source, 0,
    NOW(6), NOW(6)
FROM course c
LEFT JOIN member m ON m.id = c.member_id                  -- 탈퇴 회원도 uuid 유지(소프트삭제)
WHERE c.is_public = TRUE
  AND c.deleted = FALSE
  AND NOT EXISTS (SELECT 1 FROM course_read_model rm WHERE rm.course_id = c.id);

-- §2-2. TOP4 + runners_count 전체 재계산 (멱등 — 항상 원본 기준으로 덮어씀)
--   윈도우 함수로 코스×멤버별 최고 기록에 순위를 매기고 1~4위를 피벗해서 채운다.
--   동률(같은 기록)은 member_id 오름차순으로 결정적으로 처리.
--   러닝이 하나도 없는 코스는 agg가 NULL → TOP4 전부 NULL + runners_count 0 (정상).
UPDATE course_read_model rm
LEFT JOIN (
    SELECT
        course_id,
        MAX(CASE WHEN ranking = 1 THEN member_id END)                 AS top1_member_id,
        CAST(MAX(CASE WHEN ranking = 1 THEN best_duration END) AS SIGNED) AS top1_time_seconds,
        MAX(CASE WHEN ranking = 2 THEN member_id END)                 AS top2_member_id,
        CAST(MAX(CASE WHEN ranking = 2 THEN best_duration END) AS SIGNED) AS top2_time_seconds,
        MAX(CASE WHEN ranking = 3 THEN member_id END)                 AS top3_member_id,
        CAST(MAX(CASE WHEN ranking = 3 THEN best_duration END) AS SIGNED) AS top3_time_seconds,
        MAX(CASE WHEN ranking = 4 THEN member_id END)                 AS top4_member_id,
        CAST(MAX(CASE WHEN ranking = 4 THEN best_duration END) AS SIGNED) AS top4_time_seconds,
        COUNT(*)                                                      AS runners_count
    FROM (
        SELECT course_id, member_id, best_duration,
               ROW_NUMBER() OVER (
                   PARTITION BY course_id
                   ORDER BY best_duration ASC, member_id ASC
               ) AS ranking
        FROM (
            SELECT r.course_id, r.member_id, MIN(r.duration_sec) AS best_duration
            FROM running_record r
            WHERE r.is_public = TRUE
              AND r.deleted = FALSE
              AND r.has_paused = FALSE                    -- Q1: 일시정지 제외
            GROUP BY r.course_id, r.member_id             -- idx_record_course 루즈 인덱스 스캔
        ) best_per_member
    ) ranked
    GROUP BY course_id
) agg ON agg.course_id = rm.course_id
SET rm.top1_member_id    = agg.top1_member_id,
    rm.top1_time_seconds = agg.top1_time_seconds,
    rm.top2_member_id    = agg.top2_member_id,
    rm.top2_time_seconds = agg.top2_time_seconds,
    rm.top3_member_id    = agg.top3_member_id,
    rm.top3_time_seconds = agg.top3_time_seconds,
    rm.top4_member_id    = agg.top4_member_id,
    rm.top4_time_seconds = agg.top4_time_seconds,
    rm.runners_count     = COALESCE(agg.runners_count, 0),
    rm.updated_at        = NOW(6);


-- ============================================================================
-- §3. 검증 쿼리
-- ============================================================================

-- ① 커버리지: 공개·미삭제 코스 수 == 리드모델 수 (둘이 같아야 함)
SELECT
    (SELECT COUNT(*) FROM course WHERE is_public = TRUE AND deleted = FALSE)                    AS public_courses,
    (SELECT COUNT(*) FROM course_read_model rm
       JOIN course c ON c.id = rm.course_id WHERE c.is_public = TRUE AND c.deleted = FALSE)     AS backfilled;

-- ② ALTER 기본값(0) 잔여 행 확인 — 0행이어야 백필 완료 (pr1.sql DEFAULT 0 흔적 검출)
SELECT rm.course_id, rm.name
FROM course_read_model rm JOIN course c ON c.id = rm.course_id
WHERE rm.distance_km = 0 AND c.distance_km <> 0;

-- ③ 샘플 정합: 임의 코스의 TOP4 vs 원본 실집계 비교 (코스 ID 바꿔가며 스팟체크)
SELECT r.member_id, MIN(r.duration_sec) AS best
FROM running_record r
WHERE r.course_id = 1 AND r.is_public = TRUE AND r.deleted = FALSE AND r.has_paused = FALSE
GROUP BY r.member_id ORDER BY best ASC, r.member_id ASC LIMIT 4;
SELECT top1_member_id, top1_time_seconds, top2_member_id, top2_time_seconds,
       top3_member_id, top3_time_seconds, top4_member_id, top4_time_seconds, runners_count
FROM course_read_model WHERE course_id = 1;

-- ④ runners_count 드리프트 전수 검사 — 0행이어야 정상
SELECT rm.course_id, rm.runners_count AS in_read_model, agg.cnt AS actual
FROM course_read_model rm
LEFT JOIN (
    SELECT course_id, COUNT(DISTINCT member_id) AS cnt
    FROM running_record
    WHERE is_public = TRUE AND deleted = FALSE AND has_paused = FALSE
    GROUP BY course_id
) agg ON agg.course_id = rm.course_id
WHERE rm.runners_count <> COALESCE(agg.cnt, 0);
