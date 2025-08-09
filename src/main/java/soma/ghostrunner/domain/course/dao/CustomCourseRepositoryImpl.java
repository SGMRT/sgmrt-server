package soma.ghostrunner.domain.course.dao;

import static soma.ghostrunner.domain.course.domain.QCourse.course;
import static soma.ghostrunner.domain.running.domain.QRunning.running;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.QCourse;
import soma.ghostrunner.domain.course.dto.CourseSearchFilterDto;
import soma.ghostrunner.domain.course.dto.response.CourseDetailedResponse;
import soma.ghostrunner.domain.member.domain.QMember;

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

  @Override
  public List<Course> findCoursesWithFilters(Double minLat, Double maxLat, Double minLng, Double maxLng,
                                             CourseSearchFilterDto filters) {
    QCourse course = QCourse.course;
    QMember member = QMember.member;

    BooleanBuilder builder = new BooleanBuilder();

    // 반경 필터링
    builder.and(course.startPoint.latitude.between(minLat, maxLat));
    builder.and(course.startPoint.longitude.between(minLng, maxLng));

    // 공개 여부 필터링
    builder.and(course.isPublic.isTrue());

    if(filters != null) {
      // 거리 필터링 (m 단위 -> km로 변환)
      if (Objects.nonNull(filters.getMinDistanceM())) {
        builder.and(course.courseProfile.distance.goe(filters.getMinDistanceM() / 1000.0));
      }
      if (Objects.nonNull(filters.getMaxDistanceM())) {
        builder.and(course.courseProfile.distance.loe(filters.getMaxDistanceM() / 1000.0));
      }

      // 고도 필터링
      if (Objects.nonNull(filters.getMinElevationM())) {
        builder.and(course.courseProfile.elevationGain.goe(filters.getMinElevationM()));
      }
      if (Objects.nonNull(filters.getMaxElevationM())) {
        builder.and(course.courseProfile.elevationGain.loe(filters.getMaxElevationM()));
      }

      // ownerId 필터링
      if (Objects.nonNull(filters.getOwnerUuid())) {
        builder.and(course.member.uuid.eq(filters.getOwnerUuid())); // member.id를 사용하여 필터링
      }
    }

    return queryFactory
        .selectFrom(course)
        .leftJoin(course.member, member) // member 조인
        .where(builder)
        .fetch();
  }

}
