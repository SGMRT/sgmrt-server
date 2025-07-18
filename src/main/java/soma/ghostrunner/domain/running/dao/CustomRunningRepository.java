package soma.ghostrunner.domain.running.dao;

import soma.ghostrunner.domain.course.dto.CourseRunStatisticsDto;
import soma.ghostrunner.domain.running.application.dto.response.GhostRunDetailInfo;
import soma.ghostrunner.domain.running.application.dto.response.MemberAndRunRecordInfo;
import soma.ghostrunner.domain.running.application.dto.response.RunInfo;
import soma.ghostrunner.domain.running.application.dto.response.SoloRunDetailInfo;
import soma.ghostrunner.domain.running.domain.RunningMode;

import java.util.List;
import java.util.Optional;

public interface CustomRunningRepository {

    Optional<SoloRunDetailInfo> findSoloRunInfoById(long id);

    Optional<GhostRunDetailInfo> findGhostRunInfoById(long id);

    Optional<MemberAndRunRecordInfo> findMemberAndRunRecordInfoById(long id);

    List<RunInfo> findRunInfosByCursorIds(
            RunningMode runningMode, Long cursorStartedAt, Long cursorRunningId, String memberUuid);

    List<RunInfo> findRunInfosFilteredByCoursesByCursorIds(
            RunningMode runningMode, String cursorCourseName, Long cursorRunningId, String memberUuid);

    List<RunInfo> findRunInfosForGalleryViewByCursorIds(
            RunningMode runningMode, Long cursorStartedAt, Long cursorRunningId, String memberUuid);

    Optional<CourseRunStatisticsDto> findPublicRunStatisticsByCourseId(Long courseId);

}
