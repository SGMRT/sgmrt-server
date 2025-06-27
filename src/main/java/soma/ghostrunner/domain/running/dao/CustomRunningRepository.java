package soma.ghostrunner.domain.running.dao;

import soma.ghostrunner.domain.running.application.dto.response.SoloRunInfo;

import java.util.Optional;

public interface CustomRunningRepository {

    Optional<SoloRunInfo> findSoloRunInfoById(long id);
}
