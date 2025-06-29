package soma.ghostrunner.domain.course.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.course.domain.Course;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {
}
