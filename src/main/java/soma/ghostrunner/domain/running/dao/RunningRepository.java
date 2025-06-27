package soma.ghostrunner.domain.running.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.running.domain.Running;

import java.util.List;
import java.util.Optional;

@Repository
public interface RunningRepository extends JpaRepository<Running, Long>, CustomRunningRepository {

    @Query("SELECT r.id FROM Running r WHERE r.course.id = :courseId")
    List<Long> findIdsByCourseId(@Param("courseId") Long courseId);

    @Query("SELECT r FROM Running r WHERE r.id = :runningId and r.member.id = :memberId")
    Optional<Running> findByRunningIdAndMemberId(@Param("runningId") Long runningId, @Param("memberId") Long memberId);
}
