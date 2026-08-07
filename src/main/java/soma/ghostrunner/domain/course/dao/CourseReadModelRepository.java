package soma.ghostrunner.domain.course.dao;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.course.domain.CourseReadModel;
import soma.ghostrunner.domain.course.dto.query.CourseMapDto;
import soma.ghostrunner.domain.course.dto.query.TopRunnerRow;

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
            rm.thumbnail_url AS thumbnailUrl,
            rm.distance_km AS distanceKm,
            rm.elevation_average_m AS elevationAverageM,
            rm.elevation_gain_m AS elevationGainM,
            rm.elevation_loss_m AS elevationLossM,
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
        LEFT JOIN member m1 ON rm.top1_member_id = m1.id
        LEFT JOIN member m2 ON rm.top2_member_id = m2.id
        LEFT JOIN member m3 ON rm.top3_member_id = m3.id
        LEFT JOIN member m4 ON rm.top4_member_id = m4.id

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

    /**
     * TOP4 재계산: 멤버별 최고 기록(MIN) 집계 후 기록 오름차순 상위 4행
     *
     * 집계 대상 필터(재계산 3종 쿼리 공통): is_public = TRUE AND deleted = FALSE AND has_paused = FALSE
     * - 일시정지 러닝은 랭킹 신뢰도 문제로 집계에서 제외한다.
     *
     * 인덱스: idx_record_course (is_public, deleted, course_id, member_id, duration_sec)
     * - 선행 컬럼이 상수로 고정되므로 (course_id, member_id) 구간의 루즈 인덱스 스캔 + 커버링으로 동작한다.
     *
     * @param courseId 코스 ID
     * @return 멤버별 최고 기록 상위 4행 (기록 오름차순)
     */
    @Query(nativeQuery = true, value = """
        SELECT
            r.member_id AS memberId,
            CAST(MIN(r.duration_sec) AS SIGNED) AS bestDurationSeconds
        FROM running_record r
        WHERE r.is_public = TRUE
          AND r.deleted = FALSE
          AND r.has_paused = FALSE
          AND r.course_id = :courseId
        GROUP BY r.member_id
        ORDER BY bestDurationSeconds ASC
        LIMIT 4
    """)
    List<TopRunnerRow> findTop4RunnersByBestDuration(@Param("courseId") Long courseId);

    /**
     * 러너 수 재계산: 집계 대상 필터 기준의 서로 다른 멤버 수
     *
     * 집계 대상 필터: is_public = TRUE AND deleted = FALSE AND has_paused = FALSE
     * - 이름이 비슷한 (구) RunningRepository의 countDistinctRunnersByCourseId(삭제됨),
     *   {@code RunningRepository#countPublicRunnersInCourse} 와 달리 일시정지 러닝을 제외하므로
     *   TOP4 집계 결과와 러너 수가 항상 같은 모집단에서 나온다.
     *
     * @param courseId 코스 ID
     * @return 집계 대상 러너 수 (러닝이 없으면 0)
     */
    @Query(nativeQuery = true, value = """
        SELECT COUNT(DISTINCT r.member_id)
        FROM running_record r
        WHERE r.is_public = TRUE
          AND r.deleted = FALSE
          AND r.has_paused = FALSE
          AND r.course_id = :courseId
    """)
    long countDistinctPublicRunners(@Param("courseId") Long courseId);

    /**
     * 첫 러닝 판정: 판정 대상 러닝을 제외하고도 해당 멤버의 집계 대상 러닝이 남아있는지 여부
     *
     * 집계 대상 필터: is_public = TRUE AND deleted = FALSE AND has_paused = FALSE
     * - deleted = FALSE 는 Running 의 {@code @SoftDelete} 로 JPQL 에 자동 부여된다.
     *
     * 네이티브가 아닌 JPQL 인 이유: MySQL 의 EXISTS 는 0/1 을 BIGINT 로 반환해
     * boolean 리턴 타입으로 변환되지 않는다. JPQL + CASE WHEN 으로 boolean 을 직접 만든다.
     *
     * @param courseId 코스 ID
     * @param memberId 멤버 ID
     * @param excludeRunningId 제외할 러닝 ID (판정 대상 러닝)
     * @return 제외 후에도 집계 대상 러닝이 남아있으면 true (= 첫 러닝이 아님)
     */
    @Query("""
        SELECT CASE WHEN COUNT(r) > 0 THEN TRUE ELSE FALSE END
        FROM Running r
        WHERE r.isPublic = true
          AND r.hasPaused = false
          AND r.course.id = :courseId
          AND r.member.id = :memberId
          AND r.id <> :excludeRunningId
    """)
    boolean existsOtherPublicRunByCourseAndMember(
        @Param("courseId") Long courseId,
        @Param("memberId") Long memberId,
        @Param("excludeRunningId") Long excludeRunningId
    );
}
