package soma.ghostrunner.domain.running.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.running.domain.Pacemaker;

import java.util.List;
import java.util.Optional;

@Repository
public interface PacemakerRepository extends JpaRepository<Pacemaker, Long> {

    @Query("select p from Pacemaker p " +
            "where p.courseId = :courseId and p.memberUuid = :memberUuid and p.hasRunWith = false " +
            "order by p.createdAt desc " +
            "limit 1")
    Optional<Pacemaker> findByCourseId(Long courseId, String memberUuid);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Pacemaker p set p.deleted = true where p.id = :pacemakerId")
    int softDelete(Long pacemakerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PacemakerSet s set s.deleted = true where s.pacemaker.id = :pacemakerId")
    int softDeleteAllByPacemakerId(Long pacemakerId);

}
