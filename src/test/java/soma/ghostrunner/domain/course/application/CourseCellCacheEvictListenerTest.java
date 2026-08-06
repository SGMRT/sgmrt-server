package soma.ghostrunner.domain.course.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import soma.ghostrunner.DatabaseCleanserExtension;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.dao.CourseCellCache;
import soma.ghostrunner.domain.course.dao.CourseCellCacheMetrics;
import soma.ghostrunner.domain.course.dao.CourseReadModelRepository;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Coordinate;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseDataUrls;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.course.domain.CourseReadModel;
import soma.ghostrunner.domain.course.domain.GeoCell;
import soma.ghostrunner.domain.course.domain.events.CourseMapDataChangedEvent;
import soma.ghostrunner.domain.course.dto.query.CellBucket;
import soma.ghostrunner.domain.course.dto.query.CourseMapDto;
import soma.ghostrunner.domain.course.enums.CourseSource;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.running.domain.events.RunFinishedEvent;
import soma.ghostrunner.global.config.CacheType;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * 셀 버킷 이빅트의 핵심 계약 검증. (설계: docs/design/course-cell-bucket-cache-design.md §3-9, §4, §6 테스트 14)
 *
 * <p>이빅트가 지켜야 할 계약은 세 가지다.
 * <ul>
 *     <li><b>좌표를 실은 이벤트는 DB 없이도 성립한다</b> — 코스 삭제는 커밋 후 리드모델이 남아 있지 않아
 *         {@code courseId}로 셀을 역산할 수 없다(M2). 이벤트에 동봉된 좌표만으로 셀 하나가 지워져야 한다.</li>
 *     <li><b>지워지는 것은 소속 셀 하나뿐이다</b> — 팬아웃 DEL이 없다는 것이 셀 버킷 전환의 근거이므로,
 *         이웃 셀의 캐시는 반드시 살아남아야 한다.</li>
 *     <li><b>리드모델이 없으면 조용히 끝난다</b> — 비공개 코스는 애초에 지도에 없다. 정상 경로이지 예외가 아니며,
 *         AFTER_COMMIT에서 던지면 커밋이 이미 성공한 요청에 500이 나간다.</li>
 * </ul>
 *
 * <p>{@link IntegrationTestSupport}는 클래스 레벨 {@code @Transactional}이라 테스트 안에서 커밋이 나지 않는다.
 * AFTER_COMMIT 리스너는 기존 관례대로 <b>리스너를 직접 호출</b>해 재현한다.
 */
@ExtendWith(DatabaseCleanserExtension.class)
@DisplayName("CourseCellCacheEvictListener 통합 테스트 - 셀 단위 이빅트")
class CourseCellCacheEvictListenerTest extends IntegrationTestSupport {

    @Autowired CourseCellCacheEvictListener listener;
    @Autowired CourseCellCache cellCache;
    @Autowired CourseCellCacheMetrics metrics;
    @Autowired CourseReadModelRepository readModelRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired StringRedisTemplate stringRedisTemplate;

    /** 이 캐시가 쓰는 Redis 키 전부를 훑는 패턴. DatabaseCleanserExtension은 Redis를 건드리지 않는다. */
    private static final String CELL_KEY_PATTERN = CacheType.Names.COURSE_CELLS + "*";

    private static final double LAT = 37.5480;
    private static final double LNG = 127.0731;
    /** 위도 0.05° ≈ 5.5km — 셀 높이(≈610m)보다 훨씬 크므로 반드시 다른 셀이다. */
    private static final double FAR_DELTA = 0.05;

    /** 리드모델이 존재하지 않는 코스 식별자. */
    private static final Long ABSENT_COURSE_ID = 999_999L;

