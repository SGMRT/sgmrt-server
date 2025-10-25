package soma.ghostrunner.domain.running.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.running.domain.PacemakerSet;

import java.util.List;

@Repository
public interface PacemakerSetRepository extends JpaRepository<PacemakerSet, Long> {

    @Query("select s from PacemakerSet s where s.pacemaker.id = :pacemakerId order by s.setNum asc, s.pace desc ")
    List<PacemakerSet> findByPacemakerIdOrderBySetNumAsc(Long pacemakerId);

}
