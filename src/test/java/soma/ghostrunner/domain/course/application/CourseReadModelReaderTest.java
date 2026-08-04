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
import soma.ghostrunner.domain.course.dto.query.CourseMapDto;
import soma.ghostrunner.domain.course.enums.CourseSource;
import soma.ghostrunner.domain.course.exception.RegionNotFoundException;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 지도 결과셋 캐시(course-map)의 핵심 동작 검증 — 캐시키가 regionId인 경로.
 *
 * 설계 문서: docs/refactoring/course-read-model/cache/05-cache-key-design.md §4, §6-5, §8
 *
 * 이 경로가 지켜야 할 불변식은 세 가지다.
 * 1. 같은 regionId는 하나의 캐시 엔트리를 공유하고, TTL(60초) 안에서는 옛 결과를 재사용한다 (스테일 수용).
 * 2. 값은 요청자 좌표가 아니라 <b>region 대표좌표 + 고정 반경 2km</b>로 정해진다 — 같은 키에 다른 값이
 *    적재되면 캐시가 사용자마다 다른 답을 주게 되므로, 결정성이 캐시의 정합성 근거다.
 * 3. 발급된 적 없는 regionId는 조용히 폴백하지 않고 예외로 드러낸다 (빈 결과의 캐시 오염 방지).
 */
@DisplayName("CourseReadModelReader 통합 테스트 - regionId 캐시")
@ExtendWith(DatabaseCleanserExtension.class)
class CourseReadModelReaderTest extends IntegrationTestSupport {

    @Autowired CourseReadModelReader reader;
    @Autowired CourseReadModelRepository readModelRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired RegionRepository regionRepository;
    @Autowired RedisTemplate<String, Object> redisTemplate;

    private static final String REGION_NAME = "서울특별시 강남구 역삼동";

    /** 이 캐시가 쓰는 Redis 키 전부를 훑는 패턴 — 키가 regionId 하나로 뭉치는지 확인하는 데도 쓴다. */
    private static final String COURSE_MAP_KEY_PATTERN = "course-map*";

    /** region 대표좌표 — 캐시 값의 기준점 */
    private static final double CENTER_LAT = 37.5008;
    private static final double CENTER_LNG = 127.0365;

    /** 위도 +0.009 ≈ 북쪽 1km — 고정 반경 2km 안 */
    private static final double LAT_DELTA_INSIDE_RADIUS = 0.009;

    /** 위도 +0.045 ≈ 북쪽 5km — 고정 반경 2km 밖 */
    private static final double LAT_DELTA_OUTSIDE_RADIUS = 0.045;

    @BeforeEach
    void clearCourseMapCache() {
        Set<String> keys = redisTemplate.keys(COURSE_MAP_KEY_PATTERN);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @DisplayName("같은 지역을 다시 조회하면 TTL(60초) 안에서는 캐시된 옛 결과를 재사용하고, 키는 course-map::{regionId} 하나뿐이다")
    @Test
    void sameRegion_reusesCachedResult_withinTtl() {
        // given : 지역 하나와 그 안의 공개 코스 1개
        Region region = saveRegion(REGION_NAME, CENTER_LAT, CENTER_LNG);
        savePublicCourseWithReadModel("한강 코스", CENTER_LAT, CENTER_LNG);

        // when : 첫 조회로 캐시가 적재된 뒤 새 코스가 추가돼도 —
        List<CourseMapDto> first = reader.findCoursesForMapByRegion(region.getId());
        savePublicCourseWithReadModel("새 코스", CENTER_LAT, CENTER_LNG);
        List<CourseMapDto> second = reader.findCoursesForMapByRegion(region.getId());

        // then : 두 번째 응답은 캐시된 옛 결과 — TTL 상한 내 스테일은 수용된 트레이드오프 (무효화 없음)
        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
        assertThat(second.get(0).name()).isEqualTo("한강 코스");

        // 키는 regionId 하나로 뭉치고, TTL 60초가 스테일 상한이다
        String expectedKey = "course-map::" + region.getId();
        assertThat(redisTemplate.keys(COURSE_MAP_KEY_PATTERN)).containsExactly(expectedKey);
        Long ttl = redisTemplate.getExpire(expectedKey, TimeUnit.SECONDS);
        assertThat(ttl).isBetween(1L, 60L);
    }

    @DisplayName("결과셋은 대표좌표 기준 고정 반경 2km로 정해진다 - 2km 밖 코스는 제외된다")
    @Test
    void resultSet_isBoundedByRepresentativeCoordinate_andFixedRadius() {
        // given : 대표좌표에서 1km 떨어진 코스와 5km 떨어진 코스
        Region region = saveRegion(REGION_NAME, CENTER_LAT, CENTER_LNG);
        savePublicCourseWithReadModel("동네 코스", CENTER_LAT + LAT_DELTA_INSIDE_RADIUS, CENTER_LNG);
        savePublicCourseWithReadModel("옆 동네 코스", CENTER_LAT + LAT_DELTA_OUTSIDE_RADIUS, CENTER_LNG);

        // when : 요청자 좌표·반경은 개입하지 않는다 (인자가 regionId 하나뿐)
        List<CourseMapDto> courses = reader.findCoursesForMapByRegion(region.getId());

        // then : 고정 반경 2km 안의 코스만 담긴다
        assertThat(courses).extracting(CourseMapDto::name).containsExactly("동네 코스");
    }

    @DisplayName("발급된 적 없는 regionId로 조회하면 RegionNotFoundException을 던진다")
    @Test
    void unknownRegionId_throwsRegionNotFoundException() {
        // given : 어떤 지역도 등록되지 않은 상태

        // when & then : 조용한 폴백 대신 예외 — 빈 결과가 캐시에 적재되는 오염을 막는다
        assertThatThrownBy(() -> reader.findCoursesForMapByRegion(999_999L))
                .isInstanceOf(RegionNotFoundException.class);
    }

    // ========== 픽스처 ==========

    private Region saveRegion(String name, double centerLat, double centerLng) {
        return regionRepository.save(Region.of(name, centerLat, centerLng));
    }

    private Member saveMember(String nickname) {
        return memberRepository.save(Member.of(nickname, "https://example.com/" + nickname + ".jpg"));
    }

    private void savePublicCourseWithReadModel(String name, double lat, double lng) {
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
    }
}
