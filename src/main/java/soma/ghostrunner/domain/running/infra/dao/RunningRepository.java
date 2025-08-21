package soma.ghostrunner.domain.running.infra.dao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.running.domain.Running;

import java.util.List;
import java.util.Optional;

@Repository
public interface RunningRepository extends JpaRepository<Running, Long>, CustomRunningRepository {

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
        + "AND m.uuid = :memberUuid ORDER BY r.runningRecord.averagePace LIMIT 1")
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
    void deleteAllByIdIn(@Param("runningIds") List<Long> runningIds);

    @Query("select count(r.id) from Running r where r.course.id = :courseId")
    long countTotalRunningsCount(Long courseId);

}
