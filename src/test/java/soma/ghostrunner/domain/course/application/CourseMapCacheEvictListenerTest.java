package soma.ghostrunner.domain.course.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import soma.ghostrunner.DatabaseCleanserExtension;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.dao.CourseReadModelRepository;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.dao.RegionRepository;
import soma.ghostrunner.domain.course.domain.Coordinate;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseDataUrls;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.course.domain.CourseReadModel;
import soma.ghostrunner.domain.course.domain.Region;
import soma.ghostrunner.domain.course.enums.CourseSource;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.running.domain.events.RunFinishedEvent;

import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 완주 이빅트의 핵심 계약 검증. (설계 cache/05 §6-9)
 * - 코스 시작점 ±2km 박스 안 region 의 캐시 엔트리만 지워지고, 밖의 엔트리는 살아남는다
 * - 비공개 코스(리드모델 부재)는 조용히 스킵 — 예외도, 이빅트도 없다
 */
@ExtendWith(DatabaseCleanserExtension.class)
class CourseMapCacheEvictListenerTest extends IntegrationTestSupport {

    @Autowired CourseMapCacheEvictListener listener;
    @Autowired CourseReadModelReader reader;
    @Autowired CourseReadModelRepository readModelRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired RegionRepository regionRepository;
    @Autowired RedisTemplate<String, Object> redisTemplate;

    private static final double LAT = 37.5480;
    private static final double LNG = 127.0731;
    /** 위도 0.05° ≈ 5.5km — 2km 역산 박스 밖 */
    private static final double FAR_DELTA = 0.05;

    @BeforeEach
    void clearCourseMapCache() {
        Set<String> keys = redisTemplate.keys("course-map*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @DisplayName("완주 이벤트는 코스 주변(±2km) 지역의 캐시만 지우고, 먼 지역의 캐시는 남긴다")
    @Test
    void runFinished_evictsOnlyRegionsCoveringTheCourse() {
        // given : 코스 근처 지역과 먼 지역, 각각 캐시 적재
        Long courseId = savePublicCourseWithReadModel("완주 코스", LAT, LNG);
        Region nearRegion = regionRepository.save(Region.of("서울특별시 광진구 자양동", LAT + 0.001, LNG + 0.001));
        Region farRegion = regionRepository.save(Region.of("서울특별시 강서구 화곡동", LAT + FAR_DELTA, LNG + FAR_DELTA));
        reader.findCoursesForMapByRegion(nearRegion.getId());
        reader.findCoursesForMapByRegion(farRegion.getId());
        assertThat(redisTemplate.keys("course-map*")).hasSize(2);

        // when : 완주 이벤트 수신 (AFTER_COMMIT 시점을 직접 호출로 재현)
        listener.handleRunFinishedEvent(new RunFinishedEvent(1L, courseId, "uuid", 1L, 1200, 6.0));

        // then : 코스가 값에 포함되는 근처 지역 키만 삭제
        Set<String> remaining = redisTemplate.keys("course-map*");
        assertThat(remaining).hasSize(1);
        assertThat(Objects.requireNonNull(remaining).iterator().next())
                .isEqualTo("course-map::" + farRegion.getId());
    }

    @DisplayName("리드모델이 없는 코스(비공개)의 완주 이벤트는 아무 캐시도 지우지 않는다")
    @Test
    void runFinished_withoutReadModel_evictsNothing() {
        // given : 리드모델 없이 캐시만 존재
        Region region = regionRepository.save(Region.of("서울특별시 광진구 자양동", LAT, LNG));
        reader.findCoursesForMapByRegion(region.getId());
        assertThat(redisTemplate.keys("course-map*")).hasSize(1);

        // when : 리드모델이 없는 courseId
        listener.handleRunFinishedEvent(new RunFinishedEvent(1L, 999_999L, "uuid", 1L, 1200, 6.0));

        // then
        assertThat(redisTemplate.keys("course-map*")).hasSize(1);
    }

    // ========== 픽스처 ==========

    private Long savePublicCourseWithReadModel(String name, double lat, double lng) {
        Course course = courseRepository.save(Course.of(
                saveMember("주인-" + name), name,
                CourseProfile.of(5.0, 10.0, 100.0, 50.0),
                Coordinate.of(lat, lng),
                CourseSource.USER, true,
                CourseDataUrls.of("https://example.com/route.json",
                        "https://example.com/checkpoints.json",
                        "https://example.com/thumb.jpg")));
        CourseReadModel readModel = CourseReadModel.create(course);
        readModel.makePublic();
        readModelRepository.save(readModel);
        return course.getId();
    }

    private Member saveMember(String nickname) {
        return memberRepository.save(Member.of(nickname, "https://example.com/profile.jpg"));
    }
}
