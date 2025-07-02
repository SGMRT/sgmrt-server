package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.clients.aws.TelemetryClient;
import soma.ghostrunner.domain.running.api.dto.RunningApiMapper;
import soma.ghostrunner.domain.running.application.dto.response.GhostRunDetailInfo;
import soma.ghostrunner.domain.running.application.dto.response.MemberAndRunRecordInfo;
import soma.ghostrunner.domain.running.application.dto.response.RunDetailInfo;
import soma.ghostrunner.domain.running.application.dto.response.SoloRunDetailInfo;
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;
import soma.ghostrunner.domain.running.dao.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.exception.InvalidGhostRunningException;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;
import soma.ghostrunner.global.common.error.ErrorCode;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RunningQueryService {

    private final RunningRepository runningRepository;
    private final TelemetryClient telemetryClient;
    private final RunningApiMapper runningApiMapper;

    public List<TelemetryDto> findTelemetriesById(Long runningId) {
        String s3Url = findTelemetryUrl(runningId);
        return telemetryClient.downloadTelemetryFromUrl(s3Url);
    }

    public Running findByRunningAndMemberId(Long runningId, Long memberId) {
        return runningRepository.findByIdAndMemberId(runningId, memberId)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.RUNNING_NOT_FOUND, "러닝 ID : " + runningId + ", 멤버 ID : " + memberId + "에 해당하는 엔티티를 찾을 수 없습니다."));
    }
  
    public SoloRunDetailInfo findSoloRunInfoById(Long runningId) {
        SoloRunDetailInfo soloRunDetailInfo = findSoloRunInfo(runningId);
        downloadTelemetries(runningId, soloRunDetailInfo);
        return soloRunDetailInfo;
    }

    public GhostRunDetailInfo findGhostRunInfoById(Long myRunningId, Long ghostRunningId) {
        // 나의 러닝 정보
        GhostRunDetailInfo myGhostModeRunDetailInfo = findGhostRunDetailInfo(myRunningId);
        verifyGhostRunningId(ghostRunningId, myGhostModeRunDetailInfo);

        // 고스트의 러닝 정보
        MemberAndRunRecordInfo ghostMemberAndRunRecordInfo = findGhostMemberAndRunInfo(ghostRunningId);
        myGhostModeRunDetailInfo.setGhostRunInfo(ghostMemberAndRunRecordInfo);

        // 시계열
        downloadTelemetries(myRunningId, myGhostModeRunDetailInfo);
        return myGhostModeRunDetailInfo;
    }

    public Running findRunningById(Long id) {
        return runningRepository.findById(id)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.RUNNING_NOT_FOUND, id));
    }

    @Transactional(readOnly = true)
    public Page<Object> findPublicGhostRunsByCourseId(
        Long courseId, Pageable pageable) {
        Page<Running> ghostRuns = runningRepository.findByCourse_IdAndIsPublicTrue(courseId, pageable);
        return ghostRuns.map(runningApiMapper::toGhostResponse);
    }

    private void verifyGhostRunningId(Long ghostRunningId, GhostRunDetailInfo myGhostModeRunInfo) {
        if (myGhostModeRunInfo.getGhostRunId() == null || !myGhostModeRunInfo.getGhostRunId().equals(ghostRunningId)) {
            throw new InvalidGhostRunningException(ErrorCode.INVALID_GHOST_RUNNING_ID);
        }
    }

    private String findTelemetryUrl(Long runningId) {
        return runningRepository.findTelemetryUrlById(runningId)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.RUNNING_NOT_FOUND, runningId));
    }

    private void downloadTelemetries(Long runningId, RunDetailInfo runDetailInfo) {
        try {
            List<TelemetryDto> telemetries = telemetryClient.downloadTelemetryFromUrl(runDetailInfo.getTelemetryUrl());
            runDetailInfo.setTelemetries(telemetries);
        } catch (Exception e) {
            log.error("runningId {}의 요청에 대해 S3에서 다운로드를 실패했습니다.", runningId, e);
            runDetailInfo.setTelemetries(Collections.emptyList());
        }
    }

    private MemberAndRunRecordInfo findGhostMemberAndRunInfo(Long ghostRunningId) {
        return runningRepository.findMemberAndRunRecordInfoById(ghostRunningId)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.RUNNING_NOT_FOUND, ghostRunningId));
    }

    private GhostRunDetailInfo findGhostRunDetailInfo(Long myRunningId) {
        return runningRepository.findGhostRunInfoById(myRunningId)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.RUNNING_NOT_FOUND, myRunningId));
    }

    private SoloRunDetailInfo findSoloRunInfo(Long runningId) {
        return runningRepository.findSoloRunInfoById(runningId)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.RUNNING_NOT_FOUND, runningId));
    }
}
