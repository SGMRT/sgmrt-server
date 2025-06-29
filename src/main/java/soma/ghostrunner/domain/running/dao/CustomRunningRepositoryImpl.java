package soma.ghostrunner.domain.running.dao;

import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
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
                                )))
                        .from(running)
                        .join(running.member, member)
                        .where(running.id.eq(runningId))
                        .fetchOne());
    }
}
