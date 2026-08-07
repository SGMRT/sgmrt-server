-- ============================================================================
-- region 테이블 생성 SQL (MySQL 8.0)
--
-- 목적: 지도 캐시키(regionId) 발급을 위한 지역 테이블 생성.
--       설계: docs/refactoring/course-read-model/cache/05-cache-key-design.md §3
--
-- [실행 순서] (cache/05 §9 롤아웃)
--   ① BE 배포 전 본 스크립트 실행 (dev/prod — 테스트/로컬은 ddl-auto: create 로 자동 생성)
--   ② BE 배포 (POST /v1/regions + GET /v1/courses regionId 파라미터)
--   ③ FE 배포 — 이때부터 행이 쌓이기 시작
--
-- [백필 없음] 데이터는 사용자 요청(멱등 resolve)이 채운다 — 사전 적재 불필요.
-- [멱등성] CREATE TABLE IF NOT EXISTS — 재실행 안전.
-- ============================================================================

CREATE TABLE IF NOT EXISTS region (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,   -- 시 구 동 전체 경로: "서울특별시 강남구 역삼동" (동명 지역 충돌 방지)
    center_lat  DOUBLE       NOT NULL,   -- 대표좌표 = 최초 등록 요청의 좌표 (이후 불변)
    center_lng  DOUBLE       NOT NULL,
    created_at  DATETIME(6)  NULL,
    updated_at  DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_region_name (name)     -- 동시 등록 경쟁의 최종 심판 (RegionService 복구 로직의 근거)
);

-- 검증: 배포 후 행이 쌓이는지 확인
-- SELECT COUNT(*), MIN(created_at), MAX(created_at) FROM region;
