-- ================================================
-- GhostRunner 스키마 (JPA 엔티티 외 테이블)
-- ================================================

-- ShedLock 테이블 (분산 스케줄러 락)
CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
