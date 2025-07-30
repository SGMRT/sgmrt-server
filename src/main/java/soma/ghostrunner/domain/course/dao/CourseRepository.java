package soma.ghostrunner.domain.course.dao;

import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.course.domain.Course;

import java.util.List;

@Repository
public interface CourseRepository extends CustomCourseRepository, JpaRepository<Course, Long> {


    // TODO: owner 필드 포함한 후에는, ownerId가 일치하면 isPublic=false여도 보여줘야 함
    /** 기준점 위경도 반경 내에 Course의 startPoint가 존재하는 코스 검색 (직사각형 형태) */
    @Query("SELECT c FROM Course c WHERE " +
            "c.startPoint.latitude BETWEEN :minLat AND :maxLat AND " +
            "c.startPoint.longitude BETWEEN :minLng AND :maxLng AND " +
            "c.isPublic = true")
    List<Course> findPublicCoursesByBoundingBox(
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng,
            @Param("maxLng") Double maxLng
    );

    @Query("SELECT c FROM Course c LEFT JOIN FETCH c.member m WHERE m.uuid = :memberUuid ORDER BY c.createdAt DESC")
    Page<Course> findCoursesFetchJoinMembersByMemberUuidOrderByCreatedAtDesc(String memberUuid, Pageable pageable);

}
