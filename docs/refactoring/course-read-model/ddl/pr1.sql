-- ============================================================================
-- PR-1 (CourseReadModel 역정규화 컬럼 확장) 수동 DDL
--
-- 설계 문서: docs/refactoring/course-read-model/04-detailed-design.md §4 "운영 주의"
--
-- [적용 순서] 이 DDL은 PR-1 코드 배포 *이전*에 적용한다.
--   미적용 상태로 코드가 배포되면 Hibernate 가 생성하는 모든 INSERT/SELECT 가 신규 컬럼을
--   포함하므로 `Unknown column 'crm1_0.distance_km' in 'field list'` 로 리드모델을 읽거나 쓰는
--   전 경로가 실패한다. CourseReadModelWriter 는 @Transactional(MANDATORY) 로 호출자
--   트랜잭션에 묶여 있으므로, 리드모델 실패가 러닝 종료(createRun)·러닝 삭제·코스 공개 전환까지
--   롤백시킨다. 즉 사용자가 방금 뛴 기록이 유실된다.
--
-- [적용 대상] prod / dev 중 ddl-auto 가 create 가 아닌 환경.
--   prod 의 spring.jpa.hibernate.ddl-auto 는 외부 주입 값이라 저장소 코드로 확인할 수 없다.
--   단, 그 값이 `update` 여도 Hibernate 는 컬럼 추가만 수행하고 기존 컬럼 정의는 바꾸지 않으므로
--   `MODIFY owner_uuid ... NULL` 은 어느 경우든 수동 적용이 필요하다.
--   (`validate`/`none` 이면 ADD COLUMN 까지 전부 수동 적용 대상)
--
-- [멱등성] MySQL 은 ADD COLUMN IF NOT EXISTS 를 지원하지 않는다. 재실행 시 이미 존재하는
--   컬럼에서 실패하므로, 적용 전 `SHOW CREATE TABLE course_read_model;` 로 상태를 확인한다.
-- ============================================================================

ALTER TABLE course_read_model
    ADD COLUMN distance_km          DOUBLE       NOT NULL DEFAULT 0,
    ADD COLUMN elevation_average_m  DOUBLE       NULL,
    ADD COLUMN elevation_gain_m     DOUBLE       NULL,
    ADD COLUMN elevation_loss_m     DOUBLE       NULL,
    ADD COLUMN thumbnail_url        TEXT         NULL,
    MODIFY COLUMN owner_uuid        VARCHAR(36)  NULL;

-- distance_km 에 DEFAULT 0 을 두는 이유:
--   NOT NULL 컬럼을 기본값 없이 추가하면 기존 행 처리 방식이 sql_mode 에 따라 갈린다
--   (STRICT 모드에서는 에러). 명시적 기본값을 주면 결정적으로 동작하며,
--   PR-2 백필이 실제 코스 거리 값으로 덮어쓴다.

-- 적용 확인
--   SHOW CREATE TABLE course_read_model;
--   -> distance_km / elevation_average_m / elevation_gain_m / elevation_loss_m / thumbnail_url 5개 존재
--   -> owner_uuid 가 NULL 허용


-- ============================================================================
-- [롤백 DDL]
--
-- ALTER TABLE course_read_model
--     DROP COLUMN distance_km,
--     DROP COLUMN elevation_average_m,
--     DROP COLUMN elevation_gain_m,
--     DROP COLUMN elevation_loss_m,
--     DROP COLUMN thumbnail_url;
--
-- 주의: owner_uuid 는 되돌리지 않는다.
--   nullable 완화 이후 OFFICIAL/더미 코스의 리드모델이 owner_uuid = NULL 로 생성됐을 수 있으므로,
--   `MODIFY COLUMN owner_uuid VARCHAR(36) NOT NULL` 복원은 기존 NULL 행 때문에 실패한다.
--   NOT NULL 로 되돌려야 한다면 먼저 NULL 행을 정리(또는 삭제)해야 한다.
--   owner_uuid 를 NULL 허용으로 남겨두는 것 자체는 구버전 코드와 호환된다(구버전은 항상 값을 채움).
-- ============================================================================
