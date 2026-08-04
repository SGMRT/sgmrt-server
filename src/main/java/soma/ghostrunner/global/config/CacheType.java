package soma.ghostrunner.global.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;

/**
 * Spring Cache 캐시 레지스트리 — 캐시 이름과 TTL을 한곳에서 관리한다.
 * 새 캐시는 여기에 상수를 추가하면 CacheConfig가 자동으로 등록한다.
 */
@AllArgsConstructor
@Getter
public enum CacheType {

    /**
     * 지도 결과셋 캐시 (key: regionId). 완주·러닝 공개 전환은 AFTER_COMMIT 이빅트로 즉시 반영되고
     * (CourseMapCacheEvictListener), 그 외 변경(코스 공개 전환 등)의 스테일 상한이 TTL이다.
     * (설계 cache/05 §4 — 이빅트가 있어야 TTL을 늘려 히트율을 확보할 수 있다, 시뮬레이션 v4)
     */
    COURSE_MAP(Names.COURSE_MAP, Duration.ofSeconds(600));

    private final String cacheName;
    private final Duration ttl;

    /** &#64;Cacheable 등 애노테이션 속성은 컴파일 타임 상수만 허용하므로 이름을 상수로도 노출한다. */
    public static final class Names {
        public static final String COURSE_MAP = "course-map";
        private Names() {}
    }
}
