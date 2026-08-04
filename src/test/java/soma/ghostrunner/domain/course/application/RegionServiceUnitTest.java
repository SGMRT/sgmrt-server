package soma.ghostrunner.domain.course.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import soma.ghostrunner.domain.course.dao.RegionRepository;
import soma.ghostrunner.domain.course.domain.Region;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * RegionService 단위 테스트 — 동시 등록 경쟁.
 *
 * 설계 문서: docs/refactoring/course-read-model/cache/05-cache-key-design.md §6-3, §8
 *
 * 같은 동네의 두 사용자가 동시에 처음 등록하면 유니크 제약(uk_region_name)이 한쪽을 탈락시킨다.
 * 진 쪽은 예외를 밖으로 흘리지 말고 승자의 행을 재조회해 돌려줘야 한다 —
 * FE 입장에서 resolve는 언제나 성공해야 하는 멱등 연산이기 때문이다.
 * (실제 경쟁은 재현이 어려워 안전망이 되지 못하므로, 충돌 예외를 주입해 복구 경로만 고정한다)
 */
@DisplayName("RegionService 단위 테스트 - 동시 등록 경쟁")
@ExtendWith(MockitoExtension.class)
class RegionServiceUnitTest {

    private static final String REGION_NAME = "서울특별시 강남구 역삼동";

    /** 경쟁에서 이긴 쪽이 먼저 커밋한 좌표 — 대표좌표가 된다 */
    private static final double WINNER_LAT = 37.5008;
    private static final double WINNER_LNG = 127.0365;

    /** 경쟁에서 진 쪽이 보낸 좌표 — 버려진다 */
    private static final double LOSER_LAT = 37.4995;
    private static final double LOSER_LNG = 127.0410;

    @Mock
    private RegionRepository regionRepository;

    @InjectMocks
    private RegionService regionService;

    @DisplayName("동시 등록 경쟁에서 유니크 충돌이 나면 예외를 흘리지 않고 승자의 행을 반환한다")
    @Test
    void resolve_whenUniqueViolation_returnsRaceWinner() {
        // given : 첫 조회는 비어 있고(둘 다 신규로 판단), 저장은 승자에게 밀려 유니크 충돌,
        //         충돌 후 재조회에는 승자가 커밋한 행이 보인다
        Region winner = Region.of(REGION_NAME, WINNER_LAT, WINNER_LNG);
        given(regionRepository.findByName(REGION_NAME))
                .willReturn(Optional.empty())
                .willReturn(Optional.of(winner));
        given(regionRepository.save(any(Region.class)))
                .willThrow(new DataIntegrityViolationException("uk_region_name"));

        // when
        Region resolved = regionService.resolve(REGION_NAME, LOSER_LAT, LOSER_LNG);

        // then : 진 쪽도 승자의 행(= 승자가 등록한 대표좌표)을 받는다
        assertThat(resolved).isSameAs(winner);
        assertThat(resolved.getCenterLat()).isEqualTo(WINNER_LAT);
        assertThat(resolved.getCenterLng()).isEqualTo(WINNER_LNG);
    }
}
