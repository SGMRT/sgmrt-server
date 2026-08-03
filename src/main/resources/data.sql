-- ================================================
-- GhostRunner 더미 데이터
-- ================================================

-- Member 테이블
INSERT INTO member (id, uuid, nickname, gender, age, weight, height, profile_picture_url, role, last_login_at, created_at, updated_at, deleted_at)
VALUES
    (1, 'test-uuid-001', '러너원', 'MALE', 28, 70, 175, 'https://example.com/profile1.png', 'USER', NOW(), NOW(), NOW(), NULL),
    (2, 'test-uuid-002', '러너투', 'FEMALE', 25, 55, 163, 'https://example.com/profile2.png', 'USER', NOW(), NOW(), NOW(), NULL),
    (3, 'test-uuid-003', '관리자', 'MALE', 30, 75, 180, 'https://example.com/admin.png', 'ADMIN', NOW(), NOW(), NOW(), NULL);

-- MemberAuthInfo 테이블
INSERT INTO member_auth_info (id, external_auth_uid, member_id)
VALUES
    (1, 'firebase-uid-001', 1),
    (2, 'firebase-uid-002', 2),
    (3, 'firebase-uid-003', 3);

-- MemberSettings 테이블
INSERT INTO member_settings (id, member_id, push_alarm_enabled, vibration_enabled, voice_guidance_enabled)
VALUES
    (1, 1, true, true, true),
    (2, 2, true, false, true),
    (3, 3, true, true, true);

-- MemberVdot 테이블
INSERT INTO member_vdot (id, vdot, member_id)
VALUES
    (1, 45, 1),
    (2, 40, 2),
    (3, 50, 3);

-- Course 테이블
INSERT INTO course (id, member_id, name, distance_km, elevation_average_m, elevation_gain_m, elevation_loss_m,
                    start_latitude, start_longtitude, source, is_public, route_url, checkpoints_url, thumbnail_url,
                    deleted, created_at, updated_at)
VALUES
    (1, 1, '한강 러닝 코스', 5.0, 10.5, 20.0, 18.0, 37.5283, 126.9340, 'USER', true,
     'https://example.com/routes/1.json', 'https://example.com/checkpoints/1.json', 'https://example.com/thumbnails/1.png',
     false, NOW(), NOW()),
    (2, 1, '남산 등산 코스', 3.5, 150.0, 250.0, 230.0, 37.5512, 126.9882, 'USER', false,
     'https://example.com/routes/2.json', 'https://example.com/checkpoints/2.json', 'https://example.com/thumbnails/2.png',
     false, NOW(), NOW()),
    (3, 2, '올림픽공원 코스', 7.2, 5.0, 15.0, 15.0, 37.5202, 127.1213, 'USER', true,
     'https://example.com/routes/3.json', 'https://example.com/checkpoints/3.json', 'https://example.com/thumbnails/3.png',
     false, NOW(), NOW()),
    (4, 2, '서울숲 공식 코스', 4.0, 8.0, 10.0, 10.0, 37.5443, 127.0374, 'OFFICIAL', true,
     'https://example.com/routes/4.json', 'https://example.com/checkpoints/4.json', 'https://example.com/thumbnails/4.png',
     false, NOW(), NOW());

-- CourseSubscription 테이블
-- 시나리오 설명:
-- 1. 코스1 (한강): 주인(러너원) 등록 상태, 러너투도 따라뜀
-- 2. 코스2 (남산): 주인(러너원) 등록한 적 없음 → subscription 없음
-- 3. 코스3 (올림픽공원): 주인(러너투) 등록 상태, 러너원도 따라뜀
-- 4. 코스4 (서울숲): 주인(러너투) 등록 해제함, 러너원은 따라뛴 상태
INSERT INTO course_subscription (id, course_id, member_id, deleted, created_at, updated_at)
VALUES
    -- 코스1: 러너원(주인) 등록 상태, 러너투 따라뜀
    (1, 1, 1, false, NOW(), NOW()),  -- 주인(러너원) 등록 상태
    (2, 1, 2, false, NOW(), NOW()),  -- 러너투 따라뜀

    -- 코스3: 러너투(주인) 등록 상태, 러너원 따라뜀
    (3, 3, 2, false, NOW(), NOW()),  -- 주인(러너투) 등록 상태
    (4, 3, 1, false, NOW(), NOW()),  -- 러너원 따라뜀

    -- 코스4: 러너투(주인) 등록 해제함, 러너원은 따라뛴 상태 (핵심 시나리오!)
    (5, 4, 2, true, NOW(), NOW()),   -- 주인(러너투) 등록 해제! deleted=true
    (6, 4, 1, false, NOW(), NOW());  -- 러너원 따라뜀 (deleted=false → courseInfo 보여야 함)

