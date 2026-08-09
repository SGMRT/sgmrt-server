package soma.ghostrunner.domain.course.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import soma.ghostrunner.DatabaseCleanserExtension;
import soma.ghostrunner.IntegrationTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RegionResolver}의 <b>전파 계약</b> 통합 테스트.
 *
 * <p>전파는 프록시가 있어야 성립하므로 단위 테스트로는 검증되지 않는다.
 * 여기서 고정하는 계약은 하나다 — {@code resolve}는 호출자의 트랜잭션에 <b>합류하지 않는다</b>
 * ({@code @Transactional(propagation = NEVER)}).
 *
 * <p>동시 등록 경쟁에서 유니크 충돌({@code uk_region_name})이 나면 그 트랜잭션은 rollback-only로 오염되어
 * {@code 충돌 → 재조회} 복구 경로가 통째로 무력화된다. NEVER는 트랜잭션 있는 호출자가 생겼을 때
 * 나중에 호출자 쪽에서 {@code UnexpectedRollbackException}으로 터지는 대신 <b>진입 즉시</b> 실패시킨다.
 * 기존 {@code RegionResolverTest}는 {@code NOT_SUPPORTED}로 트랜잭션을 벗어나 멱등 upsert만 검증하므로
 * 이 계약이 유실돼도 깨지지 않는다 — 그 공백을 메운다.
 *
 * <p>설계 문서: docs/design/reader-writer-layering.md §4 D4
 * (패턴 출처: {@code CourseReadModelWriterTest} / {@code MemberVdotWriterPropagationTest}의 전파 계약 테스트)
 */
@DisplayName("RegionResolver 전파 계약 통합 테스트")
@ExtendWith(DatabaseCleanserExtension.class)
class RegionResolverPropagationTest extends IntegrationTestSupport {

    private static final String REGION_NAME = "서울특별시 강남구 역삼동";

    /** 서비스 영역(위도 33~39, 경도 124~132) 안의 유효 좌표 — 인자 검증에서 먼저 터지면 전파 계약을 검증하지 못한다. */
    private static final double VALID_LAT = 37.5008;
    private static final double VALID_LNG = 127.0365;

    @Autowired
    private RegionResolver regionResolver;

    @DisplayName("활성 트랜잭션 안에서 resolve 를 호출하면 진입 즉시 예외를 던진다 — 유니크 충돌 복구 경로를 트랜잭션 오염으로부터 지킨다")
    @Test
    @Transactional  // 상위(IntegrationTestSupport)의 테스트 트랜잭션을 명시적으로 드러낸다
    void resolve_withinTransaction_throws() {
        // given : 활성 트랜잭션이 있는 상태 (전파 계약의 전제 조건)
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();

        // when & then : 프록시 인터셉터가 본문 실행 전에 막으므로 조회도 등록도 일어나지 않는다
        assertThatThrownBy(() -> regionResolver.resolve(REGION_NAME, VALID_LAT, VALID_LNG))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
