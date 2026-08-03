package soma.ghostrunner.domain.course.dao;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.course.domain.CourseReadModel;
import soma.ghostrunner.domain.course.dto.query.CourseMapDto;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseReadModelRepository extends JpaRepository<CourseReadModel, Long> {
    
    Optional<CourseReadModel> findByCourseId(Long courseId);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rm FROM CourseReadModel rm WHERE rm.courseId = :courseId")
    Optional<CourseReadModel> findByCourseIdForUpdate(@Param("courseId") Long courseId);
    
    /**
     * 지도에서 주변 코스 조회 (리드모델 + Member JOIN)
     * 
     * 목적:
     * - N+1 쿼리 제거
     * - 단일 쿼리로 코스 정보 + TOP4 러너 정보 조회
     * 
     * @param minLat 최소 위도
     * @param maxLat 최대 위도
     * @param minLng 최소 경도
     * @param maxLng 최대 경도
     * @param limit 조회 개수 제한
     * @return 코스 + TOP4 러너 정보
     */
    @Query(nativeQuery = true, value = """
        SELECT 
            rm.course_id AS courseId,
            rm.name AS name,
            rm.owner_uuid AS ownerUuid,
            rm.source AS source,
            rm.route_url AS routeUrl,
            rm.start_lat AS startLat,
            rm.start_lng AS startLng,
            rm.runners_count AS runnersCount,
            
            rm.top1_time_seconds AS top1TimeSeconds,
            m1.uuid AS top1Uuid,
            m1.profile_picture_url AS top1ProfileUrl,
            
            rm.top2_time_seconds AS top2TimeSeconds,
            m2.uuid AS top2Uuid,
            m2.profile_picture_url AS top2ProfileUrl,
            
            rm.top3_time_seconds AS top3TimeSeconds,
            m3.uuid AS top3Uuid,
            m3.profile_picture_url AS top3ProfileUrl,
            
            rm.top4_time_seconds AS top4TimeSeconds,
            m4.uuid AS top4Uuid,
            m4.profile_picture_url AS top4ProfileUrl
            
        FROM course_read_model rm
        LEFT JOIN member m1 ON rm.top1_member_id = m1.id AND m1.deleted_at IS NULL
        LEFT JOIN member m2 ON rm.top2_member_id = m2.id AND m2.deleted_at IS NULL
        LEFT JOIN member m3 ON rm.top3_member_id = m3.id AND m3.deleted_at IS NULL
        LEFT JOIN member m4 ON rm.top4_member_id = m4.id AND m4.deleted_at IS NULL
        
        WHERE rm.is_public = true
          AND rm.start_lat BETWEEN :minLat AND :maxLat
          AND rm.start_lng BETWEEN :minLng AND :maxLng
        
        ORDER BY rm.start_lat, rm.start_lng
        LIMIT :limit
    """)
    List<CourseMapDto> findCoursesForMap(
        @Param("minLat") Double minLat,
        @Param("maxLat") Double maxLat,
        @Param("minLng") Double minLng,
        @Param("maxLng") Double maxLng,
        @Param("limit") int limit
    );
}
