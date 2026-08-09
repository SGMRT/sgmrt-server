package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.util.Pair;
import org.springframework.security.access.AccessDeniedException;
import soma.ghostrunner.domain.course.dto.CourseRunDto;
import soma.ghostrunner.domain.course.dto.CourseRunStatisticsDto;
import soma.ghostrunner.domain.course.dto.UserPaceStatsDto;
import soma.ghostrunner.domain.running.application.dto.response.*;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;
import soma.ghostrunner.global.error.ErrorCode;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 러닝 도메인의 <b>조회 전용</b> Reader.
 *
 * <p><b>이 클래스가 하는 일은 두 가지뿐이다</b> — {@link RunningRepository} 호출과, 그 결과를
 * "없으면 예외 / 키로 정규화된 뷰"로 바꾸는 것. 그 이상의 조율(다른 도메인 조회, 응답 DTO 매핑,
 * 여러 조회의 조합, 요청 파라미터 검증)은 {@link RunningQueryService}나 호출하는 Facade의 몫이다.
 * 그래야 다른 도메인(CourseFacade·PushEventListener·PacemakerValidator)이 이 Reader를 직접 써도
 * MemberService나 매퍼가 딸려 들어오지 않는다.
 *
 * <p><b>{@code @Transactional(readOnly = true)}는 여기에 둔다.</b> 조회 트랜잭션 경계는 Reader가 갖고,
 * 상위 Service는 원칙적으로 트랜잭션을 열지 않는다. 쓰기 트랜잭션 안에서 호출되면(예:
 * {@code RunningWriter#saveRun}의 재조회) 호출자 트랜잭션에 그대로 참여한다.
 *
 * <p>쓰기는 {@link RunningWriter}가 담당한다 — 이 클래스에는 부수효과가 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RunningReader {

    private final RunningRepository runningRepository;

    public SoloRunDetailInfo findSoloRunInfo(Long runningId, String memberUuid) {
        return runningRepository.findSoloRunInfoById(runningId, memberUuid)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, runningId));
    }

    public GhostRunDetailInfo findGhostRunInfo(Long runningId, String memberUuid) {
        return runningRepository.findGhostRunInfoById(runningId, memberUuid)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, runningId));
    }

    public MemberAndRunRecordInfo findMemberAndRunRecordInfo(Long runningId) {
        return runningRepository.findMemberAndRunRecordInfoById(runningId)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, runningId));
    }

    /** 조회되지 않으면 남의 러닝이거나 존재하지 않는 러닝이므로 접근 거부로 응답한다. */
    public String findInterpolatedTelemetryUrl(Long runningId, String memberUuid) {
        return runningRepository.findInterpolatedTelemetryUrlByIdAndMemberUuid(runningId, memberUuid)
                .orElseThrow(() -> new AccessDeniedException("접근할 수 없는 러닝 데이터입니다."));
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

    /** 코스의 공개 러닝(고스트)을 페이징 조회한다. 러너(member)는 fetch join으로 함께 적재된다. */
    public Page<Running> findPublicGhostRuns(Long courseId, Pageable pageable) {
        return runningRepository.findByCourse_IdAndIsPublicTrue(courseId, pageable);
    }

    public long countRunningsInCourse(Long courseId) {
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

    /** 코스 ID 별로 사용자의 최고기록을 매핑하여 반환한다. (Key: 코스 ID, Value: 최고 러닝 (nullable)) */
    public Map<Long, Running> findBestRunningRecordsForCourses(List<Long> courseIds, String memberUuid) {

        Comparator<Running> runningComparator = Comparator.comparingLong(Running::getId);

        // IN 절로 한 번에 조회한 후 courseId 별로 맵핑
        Map<Long, Running> bestRunsByCourseId = runningRepository.findBestRunningRecordsByMemberIdAndCourseIds(memberUuid, courseIds)
                .stream()
                .collect(Collectors.toMap(
                        r -> r.getCourse().getId(),
                        Function.identity(),
                        (a, b) -> runningComparator.compare(a, b) <= 0 ? a : b)
                );

        Map<Long, Running> result = new HashMap<>();
        for (Long courseId : courseIds) {
            result.put(courseId, bestRunsByCourseId.get(courseId)); // 최고 기록이 없으면 null이 들어감
        }
        return result;
    }

    public List<RunInfo> findRunInfosFilteredByDate(
            Long cursorStartedAt, Long cursorRunningId, Long startEpoch, Long endEpoch, Long memberId) {
        return runningRepository.findRunInfosFilteredByDate(
                cursorStartedAt, cursorRunningId, startEpoch, endEpoch, memberId);
    }

    public List<RunInfo> findRunInfosFilteredByCourses(
            String cursorCourseName, Long cursorRunningId, Long startEpoch, Long endEpoch, Long memberId) {
        return runningRepository.findRunInfosFilteredByCourses(
                cursorCourseName, cursorRunningId, startEpoch, endEpoch, memberId);
    }

    public List<Running> findRunningsByCourseAndMember(Long courseId, Long memberId) {
        return runningRepository.findRunningsByCourseIdAndMemberId(courseId, memberId);
    }

    public List<DayRunInfo> findDayRunInfos(Integer year, Integer month, Long memberId) {
        return runningRepository.findDayRunInfosFilteredByDate(year, month, memberId);
    }

    public long findPublicRunnersCount(Long courseId) {
        return runningRepository.countPublicRunnersInCourse(courseId);
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

    /** 회원의 러닝 중 :runStartedAt 이전의 가장 좋은 기록을 가지고 온다. */
    public Optional<Running> findMemberBestRunBefore(Long courseId, String memberUuid, Long runStartedAt) {
        return runningRepository.findBestRunByCourseIdAndMemberUuidBefore(courseId, memberUuid, runStartedAt);
    }

}
