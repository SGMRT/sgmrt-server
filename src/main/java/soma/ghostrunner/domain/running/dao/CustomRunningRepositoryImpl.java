package soma.ghostrunner.domain.running.dao;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.course.dto.CourseRunStatisticsDto;
import soma.ghostrunner.domain.running.application.dto.response.*;
import soma.ghostrunner.domain.running.domain.RunningMode;

import java.util.List;
import java.util.Optional;

import static soma.ghostrunner.domain.course.domain.QCourse.course;
import static soma.ghostrunner.domain.member.domain.QMember.member;
import static soma.ghostrunner.domain.running.domain.QRunning.running;

@Repository
@RequiredArgsConstructor
public class CustomRunningRepositoryImpl implements CustomRunningRepository {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<SoloRunDetailInfo> findSoloRunInfoById(long runningId, String memberUuid) {

        return Optional.ofNullable(
                queryFactory
                        .select(new QSoloRunDetailInfo(
                                running.startedAt,
                                running.runningName,
                                new QCourseInfo(
                                        course.id,
                                        course.name,
                                        course.isPublic),
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
                                        running.runningRecord.elevationLoss,
                                        running.runningRecord.elevationAverage
                                ),
                                running.runningDataUrls.interpolatedTelemetryUrl,
                                running.isPublic
                        ))
                        .from(running)
                        .join(running.course, course)
                        .join(running.member, member)
                        .where(running.id.eq(runningId).and(member.uuid.eq(memberUuid)))
                        .fetchOne());
    }

    @Override
    public Optional<GhostRunDetailInfo> findGhostRunInfoById(long runningId, String memberUuid) {

        return Optional.ofNullable(
                queryFactory
                        .select(new QGhostRunDetailInfo(
                                running.startedAt,
                                running.runningName,
                                new QCourseInfo(
                                        course.id,
                                        course.name
                                ),
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
                                                running.runningRecord.elevationLoss,
                                                running.runningRecord.elevationAverage
                                        )),
                                running.ghostRunningId,
                                running.runningDataUrls.interpolatedTelemetryUrl,
                                running.isPublic
                        ))
                        .from(running)
                        .join(running.course, course)
                        .join(running.member, member)
                        .where(running.id.eq(runningId)
                                .and(member.uuid.eq(memberUuid)))
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

    public List<RunInfo> findRunInfosFilteredByDate(
            RunningMode runningMode,
            Long cursorStartedAt, Long cursorRunningId,
            Long startEpoch, Long endEpoch,
            Long memberId
    ) {
        return queryFactory
                .select(new QRunInfo(
                        running.id,
                        running.runningName,
                        running.startedAt,
                        new QRunRecordInfo(
                                running.runningRecord.distance,
                                running.runningRecord.duration,
                                running.runningRecord.averagePace,
                                running.runningRecord.cadence
                        ),
                        new QCourseInfo(
                                running.course.id,
                                running.course.name,
                                running.course.isPublic
                        ),
                        running.ghostRunningId,
                        running.runningDataUrls.screenShotUrl
                ))
                .from(running)
                .join(running.course, course)
                .where(
                        running.member.id.eq(memberId),
                        running.runningMode.eq(runningMode),
                        startedAtRange(startEpoch, endEpoch),
                        seekAfterAsc(cursorStartedAt, cursorRunningId)
                )
                .orderBy(running.startedAt.asc(), running.id.asc())
                .limit(DEFAULT_PAGE_SIZE + 1)
                .fetch();
    }

    private BooleanExpression startedAtRange(Long start, Long end) {
        return running.startedAt.between(start, end);
    }

    private BooleanExpression seekAfterAsc(Long cursorStartedAt, Long cursorId) {
        if (cursorStartedAt == null || cursorId == null) return null; // 첫 페이지
        return running.startedAt.gt(cursorStartedAt)
                .or(running.startedAt.eq(cursorStartedAt).and(running.id.gt(cursorId)));
    }

    @Override
    public List<RunInfo> findRunInfosFilteredByCourses(
            RunningMode runningMode,
            String cursorCourseName, Long cursorRunningId,
            Long startEpoch, Long endEpoch,
            Long memberId
    ) {
        return queryFactory
                .select(new QRunInfo(
                        running.id,
                        running.runningName,
                        running.startedAt,
                        new QRunRecordInfo(
                                running.runningRecord.distance,
                                running.runningRecord.duration,
                                running.runningRecord.averagePace,
                                running.runningRecord.cadence
                        ),
                        new QCourseInfo(
                                running.course.id,
                                running.course.name,
                                running.course.isPublic
                        ),
                        running.ghostRunningId,
                        running.runningDataUrls.screenShotUrl
                ))
                .from(running)
                .join(running.course, course)
                .where(
                        running.member.id.eq(memberId),
                        running.runningMode.eq(runningMode),
                        startedAtRange(startEpoch, endEpoch),
                        seekAfterAsc(cursorCourseName, cursorRunningId)

                )
                .orderBy(running.course.name.asc(), running.id.asc())
                .limit(DEFAULT_PAGE_SIZE + 1)
                .fetch();
    }

    private BooleanExpression seekAfterAsc(String cursorCourseName, Long cursorRunningId) {
        if (cursorRunningId == null || cursorCourseName == null) {
            return null;
        }
        return running.course.name.gt(cursorCourseName)
                .or(running.course.name.eq(cursorCourseName).and(running.id.gt(cursorRunningId)));
    }

    @Override
    public Optional<CourseRunStatisticsDto> findPublicRunStatisticsByCourseId(Long courseId) {
        return Optional.ofNullable(
                queryFactory
                        .select(Projections.constructor(CourseRunStatisticsDto.class,
                                running.runningRecord.duration.avg(),
                                running.runningRecord.averagePace.avg(),
                                running.runningRecord.cadence.avg(),
                                running.runningRecord.averagePace.min(),
                                running.member.countDistinct().intValue(),
                                running.count().intValue()
                        ))
                        .from(running)
                        .where(running.course.id.eq(courseId)
                                .and(running.isPublic.isTrue())
                                // todo: memberId 받은 다음에 본인 꺼는 isPublic = false여도 보여줘야 하나?
                        )
                        .fetchOne()
        );
    }

}
