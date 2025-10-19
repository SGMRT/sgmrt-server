package soma.ghostrunner.domain.course.dao;

import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import soma.ghostrunner.domain.course.domain.CourseReadModel;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CourseReadModelRepository extends JpaRepository<CourseReadModel, Long> {

    Optional<CourseReadModel> findByCourseId(Long courseId);

    @Query("select crm " +
            "from CourseReadModel crm " +
            "where crm.startLatitude between :minLat and :maxLat and crm.startLongitude between :minLng and :maxLng ")
    List<CourseReadModel> findNearCoursesReadModel(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLng") double minLng,
            @Param("maxLng") double maxLng
    );

    @Query("select distinct r.course.id from Running r where r.course.id in :courseIds and r.member.id = :memberId")
    List<Long> findMemberRunningIdsInCourses(Set<Long> courseIds, Long memberId);

}
