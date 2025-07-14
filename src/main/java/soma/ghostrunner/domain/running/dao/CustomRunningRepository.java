package soma.ghostrunner.domain.running.dao;

import soma.ghostrunner.domain.running.application.dto.response.GhostRunDetailInfo;
import soma.ghostrunner.domain.running.application.dto.response.MemberAndRunRecordInfo;
import soma.ghostrunner.domain.running.application.dto.response.RunInfo;
import soma.ghostrunner.domain.running.application.dto.response.SoloRunDetailInfo;

import java.util.List;
import java.util.Optional;

public interface CustomRunningRepository {

    Optional<SoloRunDetailInfo> findSoloRunInfoById(long id);

    Optional<GhostRunDetailInfo> findGhostRunInfoById(long id);

    Optional<MemberAndRunRecordInfo> findMemberAndRunRecordInfoById(long id);

    List<RunInfo> findRunInfosByCursorIds(Long cursorStartedAt, Long cursorRunningId);

}
