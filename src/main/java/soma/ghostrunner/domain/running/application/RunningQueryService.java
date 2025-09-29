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
import soma.ghostrunner.domain.course.dto.UserPaceStatsDto;
import soma.ghostrunner.domain.course.dto.response.CourseGhostResponse;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.course.enums.GhostSortType;
import soma.ghostrunner.domain.running.api.support.RunningApiMapper;
import soma.ghostrunner.domain.running.application.dto.response.*;
import soma.ghostrunner.domain.running.application.support.RunningInfoFilter;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;
import soma.ghostrunner.global.error.ErrorCode;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RunningQueryService {

    private final RunningRepository runningRepository;

    private final RunningApiMapper runningApiMapper;

    private final MemberService memberService;

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

    public Page<CourseGhostResponse> findTopRankingGhostsByCourseId(
            Long courseId, Integer count) {
        Sort defaultSort = Sort.by(Sort.Direction.ASC, "runningRecord.averagePace");
        Pageable topNPageable = PageRequest.of(0, count, defaultSort);
        return findPublicGhostRunsByCourseId(courseId, topNPageable);
    }

    public Page<CourseGhostResponse> findPublicGhostRunsByCourseId(
        Long courseId, Pageable pageable) {
        validateSortProperty(pageable);
        Page<Running> ghostRuns = runningRepository.findByCourse_IdAndIsPublicTrue(courseId, pageable);
        return ghostRuns.map(runningApiMapper::toGhostResponse);
    }

    public Page<CourseGhostResponse> findTopPercentageGhostsByCourseId(
            Long courseId, Double percentage) {
        int percentageToCount = (int) Math.ceil(findRunningsCountInCourse(courseId) * percentage) + 1;
        Sort defaultSort = Sort.by(Sort.Direction.ASC, "runningRecord.averagePace");
        Pageable topNPageable = PageRequest.of(0, percentageToCount, defaultSort);
        return findPublicGhostRunsByCourseId(courseId, topNPageable);
    }

    private long findRunningsCountInCourse(Long courseId) {
        return runningRepository.countTotalRunningsCount(courseId);
    }

    public Optional<CourseRunStatisticsDto> findCourseRunStatistics(Long courseId) {
        return runningRepository.findPublicRunStatisticsByCourseId(courseId);
    }

    public Optional<UserPaceStatsDto> findUserPaceStatistics(Long courseId, String memberUuid) {
        return runningRepository.findUserRunStatisticsByCourseId(courseId, memberUuid);
    }

    public Integer findPublicRankForCourse(Long courseId, Running running) {
        return 1 + runningRepository.countByCourseIdAndIsPublicTrueAndAveragePaceLessThan(
                        courseId, running.getRunningRecord().getAveragePace())
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, courseId));
    }

    public Optional<Running> findBestPublicRunForCourse(Long courseId, String memberUuid) {
        return runningRepository.findBestPublicRunByCourseIdAndMemberId(courseId, memberUuid);
    }

    public Running findRunningByRunningId(Long id) {
        return runningRepository.findById(id)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, id));
    }

    public Optional<Running> findFirstRunning(Long courseId) {
        return runningRepository.findFirstRunningByCourseId(courseId);
    }

    private void validateSortProperty(Pageable pageable) {
        pageable.getSort().stream()
            .forEach(order -> {
                if(!GhostSortType.isValidField(order.getProperty())){
                    throw new IllegalArgumentException("잘못된 고스트 정렬 필드");
                };
            });
    }

    /** memberId 사용자가 courseIds에 해당하는 코스에서의 최고기록을 반환한다. (기록이 없으면 null) */
    public Map<Long, Running> findBestRunningRecordsForCourses(List<Long> courseIds, String memberUuid) {
        // IN 절로 한 번에 조회한 후 courseId 별로 맵핑
        Map<Long, Running> bestRunsByCourseId = runningRepository.findBestRunningRecordsByMemberIdAndCourseIds(memberUuid, courseIds)
                .stream()
                .collect(Collectors.toMap(
                        run -> run.getCourse().getId(),
                        Function.identity()
                ));

        Map<Long, Running> result = new HashMap<>();
        for (Long courseId : courseIds) {
            result.put(courseId, bestRunsByCourseId.get(courseId));
        }
        return result;
    }

    public Map<Long, Boolean> checkRunningHistoryForCourses(List<Long> courseIds, String memberUuid) {
        // 1. DB에서 한 번의 쿼리로 사용자가 뛴 코스 ID 목록을 가져옵니다.
        List<Long> ranCourseIds = runningRepository.findRanCourseIdsByMemberIdAndCourseIds(memberUuid, courseIds);

        // 2. 결과를 가공하여 반환합니다.
        // Set으로 변환하여 조회 성능을 높입니다.
        Set<Long> ranCourseIdSet = new HashSet<>(ranCourseIds);

        // courseIds 목록을 순회하며, 각 코스 ID에 대한 기록 존재 여부를 Map에 담습니다.
        return courseIds.stream()
                .collect(Collectors.toMap(
                        courseId -> courseId, // Key: 코스 ID
                        ranCourseIdSet::contains // Value: 기록 존재 여부 (true/false)
                ));
    }

    public List<RunInfo> findRunnings(String filteredBy,
                                      Long startEpoch, Long endEpoch,
                                      Long cursorStartedAt,
                                      String cursorCourseName,
                                      Long cursorRunningId,String memberUuid) {
        Member member = findMember(memberUuid);
        if (filteredBy.equals(RunningInfoFilter.DATE.name())) {
            return runningRepository.findRunInfosFilteredByDate(
                    cursorStartedAt, cursorRunningId,
                    startEpoch, endEpoch, member.getId());
        } else if (filteredBy.equals(RunningInfoFilter.COURSE.name())) {
            return runningRepository.findRunInfosFilteredByCourses(
                    cursorCourseName, cursorRunningId,
                    startEpoch, endEpoch, member.getId());
        }
        throw new IllegalArgumentException("올바르지 않은 필터 형식이 요청됐습니다.");
    }

    private Member findMember(String memberUuid) {
        return memberService.findMemberByUuid(memberUuid);
    }
  
}
