package soma.ghostrunner.domain.course.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.DatabaseCleanserExtension;
import soma.ghostrunner.domain.course.dao.CourseReadModelRepository;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Coordinate;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseDataUrls;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.course.domain.CourseReadModel;
import soma.ghostrunner.domain.course.dto.query.CourseMapDto;
import soma.ghostrunner.domain.course.enums.CourseSource;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 지도 결과셋 캐시(course-map)의 핵심 동작 검증. (설계 04 §2-2)
 * - 키 = 반올림 좌표(1.1km 격자) + 반경, TTL 60초, 무효화 없음
 * - 기본 요청만 캐싱 (비기본 요청은 우회)
 */
@ExtendWith(DatabaseCleanserExtension.class)
class CourseReadModelReaderTest extends IntegrationTestSupport {

    @Autowired CourseReadModelReader reader;
    @Autowired CourseReadModelRepository readModelRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired RedisTemplate<String, Object> redisTemplate;

    private static final double LAT = 37.5480;
    private static final double LNG = 127.0731;
    private static final int RADIUS_M = 2000;

    @BeforeEach
    void clearCourseMapCache() {
        Set<String> keys = redisTemplate.keys("course-map*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @DisplayName("같은 격자(1.1km 셀)의 다른 좌표 요청은 하나의 캐시 키로 뭉치고, TTL 안에서는 캐시된 결과를 재사용한다")
    @Test
    void sameCellRequests_shareOneCacheEntry_andReuseCachedResult() {
        // given : 격자 내 공개 코스 1개
        savePublicCourseWithReadModel("한강 코스", LAT, LNG);

        // when : 같은 셀에 속하는 서로 다른 두 좌표로 조회 (round(…,2)가 같음)
        List<CourseMapDto> first = reader.findCoursesForMap(37.5481, 127.0729, RADIUS_M, true);

        // 캐시 적재 후 새 코스가 추가돼도 —
        savePublicCourseWithReadModel("새 코스", LAT, LNG);
        List<CourseMapDto> second = reader.findCoursesForMap(37.5479, 127.0733, RADIUS_M, true);

        // then : 두 번째 응답은 캐시된 옛 결과(코스 1개) — TTL 상한 내 스테일은 수용된 트레이드오프
        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
        assertThat(second.get(0).name()).isEqualTo("한강 코스");

        // 키는 정확히 1개(반올림 키로 뭉침)이고 TTL이 60초 이하로 설정돼 있다
        Set<String> keys = redisTemplate.keys("course-map*");
        assertThat(keys).hasSize(1);
        String key = keys.iterator().next();
        assertThat(key).contains("37.55:127.07:2000");
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertThat(ttl).isBetween(1L, 60L);
    }

    @DisplayName("기본 요청이 아니면(cacheable=false) 캐시를 우회한다 — 키가 생기지 않고 항상 최신 DB를 읽는다")
    @Test
    void nonDefaultRequest_bypassesCache() {
        // given
        savePublicCourseWithReadModel("한강 코스", LAT, LNG);

        // when
        List<CourseMapDto> first = reader.findCoursesForMap(LAT, LNG, RADIUS_M, false);
        savePublicCourseWithReadModel("새 코스", LAT, LNG);
        List<CourseMapDto> second = reader.findCoursesForMap(LAT, LNG, RADIUS_M, false);

        // then : 캐시 키 없음 + 두 번째 조회는 최신 상태를 반영
        assertThat(redisTemplate.keys("course-map*")).isEmpty();
        assertThat(first).hasSize(1);
        assertThat(second).hasSize(2);
    }

    // ========== fixtures ==========

    private void savePublicCourseWithReadModel(String name, double lat, double lng) {
        Member owner = memberRepository.save(Member.of("주인-" + name, "https://example.com/p.jpg"));
        Course course = courseRepository.save(Course.of(
                owner, name,
                CourseProfile.of(5.0, 10.0, 100.0, 50.0),
                Coordinate.of(lat, lng),
                CourseSource.USER, true,
                CourseDataUrls.of("https://example.com/route.json",
                        "https://example.com/checkpoints.json",
                        "https://example.com/thumb.jpg")));
        CourseReadModel readModel = CourseReadModel.create(course);
        readModel.makePublic();
        readModelRepository.save(readModel);
    }
}
