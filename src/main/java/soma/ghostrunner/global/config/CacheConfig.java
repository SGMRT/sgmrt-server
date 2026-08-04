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
 * - course-map: 600초 — 지도 결과셋 캐시. 완주·러닝 공개 전환은 AFTER_COMMIT 이빅트로 즉시 반영되고
 *   (CourseMapCacheEvictListener), 그 외 변경(코스 공개 전환 등)의 스테일 상한이 이 값이다.
 *   (설계 cache/05 §4 — 이빅트가 있어야 TTL을 늘려 히트율을 확보할 수 있다, 시뮬레이션 v4)
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Duration COURSE_MAP_TTL = Duration.ofSeconds(600);

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
                .enableStatistics()
                .build();
    }
}
