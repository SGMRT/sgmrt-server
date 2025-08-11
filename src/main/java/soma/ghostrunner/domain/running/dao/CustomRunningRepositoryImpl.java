package soma.ghostrunner.domain.running.dao;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.course.dto.CourseRunStatisticsDto;
import soma.ghostrunner.domain.running.application.dto.response.*;
import soma.ghostrunner.domain.running.domain.QRunning;
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
    private static final int GALLERY_VIEW_PAGE_SIZE = 8;

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<SoloRunDetailInfo> findSoloRunInfoById(long runningId, String memberUuid) {

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
                                running.runningDataUrls.simplifiedTelemetrySavedUrl
                        ))
                        .from(running)
                        .join(running.course, course)
                        .join(running.member, member)
                        .where(running.id.eq(runningId).and(member.uuid.eq(memberUuid)))
                        .fetchOne());
    }

    @Override
    public Optional<GhostRunDetailInfo> findGhostRunInfoById(long runningId, String memberUuid) {

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
                                running.runningDataUrls.simplifiedTelemetrySavedUrl
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
    public List<RunInfo> findRunInfosByCursorIds(
            RunningMode runningMode, Long cursorStartedAt, Long cursorRunningId, String memberUuid) {
        return queryFactory
                .select(new QRunInfo(
                        running.id, running.runningName, running.startedAt,
                        new QRunRecordInfo(running.runningRecord.distance, running.runningRecord.duration,
                                running.runningRecord.averagePace, running.runningRecord.cadence),
                        new QCourseInfo(running.course.id, running.course.name, running.course.isPublic),
                        running.ghostRunningId
                ))
                .from(running)
                .join(running.course, course)
                .where(cursorCondition(cursorStartedAt, cursorRunningId))
                .where(running.member.uuid.eq(memberUuid), running.runningMode.eq(runningMode))
                .orderBy(running.startedAt.desc(), running.id.desc())
                .limit(DEFAULT_PAGE_SIZE)
                .fetch();
    }

    private BooleanExpression cursorCondition(Long cursorStartedAt, Long cursorRunningId) {
        if (cursorRunningId == null || cursorStartedAt == null) {
            return null;
        }
        return running.startedAt.lt(cursorStartedAt)
                .or(running.startedAt.eq(cursorStartedAt)
                        .and(running.id.lt(cursorRunningId)));
    }

    @Override
    public List<RunInfo> findRunInfosFilteredByCoursesByCursorIds(
            RunningMode runningMode, String cursorCourseName, Long cursorRunningId, String memberUuid) {
        return queryFactory
                .select(new QRunInfo(
                        running.id, running.runningName, running.startedAt,
                        new QRunRecordInfo(running.runningRecord.distance, running.runningRecord.duration,
                                running.runningRecord.averagePace, running.runningRecord.cadence),
                        new QCourseInfo(running.course.id, running.course.name),
                        running.ghostRunningId
                ))
                .from(running)
                .join(running.course, course)
                .where(cursorCondition(cursorCourseName, cursorRunningId))
                .where(running.member.uuid.eq(memberUuid), running.runningMode.eq(runningMode), running.course.isPublic.eq(true))
                .orderBy(running.course.name.asc(), running.id.desc())
                .limit(DEFAULT_PAGE_SIZE)
                .fetch();
    }

    private BooleanExpression cursorCondition(String cursorCourseName, Long cursorRunningId) {
        if (cursorRunningId == null || cursorCourseName == null) {
            return null;
        }
        return running.course.name.gt(cursorCourseName)
                .or(running.course.name.eq(cursorCourseName)
                        .and(running.id.lt(cursorRunningId)));
    }

    @Override
    public List<RunInfo> findRunInfosForGalleryViewByCursorIds(
            RunningMode runningMode, Long cursorStartedAt, Long cursorRunningId, String memberUuid) {
        return queryFactory
                .select(new QRunInfo(
                        running.id, running.runningName, running.startedAt,
                        new QRunRecordInfo(running.runningRecord.distance, running.runningRecord.duration,
                                running.runningRecord.averagePace, running.runningRecord.cadence),
                        new QCourseInfo(running.course.id, running.course.name,
                                running.course.isPublic, running.course.pathDataSavedUrl),
                        running.ghostRunningId
                ))
                .from(running)
                .join(running.course, course)
                .where(cursorCondition(cursorStartedAt, cursorRunningId))
                .where(running.member.uuid.eq(memberUuid), running.runningMode.eq(runningMode))
                .orderBy(running.startedAt.desc(), running.id.desc())
                .limit(GALLERY_VIEW_PAGE_SIZE)
                .fetch();
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
