package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.clients.aws.TelemetryClient;
import soma.ghostrunner.domain.running.application.dto.response.SoloRunInfo;
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;
import soma.ghostrunner.domain.running.dao.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;
import soma.ghostrunner.global.common.error.ErrorCode;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RunningQueryService {

    private final RunningRepository runningRepository;
    private final TelemetryClient telemetryClient;

    public List<TelemetryDto> findTelemetriesById(Long runningId) {
        Running running = findRunningById(runningId);
        return telemetryClient.downloadTelemetryFromUrl(running.getTelemetryUrl());
    }

    public Running findRunningById(Long id) {
        return runningRepository.findById(id)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.RUNNING_NOT_FOUND, id));
    }

    public Running findByRunningIdAndMemberId(Long runningId, Long memberId) {
        return runningRepository.findByRunningIdAndMemberId(runningId, memberId)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.RUNNING_NOT_FOUND, "러닝 ID : " + runningId + ", 멤버 ID : " + memberId + "에 해당하는 엔티티를 찾을 수 없습니다."));
    }

    public SoloRunInfo findSoloRunInfoById(Long runningId) {
        // 러닝 조회
        SoloRunInfo soloRunInfo = findSoloRunInfo(runningId);
        // 시계열 조회
        try {
            List<TelemetryDto> telemetries = telemetryClient.downloadTelemetryFromUrl(soloRunInfo.getTelemetryUrl());
            soloRunInfo.setTelemetries(telemetries);
        } catch (Exception e) {
            log.error("S3에서 다운로드를 실패했습니다.");
        }
        return soloRunInfo;
    }

    private SoloRunInfo findSoloRunInfo(Long runningId) {
        return runningRepository.findSoloRunInfoById(runningId)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.RUNNING_NOT_FOUND, runningId));
    }
}
