package soma.ghostrunner.domain.course.dao;

import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.course.domain.Course;

import java.util.List;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {


    /** 기준점 위경도 반경 내에 Course의 startPoint가 존재하는 코스 검색 (직사각형 형태) */
    @Query("SELECT c FROM Course c WHERE " +
            "c.startPoint.latitude BETWEEN :minLat AND :maxLat AND " +
            "c.startPoint.longitude BETWEEN :minLng AND :maxLng")
    List<Course> findCoursesByBoundingBox(
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng,
            @Param("maxLng") Double maxLng
    );

}
