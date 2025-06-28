package soma.ghostrunner.domain.running.dao;

import soma.ghostrunner.domain.running.application.dto.response.GhostRunInfo;
import soma.ghostrunner.domain.running.application.dto.response.MemberAndRunRecordInfo;
import soma.ghostrunner.domain.running.application.dto.response.SoloRunInfo;

import java.util.Optional;

public interface CustomRunningRepository {

    Optional<SoloRunInfo> findSoloRunInfoById(long id);

    Optional<GhostRunInfo> findGhostRunInfoById(long id);

    Optional<MemberAndRunRecordInfo> findMemberAndRunRecordInfoById(long id);

}
