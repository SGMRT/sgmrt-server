package soma.ghostrunner.domain.running.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.running.domain.PacemakerSet;

import java.util.List;

@Repository
public interface PacemakerSetRepository extends JpaRepository<PacemakerSet, Long> {

    @Modifying(clearAutomatically = true)
    @Query("delete from PacemakerSet ps where ps.pacemaker.id in " +
            "(select p.id from Pacemaker p where p.runningId in :runningIds)")
    void deletePacemakerSetsInRunningIds(@Param("runningIds") List<Long> runningIds);

}
