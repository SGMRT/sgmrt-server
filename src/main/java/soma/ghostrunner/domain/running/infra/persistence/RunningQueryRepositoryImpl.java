package soma.ghostrunner.domain.running.infra.persistence;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.NumberTemplate;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.course.dto.CourseRunStatisticsDto;
import soma.ghostrunner.domain.course.dto.QUserPaceStatsDto;
import soma.ghostrunner.domain.course.dto.UserPaceStatsDto;
import soma.ghostrunner.domain.running.application.dto.response.*;
import soma.ghostrunner.domain.running.domain.QRunning;
import soma.ghostrunner.domain.running.domain.RunningMode;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static soma.ghostrunner.domain.course.domain.QCourse.course;
import static soma.ghostrunner.domain.member.domain.QMember.member;
import static soma.ghostrunner.domain.running.domain.QRunning.running;

@Repository
@RequiredArgsConstructor
public class RunningQueryRepositoryImpl implements RunningQueryRepository {

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
                                        course.isPublic,
                                        course.courseProfile.distance),
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
                                        course.name,
                                        course.isPublic,
                                        course.courseProfile.distance
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

    @Override
    public List<RunInfo> findRunInfosFilteredByDate(
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
                                running.course.isPublic,
                                course.courseProfile.distance
                        ),
                        running.ghostRunningId,
                        running.runningDataUrls.screenShotUrl
                ))
                .from(running)
                .leftJoin(running.course, course)
                .where(
                        running.member.id.eq(memberId),
                        startedAtRange(startEpoch, endEpoch),
                        seekAfterAsc(cursorStartedAt, cursorRunningId)
                )
                .orderBy(running.startedAt.desc(), running.id.desc())
                .limit(DEFAULT_PAGE_SIZE)
                .fetch();
    }

    private BooleanExpression startedAtRange(Long start, Long end) {
        return running.startedAt.between(start, end);
    }

    private BooleanExpression seekAfterAsc(Long cursorStartedAt, Long cursorId) {
        if (cursorStartedAt == null || cursorId == null) return null; // 첫 페이지
        return running.startedAt.lt(cursorStartedAt)
                .or(running.startedAt.eq(cursorStartedAt).and(running.id.lt(cursorId)));
    }

    @Override
    public List<RunInfo> findRunInfosFilteredByCourses(
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
                                running.course.isPublic,
                                course.courseProfile.distance
                        ),
                        running.ghostRunningId,
                        running.runningDataUrls.screenShotUrl
                ))
                .from(running)
                .leftJoin(running.course, course)
                .where(
                        running.member.id.eq(memberId),
                        startedAtRange(startEpoch, endEpoch),
                        seekAfterAsc(cursorCourseName, cursorRunningId)
                )
                .orderBy(running.course.name.asc(), running.id.desc())
                .limit(DEFAULT_PAGE_SIZE)
                .fetch();
    }

    private BooleanExpression seekAfterAsc(String cursorCourseName, Long cursorRunningId) {
        if (cursorRunningId == null || cursorCourseName == null) {
            return null;
        }
        return running.course.name.gt(cursorCourseName)
                .or(running.course.name.eq(cursorCourseName).and(running.id.lt(cursorRunningId)));
    }

    @Override
    public Optional<CourseRunStatisticsDto> findPublicRunStatisticsByCourseId(Long courseId) {
        return Optional.ofNullable(
                queryFactory
                        .select(Projections.constructor(CourseRunStatisticsDto.class,
                                running.runningRecord.duration.avg(),
                                running.runningRecord.averagePace.avg(),
                                running.runningRecord.cadence.avg(),
                                running.runningRecord.burnedCalories.avg(),
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

    @Override
    public Optional<UserPaceStatsDto> findUserRunStatisticsByCourseId(Long courseId, String memberUuid) {
        return Optional.ofNullable(
           queryFactory
                    .select(new QUserPaceStatsDto(
                            running.member.uuid,
                            running.runningRecord.averagePace.min(),
                            running.runningRecord.averagePace.avg(),
                            running.runningRecord.averagePace.max()
                    ))
                    .from(running)
                    .join(running.member, member)
                    .where(running.member.uuid.eq(memberUuid)
                            .and(running.course.id.eq(courseId)))
                   .groupBy(running.member.uuid)
                   .fetchOne()
        );
    }

    @Override
    public List<DayRunInfo> findDayRunInfosFilteredByDate(Integer year, Integer month, Long memberId) {

        String tz = "Asia/Seoul";
        ZoneId zone = ZoneId.of(tz);

        var start = ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, zone);
        var end   = start.plusMonths(1);
        long startMs = start.toInstant().toEpochMilli();
        long endMs   = end.toInstant().toEpochMilli();

        QRunning r = QRunning.running;

        NumberExpression<Integer> day =
                Expressions.numberTemplate(Integer.class,
                        "floor(({0} - {1})/86400000) + 1", r.startedAt, startMs);

        return queryFactory
                .select(new QDayRunInfo(
                        Expressions.constant(year),      // 입력값 그대로
                        Expressions.constant(month),     // 입력값 그대로
                        day,
                        r.id.count().intValue()
                ))
                .from(r)
                .where(
                        r.member.id.eq(memberId),
                        r.startedAt.goe(startMs),
                        r.startedAt.lt(endMs)
                )
                .groupBy(day)
                .orderBy(day.asc())
                .fetch();
    }

}
