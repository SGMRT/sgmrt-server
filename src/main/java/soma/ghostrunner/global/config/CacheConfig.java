package soma.ghostrunner.global.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import soma.ghostrunner.domain.course.application.CourseReadModelReader;

import java.time.Duration;

/**
 * Spring Cache(Redis) 구성.
 *
 * 캐시별 TTL:
 * - course-map: 60초 — 지도 결과셋 캐시. 무효화 없이 TTL 만료에만 의존하므로
 *   데이터 변경 후 스테일 상한이 곧 이 값이다. (설계 04 §2-2)
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Duration COURSE_MAP_TTL = Duration.ofSeconds(60);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withCacheConfiguration(CourseReadModelReader.COURSE_MAP_CACHE,
                        defaults.entryTtl(COURSE_MAP_TTL))
                // 캐시별 히트/미스 카운터를 Micrometer 로 노출 (cache_gets_total{cache="course-map",result=...})
                // — regionId 키 전략의 히트율을 운영에서 실측하기 위함 (설계 cache/05 §4, 오버헤드는 카운터 증가 수준)
                .enableStatistics()
                .build();
    }
}
