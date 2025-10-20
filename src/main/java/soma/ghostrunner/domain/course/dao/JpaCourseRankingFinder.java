package soma.ghostrunner.domain.course.dao;

import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import io.lettuce.core.dynamic.annotation.Param;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.course.domain.CourseRankFinder;
import soma.ghostrunner.domain.course.domain.QCourse;
import soma.ghostrunner.domain.course.dto.CourseRankInfo;
import soma.ghostrunner.domain.course.dto.QCourseRankInfo;
import soma.ghostrunner.domain.member.domain.QMember;
import soma.ghostrunner.domain.running.domain.QRunning;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class JpaCourseRankingFinder implements CourseRankFinder {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<CourseRankInfo> findCourseTop4RankInfoByCourseId(Long courseId) {

        QRunning r = QRunning.running;
        QMember m = QMember.member;

        NumberExpression<Long> minDuration = r.runningRecord.duration.min();

        return queryFactory
                .select(new QCourseRankInfo(
                        r.course.id,
                        r.member.id,
                        minDuration,
                        m.profilePictureUrl
                ))
                .from(r)
                .join(r.member, m)
                .where(
                        r.course.id.eq(courseId)
                                .and(r.isPublic.isTrue())
                )
                .groupBy(r.course.id, r.member.id, m.profilePictureUrl)
                .orderBy(minDuration.asc())
                .limit(4)
                .fetch();
    }

    @Override
    public Long countRunnersByCourseId(Long courseId) {
        QRunning r = QRunning.running;

        return queryFactory
                .select(r.member.id.countDistinct())
                .from(r)
                .where(r.course.id.eq(courseId))
                .fetchOne();
    }

    @Override
    public CourseRankInfo findFirstRunnerByCourseId(Long courseId) {

        QRunning r = QRunning.running;
        QMember m = QMember.member;

        return queryFactory
                .select(new QCourseRankInfo(
                        r.course.id,
                        m.id,
                        m.profilePictureUrl
                ))
                .from(r)
                .join(r.member, m)
                .where(r.course.id.eq(courseId))
                .orderBy(r.createdAt.asc())
                .limit(1)
                .fetchOne();
    }

}
