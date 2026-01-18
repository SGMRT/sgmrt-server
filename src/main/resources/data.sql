-- ===================================
-- 로컬 개발 환경 샘플 데이터
-- Spring Boot 시작 시 자동 실행
-- ===================================

-- ===================================
-- 1. 멤버 생성 (5명)
-- ===================================
INSERT INTO member (uuid, nickname, profile_picture_url, role, last_login_at, created_at, updated_at)
VALUES 
    ('user-uuid-001', '러너1', 'https://example.com/profile1.jpg', 'USER', NOW(), NOW(), NOW()),
    ('user-uuid-002', '러너2', 'https://example.com/profile2.jpg', 'USER', NOW(), NOW(), NOW()),
    ('user-uuid-003', '러너3', 'https://example.com/profile3.jpg', 'USER', NOW(), NOW(), NOW()),
    ('user-uuid-004', '러너4', 'https://example.com/profile4.jpg', 'USER', NOW(), NOW(), NOW()),
    ('user-uuid-005', '러너5', 'https://example.com/profile5.jpg', 'USER', NOW(), NOW(), NOW());

-- ===================================
-- 2. 코스 생성 (5개)
-- ===================================
INSERT INTO course (member_id, name, source, is_public, deleted, start_latitude, start_longtitude, 
                    route_url, distance_km, elevation_average_m, elevation_gain_m, elevation_loss_m, 
                    created_at, updated_at)
VALUES 
    -- 서울 한강공원 (공개)
    (1, '한강 러닝 코스', 'USER', true, false, 37.5219, 127.0411, 
     'https://example.com/route1.json', 5.2, 10.0, 50.0, 30.0, NOW(), NOW()),
    
    -- 서울 올림픽공원 (공개)
    (2, '올림픽공원 달리기', 'USER', true, false, 37.5219, 127.1263, 
     'https://example.com/route2.json', 3.5, 15.0, 30.0, 25.0, NOW(), NOW()),
    
    -- 강남 (추천 코스, 공개)
    (3, '강남 야간 러닝', 'RECOMMENDED', true, false, 37.4979, 127.0276, 
     'https://example.com/route3.json', 7.0, 5.0, 20.0, 15.0, NOW(), NOW()),
    
    -- 여의도 (공개)
    (4, '여의도 한강 순환', 'USER', true, false, 37.5219, 126.9245, 
     'https://example.com/route4.json', 10.5, 8.0, 40.0, 35.0, NOW(), NOW()),
    
    -- 성수동 (비공개)
    (5, '성수 카페 투어', 'USER', false, false, 37.5443, 127.0557, 
     'https://example.com/route5.json', 4.0, 12.0, 25.0, 20.0, NOW(), NOW());

-- ===================================
-- 3. 러닝 기록 생성
-- ===================================

-- 코스 1: 한강 (5개 기록)
INSERT INTO running_record (member_id, course_id, running_name, running_mode, is_public, deleted, has_paused,
                            distance_km, duration_sec, `average_pace_min/km`, elevation_average_m, elevation_gain_m, elevation_loss_m,
                            burned_calories_kcal, average_bpm, average_cadence_spm,
                            raw_telemetry_url, interpolated_telemetry_url, started_at_ms, created_at, updated_at)
VALUES 
    (2, 1, '러너2의 기록', 'SOLO', true, false, false, 5.2, 1500, 4.8, 10.0, 50.0, 30.0, 350, 140, 170,
     'https://example.com/raw1.json', 'https://example.com/interp1.json', UNIX_TIMESTAMP(NOW()) * 1000, NOW(), NOW()),
    (3, 1, '러너3의 기록', 'SOLO', true, false, false, 5.2, 1800, 5.8, 10.0, 50.0, 30.0, 380, 135, 165,
     'https://example.com/raw2.json', 'https://example.com/interp2.json', UNIX_TIMESTAMP(NOW()) * 1000, NOW(), NOW()),
    (4, 1, '러너4의 기록', 'SOLO', true, false, false, 5.2, 1600, 5.1, 10.0, 50.0, 30.0, 360, 145, 175,
     'https://example.com/raw3.json', 'https://example.com/interp3.json', UNIX_TIMESTAMP(NOW()) * 1000, NOW(), NOW()),
    (5, 1, '러너5의 기록', 'SOLO', true, false, false, 5.2, 2000, 6.4, 10.0, 50.0, 30.0, 400, 130, 160,
     'https://example.com/raw4.json', 'https://example.com/interp4.json', UNIX_TIMESTAMP(NOW()) * 1000, NOW(), NOW()),
    (1, 1, '러너1의 기록', 'SOLO', true, false, false, 5.2, 1700, 5.4, 10.0, 50.0, 30.0, 370, 142, 168,
     'https://example.com/raw5.json', 'https://example.com/interp5.json', UNIX_TIMESTAMP(NOW()) * 1000, NOW(), NOW());

