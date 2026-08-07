package soma.ghostrunner.domain.pacemaker.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.pacemaker.domain.Pacemaker;

import java.util.Optional;

@Repository
public interface PacemakerRepository extends JpaRepository<Pacemaker, Long> {

    @Query("select p from Pacemaker p " +
            "where p.courseId = :courseId and p.memberUuid = :memberUuid and p.hasRunWith = false " +
            "order by p.createdAt desc " +
            "limit 1")
    Optional<Pacemaker> findByCourseId(Long courseId, String memberUuid);

    /**
     * 미완료(INIT/PROCEEDING) 상태일 때만 FAILED로 전환하는 원자적 조건부 UPDATE.
     * 완료 콜백과의 경쟁에서 이미 COMPLETED가 커밋됐다면 0건 매치로 물러나 —
     * 읽고-쓰기(dirty checking) 방식에서 생기던 Lost Update(LLM 결과 덮어쓰기)를 차단한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Pacemaker p " +
            "set p.status = soma.ghostrunner.domain.pacemaker.domain.Pacemaker.Status.FAILED " +
            "where p.id = :pacemakerId " +
            "and p.status in (soma.ghostrunner.domain.pacemaker.domain.Pacemaker.Status.INIT, " +
            "                 soma.ghostrunner.domain.pacemaker.domain.Pacemaker.Status.PROCEEDING)")
    int fallbackIfNotCompleted(Long pacemakerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Pacemaker p set p.deleted = true where p.id = :pacemakerId")
    int softDelete(Long pacemakerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PacemakerSet s set s.deleted = true where s.pacemaker.id = :pacemakerId")
    int softDeleteAllByPacemakerId(Long pacemakerId);

}
