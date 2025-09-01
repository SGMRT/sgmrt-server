package soma.ghostrunner.domain.running.infra;

import soma.ghostrunner.domain.course.dto.CourseRunStatisticsDto;
import soma.ghostrunner.domain.course.dto.UserPaceStatsDto;
import soma.ghostrunner.domain.running.application.dto.response.GhostRunDetailInfo;
import soma.ghostrunner.domain.running.application.dto.response.MemberAndRunRecordInfo;
import soma.ghostrunner.domain.running.application.dto.response.RunInfo;
import soma.ghostrunner.domain.running.application.dto.response.SoloRunDetailInfo;
import soma.ghostrunner.domain.running.domain.RunningMode;

import java.util.List;
import java.util.Optional;

public interface RunningQueryRepository {

    Optional<SoloRunDetailInfo> findSoloRunInfoById(long id, String memberUuid);

    Optional<GhostRunDetailInfo> findGhostRunInfoById(long id, String memberUuid);

    Optional<MemberAndRunRecordInfo> findMemberAndRunRecordInfoById(long id);

    List<RunInfo> findRunInfosFilteredByDate(
            RunningMode runningMode,
            Long cursorStartedAt, Long cursorRunningId,
            Long startEpoch, Long endEpoch,
            Long memberId
    );

    List<RunInfo> findRunInfosFilteredByCourses(
            RunningMode runningMode,
            String cursorCourseName, Long cursorRunningId,
            Long startEpoch, Long endEpoch,
            Long memberId
    );

    Optional<CourseRunStatisticsDto> findPublicRunStatisticsByCourseId(Long courseId);

    Optional<UserPaceStatsDto> findUserRunStatisticsByCourseId(Long courseId, String memberUuid);
}
