package soma.ghostrunner.domain.running.infra.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.course.dto.CourseRunDto;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.domain.Running;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface RunningRepository extends JpaRepository<Running, Long>, RunningQueryRepository {

    @Query("SELECT r.id FROM Running r WHERE r.course.id = :courseId")
    List<Long> findIdsByCourseId(@Param("courseId") Long courseId);

    Optional<Running> findByIdAndMemberId(Long runningId, Long memberId);

    @Query("""
        select r.runningDataUrls.interpolatedTelemetryUrl
        from Running r
        where r.id = :runningId
          and r.member.uuid = :memberUuid
    """)
    Optional<String> findInterpolatedTelemetryUrlByIdAndMemberUuid(Long runningId, String memberUuid);

    @Query("SELECT r FROM Running r JOIN FETCH r.member "
        + "WHERE r.course.id = :courseId AND r.isPublic = true")
    Page<Running> findByCourse_IdAndIsPublicTrue(Long courseId, Pageable pageable);

    @Query("SELECT r FROM Running r JOIN FETCH r.member m WHERE r.course.id = :courseId AND r.isPublic = true "
        + "AND m.uuid = :memberUuid ORDER BY r.runningRecord.duration LIMIT 1")
    Optional<Running> findBestPublicRunByCourseIdAndMemberId(Long courseId, String memberUuid);

    @Query("SELECT COUNT(r) FROM Running r "
        + "WHERE r.course.id = :courseId AND r.isPublic = true AND r.runningRecord.averagePace < :averagePace")
    Optional<Integer> countByCourseIdAndIsPublicTrueAndAveragePaceLessThan(Long courseId, Double averagePace);

    @Query("select r from Running r where r.course.id = :courseId order by r.id asc limit 1")
    Optional<Running> findFirstRunningByCourseId(Long courseId);

    @Query("select r from Running r where r.id in :runningIds")
    List<Running> findByIds(List<Long> runningIds);

    @Query(
            value = "select * from running_record r where r.id in :runningIds",
            nativeQuery = true
    )
    List<Running> findByIdsNoMatterDeleted(@Param("runningIds") List<Long> runningIds);

    @Modifying(clearAutomatically = true)
    @Query("delete from Running r where r.id in :runningIds")
    void deleteInRunningIds(@Param("runningIds") List<Long> runningIds);

    @Query("select count(r.id) from Running r where r.course.id = :courseId")
    long countTotalRunningsCount(Long courseId);

    @Query(value = "SELECT DISTINCT r.member_id FROM running_record r WHERE r.course_id = :courseId AND r.is_public = true",
            nativeQuery = true)
    List<Long> countPublicRunnersInCourse(Long courseId);

    @Query("select r from Running r where r.course.id = :courseId and r.member.id = :memberId order by r.startedAt desc, r.id desc")
    List<Running> findRunningsByCourseIdAndMemberId(Long courseId, Long memberId);

    @Query(value = """
        SELECT
            m.uuid AS runnerUuid,
            m.profile_picture_url AS runnerProfileUrl,
            m.nickname AS runnerNickname,
            rr.id AS runningId,
            rr.running_name AS runningName,
            rr.`average_pace_min/km` AS averagePace,
            rr.`average_cadence_spm` AS cadence,
            rr.`average_bpm` AS bpm,
            rr.`duration_sec` AS duration,
            rr.is_public AS isPublic,
            rr.started_at_ms  AS startedAt
        FROM (
            SELECT
                *,
                ROW_NUMBER() OVER(PARTITION BY member_id ORDER BY duration_sec ASC) as rn
            FROM running_record
            WHERE course_id = :courseId AND is_public = true
        ) AS rr
        JOIN member m ON rr.member_id = m.id
        WHERE rr.rn = 1
        ORDER BY rr.`duration_sec` ASC
        LIMIT :count
        """, nativeQuery = true
    )
    List<CourseRunDto> findTopRankingRunsByCourseIdWithDistinctMember(@Param("courseId") Long courseId, @Param("count") Integer count);

    void deleteAllByMember(Member member);

}
