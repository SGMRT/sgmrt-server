package soma.ghostrunner.domain.course.dao;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.dto.CourseSearchFilterDto;
import soma.ghostrunner.domain.course.dto.response.CourseDetailedResponse;
import soma.ghostrunner.domain.course.enums.CourseSortType;

import java.util.List;
import java.util.Optional;

import static soma.ghostrunner.domain.course.domain.QCourse.course;
import static soma.ghostrunner.domain.running.domain.QRunning.running;

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
  public List<Course> findCoursesWithFilters(Double curLat, Double curLng, Double minLat, Double maxLat,
                                                            Double minLng, Double maxLng, CourseSearchFilterDto filters, CourseSortType sort) {
    // todo - 코스에 딸린 러닝기록 수 반정규화해서 따로 저장해두면 굳이 Running 테이블까지 조인할 필요 없음
    JPAQuery<Course> query = queryFactory
            .selectFrom(course)
            .leftJoin(running).on(running.course.id.eq(course.id).and(running.isPublic.isTrue()))
            .leftJoin(course.member)
            .where(
                    course.isPublic.isTrue(),
                    startPointWithinBoundary(minLat, maxLat, minLng, maxLng),
                    withSearchFilters(filters)
            )
            .groupBy(course);

    // 정렬 조건 분기 처리
    if (sort == CourseSortType.DISTANCE) {
      query.orderBy(calculateDistance(curLat, curLng).asc());
    } else if (sort == CourseSortType.POPULARITY) {
      query.orderBy(running.id.count().desc(), course.id.desc());
    }

    return query.fetch();
  }
  
  /** Haversine 공식을 사용하여 (lat, lng)와 코스 사이 실제 거리를 계산하는 표현식 반환 */
  private NumberExpression<Double> calculateDistance(Double lat, Double lng) {
    NumberExpression<Double> latRad = Expressions.numberTemplate(Double.class, "RADIANS({0})", course.startCoordinate.latitude);
    NumberExpression<Double> userLatRad = Expressions.numberTemplate(Double.class, "RADIANS({0})", lat);
    NumberExpression<Double> lngRad = Expressions.numberTemplate(Double.class, "RADIANS({0})", course.startCoordinate.longitude);
    NumberExpression<Double> userLngRad = Expressions.numberTemplate(Double.class, "RADIANS({0})", lng);

    return Expressions.numberTemplate(Double.class,
            "6371 * acos(cos({0}) * cos({1}) * cos({2} - {3}) + sin({0}) * sin({1}))",
            latRad, userLatRad, lngRad, userLngRad);
  }

  /** 코스의 시작점이 주어진 위경도 범위 내에 존재하는지 판단하는 표현식 반환 */
  private BooleanExpression startPointWithinBoundary(Double minLat, Double maxLat, Double minLng, Double maxLng) {
    return course.startCoordinate.latitude.between(minLat, maxLat)
            .and(course.startCoordinate.longitude.between(minLng, maxLng));
  }

  /** 코스 검색 필터를 적용하는 반환식 반환 (최소 최대 거리, 고도, 소유자 id) */
  private BooleanExpression withSearchFilters(CourseSearchFilterDto filters) {
    if (filters == null) return null;

    return Expressions.allOf(
            filters.getMinElevationM() != null ?
                    course.courseProfile.distance.goe(filters.getMinDistanceM() / 1000.0) : null,
            filters.getMaxDistanceM() != null ?
                    course.courseProfile.distance.loe(filters.getMaxDistanceM() / 1000.0) : null,
            filters.getMinElevationM() != null ?
                    course.courseProfile.elevationGain.goe(filters.getMinElevationM()) : null,
            filters.getMaxElevationM() != null ?
                    course.courseProfile.elevationGain.loe(filters.getMaxElevationM()) : null,
            filters.getOwnerUuid() != null ?
                    course.member.uuid.eq(filters.getOwnerUuid()) : null
    );
  }

}