-- Running 테이블
-- 시나리오별 러닝 데이터:
-- 1. 러너원 - 한강(코스1): subscription 있음 → courseInfo 보임
-- 2. 러너원 - 남산(코스2): subscription 없음 → courseInfo null
-- 3. 러너투 - 한강(코스1): subscription 있음 → courseInfo 보임
-- 4. 러너투 - 올림픽공원(코스3): 주인+subscription 있음 → courseInfo 보임
-- 5. 러너투 - 서울숲(코스4): 주인이지만 등록 해제(deleted=true) → courseInfo null
-- 6. 러너원 - 서울숲(코스4): 따라뛴 러너(deleted=false) → courseInfo 보임!
INSERT INTO running_record (id, running_name, running_mode, ghost_running_id, distance_km, elevation_average_m,
                            elevation_gain_m, elevation_loss_m, `average_pace_min/km`, `highest_pace_min/km`, `lowest_pace_min/km`,
                            duration_sec, burned_calories_kcal, average_cadence_spm, average_bpm,
                            started_at_ms, is_public, has_paused, raw_telemetry_url, interpolated_telemetry_url, screen_shot_url,
                            member_id, course_id, deleted, created_at, updated_at)
VALUES
    -- 러너원(member_id=1)의 러닝들
    (1, '아침 한강 러닝', 'SOLO', NULL, 5.0, 10.5, 20.0, 18.0, 5.30, 4.50, 6.20, 1590, 350, 175, 145,
     1705881600000, true, false,
     'https://example.com/telemetry/raw/1.json', 'https://example.com/telemetry/interpolated/1.json', 'https://example.com/screenshots/1.png',
     1, 1, false, NOW(), NOW()),  -- 코스1(한강) - subscription 있음 → courseInfo O

    (2, '남산 점심 조깅', 'SOLO', NULL, 3.2, 5.0, 8.0, 8.0, 6.00, 5.30, 7.00, 1152, 220, 168, 138,
     1705968000000, true, false,
     'https://example.com/telemetry/raw/2.json', 'https://example.com/telemetry/interpolated/2.json', 'https://example.com/screenshots/2.png',
     1, 2, false, NOW(), NOW()),  -- 코스2(남산) - subscription 없음 → courseInfo X

    (3, '서울숲 따라뛰기', 'GHOST', 6, 4.0, 8.0, 10.0, 10.0, 5.20, 4.40, 5.50, 1248, 280, 170, 140,
     1706227200000, true, false,
     'https://example.com/telemetry/raw/5.json', 'https://example.com/telemetry/interpolated/5.json', 'https://example.com/screenshots/5.png',
     1, 4, false, NOW(), NOW()),  -- 코스4(서울숲) - 러너원은 subscription.deleted=false → courseInfo O!

    -- 러너투(member_id=2)의 러닝들
    (4, '저녁 한강 러닝', 'GHOST', 1, 5.0, 10.5, 20.0, 18.0, 5.15, 4.45, 5.50, 1545, 360, 178, 150,
     1706054400000, true, false,
     'https://example.com/telemetry/raw/3.json', 'https://example.com/telemetry/interpolated/3.json', 'https://example.com/screenshots/3.png',
     2, 1, false, NOW(), NOW()),  -- 코스1(한강) - subscription 있음 → courseInfo O

    (5, '올림픽공원 완주', 'SOLO', NULL, 7.2, 5.0, 15.0, 15.0, 5.45, 5.00, 6.30, 2376, 480, 172, 142,
     1706140800000, true, false,
     'https://example.com/telemetry/raw/4.json', 'https://example.com/telemetry/interpolated/4.json', 'https://example.com/screenshots/4.png',
     2, 3, false, NOW(), NOW()),  -- 코스3(올림픽공원) - 주인+subscription 있음 → courseInfo O

    (6, '서울숲 아침 러닝', 'SOLO', NULL, 4.0, 8.0, 10.0, 10.0, 5.00, 4.30, 5.30, 1200, 270, 172, 138,
     1706313600000, true, false,
     'https://example.com/telemetry/raw/6.json', 'https://example.com/telemetry/interpolated/6.json', 'https://example.com/screenshots/6.png',
     2, 4, false, NOW(), NOW());  -- 코스4(서울숲) - 주인이지만 등록 해제(deleted=true) → courseInfo X!

-- Pacemaker 테이블
INSERT INTO pacemaker (id, running_type, norm, summary, goal_km, expected_time_min, initial_message, status, has_run_with,
                       deleted, running_id, course_id, member_uuid, condition_level, temperature, last_retry_at, created_at, updated_at)
VALUES
    (1, 'E', 'DISTANCE', '오늘은 가볍게 회복 조깅을 해볼까요? 천천히 시작해서 몸을 풀어봅시다.',
     5.0, 30, '안녕하세요! 오늘의 회복 조깅을 시작합니다. 편안한 페이스로 달려볼게요.',
     'COMPLETED', false, false, NULL, 1, 'test-uuid-001', 3, 20, NULL, NOW(), NOW()),
    (2, 'M', 'DISTANCE', '마라톤 페이스 훈련입니다. 일정한 페이스를 유지해주세요.',
     10.0, 50, '마라톤 페이스 훈련을 시작합니다. 꾸준히 달려봅시다!',
     'COMPLETED', true, false, 1, 1, 'test-uuid-001', 4, 15, NULL, NOW(), NOW()),
    (3, 'T', 'DISTANCE', '템포런 훈련입니다. 조금 빠른 페이스로 달려봅시다.',
     5.0, 22, '템포런을 시작합니다. 힘들지만 할 수 있어요!',
     'PROCEEDING', false, false, NULL, 3, 'test-uuid-002', 3, 25, NULL, NOW(), NOW());

