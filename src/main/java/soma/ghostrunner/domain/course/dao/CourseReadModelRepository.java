package soma.ghostrunner.domain.course.dao;

import io.lettuce.core.dynamic.annotation.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import soma.ghostrunner.domain.course.domain.CourseReadModel;

import java.util.List;
import java.util.Optional;

public interface CourseReadModelRepository extends JpaRepository<CourseReadModel, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CourseReadModel> findByCourseId(Long courseId);

    List<CourseReadModel> findByCourseIdIn(List<Long> courseIds);

    @Query("select distinct r.course.id from Running r where r.course.id in :courseIds and r.member.id = :memberId")
    List<Long> findMemberRunningIdsInCourses(List<Long> courseIds, Long memberId);

    @Query(value = """
        SELECT id
        FROM `ghost-runner`.course
        WHERE is_public = TRUE AND deleted = FALSE
              AND start_latitude BETWEEN :minLat AND :maxLat
              AND start_longtitude BETWEEN :minLng AND :maxLng
    """, nativeQuery = true)
    List<Long> findNearCourseIds(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLng") double minLng,
            @Param("maxLng") double maxLng
    );

}
