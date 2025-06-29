package soma.ghostrunner.domain.running.dao;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