-- PacemakerSet 테이블
INSERT INTO pacemaker_set (id, set_num, message, start_point, end_point, `pace_min/km`, deleted, pacemaker_id)
VALUES
    (1, 1, '처음 1km는 천천히 워밍업 해볼까요?', 0.0, 1.0, 6.30, false, 1),
    (2, 2, '좋아요! 이제 조금 속도를 올려볼게요.', 1.0, 3.0, 6.00, false, 1),
    (3, 3, '중간 지점입니다. 페이스를 유지해주세요.', 3.0, 4.0, 6.00, false, 1),
    (4, 4, '마지막 1km! 쿨다운 하면서 마무리합시다.', 4.0, 5.0, 6.30, false, 1),
    (5, 1, '마라톤 페이스로 시작합니다.', 0.0, 3.0, 5.00, false, 2),
    (6, 2, '좋은 페이스입니다! 유지해주세요.', 3.0, 7.0, 5.00, false, 2),
    (7, 3, '마지막 구간입니다. 끝까지 화이팅!', 7.0, 10.0, 5.00, false, 2);

-- Device (push_token) 테이블
INSERT INTO push_token (id, member_id, token, uuid, app_version_major, app_version_minor, app_version_patch,
                        os_name, os_version, model_name, created_at, updated_at, deleted_at)
VALUES
    (1, 1, 'ExponentPushToken[test-token-001]', 'device-uuid-001', 1, 0, 0, 'iOS', '17.0', 'iPhone 15 Pro', NOW(), NOW(), NULL),
    (2, 2, 'ExponentPushToken[test-token-002]', 'device-uuid-002', 1, 0, 0, 'Android', '14', 'Galaxy S24', NOW(), NOW(), NULL),
    (3, 3, 'ExponentPushToken[test-token-003]', 'device-uuid-003', 1, 0, 0, 'iOS', '17.0', 'iPhone 14', NOW(), NOW(), NULL);
-- ===================================
-- 4. 리드모델 생성 (공개 코스만)
-- ===================================
INSERT INTO course_read_model (
    course_id, name, owner_uuid, source, route_url, start_lat, start_lng,
    top1_member_id, top1_time_seconds,
    top2_member_id, top2_time_seconds,
    top3_member_id, top3_time_seconds,
    top4_member_id, top4_time_seconds,
    runners_count, is_public, created_at, updated_at
)
VALUES
    -- 코스 1: 한강 (TOP4: 러너2, 러너4, 러너1, 러너3)
    (1, '한강 러닝 코스', 'user-uuid-001', 'USER', 'https://example.com/route1.json', 37.5219, 127.0411,
     2, 1500, 4, 1600, 1, 1700, 3, 1800, 5, true, NOW(), NOW()),

    -- 코스 2: 올림픽공원 (TOP3: 러너4, 러너3, 러너5)
    (2, '올림픽공원 달리기', 'user-uuid-002', 'USER', 'https://example.com/route2.json', 37.5219, 127.1263,
     4, 1100, 3, 1200, 5, 1300, NULL, NULL, 3, true, NOW(), NOW()),

    -- 코스 3: 강남 (TOP2: 러너2, 러너1)
    (3, '강남 야간 러닝', 'user-uuid-003', 'RECOMMENDED', 'https://example.com/route3.json', 37.4979, 127.0276,
     2, 2200, 1, 2400, NULL, NULL, NULL, NULL, 2, true, NOW(), NOW()),

    -- 코스 4: 여의도 (TOP1: 러너1)
    (4, '여의도 한강 순환', 'user-uuid-004', 'USER', 'https://example.com/route4.json', 37.5219, 126.9245,
     1, 3600, NULL, NULL, NULL, NULL, NULL, NULL, 1, true, NOW(), NOW());

-- Notice 테이블
INSERT INTO notice (id, title, content, type, image_url, priority, start_at, end_at, created_at, updated_at)
VALUES
    (1, '앱 업데이트 안내', '새로운 기능이 추가되었습니다. 지금 업데이트 해보세요!', 'GENERAL_V2',
     'https://example.com/notices/update.png', 1, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), NOW(), NOW()),
    (2, '러닝 이벤트 안내', '이번 주 토요일 한강 러닝 이벤트에 참여해보세요!', 'EVENT_V2',
     'https://example.com/notices/event.png', 2, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), NOW(), NOW()),
    (3, '서버 점검 안내', '2월 1일 오전 2시부터 4시까지 서버 점검이 예정되어 있습니다.', 'GENERAL_V2',
     NULL, 0, NOW(), DATE_ADD(NOW(), INTERVAL 14 DAY), NOW(), NOW());
-- ===================================
-- 5. 외래키 체크 재활성화
-- ===================================
SET FOREIGN_KEY_CHECKS = 1;
