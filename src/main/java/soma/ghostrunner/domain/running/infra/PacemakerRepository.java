package soma.ghostrunner.domain.running.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.running.domain.Pacemaker;

import java.util.List;

@Repository
public interface PacemakerRepository extends JpaRepository<Pacemaker, Long> {

    @Modifying(clearAutomatically = true)
    @Query("delete from Pacemaker p where p.runningId in :runningIds")
    void deletePacemakersInRunningIds(@Param("runningIds") List<Long> runningIds);

}
