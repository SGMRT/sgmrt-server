package soma.ghostrunner.domain.running.dao;

import soma.ghostrunner.domain.running.application.dto.response.GhostRunDetailInfo;
import soma.ghostrunner.domain.running.application.dto.response.MemberAndRunRecordInfo;
import soma.ghostrunner.domain.running.application.dto.response.SoloRunDetailInfo;

import java.util.Optional;

public interface CustomRunningRepository {

    Optional<SoloRunDetailInfo> findSoloRunInfoById(long id);

    Optional<GhostRunDetailInfo> findGhostRunInfoById(long id);

    Optional<MemberAndRunRecordInfo> findMemberAndRunRecordInfoById(long id);

    Optional<CourseRunStatisticsDto> findPublicRunStatisticsByCourseId(Long courseId);
}
