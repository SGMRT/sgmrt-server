package soma.ghostrunner.domain.course.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.DatabaseCleanserExtension;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.dao.RegionRepository;
import soma.ghostrunner.domain.course.domain.Region;
import soma.ghostrunner.domain.course.exception.InvalidRegionCoordinateException;

import java.text.Normalizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RegionService 통합 테스트 — 멱등 upsert.
 *
 * 설계 문서: docs/refactoring/course-read-model/cache/05-cache-key-design.md §3-2, §6-3, §8
 *
 * 핵심 불변식은 두 가지다.
 * 1. 같은 이름은 언제나 같은 id로 해소된다 (멱등) — regionId가 캐시키이므로 이름 하나에 id가 둘이면 키가 갈라진다.
 * 2. 대표좌표는 최초 등록 좌표로 고정된다 — 캐시 값이 대표좌표 기준 고정 반경 조회 결과라,
 *    좌표가 뒤 요청으로 갱신되면 같은 키에 다른 값이 적재되어 결정성이 깨진다.
 *
 * <p>{@code NOT_SUPPORTED}로 상위({@code IntegrationTestSupport})의 테스트 트랜잭션을 벗어난다.
 * {@code RegionService}는 {@code @Transactional(propagation = NEVER)}라 트랜잭션 안에서는 호출 자체가 거부되며,
 * 실제 호출자({@code RegionApi})도 트랜잭션 밖이다 — 즉 프로덕션과 같은 조건에서 검증하기 위한 것이다.
 * 커밋이 실제로 일어나므로 정리는 {@link DatabaseCleanserExtension}이 맡는다.</p>
 */
@DisplayName("RegionService 통합 테스트 - 멱등 upsert")
@ExtendWith(DatabaseCleanserExtension.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RegionServiceTest extends IntegrationTestSupport {

    private static final String REGION_NAME = "서울특별시 강남구 역삼동";

    /** 최초 등록 요청의 좌표 — 이 좌표가 지역의 대표좌표로 고정된다 */
    private static final double FIRST_REQUEST_LAT = 37.5008;
    private static final double FIRST_REQUEST_LNG = 127.0365;

    /** 같은 동네 안 다른 지점에서 온 재요청 좌표 — 대표좌표를 덮어쓰지 못한다 */
    private static final double LATER_REQUEST_LAT = 37.4995;
    private static final double LATER_REQUEST_LNG = 127.0410;

    @Autowired
    private RegionService regionService;

    @Autowired
    private RegionRepository regionRepository;

    @DisplayName("같은 이름으로 좌표를 다르게 재요청해도 같은 지역을 반환하고 대표좌표는 최초 등록 좌표를 유지한다")
    @Test
    void resolve_withSameName_isIdempotentAndKeepsFirstCoordinate() {
        // given : 최초 등록 (대표좌표가 되는 좌표)
        Region firstResolved = regionService.resolve(REGION_NAME, FIRST_REQUEST_LAT, FIRST_REQUEST_LNG);

        // when : 같은 동네 안의 다른 지점에서 온 재요청 (좌표가 다름)
        Region secondResolved = regionService.resolve(REGION_NAME, LATER_REQUEST_LAT, LATER_REQUEST_LNG);

        // then : 같은 id로 해소되고, 행은 하나뿐이며, 대표좌표는 최초 등록 좌표 그대로다
        assertThat(secondResolved.getId()).isEqualTo(firstResolved.getId());
        assertThat(regionRepository.count()).isEqualTo(1L);
        assertThat(secondResolved.getCenterLat()).isEqualTo(FIRST_REQUEST_LAT);
        assertThat(secondResolved.getCenterLng()).isEqualTo(FIRST_REQUEST_LNG);
    }

    /**
     * iOS는 자모 분해형(NFD), Android는 완성형(NFC)으로 같은 동네 이름을 보낼 수 있다.
     * 정규화하지 않으면 두 문자열이 서로 다른 유니크 키가 되어 한 동네에 regionId가 둘 생기고,
     * 그 순간 캐시 키가 갈라져 같은 동네 사용자들이 캐시를 공유하지 못한다.
     */
    @DisplayName("자모 분해형(NFD) 이름으로 요청해도 완성형(NFC)으로 등록된 기존 지역과 같은 id로 해소된다")
    @Test
    void resolve_withDecomposedName_resolvesToSameRegionAsComposedName() {
        // given : 완성형(NFC) 이름으로 먼저 등록된 지역
        Region composedResolved = regionService.resolve(REGION_NAME, FIRST_REQUEST_LAT, FIRST_REQUEST_LNG);

        // when : 같은 동네를 자모 분해형(NFD)으로 보낸 재요청
        String decomposedName = Normalizer.normalize(REGION_NAME, Normalizer.Form.NFD);
        Region decomposedResolved = regionService.resolve(decomposedName, FIRST_REQUEST_LAT, FIRST_REQUEST_LNG);

        // then : 같은 id로 해소되고 행도 하나뿐이다 (키가 갈라지지 않는다)
        assertThat(decomposedResolved.getId()).isEqualTo(composedResolved.getId());
        assertThat(regionRepository.count()).isEqualTo(1L);
    }

    /**
     * 대표좌표는 최초 등록 후 불변이므로(위 멱등 테스트), 이상 좌표가 한 번 적재되면 그 동네 전원의 지도가 영구히 어긋난다.
     * 복구 수단은 운영자의 수동 삭제뿐이라 적재 시점에 막아야 한다.
     * 단 검증은 <b>신규 생성 경로에만</b> 건다 — 이미 등록된 동네의 조회(정상 트래픽 대다수)를 막으면 안 된다.
     */
    @DisplayName("서비스 영역 밖 좌표는 신규 지역 등록만 막고, 이미 등록된 지역의 조회는 막지 않는다")
    @Test
    void resolve_withCoordinateOutsideServiceArea_rejectsOnlyNewRegion() {
        // given : 서비스 영역(한국) 밖 좌표와, 정상 좌표로 이미 등록된 지역
        double outsideLat = 0.0;
        double outsideLng = 0.0;
        Region existingRegion = regionService.resolve(REGION_NAME, FIRST_REQUEST_LAT, FIRST_REQUEST_LNG);

        // when & then : 신규 이름 + 영역 밖 좌표는 거부된다
        assertThatThrownBy(() -> regionService.resolve("알 수 없는 동네", outsideLat, outsideLng))
                .isInstanceOf(InvalidRegionCoordinateException.class);
        assertThat(regionRepository.count()).isEqualTo(1L);

        // when & then : 이미 등록된 지역은 영역 밖 좌표로 요청해도 기존 id를 그대로 반환한다
        assertThatCode(() -> {
            Region resolved = regionService.resolve(REGION_NAME, outsideLat, outsideLng);
            assertThat(resolved.getId()).isEqualTo(existingRegion.getId());
        }).doesNotThrowAnyException();
    }
}