-- 코스 2: 올림픽공원 (3개 기록)
INSERT INTO running_record (member_id, course_id, running_name, running_mode, is_public, deleted, has_paused,
                            distance_km, duration_sec, `average_pace_min/km`, elevation_average_m, elevation_gain_m, elevation_loss_m,
                            burned_calories_kcal, average_bpm, average_cadence_spm,
                            raw_telemetry_url, interpolated_telemetry_url, started_at_ms, created_at, updated_at)
VALUES 
    (3, 2, '올림픽공원 달리기', 'SOLO', true, false, false, 3.5, 1200, 5.7, 15.0, 30.0, 25.0, 280, 138, 172,
     'https://example.com/raw6.json', 'https://example.com/interp6.json', UNIX_TIMESTAMP(NOW()) * 1000, NOW(), NOW()),
    (4, 2, '아침 러닝', 'SOLO', true, false, false, 3.5, 1100, 5.2, 15.0, 30.0, 25.0, 270, 142, 168,
     'https://example.com/raw7.json', 'https://example.com/interp7.json', UNIX_TIMESTAMP(NOW()) * 1000, NOW(), NOW()),
    (5, 2, '저녁 러닝', 'SOLO', true, false, false, 3.5, 1300, 6.2, 15.0, 30.0, 25.0, 290, 135, 165,
     'https://example.com/raw8.json', 'https://example.com/interp8.json', UNIX_TIMESTAMP(NOW()) * 1000, NOW(), NOW());

-- 코스 3: 강남 (2개 기록)
INSERT INTO running_record (member_id, course_id, running_name, running_mode, is_public, deleted, has_paused,
                            distance_km, duration_sec, `average_pace_min/km`, elevation_average_m, elevation_gain_m, elevation_loss_m,
                            burned_calories_kcal, average_bpm, average_cadence_spm,
                            raw_telemetry_url, interpolated_telemetry_url, started_at_ms, created_at, updated_at)
VALUES 
    (1, 3, '강남 야간 러닝', 'SOLO', true, false, false, 7.0, 2400, 5.7, 5.0, 20.0, 15.0, 520, 140, 170,
     'https://example.com/raw9.json', 'https://example.com/interp9.json', UNIX_TIMESTAMP(NOW()) * 1000, NOW(), NOW()),
    (2, 3, '주말 러닝', 'SOLO', true, false, false, 7.0, 2200, 5.2, 5.0, 20.0, 15.0, 500, 145, 175,
     'https://example.com/raw10.json', 'https://example.com/interp10.json', UNIX_TIMESTAMP(NOW()) * 1000, NOW(), NOW());

-- 코스 4: 여의도 (1개 기록)
INSERT INTO running_record (member_id, course_id, running_name, running_mode, is_public, deleted, has_paused,
                            distance_km, duration_sec, `average_pace_min/km`, elevation_average_m, elevation_gain_m, elevation_loss_m,
                            burned_calories_kcal, average_bpm, average_cadence_spm,
                            raw_telemetry_url, interpolated_telemetry_url, started_at_ms, created_at, updated_at)
VALUES 
    (1, 4, '여의도 순환', 'SOLO', true, false, false, 10.5, 3600, 5.7, 8.0, 40.0, 35.0, 750, 138, 168,
     'https://example.com/raw11.json', 'https://example.com/interp11.json', UNIX_TIMESTAMP(NOW()) * 1000, NOW(), NOW());

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
