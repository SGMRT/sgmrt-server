package soma.ghostrunner.domain.course.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.course.domain.CourseSubscription;

import java.util.Optional;

@Repository
public interface CourseSubscriptionRepository extends JpaRepository<CourseSubscription, Long> {

    /**
     * 특정 코스와 멤버로 구독 정보를 조회
     */
    @Query("SELECT cs FROM CourseSubscription cs " +
            "WHERE cs.course.id = :courseId AND cs.member.id = :memberId")
    Optional<CourseSubscription> findByCourseIdAndMemberId(
            @Param("courseId") Long courseId,
            @Param("memberId") Long memberId
    );

    /**
     * 특정 코스의 활성 구독자 수 조회
     */
    @Query("SELECT COUNT(cs) FROM CourseSubscription cs " +
            "WHERE cs.course.id = :courseId AND cs.deleted = false")
    Long countActiveByCourseId(@Param("courseId") Long courseId);
}
