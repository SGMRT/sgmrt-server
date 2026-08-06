package soma.ghostrunner.domain.course.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import soma.ghostrunner.domain.course.domain.GeoCell;
import soma.ghostrunner.domain.course.dto.query.CellBucket;
import soma.ghostrunner.domain.course.dto.query.CourseMapDto;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;

/**
 * Redis가 불안정할 때의 캐시 어댑터 계약. (설계: docs/design/course-cell-bucket-cache-design.md §3-6, §6 테스트 8, [R4])
 *
 * <p>확정 결정 2는 "Redis 장애 시 전체-미스 강등 금지, 요청 단위 직행 강등"이다. 전체-미스로 뭉뚱그리면
 * 장애 순간에 대형 채움 쿼리와 재적재가 동시에 몰려 DB 부하가 증폭된다. 그래서 lookup은 "미스"가 아니라
 * <b>degraded</b>라는 별도 신호를 반환해야 하고, 쓰기(putAll/evict)는 best-effort로 조용히 실패해야 한다.
 *
 * <p>Redis를 mock으로 두는 이유 — 연결 실패·null 응답·응답 크기 불일치는 실제 컨테이너로 재현할 수 없다.
 */
@DisplayName("CourseCellCache 단위 테스트 - Redis 장애 강등")
@ExtendWith(MockitoExtension.class)
class CourseCellCacheDegradeTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private CourseCellCache cellCache;

    private static final GeoCell CELL_A = GeoCell.of(37.5665, 126.9780);
    private static final GeoCell CELL_B = GeoCell.of(37.5265, 127.0280);

    @BeforeEach
    void setUp() {
        cellCache = new CourseCellCache(
                redisTemplate,
                new ObjectMapper(),
                new CourseCellCacheMetrics(new SimpleMeterRegistry()));
    }

    @DisplayName("MGET이 실패하거나 응답이 null이거나 요청 키 수와 개수가 어긋나면 미스가 아니라 강등으로 신호한다")
    @Test
    void lookup_SignalsDegraded_WhenRedisResponseIsUnusable() {
        // given : 한 테스트 안에서 multiGet 응답을 세 번 갈아끼운다.
        //   스터빙은 반드시 will...().given(mock).method() 형태여야 한다 — given(mock.multiGet(...))는 인자를
        //   평가하며 목을 실제로 호출하므로, 앞서 등록해 둔 throwing 스텁이 스터빙 도중에 터진다.
        List<GeoCell> covering = List.of(CELL_A, CELL_B);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        // when & then : 연결 실패
        willThrow(new RedisConnectionFailureException("connection refused"))
                .given(valueOperations).multiGet(anyCollection());
        assertThat(cellCache.lookup(covering).degraded()).isTrue();

        // when & then : null 응답
        willReturn(null).given(valueOperations).multiGet(anyCollection());
        assertThat(cellCache.lookup(covering).degraded()).isTrue();

        // when & then : [R4] 요청 키가 2개인데 응답이 1개 — 인덱스로 셀과 값을 맞출 수 없으므로 장애로 본다
        willReturn(List.of("[]")).given(valueOperations).multiGet(anyCollection());
        assertThat(cellCache.lookup(covering).degraded()).isTrue();
    }

    @DisplayName("적재와 이빅트는 best-effort다 - Redis 예외를 호출자에게 전파하지 않는다")
    @Test
    void putAllAndEvict_SwallowRedisFailure() {
        // given
        given(redisTemplate.executePipelined(any(RedisCallback.class)))
                .willThrow(new RedisConnectionFailureException("connection refused"));
        given(redisTemplate.delete(anyString()))
                .willThrow(new RedisConnectionFailureException("connection refused"));

        // when & then : 채움 실패는 "캐시가 차갑게 남는" 손해일 뿐이라 조회 응답을 깨뜨려선 안 된다
        assertThatCode(() -> cellCache.putAll(List.of(new CellBucket(CELL_A, List.of(card())))))
                .doesNotThrowAnyException();

        // when & then : 이빅트 실패는 최대 TTL(600s) 지연일 뿐이고, AFTER_COMMIT에서 던지면 커밋 성공 후 500이 나간다
        assertThatCode(() -> cellCache.evict(CELL_A)).doesNotThrowAnyException();
    }

    private CourseMapDto card() {
        return new CourseMapDto(
                1L, "남산 순환 코스", "owner-uuid", "USER",
                "https://cdn.ghostrunner.io/routes/1.json",
                "https://cdn.ghostrunner.io/thumbnails/1.png",
                7.42, 123.4, 56.7, 45.6, 37.5665, 126.9780, 17L,
                1801, "top1-uuid", "https://cdn.ghostrunner.io/profiles/top1.png",
                null, null, null,
                null, null, null,
                null, null, null);
    }
}
