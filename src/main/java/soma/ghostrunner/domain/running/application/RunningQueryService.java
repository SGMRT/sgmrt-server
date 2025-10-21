package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.util.Pair;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.course.dto.CourseRunDto;
import soma.ghostrunner.domain.course.dto.CourseRunStatisticsDto;
import soma.ghostrunner.domain.course.dto.UserPaceStatsDto;
import soma.ghostrunner.domain.course.dto.response.CourseGhostResponse;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.course.enums.GhostSortType;
import soma.ghostrunner.domain.running.api.dto.response.RunMonthlyStatusResponse;
import soma.ghostrunner.domain.running.application.dto.response.*;
import soma.ghostrunner.domain.running.application.support.RunningApplicationMapper;
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

    private final RunningApplicationMapper mapper;

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

    public List<CourseGhostResponse> findTopRankingDistinctGhostsByCourseId(Long courseId, Integer count) {
        return runningRepository.findTopRankingRunsByCourseIdWithDistinctMember(courseId, count)
                .stream()
                .map(mapper::toGhostResponse)
                .toList();
    }

    /** 코스 ID 별로 상위 랭킹 :limit위까지의 러닝 기록을 리스트로 매핑하여 반환한다. (러너 별로 최대 하나의 기록만 포함된다) (Key = 코스 ID, Value = 랭킹 내의 러닝 기록 리스트)*/
    public Map<Long, List<CourseRunDto>> findTopRankingDistinctGhostsByCourseIds(
            List<Long> cachedMissedCourseIds, int limit) {
        Map<Long, List<CourseRunDto>> map = new HashMap<>();
        cachedMissedCourseIds.forEach(id -> map.put(id, new ArrayList<>()));
        runningRepository.findTopRankingRunsByCourseIdsWithDistinctMember(cachedMissedCourseIds, limit)
                .forEach(proj -> map.get(proj.courseId()).add(proj));
        return map;
    }

    public Page<CourseGhostResponse> findPublicGhostRunsByCourseId(
        Long courseId, Pageable pageable) {
        validateSortProperty(pageable);
        Page<Running> ghostRuns = runningRepository.findByCourse_IdAndIsPublicTrue(courseId, pageable);
        return ghostRuns.map(mapper::toGhostResponse);
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

    /** 코스 ID 별로 사용자의 최고기록을 매핑하여 반환한다. (Key: 코스 ID, Value: 최고 러닝 (nullable)) */
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
            result.put(courseId, bestRunsByCourseId.get(courseId)); // 최고 기록이 없으면 null이 들어감
        }
        return result;
    }

    /** 사용자가 couseId에 해당하는 코스를 달렸는지 여부를 매핑하여 반환한다. (Key: 코스 ID, Value: 러닝 여부) */
    public Map<Long, Boolean> checkRunningHistoryForCourses(List<Long> courseIds, String memberUuid) {
        // 사용자가 달린 코스 ID 리스트를 조회하여 courseId와 비교한다
        List<Long> ranCourseIds = runningRepository.findRanCourseIdsByMemberIdAndCourseIds(memberUuid, courseIds);
        Set<Long> ranCourseIdSet = new HashSet<>(ranCourseIds);
        return courseIds.stream()
                .collect(Collectors.toMap(
                        courseId -> courseId,
                        ranCourseIdSet::contains
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

    public List<RunInfo> findRunnings(Long courseId, String memberUuid) {
        Member member = findMember(memberUuid);
        List<Running> runnings = runningRepository.findRunningsByCourseIdAndMemberId(courseId, member.getId());
        return mapper.toResponse(runnings);
    }

    public long findPublicRunnersCount(Long courseId) {
        return runningRepository.countPublicRunnersInCourse(courseId);
    }

    public List<RunMonthlyStatusResponse> findMonthlyDayRunStatus(Integer year, Integer month, String memberUuid) {
        Member member = findMember(memberUuid);
        List<DayRunInfo> dayRunInfos = runningRepository.findDayRunInfosFilteredByDate(year, month, member.getId());
        return mapper.toDayRunStatusResponses(dayRunInfos);
    }
  
    /** 코스 ID 별로 러너의 수를 매핑하여 반환한다. (Key = 코스 ID, Value = 러너 수) */
    public Map<Long, Long> findPublicRunnersCountByCourseIds(List<Long> courseIds) {
        Map<Long, Long> ret = courseIds.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        id -> 0L
                ));
        List<Pair<Long, Long>> counts = runningRepository.findPublicRunnerCountsByCourseIds(courseIds);
        for (var count: counts) {
            ret.put(count.getFirst(), count.getSecond());
        }
        return ret;
    }

}
