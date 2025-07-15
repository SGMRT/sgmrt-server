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

    List<RunInfo> findRunInfosByCursorIds(RunningMode runningMode, Long cursorStartedAt, Long cursorRunningId, Long memberId);

    List<RunInfo> findRunInfosFilteredByCoursesByCursorIds(RunningMode runningMode, String cursorCourseName,
                                                           Long cursorRunningId, Long memberId);

    List<RunInfo> findRunInfosForGalleryViewByCursorIds(RunningMode runningMode, Long cursorStartedAt,
                                                        Long cursorRunningId, Long memberId);

    Optional<CourseRunStatisticsDto> findPublicRunStatisticsByCourseId(Long courseId);

}
