package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.course.dto.CourseRunStatisticsDto;
import soma.ghostrunner.domain.course.dto.response.CourseGhostResponse;
import soma.ghostrunner.domain.course.enums.AvailableGhostSortField;
import soma.ghostrunner.domain.running.api.dto.RunningApiMapper;
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;
import soma.ghostrunner.domain.running.application.dto.response.*;
import soma.ghostrunner.domain.running.dao.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;
import soma.ghostrunner.global.error.ErrorCode;

import java.util.List;
import java.util.Optional;

// TODO : 서비스 단 쪼개기
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RunningQueryService {

    private final RunningRepository runningRepository;

    private final RunningApiMapper runningApiMapper;

    public SoloRunDetailInfo findSoloRunInfo(Long runningId, String memberUuid) {
        return findSoloRunInfoByRunningId(runningId, memberUuid);
    }

    private SoloRunDetailInfo findSoloRunInfoByRunningId(Long runningId, String memberUuid) {
        return runningRepository.findSoloRunInfoById(runningId, memberUuid)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, runningId));
    }

    public GhostRunDetailInfo findGhostRunInfo(Long myRunningId, Long ghostRunningId, String memberUuid) {
        GhostRunDetailInfo myGhostRunDetailInfo = findGhostRunInfoByRunningId(myRunningId, memberUuid);
        verifyGhostRunningId(ghostRunningId, myGhostRunDetailInfo);

        MemberAndRunRecordInfo ghostMemberAndRunRecordInfo = findGhostMemberAndRunInfoByRunningId(ghostRunningId);
        myGhostRunDetailInfo.setGhostRunInfo(ghostMemberAndRunRecordInfo);
        return myGhostRunDetailInfo;
    }

    private GhostRunDetailInfo findGhostRunInfoByRunningId(Long myRunningId, String memberUuid) {
        return runningRepository.findGhostRunInfoById(myRunningId, memberUuid)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, myRunningId));
    }

    private void verifyGhostRunningId(Long ghostRunningId, GhostRunDetailInfo myGhostRunDetailInfo) {
        if (myGhostRunDetailInfo.getGhostRunId() == null || !myGhostRunDetailInfo.getGhostRunId().equals(ghostRunningId)) {
            throw new InvalidRunningException(
                    ErrorCode.INVALID_REQUEST_VALUE, "고스트의 러닝 ID가 Null이거나 실제로 뛴 고스트러닝 ID가 아닌 경우");
        }
    }

    private MemberAndRunRecordInfo findGhostMemberAndRunInfoByRunningId(Long ghostRunningId) {
        return runningRepository.findMemberAndRunRecordInfoById(ghostRunningId)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, ghostRunningId));
    }

    public String findRunningTelemetries(Long runningId, String memberUuid) {
        return runningRepository.findInterpolatedTelemetryUrlByIdAndMemberUuid(runningId, memberUuid)
                .orElseThrow(() -> new AccessDeniedException("접근할 수 없는 러닝 데이터입니다."));
    }

    public Page<CourseGhostResponse> findPublicGhostRunsByCourseId(
        Long courseId, Pageable pageable) {
        validateSortProperty(pageable);
        Page<Running> ghostRuns = runningRepository.findByCourse_IdAndIsPublicTrue(courseId, pageable);
        return ghostRuns.map(runningApiMapper::toGhostResponse);
    }

    public Page<CourseGhostResponse> findTopRankingGhostsByCourseId(
            Long courseId, Integer count) {
        Sort defaultSort = Sort.by(Sort.Direction.ASC, "runningRecord.averagePace");
        Pageable topNPageable = PageRequest.of(0, count, defaultSort);
        return findPublicGhostRunsByCourseId(courseId, topNPageable);
    }

    public Optional<CourseRunStatisticsDto> findCourseRunStatistics(Long courseId) {
        return runningRepository.findPublicRunStatisticsByCourseId(courseId);
    }

    public Integer findPublicRankForCourse(Long courseId, Running running) {
        return 1 + runningRepository.countByCourseIdAndIsPublicTrueAndAveragePaceLessThan(
                        courseId, running.getRunningRecord().getAveragePace())
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, courseId));
    }

    public Running findBestPublicRunForCourse(Long courseId, String memberUuid) {
        return runningRepository.findBestPublicRunByCourseIdAndMemberId(courseId, memberUuid)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.COURSE_RUN_NOT_FOUND, courseId));
    }

    public Running findRunningByRunningId(Long id) {
        return runningRepository.findById(id)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, id));
    }

    public Running findFirstRunning(Long courseId) {
        return runningRepository.findFirstRunningByCourseId(courseId)
                .orElseThrow(() -> new RunningNotFoundException(
                        ErrorCode.ENTITY_NOT_FOUND, "코스 ID : " + courseId + "에 대한 러닝 데이터가 없습니다."));
    }

    private void validateSortProperty(Pageable pageable) {
        pageable.getSort().stream()
            .forEach(order -> {
                if(!AvailableGhostSortField.isValidField(order.getProperty())){
                    throw new IllegalArgumentException("잘못된 고스트 정렬 필드");
                };
            });
    }

    public List<RunInfo> findRunnings(String runningMode,
                                      Long cursorStartedAt, Long cursorRunningId,
                                      Long startEpoch, Long endEpoch, String memberUuid) {
        return runningRepository.findRunInfosByCursorIds(
                RunningMode.valueOf(runningMode),
                cursorStartedAt, cursorRunningId,
                startEpoch, endEpoch, memberUuid);
    }

    public List<RunInfo> findRunningsFilteredByCourse(String runningMode, String courseName, Long cursorRunningId, String memberUuid) {
        return runningRepository.findRunInfosFilteredByCoursesByCursorIds(
                RunningMode.valueOf(runningMode), courseName, cursorRunningId, memberUuid);
    }
  
}
