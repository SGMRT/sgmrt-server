package soma.ghostrunner.domain.course.dao;

import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.course.dto.BestDurationInCourseDto;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.dto.BestDurationProjection;
import soma.ghostrunner.domain.member.domain.Member;

import java.util.List;

@Repository
public interface CourseRepository extends CustomCourseRepository, JpaRepository<Course, Long> {


    @Query(value = """
        SELECT r.course_id AS courseId,
               r.member_id AS memberId,
               MIN(r.duration_sec) AS bestDurationSec
        FROM `ghost-runner`.running_record r
        INNER JOIN (
            SELECT id
            FROM `ghost-runner`.course
            WHERE is_public = TRUE AND deleted = FALSE
              AND start_latitude BETWEEN :minLat AND :maxLat
              AND start_longtitude BETWEEN :minLng AND :maxLng
        ) filtered_courses ON r.course_id = filtered_courses.id
        WHERE r.is_public = TRUE AND r.deleted = FALSE
        GROUP BY r.course_id, r.member_id
        ORDER BY r.course_id, bestDurationSec
        """, nativeQuery = true
    )
    List<BestDurationProjection> findBestDurations(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLng") double minLng,
            @Param("maxLng") double maxLng
    );

    @Query("SELECT c FROM Course c LEFT JOIN FETCH c.member m " +
            "WHERE m.uuid = :memberUuid AND c.isPublic = true " +
            "ORDER BY c.createdAt DESC")
    Page<Course> findPublicCoursesFetchJoinMembersByMemberUuidOrderByCreatedAtDesc(String memberUuid, Pageable pageable);

    List<Course> findAllByMember(Member member);

}
