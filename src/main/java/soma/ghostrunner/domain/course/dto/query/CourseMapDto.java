package soma.ghostrunner.domain.course.dto.query;

import soma.ghostrunner.domain.course.dto.CoursePreviewDto;
import soma.ghostrunner.domain.course.dto.RunnerProfile;
import soma.ghostrunner.domain.course.dto.response.CourseGhostResponse;
import soma.ghostrunner.domain.course.dto.response.CourseMapResponse;
import soma.ghostrunner.domain.course.enums.CourseSource;

import java.util.ArrayList;
import java.util.List;

/**
 * 지도 조회용 DTO (CQRS Query)
 *
 * <ul>
 *   <li>리드모델 + Member JOIN 네이티브 쿼리 프로젝션 — 컴포넌트 순서·이름이 SELECT 별칭과 일치해야 한다.</li>
 *   <li>응답의 checkpointsUrl / createdAt 은 클라이언트 미사용이 확인되어 리드모델에 역정규화하지 않고
 *       null 로 내려간다. (설계 04 §1-2 — 외부 API 필드 자체는 유지)</li>
 * </ul>
 */
public record CourseMapDto(
    // 코스 기본 정보
    Long courseId,
    String name,
    String ownerUuid,
    String source,  // CourseSource (ENUM → String for native query)
    String routeUrl,
    String thumbnailUrl,
    Double distanceKm,
    Double elevationAverageM,
    Double elevationGainM,
    Double elevationLossM,
    Double startLat,
    Double startLng,
    Long runnersCount,

    // TOP1 러너
    Integer top1TimeSeconds,
    String top1Uuid,
    String top1ProfileUrl,

    // TOP2 러너
    Integer top2TimeSeconds,
    String top2Uuid,
    String top2ProfileUrl,

    // TOP3 러너
    Integer top3TimeSeconds,
    String top3Uuid,
    String top3ProfileUrl,

    // TOP4 러너
    Integer top4TimeSeconds,
    String top4Uuid,
    String top4ProfileUrl
) {

    /**
     * CourseMapResponse로 변환
     *
     * @param myGhostInfo 내 러닝 정보 (개인화 — 별도 조회)
     */
    public CourseMapResponse toResponse(CourseGhostResponse myGhostInfo) {
        List<RunnerProfile> runners = buildRunnerProfiles();
        CourseSource courseSource = source != null ? CourseSource.valueOf(source) : null;

        return new CourseMapResponse(
            courseId,
            name,
            ownerUuid,
            courseSource,
            startLat,
            startLng,
            routeUrl,
            null, // checkpointsUrl — 클라 미사용, 역정규화 안 함
            thumbnailUrl,
            toDistanceMeters(),
            toInteger(elevationAverageM),
            toInteger(elevationGainM),
            toInteger(elevationLossM),
            null, // createdAt — 클라 미사용, 역정규화 안 함
            myGhostInfo,
            runners,
            runnersCount
        );
    }

    /**
     * CoursePreviewDto로 변환 (랜덤 선별용)
     */
    public CoursePreviewDto toPreviewDto() {
        CourseSource courseSource = source != null ? CourseSource.valueOf(source) : CourseSource.USER;

        return new CoursePreviewDto(
            courseId,
            name,
            ownerUuid,
            startLat,
            startLng,
            courseSource,
            routeUrl,
            null, // checkpointsUrl
            thumbnailUrl,
            toDistanceMeters(),
            toInteger(elevationAverageM),
            toInteger(elevationGainM),
            toInteger(elevationLossM),
            null  // createdAt
        );
    }

    /** 기존 응답 규약: 거리(km, Double) → 미터(Integer). CourseMapper 의 변환식과 동일해야 파리티가 유지된다. */
    private Integer toDistanceMeters() {
        return distanceKm != null ? (int) (distanceKm * 1000) : null;
    }

    private Integer toInteger(Double value) {
        return value != null ? value.intValue() : null;
    }

    /**
     * TOP4 러너 프로필 리스트 생성
     */
    private List<RunnerProfile> buildRunnerProfiles() {
        List<RunnerProfile> runners = new ArrayList<>(4);

        if (top1Uuid != null) {
            runners.add(new RunnerProfile(top1Uuid, top1ProfileUrl, top1TimeSeconds));
        }
        if (top2Uuid != null) {
            runners.add(new RunnerProfile(top2Uuid, top2ProfileUrl, top2TimeSeconds));
        }
        if (top3Uuid != null) {
            runners.add(new RunnerProfile(top3Uuid, top3ProfileUrl, top3TimeSeconds));
        }
        if (top4Uuid != null) {
            runners.add(new RunnerProfile(top4Uuid, top4ProfileUrl, top4TimeSeconds));
        }

        return runners;
    }
}
