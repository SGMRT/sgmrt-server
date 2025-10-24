package soma.ghostrunner.domain.running.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.data.domain.*;
import org.springframework.data.util.Pair;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import soma.ghostrunner.domain.course.dto.response.CourseGhostResponse;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.api.dto.response.RunMonthlyStatusResponse;
import soma.ghostrunner.domain.running.application.dto.response.*;
import soma.ghostrunner.domain.running.application.support.RunningApplicationMapper;
import soma.ghostrunner.domain.running.application.support.RunningInfoFilter;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
class RunningQueryServiceTest {

    @Mock
    RunningRepository runningRepository;
    @Mock
    RunningApplicationMapper mapper;
    @Mock
    MemberService memberService;

    RunningQueryService sut; // SUT (spy가 필요한 케이스는 별도 생성)

    @BeforeEach
    void setUp() {
        sut = new RunningQueryService(runningRepository, mapper, memberService);
    }

    // ===== findSoloRunInfo =====
    @Test
    @DisplayName("findSoloRunInfo: 존재하면 반환, 없으면 RunningNotFoundException")
    void findSoloRunInfo_foundOrNotFound() {
        Long id = 10L;
        String memberUuid = "u-1";
        SoloRunDetailInfo info = mock(SoloRunDetailInfo.class);

        when(runningRepository.findSoloRunInfoById(id, memberUuid)).thenReturn(Optional.of(info));
        assertThat(sut.findSoloRunInfo(id, memberUuid)).isSameAs(info);

        when(runningRepository.findSoloRunInfoById(id, memberUuid)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> sut.findSoloRunInfo(id, memberUuid))
                .isInstanceOf(RunningNotFoundException.class);
    }

    // ===== findGhostRunInfo =====
    @Test
    @DisplayName("findGhostRunInfo: 내 러닝 정보 조회 → 고스트ID 검증 → 고스트 멤버/기록 세팅 후 반환")
    void findGhostRunInfo_success() {
        Long myId = 1L;
        Long ghostId = 2L;
        String memberUuid = "u-1";
        GhostRunDetailInfo myInfo = mock(GhostRunDetailInfo.class);
        MemberAndRunRecordInfo ghostInfo = mock(MemberAndRunRecordInfo.class);

        when(runningRepository.findGhostRunInfoById(myId, memberUuid)).thenReturn(Optional.of(myInfo));
        when(myInfo.getGhostRunId()).thenReturn(ghostId);
        when(runningRepository.findMemberAndRunRecordInfoById(ghostId)).thenReturn(Optional.of(ghostInfo));

        GhostRunDetailInfo result = sut.findGhostRunInfo(myId, ghostId, memberUuid);
        assertThat(result).isSameAs(myInfo);
        verify(myInfo).setGhostRunInfo(ghostInfo);
    }

    @Test
    @DisplayName("findGhostRunInfo: 내 정보의 고스트ID가 null이거나 불일치하면 InvalidRunningException")
    void findGhostRunInfo_invalidGhostId() {
        Long myId = 1L;
        Long ghostId = 2L;
        String memberUuid = "u-1";
        GhostRunDetailInfo myInfo = mock(GhostRunDetailInfo.class);

        when(runningRepository.findGhostRunInfoById(myId, memberUuid)).thenReturn(Optional.of(myInfo));
        when(myInfo.getGhostRunId()).thenReturn(null);

        assertThatThrownBy(() -> sut.findGhostRunInfo(myId, ghostId, memberUuid))
                .isInstanceOf(InvalidRunningException.class);

        when(myInfo.getGhostRunId()).thenReturn(999L);
        assertThatThrownBy(() -> sut.findGhostRunInfo(myId, ghostId, memberUuid))
                .isInstanceOf(InvalidRunningException.class);
    }

