package soma.ghostrunner.domain.pacemaker.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.pacemaker.domain.Pacemaker;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PacemakerRepository extends JpaRepository<Pacemaker, Long> {

    @Query("select p from Pacemaker p " +
            "where p.courseId = :courseId and p.memberUuid = :memberUuid and p.hasRunWith = false and " +
            "(p.status = soma.ghostrunner.domain.pacemaker.domain.Pacemaker.Status.COMPLETED " +
            "or p.status = soma.ghostrunner.domain.pacemaker.domain.Pacemaker.Status.FAILED " +
            "or p.status = soma.ghostrunner.domain.pacemaker.domain.Pacemaker.Status.PROCEEDING " +
            "or p.status = soma.ghostrunner.domain.pacemaker.domain.Pacemaker.Status.INIT) " +
            "order by p.createdAt desc " +
            "limit 1")
    Optional<Pacemaker> findByCourseId(Long courseId, String memberUuid);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Pacemaker p set p.deleted = true where p.id = :pacemakerId")
    int softDelete(Long pacemakerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PacemakerSet s set s.deleted = true where s.pacemaker.id = :pacemakerId")
    int softDeleteAllByPacemakerId(Long pacemakerId);

    /**
     * 복구 대상 Pacemaker ID 조회
     * - INIT 또는 PROCEEDING 상태
     * - lastRetryAt이 null이면 createdAt 기준, 아니면 lastRetryAt 기준으로 threshold 이전
     */
    @Query("select p.id from Pacemaker p " +
            "where (p.status = soma.ghostrunner.domain.pacemaker.domain.Pacemaker.Status.INIT " +
            "       or p.status = soma.ghostrunner.domain.pacemaker.domain.Pacemaker.Status.PROCEEDING) " +
            "and ((p.lastRetryAt is null and p.createdAt < :threshold) " +
            "     or (p.lastRetryAt is not null and p.lastRetryAt < :threshold)) " +
            "order by p.createdAt asc")
    List<Long> findRecoveryTargetIds(@Param("threshold") LocalDateTime threshold,
                                     org.springframework.data.domain.Pageable pageable);

}
