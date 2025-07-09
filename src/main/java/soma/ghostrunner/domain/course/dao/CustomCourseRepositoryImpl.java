package soma.ghostrunner.domain.course.dao;

import static soma.ghostrunner.domain.course.domain.QCourse.course;
import static soma.ghostrunner.domain.running.domain.QRunning.running;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.course.dto.response.CourseDetailedResponse;

@Repository
@RequiredArgsConstructor
public class CustomCourseRepositoryImpl implements CustomCourseRepository{

  private final JPAQueryFactory queryFactory;

  @Override
  public Optional<CourseDetailedResponse> findCourseDetailedById(Long courseId) {
    return Optional.ofNullable(
        queryFactory
            .select(Projections.constructor(CourseDetailedResponse.class,
                course.id,
                course.name,
                course.courseProfile.distance.castToNum(Integer.class),
                course.courseProfile.elevationGain,
                course.courseProfile.elevationLoss,
                running.runningRecord.duration.avg().castToNum(Integer.class),
                running.runningRecord.averagePace.avg().castToNum(Integer.class),
                running.runningRecord.cadence.avg().castToNum(Integer.class),
                running.runningRecord.lowestPace.min().castToNum(Integer.class)
            ))
            .from(course)
            .leftJoin(running).on(running.course.id.eq(course.id).and(running.isPublic.isTrue()))
            .where(course.id.eq(courseId))
            .groupBy(course.id, course.name, course.courseProfile.distance, course.courseProfile.elevationGain, course.courseProfile.elevationLoss)
            .fetchOne()
    );
  }
}
