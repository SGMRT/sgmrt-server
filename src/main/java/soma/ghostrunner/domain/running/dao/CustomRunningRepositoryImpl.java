package soma.ghostrunner.domain.running.dao;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.course.dto.CourseRunStatisticsDto;
import soma.ghostrunner.domain.running.application.dto.response.*;
import soma.ghostrunner.domain.running.domain.QRunning;

import java.util.Optional;

import static soma.ghostrunner.domain.course.domain.QCourse.course;
import static soma.ghostrunner.domain.member.QMember.member;
import static soma.ghostrunner.domain.running.domain.QRunning.running;

@Repository
@RequiredArgsConstructor
public class CustomRunningRepositoryImpl implements CustomRunningRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<SoloRunDetailInfo> findSoloRunInfoById(long runningId) {

        QRunning subRunning = new QRunning("subRunning");

        return Optional.ofNullable(
                queryFactory
                        .select(new QSoloRunDetailInfo(
                                running.startedAt,
                                running.runningName,
                                new QCourseInfo(
                                        course.id,
                                        course.name,
                                        course.isPublic,
                                        JPAExpressions.select(subRunning.count()).from(subRunning).where(subRunning.course.id.eq(course.id))),
                                new QRunRecordInfo(
                                        running.runningRecord.distance,
                                        running.runningRecord.duration,
                                        running.runningRecord.cadence,
                                        running.runningRecord.bpm,
                                        running.runningRecord.burnedCalories,
                                        running.runningRecord.averagePace,
                                        running.runningRecord.highestPace,
                                        running.runningRecord.lowestPace,
                                        running.runningRecord.elevationGain,
                                        running.runningRecord.elevationLoss
                                ),
                                running.telemetryUrl
                        ))
                        .from(running)
                        .join(running.course, course)
                        .where(running.id.eq(runningId))
                        .fetchOne());
    }

    @Override
    public Optional<GhostRunDetailInfo> findGhostRunInfoById(long runningId) {

        QRunning subRunning = new QRunning("subRunning");

        return Optional.ofNullable(
                queryFactory
                        .select(new QGhostRunDetailInfo(
                                running.startedAt,
                                running.runningName,
                                new QCourseInfo(
                                        course.id,
                                        course.name,
                                        course.isPublic,
                                        JPAExpressions.select(subRunning.count()).from(subRunning).where(subRunning.course.id.eq(course.id))),
                                new QMemberAndRunRecordInfo(
                                        member.nickname,
                                        member.profilePictureUrl,
                                        new QRunRecordInfo(
                                                running.runningRecord.distance,
                                                running.runningRecord.duration,
                                                running.runningRecord.cadence,
                                                running.runningRecord.bpm,
                                                running.runningRecord.burnedCalories,
                                                running.runningRecord.averagePace,
                                                running.runningRecord.highestPace,
                                                running.runningRecord.lowestPace,
                                                running.runningRecord.elevationGain,
                                                running.runningRecord.elevationLoss
                                        )),
                                running.ghostRunningId,
                                running.telemetryUrl
                        ))
                        .from(running)
                        .join(running.course, course)
                        .join(running.member, member)
                        .where(running.id.eq(runningId))
                        .fetchOne());
    }

    @Override
    public Optional<MemberAndRunRecordInfo> findMemberAndRunRecordInfoById(long runningId) {
        return Optional.ofNullable(
                queryFactory
                        .select(new QMemberAndRunRecordInfo(
                                member.nickname,
                                member.profilePictureUrl,
                                new QRunRecordInfo(
                                        running.runningRecord.duration,
                                        running.runningRecord.cadence,
                                        running.runningRecord.averagePace
                                )))
                        .from(running)
                        .join(running.member, member)
                        .where(running.id.eq(runningId))
                        .fetchOne());
    }

    @Override
    public Optional<CourseRunStatisticsDto> findPublicRunStatisticsByCourseId(Long courseId) {
        return Optional.ofNullable(
                queryFactory
                        .select(Projections.constructor(CourseRunStatisticsDto.class,
                                running.runningRecord.duration.avg(),
                                running.runningRecord.averagePace.avg(),
                                running.runningRecord.cadence.avg(),
                                running.runningRecord.averagePace.min()
                        ))
                        .from(running)
                        .where(running.course.id.eq(courseId)
                                .and(running.isPublic.isTrue()))
                        .fetchOne()
        );
    }

}
