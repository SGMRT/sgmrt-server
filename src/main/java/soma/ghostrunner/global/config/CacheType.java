package soma.ghostrunner.global.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;

/**
 * RedisTemplate 기반 캐시의 이름·TTL 단일 출처 레지스트리. (Spring Cache 애노테이션과 무관)
 *
 * <p>이 프로젝트는 {@code @EnableCaching}/{@code CacheManager}를 쓰지 않는다 (설계 D9).
 * 캐시 어댑터가 이 enum에서 이름과 TTL을 읽어 직접 Redis에 읽고 쓴다.</p>
 */
@AllArgsConstructor
@Getter
public enum CacheType {

    /**
     * 셀 버킷 캐시 (key: geohash p6 셀). 값은 그 셀에 시작점을 둔 코스 카드 목록이며,
     * 정합성은 코스 변경 시 소속 셀 1개 DEL이 담당한다. TTL은 이빅트 밖 변경의 스테일 상한이다.
     * CourseCellCache가 직접 쓰는 이름·TTL 출처다.
     * (설계 course-cell-bucket-cache-design §3-6·§3-11)
     */
    COURSE_CELLS(Names.COURSE_CELLS, Duration.ofSeconds(600));

    private final String cacheName;
    private final Duration ttl;

    /** 키 접두사를 컴파일 타임 상수로 써야 하는 곳(테스트 키 패턴 등)을 위해 이름을 상수로도 노출한다. */
    public static final class Names {
        public static final String COURSE_CELLS = "course-cells";
        private Names() {}
    }
}
