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
 * 목적:
 * - 리드모델 + Member JOIN 결과 매핑
 * - Native Query Projection
 */
public record CourseMapDto(
    // 코스 기본 정보
    Long courseId,
    String name,
    String ownerUuid,
    String source,  // CourseSource (ENUM → String for native query)
    String routeUrl,
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
     * @param myGhostInfo 내 러닝 정보 (별도 조회)
     * @return CourseMapResponse
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
            null, // checkpointsUrl (나중에)
            null, // thumbnailUrl (나중에)
            null, // distance (나중에)
            null, // elevationAverage (나중에)
            null, // elevationGain (나중에)
            null, // elevationLoss (나중에)
            null, // createdAt (나중에)
            myGhostInfo,
            runners,
            runnersCount
        );
    }
    
    /**
     * CoursePreviewDto로 변환 (랜덤 선별용)
     * 
     * @return CoursePreviewDto
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
            null, // thumbnailUrl
            null, // distance
            null, // elevationAverage
            null, // elevationGain
            null, // elevationLoss
            null  // createdAt
        );
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
