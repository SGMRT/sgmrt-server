package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.api.dto.response.RunMonthlyStatusResponse;
import soma.ghostrunner.domain.running.application.dto.response.*;
import soma.ghostrunner.domain.running.application.support.RunningApplicationMapper;
import soma.ghostrunner.domain.running.application.support.RunningInfoFilter;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;
import soma.ghostrunner.global.error.ErrorCode;

import java.util.List;

/**
 * 러닝 도메인의 <b>조회 유즈케이스</b>를 조율하는 애플리케이션 서비스. {@code RunningApi}의 조회 진입점이다.
 *
 * <p>{@link RunningCommandService}(쓰기)와 대칭을 이룬다 — Api는 Service만 바라보고,
 * 리포지토리를 보는 것은 {@link RunningReader}/{@link RunningWriter}뿐이다.
 * 이 클래스가 갖는 것은 Reader가 가지면 안 되는 것들이다: 다른 도메인 조회({@link MemberService}),
 * 응답 DTO 매핑, 여러 조회의 조합과 그 결과 검증.
 *
 * <p><b>트랜잭션을 열지 않는다.</b> 조회 트랜잭션 경계는 {@link RunningReader}에 있다.
 * 여기의 어느 메서드도 "여러 조회가 한 스냅샷이어야 한다"를 요구하지 않기 때문이다 —
 * {@code findGhostRunInfo}의 두 조회는 첫 조회 결과로 검증을 끝낸 뒤 두 번째를 던지므로 순차 실행으로 충분하고,
 * 나머지는 회원 조회 + 러닝 조회의 단순 연결이다. 조합된 조회가 원자적 스냅샷을 요구하게 되는 순간
 * <b>그 메서드에만</b> {@code @Transactional(readOnly = true)}를 붙인다 (클래스 단위로 걸지 말 것).
 *
 * <p>엔티티를 응답으로 매핑하는 경로가 Reader의 트랜잭션 밖에서 도는 것도 같은 이유로 안전하다 —
 * {@link RunInfo}는 임베디드 필드만 읽고, 지연 로딩 연관을 건드리는 매핑은 이 클래스에 없다.
 * ({@code open-in-view: false}이므로 새 매핑을 추가할 때는 이 전제를 반드시 다시 확인할 것.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RunningQueryService {

    private final RunningReader runningReader;

    private final MemberService memberService;

    private final RunningApplicationMapper mapper;

    public SoloRunDetailInfo findSoloRunInfo(Long runningId, String memberUuid) {
        return runningReader.findSoloRunInfo(runningId, memberUuid);
    }

    public GhostRunDetailInfo findGhostRunInfo(Long myRunningId, Long ghostRunningId, String memberUuid) {
        GhostRunDetailInfo myGhostRunDetailInfo = runningReader.findGhostRunInfo(myRunningId, memberUuid);
        verifyGhostRunningId(ghostRunningId, myGhostRunDetailInfo);
        myGhostRunDetailInfo.setGhostRunInfo(runningReader.findMemberAndRunRecordInfo(ghostRunningId));
        return myGhostRunDetailInfo;
    }

    private void verifyGhostRunningId(Long ghostRunningId, GhostRunDetailInfo myGhostRunDetailInfo) {
        if (myGhostRunDetailInfo.getGhostRunId() == null || !myGhostRunDetailInfo.getGhostRunId().equals(ghostRunningId)) {
            throw new InvalidRunningException(
                    ErrorCode.INVALID_REQUEST_VALUE, "고스트의 러닝 ID가 Null이거나 실제로 뛴 고스트러닝 ID가 아닌 경우");
        }
    }

    public String findRunningTelemetries(Long runningId, String memberUuid) {
        return runningReader.findInterpolatedTelemetryUrl(runningId, memberUuid);
    }

    public List<RunInfo> findRunnings(String filteredBy,
                                      Long startEpoch, Long endEpoch,
                                      Long cursorStartedAt,
                                      String cursorCourseName,
                                      Long cursorRunningId, String memberUuid) {
        Member member = findMember(memberUuid);
        if (filteredBy.equals(RunningInfoFilter.DATE.name())) {
            return runningReader.findRunInfosFilteredByDate(
                    cursorStartedAt, cursorRunningId,
                    startEpoch, endEpoch, member.getId());
        } else if (filteredBy.equals(RunningInfoFilter.COURSE.name())) {
            return runningReader.findRunInfosFilteredByCourses(
                    cursorCourseName, cursorRunningId,
                    startEpoch, endEpoch, member.getId());
        }
        throw new IllegalArgumentException("올바르지 않은 필터 형식이 요청됐습니다.");
    }

    public List<RunInfo> findRunnings(Long courseId, String memberUuid) {
        Member member = findMember(memberUuid);
        List<Running> runnings = runningReader.findRunningsByCourseAndMember(courseId, member.getId());
        return mapper.toResponse(runnings);
    }

    public List<RunMonthlyStatusResponse> findMonthlyDayRunStatus(Integer year, Integer month, String memberUuid) {
        Member member = findMember(memberUuid);
        List<DayRunInfo> dayRunInfos = runningReader.findDayRunInfos(year, month, member.getId());
        return mapper.toDayRunStatusResponses(dayRunInfos);
    }

    private Member findMember(String memberUuid) {
        return memberService.findMemberByUuid(memberUuid);
    }

}