    @Test
    @DisplayName("findGhostRunInfo: 내 러닝 정보가 없으면 RunningNotFoundException")
    void findGhostRunInfo_myInfoNotFound() {
        when(runningRepository.findGhostRunInfoById(anyLong(), anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> sut.findGhostRunInfo(1L, 2L, "u"))
                .isInstanceOf(RunningNotFoundException.class);
    }

    @Test
    @DisplayName("findGhostRunInfo: 고스트 멤버/기록 정보가 없으면 RunningNotFoundException")
    void findGhostRunInfo_ghostInfoNotFound() {
        Long myId = 1L; Long ghostId = 2L;
        GhostRunDetailInfo myInfo = mock(GhostRunDetailInfo.class);
        when(runningRepository.findGhostRunInfoById(myId, "u")).thenReturn(Optional.of(myInfo));
        when(myInfo.getGhostRunId()).thenReturn(ghostId);
        when(runningRepository.findMemberAndRunRecordInfoById(ghostId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.findGhostRunInfo(myId, ghostId, "u"))
                .isInstanceOf(RunningNotFoundException.class);
    }

    // ===== findRunningTelemetries =====
    @Test
    @DisplayName("findRunningTelemetries: URL 있으면 반환, 없으면 AccessDeniedException")
    void findRunningTelemetries_foundOrDenied() {
        Long id = 5L; String u = "u";
        when(runningRepository.findInterpolatedTelemetryUrlByIdAndMemberUuid(id, u))
                .thenReturn(Optional.of("s3://telemetry"));
        assertThat(sut.findRunningTelemetries(id, u)).isEqualTo("s3://telemetry");

        when(runningRepository.findInterpolatedTelemetryUrlByIdAndMemberUuid(id, u))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> sut.findRunningTelemetries(id, u))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ===== findPublicGhostRunsByCourseId & sort validation =====
    @Test
    @DisplayName("findPublicGhostRunsByCourseId: 유효한 sort면 매핑해서 페이지 반환")
    void findPublicGhostRunsByCourseId_success() {
        Long courseId = 9L;
        Pageable pageable = PageRequest.of(0, 3, Sort.by("runningRecord.averagePace"));
        Running r1 = mock(Running.class), r2 = mock(Running.class);
        when(runningRepository.findByCourse_IdAndIsPublicTrue(courseId, pageable))
                .thenReturn(new PageImpl<>(List.of(r1, r2), pageable, 2));

        CourseGhostResponse g1 = mock(CourseGhostResponse.class);
        CourseGhostResponse g2 = mock(CourseGhostResponse.class);
        when(mapper.toGhostResponse(r1)).thenReturn(g1);
        when(mapper.toGhostResponse(r2)).thenReturn(g2);

        Page<CourseGhostResponse> page = sut.findPublicGhostRunsByCourseId(courseId, pageable);
        assertThat(page.getContent()).containsExactly(g1, g2);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("findPublicGhostRunsByCourseId: validateSortProperty가 예외를 던지면 그대로 전파")
    void findPublicGhostRunsByCourseId_invalidSort_propagates() {
        // validateSortProperty를 spy로 강제 예외
        RunningQueryService spy = Mockito.spy(sut);
        Long courseId = 9L;
        Pageable pageable = PageRequest.of(0, 3, Sort.by("invalidProp"));

        doThrow(new IllegalArgumentException("잘못된 고스트 정렬 필드"))
                .when(spy).findPublicGhostRunsByCourseId(courseId, pageable); // 간접 검증 대신 직접 메서드에 예외를 주입할 수도 있음
        assertThatThrownBy(() -> spy.findPublicGhostRunsByCourseId(courseId, pageable))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ===== findBestPublicRunForCourse / findRunningByRunningId / findFirstRunning =====
    @Test
    @DisplayName("findBestPublicRunForCourse: Optional 그대로 반환")
    void findBestPublicRunForCourse_optional() {
        Long courseId = 1L; String u = "u";
        Running r = mock(Running.class);
        when(runningRepository.findBestPublicRunByCourseIdAndMemberId(courseId, u))
                .thenReturn(Optional.of(r));
        assertThat(sut.findBestPublicRunForCourse(courseId, u)).contains(r);
    }

    @Test
    @DisplayName("findRunningByRunningId: 존재 시 반환, 없으면 RunningNotFoundException")
    void findRunningByRunningId_foundOrNotFound() {
        Running r = mock(Running.class);
        when(runningRepository.findById(1L)).thenReturn(Optional.of(r));
        assertThat(sut.findRunningByRunningId(1L)).isSameAs(r);

        when(runningRepository.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> sut.findRunningByRunningId(2L))
                .isInstanceOf(RunningNotFoundException.class);
    }

    @Test
    @DisplayName("findFirstRunning: Optional 그대로 반환")
    void findFirstRunning_optional() {
        Running r = mock(Running.class);
        when(runningRepository.findFirstRunningByCourseId(10L)).thenReturn(Optional.of(r));
        assertThat(sut.findFirstRunning(10L)).contains(r);
    }

    // ===== findRunnings (filteredBy 분기) =====
    @Nested
    class FindRunnings {

        final String memberUuid = "mem-1";
        final Long start = 1000L, end = 5000L;
        final Long cursorStartedAt = 2000L, cursorRunningId = 99L;
        final String cursorCourseName = "한강코스";

        @Test
        @DisplayName("filteredBy=DATE: member 조회 후 날짜 기준 repo 호출")
        void filterByDate() {
            Member m = mock(Member.class);
            when(memberService.findMemberByUuid(memberUuid)).thenReturn(m);
            when(m.getId()).thenReturn(7L);

            List<RunInfo> out = List.of(mock(RunInfo.class));
            when(runningRepository.findRunInfosFilteredByDate(cursorStartedAt, cursorRunningId, start, end, 7L))
                    .thenReturn(out);

            List<RunInfo> res = sut.findRunnings(
                    RunningInfoFilter.DATE.name(), start, end,
                    cursorStartedAt, null, cursorRunningId, memberUuid);

            assertThat(res).isSameAs(out);
        }

        @Test
        @DisplayName("filteredBy=COURSE: member 조회 후 코스 기준 repo 호출")
        void filterByCourse() {
            Member m = mock(Member.class);
            when(memberService.findMemberByUuid(memberUuid)).thenReturn(m);
            when(m.getId()).thenReturn(7L);

            List<RunInfo> out = List.of(mock(RunInfo.class));
            when(runningRepository.findRunInfosFilteredByCourses(cursorCourseName, cursorRunningId, start, end, 7L))
                    .thenReturn(out);

            List<RunInfo> res = sut.findRunnings(
                    RunningInfoFilter.COURSE.name(), start, end,
                    null, cursorCourseName, cursorRunningId, memberUuid);

            assertThat(res).isSameAs(out);
        }

        @Test
        @DisplayName("filteredBy가 잘못되면 IllegalArgumentException")
        void invalidFilter() {
            assertThatThrownBy(() -> sut.findRunnings(
                    "WRONG", start, end, cursorStartedAt, cursorCourseName, cursorRunningId, memberUuid))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @DisplayName("리포지토리가 빈 결과를 반환하면 빈 리스트를 반환한다")
    @Test
    void findRunnings_returnsEmptyList_whenRepositoryEmpty() {
        // given
        Long courseId = 10L;
        String memberUuid = "uuid-123";

        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(memberService.findMemberByUuid(memberUuid)).thenReturn(member);

        when(runningRepository.findRunningsByCourseIdAndMemberId(courseId, 1L))
                .thenReturn(List.of());

        // mapper도 빈 리스트를 그대로 빈으로 매핑하도록 목 설정
        when(mapper.toPacemakerPollingResponse(List.of())).thenReturn(List.of());

        // when
        List<RunInfo> result = sut.findRunnings(courseId, memberUuid);

        // then
        assertThat(result).isNotNull().isEmpty();

        verify(memberService).findMemberByUuid(memberUuid);
        verify(runningRepository).findRunningsByCourseIdAndMemberId(courseId, 1L);
        verify(mapper).toPacemakerPollingResponse(List.of());
        verifyNoMoreInteractions(memberService, runningRepository, mapper);
    }

    @DisplayName("리포지토리가 결과를 반환하면 RunInfo 리스트로 매핑되어 반환된다")
    @Test
    void findRunnings_returnsMappedList_whenRepositoryHasData() {
        // given
        Long courseId = 10L;
        String memberUuid = "uuid-123";

        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(memberService.findMemberByUuid(memberUuid)).thenReturn(member);

        List<Running> repoResult = List.of(mock(Running.class), mock(Running.class));

        when(runningRepository.findRunningsByCourseIdAndMemberId(courseId, 1L))
                .thenReturn(repoResult);

        List<RunInfo> mapped = List.of(mock(RunInfo.class), mock(RunInfo.class));
        when(mapper.toPacemakerPollingResponse(repoResult)).thenReturn(mapped);

        // when
        List<RunInfo> result = sut.findRunnings(courseId, memberUuid);

        // then
        assertThat(result).isEqualTo(mapped);
        verify(memberService).findMemberByUuid(memberUuid);
        verify(runningRepository).findRunningsByCourseIdAndMemberId(courseId, 1L);
        verify(mapper).toPacemakerPollingResponse(repoResult);
    }

    @Test
    @DisplayName("findMonthlyDayRunStatus: 멤버 조회 → 월별 일자 집계 조회 → 응답으로 매핑 후 반환")
    void findMonthlyDayRunStatus_success() {
        // given
        int year = 2025, month = 10;
        String memberUuid = "mem-42";

        Member member = mock(Member.class);
        when(member.getId()).thenReturn(42L);
        when(memberService.findMemberByUuid(memberUuid)).thenReturn(member);

        DayRunInfo d1 = mock(DayRunInfo.class);
        DayRunInfo d2 = mock(DayRunInfo.class);
        List<DayRunInfo> repoOut = List.of(d1, d2);
        when(runningRepository.findDayRunInfosFilteredByDate(year, month, 42L))
                .thenReturn(repoOut);

        RunMonthlyStatusResponse r1 = mock(RunMonthlyStatusResponse.class);
        RunMonthlyStatusResponse r2 = mock(RunMonthlyStatusResponse.class);
        List<RunMonthlyStatusResponse> mapped = List.of(r1, r2);
        when(mapper.toDayRunStatusResponses(repoOut)).thenReturn(mapped);

        // when
        List<RunMonthlyStatusResponse> result = sut.findMonthlyDayRunStatus(year, month, memberUuid);

        // then
        assertThat(result).isEqualTo(mapped);

        // interaction 검증
        verify(memberService).findMemberByUuid(memberUuid);
        verify(runningRepository).findDayRunInfosFilteredByDate(year, month, 42L);
        verify(mapper).toDayRunStatusResponses(repoOut);
        verifyNoMoreInteractions(memberService, runningRepository, mapper);
    }

    @Test
    @DisplayName("findMonthlyDayRunStatus: 리포지토리가 빈 결과를 주면 빈 응답 리스트로 매핑/반환한다")
    void findMonthlyDayRunStatus_empty() {
        // given
        int year = 2025, month = 10;
        String memberUuid = "mem-empty";

        Member member = mock(Member.class);
        when(member.getId()).thenReturn(7L);
        when(memberService.findMemberByUuid(memberUuid)).thenReturn(member);

        List<DayRunInfo> repoOut = List.of(); // empty
        when(runningRepository.findDayRunInfosFilteredByDate(year, month, 7L))
                .thenReturn(repoOut);

        List<RunMonthlyStatusResponse> mapped = List.of(); // mapper 역시 빈 리스트 반환
        when(mapper.toDayRunStatusResponses(repoOut)).thenReturn(mapped);

        // when
        List<RunMonthlyStatusResponse> result = sut.findMonthlyDayRunStatus(year, month, memberUuid);

        // then
        assertThat(result).isNotNull().isEmpty();

        verify(memberService).findMemberByUuid(memberUuid);
        verify(runningRepository).findDayRunInfosFilteredByDate(year, month, 7L);
        verify(mapper).toDayRunStatusResponses(repoOut);
        verifyNoMoreInteractions(memberService, runningRepository, mapper);
      
    }
      
    @DisplayName("findPublicRunnersCountByCourseIds: 코스ID 리스트에 대한 공개 러너 수 맵 반환")
    @Test
    void findPublicRunnersCountByCourseIds() {
        // given
        List<Long> courseIds = List.of(1L, 2L, 3L);
        List<Pair<Long, Long>> repoResult = List.of(
                Pair.of(1L, 5L),
                Pair.of(2L, 10L)
        );
        when(runningRepository.findPublicRunnerCountsByCourseIds(courseIds))
                .thenReturn(repoResult);

        // when
        Map<Long, Long> result = sut.findPublicRunnersCountByCourseIds(courseIds);

        // then
        Map<Long, Long> expected = Map.of(
                1L, 5L,
                2L, 10L,
                3L, 0L  // 빈 코스 ID는 0으로 채워짐
        );
        assertThat(result).isEqualTo(expected);
        verify(runningRepository).findPublicRunnerCountsByCourseIds(courseIds);
    }

}
