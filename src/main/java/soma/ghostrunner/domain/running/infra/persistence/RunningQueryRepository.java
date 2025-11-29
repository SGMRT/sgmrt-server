package soma.ghostrunner.domain.running.infra.persistence;

import soma.ghostrunner.domain.course.dto.CourseRunStatisticsDto;
import soma.ghostrunner.domain.course.dto.UserPaceStatsDto;
import soma.ghostrunner.domain.running.application.dto.response.*;
import soma.ghostrunner.domain.running.domain.RunningMode;

import java.util.List;
import java.util.Optional;

public interface RunningQueryRepository {

    Optional<SoloRunDetailInfo> findSoloRunInfoById(long id, String memberUuid);

    Optional<GhostRunDetailInfo> findGhostRunInfoById(long id, String memberUuid);

    Optional<MemberAndRunRecordInfo> findMemberAndRunRecordInfoById(long id);

    List<RunInfo> findRunInfosFilteredByDate(
            Long cursorStartedAt, Long cursorRunningId,
            Long startEpoch, Long endEpoch,
            Long memberId
    );

    List<RunInfo> findRunInfosFilteredByCourses(
            String cursorCourseName, Long cursorRunningId,
            Long startEpoch, Long endEpoch,
            Long memberId
    );

    Optional<CourseRunStatisticsDto> findPublicRunStatisticsByCourseId(Long courseId);

    Optional<UserPaceStatsDto> findUserRunStatisticsByCourseId(Long courseId, String memberUuid);

    List<DayRunInfo> findDayRunInfosFilteredByDate(Integer year, Integer month, Long memberId);

}
