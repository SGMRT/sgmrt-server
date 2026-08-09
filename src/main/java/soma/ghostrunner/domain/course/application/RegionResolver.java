package soma.ghostrunner.domain.course.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.course.dao.RegionRepository;
import soma.ghostrunner.domain.course.domain.Region;
import soma.ghostrunner.domain.course.exception.InvalidRegionCoordinateException;

import java.text.Normalizer;

/**
 * 이름으로 지역을 해소(resolve)한다 — 없으면 등록하고, 있으면 기존 행을 그대로 돌려주는 멱등 연산.
 * 반환된 지역의 id가 코스 지도 캐시키가 되므로, 같은 이름은 언제나 같은 id로 해소되어야 한다.
 *
 * 설계 문서: docs/refactoring/course-read-model/cache/05-cache-key-design.md §6-3
 *
 * <p><b>이 클래스는 트랜잭션에 합류하지 않는다 — {@code @Transactional(propagation = NEVER)}로 런타임 강제한다.</b>
 * (의도적 결정이며 되돌리지 말 것)
 * 동시 등록 경쟁에서 유니크 충돌({@code uk_region_name})이 나면 그 트랜잭션은 rollback-only로 오염되어
 * 같은 트랜잭션 안에서 이어지는 재조회까지 실패한다 — 즉 아래 복구 경로가 통째로 무력화된다.
 * 트랜잭션이 없으면 리포지토리 호출이 각각 독립 트랜잭션으로 수행되어 {@code 충돌 → 재조회} 복구가 안전하다.
 * 조회 1건 + 삽입 1건뿐이라 원자성으로 묶어야 할 불변식도 없다.
 * NEVER는 이 계약을 주석이 아닌 실행되는 규칙으로 만든다 — 트랜잭션 있는 호출자가 생기면
 * 나중에 호출자 쪽에서 {@code UnexpectedRollbackException}으로 터지는 대신 <b>진입 즉시</b> 실패한다.</p>
 *
 * <p><b>Reader도 Writer도 아닌 {@code Resolver}다.</b> 이 저장소의 규칙은 "Writer = 쓰기 트랜잭션 경계"인데,
 * 위의 NEVER 계약 때문에 이 클래스는 트랜잭션을 열지 않는다 — 즉 Writer 규칙을 적용할 수 없다.
 * 조회와 등록이 한 멱등 연산으로 섞여 있어 Reader도 아니다. 역할 이름으로 규칙의 예외임을 드러낸다.
 * 설계 문서: docs/design/reader-writer-layering.md §4 D4</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class RegionResolver {

    /** 서비스 영역(대한민국) bbox — 신규 등록 좌표의 1차 방어선. 외부로 노출하지 않는다. */
    private static final double SERVICE_AREA_MIN_LAT = 33.0;
    private static final double SERVICE_AREA_MAX_LAT = 39.0;
    private static final double SERVICE_AREA_MIN_LNG = 124.0;
    private static final double SERVICE_AREA_MAX_LNG = 132.0;

    /** 기존 대표좌표에서 이 거리를 넘게 어긋난 재요청은 관측(WARN) 대상 — 자동 보정은 하지 않는다. */
    private static final double COORDINATE_DRIFT_WARN_M = 10_000;

    private static final double EARTH_RADIUS_M = 6_371_000;

    private final RegionRepository regionRepository;

    public Region resolve(String rawName, Double lat, Double lng) {
        String name = normalize(rawName);
        return regionRepository.findByName(name)
                .map(existingRegion -> warnIfCoordinateDrifted(existingRegion, lat, lng))
                .orElseGet(() -> saveNewRegion(name, lat, lng));
    }

    /**
     * iOS는 자모 분해형(NFD), Android는 완성형(NFC)으로 같은 동네 이름을 보낼 수 있다.
     * 정규화하지 않으면 두 문자열이 서로 다른 유니크 키가 되어 한 동네에 regionId가 둘 생기고,
     * 그 순간 캐시 키가 갈라져 같은 동네 사용자들이 캐시를 공유하지 못한다.
     *
     * <p>정규화는 이 진입점 한 곳에만 둔다 — 조회·저장 모두 여기서 만든 값만 쓴다.
     * Controller나 DTO에 흩어놓으면 "정규화된 이름만 저장된다"는 불변식의 소유자가 사라진다.</p>
     */
    private String normalize(String rawName) {
        return Normalizer.normalize(rawName, Normalizer.Form.NFC)
                .trim()
                .replaceAll("\\s+", " ");
    }

    private Region saveNewRegion(String name, Double lat, Double lng) {
        // 검증은 신규 생성 경로에만 건다 — 이미 등록된 동네의 조회(정상 트래픽 대다수)는 좌표와 무관하게 통과한다.
        validateWithinServiceArea(lat, lng);
        try {
            return regionRepository.save(Region.of(name, lat, lng));
        } catch (DataIntegrityViolationException raceLost) {
            // 동시 등록 경쟁에서 진 쪽 — 승자의 행을 재조회해 돌려준다
            return regionRepository.findByName(name).orElseThrow(() -> raceLost);
        }
    }

    /**
     * 대표좌표는 최초 등록 후 불변이라(캐시 값의 결정성 근거) 이상 좌표가 한 번 적재되면
     * 그 동네 전원의 지도가 영구히 어긋난다 — 복구 수단은 운영자의 수동 삭제뿐이므로 적재 시점에 막는다.
     */
    private void validateWithinServiceArea(Double lat, Double lng) {
        boolean withinServiceArea = lat >= SERVICE_AREA_MIN_LAT && lat <= SERVICE_AREA_MAX_LAT
                && lng >= SERVICE_AREA_MIN_LNG && lng <= SERVICE_AREA_MAX_LNG;
        if (!withinServiceArea) {
            // 예외 메시지에 좌표를 담지 않는다 (개인위치정보)
            throw new InvalidRegionCoordinateException();
        }
    }

    /**
     * 기존 행의 대표좌표와 크게 어긋난 재요청은 관측만 한다.
     * 자동 보정은 같은 캐시 키의 값을 바꿔 결정성을 훼손하므로 금지한다.
     *
     * <p>로그에는 regionId와 거리만 남긴다 — 좌표는 개인위치정보다.</p>
     */
    private Region warnIfCoordinateDrifted(Region region, Double lat, Double lng) {
        double distanceM = distanceInMeters(region.getCenterLat(), region.getCenterLng(), lat, lng);
        if (distanceM > COORDINATE_DRIFT_WARN_M) {
            log.warn("RegionResolver::resolve() - request coordinate drifted {}km from region {} center",
                    Math.round(distanceM / 1000), region.getId());
        }
        return region;
    }

    private double distanceInMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_M * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