    @BeforeEach
    void clearCellCache() {
        Set<String> keys = stringRedisTemplate.keys(CELL_KEY_PATTERN);
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @DisplayName("좌표를 실은 지도 데이터 변경 이벤트는 리드모델이 없어도 해당 셀만 지우고 이웃 셀은 남긴다")
    @Test
    void mapDataChanged_EvictsOnlyItsOwnCell_WithoutReadModel() {
        // given : 코스 셀과 이웃 셀에 캐시를 적재한다. 리드모델은 저장하지 않는다(삭제된 코스 상황)
        GeoCell courseCell = GeoCell.of(LAT, LNG);
        GeoCell neighborCell = GeoCell.of(LAT + FAR_DELTA, LNG + FAR_DELTA);
        cache(courseCell, neighborCell);
        assertThat(readModelRepository.findByCourseId(ABSENT_COURSE_ID)).isEmpty();

        // when : AFTER_COMMIT 시점을 직접 호출로 재현. 좌표는 이벤트가 들고 온 것뿐이다
        listener.handleCourseMapDataChanged(new CourseMapDataChangedEvent(ABSENT_COURSE_ID, LAT, LNG));

        // then : DB 조회 없이 소속 셀만 지워지고, 이웃 셀은 살아남는다
        assertThat(stringRedisTemplate.keys(CELL_KEY_PATTERN))
                .containsExactly(cacheKey(neighborCell));
    }

    @DisplayName("완주 이벤트는 리드모델의 좌표로 소속 셀을 찾아 지우고 이웃 셀은 남긴다")
    @Test
    void runFinished_EvictsCellOfReadModelCoordinate() {
        // given : 리드모델이 있는 공개 코스 + 코스 셀·이웃 셀 캐시
        Long courseId = savePublicCourseWithReadModel("완주 코스", LAT, LNG);
        GeoCell courseCell = GeoCell.of(LAT, LNG);
        GeoCell neighborCell = GeoCell.of(LAT + FAR_DELTA, LNG + FAR_DELTA);
        cache(courseCell, neighborCell);

        // when : 이벤트에는 좌표가 없으므로 courseId로 리드모델을 조회해야 한다
        listener.handleRunFinished(new RunFinishedEvent(1L, courseId, "uuid", 1L, 1200, 6.0));

        // then
        assertThat(stringRedisTemplate.keys(CELL_KEY_PATTERN))
                .containsExactly(cacheKey(neighborCell));
    }

    /**
     * [R3] 좌표가 없으면 지울 셀을 정할 수 없다. 코스 시작점이 비어 있는 것은 프로그래밍 오류에 가깝지만,
     * AFTER_COMMIT에서 NPE가 나면 커밋이 이미 성공한 요청에 500이 나간다. 조용한 no-op이 유일하게 옳은 동작이다.
     */
    @DisplayName("좌표가 없는 지도 데이터 변경 이벤트는 예외 없이 아무 셀도 지우지 않는다 [R3]")
    @Test
    void mapDataChanged_WithNullCoordinate_EvictsNothing() {
        // given
        GeoCell courseCell = GeoCell.of(LAT, LNG);
        cache(courseCell);

        // when & then : 던지지 않는다는 것과 아무것도 지우지 않는다는 것이 함께 no-op의 증거다
        assertThatCode(() -> listener.handleCourseMapDataChanged(
                new CourseMapDataChangedEvent(ABSENT_COURSE_ID, null, null)))
                .doesNotThrowAnyException();

        assertThat(stringRedisTemplate.keys(CELL_KEY_PATTERN))
                .containsExactly(cacheKey(courseCell));
    }

    /**
     * [R3] {@code AbstractPlatformTransactionManager.triggerAfterCommit}은 AFTER_COMMIT 동기화에서 던져진 예외를
     * <b>호출자에게 전파</b>한다 — 커밋은 성공했는데 사용자는 500을 본다. 이빅트 실패의 손해는 정합성 사고가 아니라
     * 최대 TTL(600초)만큼의 스테일뿐이므로, 무슨 일이 있어도 밖으로 던지지 않아야 한다.
     * 세 핸들러의 try/catch를 "죽은 코드"로 보고 걷어내면 이 테스트가 빨개진다.
     */
    @DisplayName("이빅트가 실패해도 AFTER_COMMIT 핸들러는 예외를 밖으로 던지지 않는다 [R3]")
    @Test
    void handlers_DoNotPropagateEvictFailure() {
        // given : Redis 왕복이 실패하는 상황 — 캐시 어댑터가 예외를 흡수하지 못한 최악의 경우를 가정한다
        CourseCellCache failingCache = mock(CourseCellCache.class);
        doThrow(new RuntimeException("redis down")).when(failingCache).evict(any(GeoCell.class));
        CourseCellCacheEvictListener listenerWithFailingCache =
                new CourseCellCacheEvictListener(readModelRepository, failingCache, metrics);
        Long courseId = savePublicCourseWithReadModel("장애 코스", LAT, LNG);

        // when & then : 좌표를 실은 경로와 리드모델로 좌표를 역산하는 경로 둘 다 조용히 끝난다
        assertThatCode(() -> {
            listenerWithFailingCache.handleCourseMapDataChanged(
                    new CourseMapDataChangedEvent(courseId, LAT, LNG));
            listenerWithFailingCache.handleRunFinished(
                    new RunFinishedEvent(1L, courseId, "uuid", 1L, 1200, 6.0));
        }).doesNotThrowAnyException();
    }

    @DisplayName("리드모델이 없는 코스(비공개)의 완주 이벤트는 예외 없이 아무 셀도 지우지 않는다")
    @Test
    void runFinished_WithoutReadModel_EvictsNothing() {
        // given
        GeoCell courseCell = GeoCell.of(LAT, LNG);
        GeoCell neighborCell = GeoCell.of(LAT + FAR_DELTA, LNG + FAR_DELTA);
        cache(courseCell, neighborCell);

        // when : 지도에 오른 적 없는 코스 — 지울 셀을 알 수 없는 정상 경로다
        listener.handleRunFinished(new RunFinishedEvent(1L, ABSENT_COURSE_ID, "uuid", 1L, 1200, 6.0));

        // then : 예외가 나지 않았다는 사실과 키가 그대로라는 사실이 함께 no-op의 증거다
        assertThat(stringRedisTemplate.keys(CELL_KEY_PATTERN))
                .containsExactlyInAnyOrder(cacheKey(courseCell), cacheKey(neighborCell));
    }

    // ========== 픽스처 ==========

    /** 셀들을 프로덕션 적재 경로 그대로 채워 둔다. 값은 검증 대상이 아니므로 빈 버킷이면 충분하다. */
    private void cache(GeoCell... cells) {
        List<CellBucket> buckets = List.of(cells).stream()
                .map(cell -> new CellBucket(cell, List.<CourseMapDto>of()))
                .toList();
        cellCache.putAll(buckets);
        assertThat(stringRedisTemplate.keys(CELL_KEY_PATTERN)).hasSize(cells.length);
    }

    private String cacheKey(GeoCell cell) {
        return CacheType.Names.COURSE_CELLS + "::" + cell.id();
    }

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
