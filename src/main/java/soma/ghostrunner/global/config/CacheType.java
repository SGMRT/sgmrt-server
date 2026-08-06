package soma.ghostrunner.global.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;

/**
 * 캐시 레지스트리 — 캐시 이름과 TTL을 한곳에서 관리한다.
 * Spring Cache(@Cacheable)와 RedisTemplate 기반 캐시가 모두 여기서 이름·TTL을 읽는다.
 */
@AllArgsConstructor
@Getter
public enum CacheType {

    /**
     * 지도 결과셋 캐시 (key: regionId). 완주·러닝 공개 전환은 AFTER_COMMIT 이빅트로 즉시 반영되고
     * (CourseMapCacheEvictListener), 그 외 변경(코스 공개 전환 등)의 스테일 상한이 TTL이다.
     * (설계 cache/05 §4 — 이빅트가 있어야 TTL을 늘려 히트율을 확보할 수 있다, 시뮬레이션 v4)
     */
    COURSE_MAP(Names.COURSE_MAP, Duration.ofSeconds(600)),

    /**
     * 셀 버킷 캐시 (key: geohash p6 셀). 값은 그 셀에 시작점을 둔 코스 카드 목록이며,
     * 정합성은 코스 변경 시 소속 셀 1개 DEL이 담당한다. TTL은 이빅트 밖 변경의 스테일 상한이다.
     * CourseCellCache가 직접 쓰는 이름·TTL 출처다.
     * (설계 course-cell-bucket-cache-design §3-6·§3-11)
     */
    COURSE_CELLS(Names.COURSE_CELLS, Duration.ofSeconds(600));

    private final String cacheName;
    private final Duration ttl;

    /** &#64;Cacheable 등 애노테이션 속성은 컴파일 타임 상수만 허용하므로 이름을 상수로도 노출한다. */
    public static final class Names {
        public static final String COURSE_MAP = "course-map";
        public static final String COURSE_CELLS = "course-cells";
        private Names() {}
    }
}
