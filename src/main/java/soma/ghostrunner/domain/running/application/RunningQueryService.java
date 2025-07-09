package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.course.dto.response.CourseGhostResponse;
import soma.ghostrunner.domain.running.api.dto.RunningApiMapper;
import soma.ghostrunner.domain.running.application.dto.response.GhostRunDetailInfo;
import soma.ghostrunner.domain.running.application.dto.response.MemberAndRunRecordInfo;
import soma.ghostrunner.domain.running.application.dto.response.SoloRunDetailInfo;
import soma.ghostrunner.domain.running.dao.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;
import soma.ghostrunner.global.common.error.ErrorCode;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RunningQueryService {

    private final RunningTelemetryQueryService runningTelemetryQueryService;

    private final RunningRepository runningRepository;

    private final RunningApiMapper runningApiMapper;
  
    public SoloRunDetailInfo findSoloRunInfo(Long runningId) {
        SoloRunDetailInfo soloRunDetailInfo = findSoloRunInfoByRunningId(runningId);
        runningTelemetryQueryService.downloadTelemetries(runningId, soloRunDetailInfo);
        return soloRunDetailInfo;
    }

    public GhostRunDetailInfo findGhostRunInfo(Long myRunningId, Long ghostRunningId) {
        GhostRunDetailInfo myGhostRunDetailInfo = findGhostRunInfoByRunningId(myRunningId);
        verifyGhostRunningId(ghostRunningId, myGhostRunDetailInfo);

        MemberAndRunRecordInfo ghostMemberAndRunRecordInfo = findGhostMemberAndRunInfoByRunningId(ghostRunningId);
        myGhostRunDetailInfo.setGhostRunInfo(ghostMemberAndRunRecordInfo);

        runningTelemetryQueryService.downloadTelemetries(myRunningId, myGhostRunDetailInfo);
        return myGhostRunDetailInfo;
    }

    private void verifyGhostRunningId(Long ghostRunningId, GhostRunDetailInfo myGhostRunDetailInfo) {
        if (myGhostRunDetailInfo.getGhostRunId() == null || !myGhostRunDetailInfo.getGhostRunId().equals(ghostRunningId)) {
            throw new InvalidRunningException(ErrorCode.INVALID_REQUEST_VALUE, "고스트의 러닝 ID가 Null이거나 실제로 뛴 고스트러닝 ID가 아닌 경우");
        }
    }

    public Page<CourseGhostResponse> findPublicGhostRunsByCourseId(
        Long courseId, Pageable pageable) {
        Page<Running> ghostRuns = runningRepository.findByCourse_IdAndIsPublicTrue(courseId, pageable);
        return ghostRuns.map(runningApiMapper::toGhostResponse);
    }

    public Integer findRankingOfUserInCourse(Long courseId, Long memberId) {
        Double bestPace = runningRepository.findMinAveragePaceByCourseIdAndMemberIdAndIsPublicTrue(courseId, memberId)
            .orElseThrow(() -> new RunningNotFoundException(ErrorCode.COURSE_RUN_NOT_FOUND, courseId));

        return 1 + runningRepository.countByCourseIdAndIsPublicTrueAndAveragePaceLessThan(courseId, bestPace)
            .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, courseId));
    }

    public Running findRunningByRunningId(Long id) {
        return runningRepository.findById(id)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, id));
    }

    public Running findRunningByRunningId(Long runningId, Long memberId) {
        return runningRepository.findByIdAndMemberId(runningId, memberId)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, "러닝 ID : " + runningId + ", 멤버 ID : " + memberId + "에 해당하는 엔티티를 찾을 수 없습니다."));
    }

    private MemberAndRunRecordInfo findGhostMemberAndRunInfoByRunningId(Long ghostRunningId) {
        return runningRepository.findMemberAndRunRecordInfoById(ghostRunningId)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, ghostRunningId));
    }

    private GhostRunDetailInfo findGhostRunInfoByRunningId(Long myRunningId) {
        return runningRepository.findGhostRunInfoById(myRunningId)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, myRunningId));
    }

    private SoloRunDetailInfo findSoloRunInfoByRunningId(Long runningId) {
        return runningRepository.findSoloRunInfoById(runningId)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, runningId));
    }

}
