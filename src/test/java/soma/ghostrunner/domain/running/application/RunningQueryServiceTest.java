package soma.ghostrunner.domain.running.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.api.dto.response.RunMonthlyStatusResponse;
import soma.ghostrunner.domain.running.application.dto.response.*;
import soma.ghostrunner.domain.running.application.support.RunningApplicationMapper;
import soma.ghostrunner.domain.running.application.support.RunningInfoFilter;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * QueryService는 Reader가 가지면 안 되는 것들만 검증한다 —
 * 여러 조회의 조합과 그 결과 검증, 다른 도메인(Member) 조회, 응답 DTO 매핑.
 * 리포지토리 위임 자체는 {@link RunningReaderTest}가 본다.
 */
@ExtendWith(SpringExtension.class)
class RunningQueryServiceTest {

    @Mock
    RunningReader runningReader;
    @Mock
    MemberService memberService;
    @Mock
    RunningApplicationMapper mapper;

    RunningQueryService sut;

    @BeforeEach
    void setUp() {
        sut = new RunningQueryService(runningReader, memberService, mapper);
    }

    @Nested
    @DisplayName("findGhostRunInfo: 내 러닝 조회 → 고스트ID 검증 → 고스트 기록 조합")
    class FindGhostRunInfo {

        @Test
        @DisplayName("고스트ID가 일치하면 고스트 멤버/기록을 세팅해 반환한다")
        void success() {
            Long myId = 1L, ghostId = 2L;
            String memberUuid = "u-1";
            GhostRunDetailInfo myInfo = mock(GhostRunDetailInfo.class);
            MemberAndRunRecordInfo ghostInfo = mock(MemberAndRunRecordInfo.class);

            when(runningReader.findGhostRunInfo(myId, memberUuid)).thenReturn(myInfo);
            when(myInfo.getGhostRunId()).thenReturn(ghostId);
            when(runningReader.findMemberAndRunRecordInfo(ghostId)).thenReturn(ghostInfo);

            GhostRunDetailInfo result = sut.findGhostRunInfo(myId, ghostId, memberUuid);

            assertThat(result).isSameAs(myInfo);
            verify(myInfo).setGhostRunInfo(ghostInfo);
        }

        @Test
        @DisplayName("고스트ID가 null이거나 실제로 뛴 고스트와 다르면 InvalidRunningException — 고스트 조회는 시도조차 하지 않는다")
        void invalidGhostId() {
            Long myId = 1L, ghostId = 2L;
            String memberUuid = "u-1";
            GhostRunDetailInfo myInfo = mock(GhostRunDetailInfo.class);
            when(runningReader.findGhostRunInfo(myId, memberUuid)).thenReturn(myInfo);

            when(myInfo.getGhostRunId()).thenReturn(null);
            assertThatThrownBy(() -> sut.findGhostRunInfo(myId, ghostId, memberUuid))
                    .isInstanceOf(InvalidRunningException.class);

            when(myInfo.getGhostRunId()).thenReturn(999L);
            assertThatThrownBy(() -> sut.findGhostRunInfo(myId, ghostId, memberUuid))
                    .isInstanceOf(InvalidRunningException.class);

            verify(runningReader, never()).findMemberAndRunRecordInfo(anyLong());
        }

        @Test
        @DisplayName("Reader가 던진 조회 예외는 그대로 전파한다")
        void readerExceptionPropagates() {
            when(runningReader.findGhostRunInfo(1L, "u")).thenThrow(new RunningNotFoundException());

            assertThatThrownBy(() -> sut.findGhostRunInfo(1L, 2L, "u"))
                    .isInstanceOf(RunningNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("findRunnings: 필터 분기")
    class FindRunningsByFilter {

        final String memberUuid = "mem-1";
        final Long start = 1000L, end = 5000L;
        final Long cursorStartedAt = 2000L, cursorRunningId = 99L;
        final String cursorCourseName = "한강코스";

        @Test
        @DisplayName("filteredBy=DATE: member 조회 후 날짜 기준 조회를 호출한다")
        void filterByDate() {
            Member m = mock(Member.class);
            when(memberService.findMemberByUuid(memberUuid)).thenReturn(m);
            when(m.getId()).thenReturn(7L);

            List<RunInfo> out = List.of(mock(RunInfo.class));
            when(runningReader.findRunInfosFilteredByDate(cursorStartedAt, cursorRunningId, start, end, 7L))
                    .thenReturn(out);

            List<RunInfo> res = sut.findRunnings(
                    RunningInfoFilter.DATE.name(), start, end,
                    cursorStartedAt, null, cursorRunningId, memberUuid);

            assertThat(res).isSameAs(out);
        }

        @Test
        @DisplayName("filteredBy=COURSE: member 조회 후 코스 기준 조회를 호출한다")
        void filterByCourse() {
            Member m = mock(Member.class);
            when(memberService.findMemberByUuid(memberUuid)).thenReturn(m);
            when(m.getId()).thenReturn(7L);

            List<RunInfo> out = List.of(mock(RunInfo.class));
            when(runningReader.findRunInfosFilteredByCourses(cursorCourseName, cursorRunningId, start, end, 7L))
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

    @DisplayName("findRunnings(코스별): 멤버 UUID를 ID로 해소한 뒤 조회하고 RunInfo로 매핑한다")
    @Test
    void findRunningsByCourse_resolvesMemberAndMaps() {
        // given
        Long courseId = 10L;
        String memberUuid = "uuid-123";

        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(memberService.findMemberByUuid(memberUuid)).thenReturn(member);

        List<Running> readerResult = List.of(mock(Running.class), mock(Running.class));
        when(runningReader.findRunningsByCourseAndMember(courseId, 1L)).thenReturn(readerResult);

        List<RunInfo> mapped = List.of(mock(RunInfo.class), mock(RunInfo.class));
        when(mapper.toResponse(readerResult)).thenReturn(mapped);

        // when
        List<RunInfo> result = sut.findRunnings(courseId, memberUuid);

        // then
        assertThat(result).isEqualTo(mapped);
        verify(memberService).findMemberByUuid(memberUuid);
        verify(runningReader).findRunningsByCourseAndMember(courseId, 1L);
        verify(mapper).toResponse(readerResult);
        verifyNoMoreInteractions(memberService, runningReader, mapper);
    }

    @DisplayName("findRunnings(코스별): 조회 결과가 비면 빈 리스트로 매핑되어 반환된다")
    @Test
    void findRunningsByCourse_empty() {
        // given
        Long courseId = 10L;
        String memberUuid = "uuid-123";

        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(memberService.findMemberByUuid(memberUuid)).thenReturn(member);

        when(runningReader.findRunningsByCourseAndMember(courseId, 1L)).thenReturn(List.of());
        when(mapper.toResponse(List.of())).thenReturn(List.of());

        // when
        List<RunInfo> result = sut.findRunnings(courseId, memberUuid);

        // then
        assertThat(result).isNotNull().isEmpty();
        verify(mapper).toResponse(List.of());
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

        List<DayRunInfo> readerOut = List.of(mock(DayRunInfo.class), mock(DayRunInfo.class));
        when(runningReader.findDayRunInfos(year, month, 42L)).thenReturn(readerOut);

        List<RunMonthlyStatusResponse> mapped =
                List.of(mock(RunMonthlyStatusResponse.class), mock(RunMonthlyStatusResponse.class));
        when(mapper.toDayRunStatusResponses(readerOut)).thenReturn(mapped);

        // when
        List<RunMonthlyStatusResponse> result = sut.findMonthlyDayRunStatus(year, month, memberUuid);

        // then
        assertThat(result).isEqualTo(mapped);
        verify(memberService).findMemberByUuid(memberUuid);
        verify(runningReader).findDayRunInfos(year, month, 42L);
        verify(mapper).toDayRunStatusResponses(readerOut);
        verifyNoMoreInteractions(memberService, runningReader, mapper);
    }

    @Test
    @DisplayName("findMonthlyDayRunStatus: 집계 결과가 비면 빈 응답 리스트로 매핑/반환한다")
    void findMonthlyDayRunStatus_empty() {
        // given
        int year = 2025, month = 10;
        String memberUuid = "mem-empty";

        Member member = mock(Member.class);
        when(member.getId()).thenReturn(7L);
        when(memberService.findMemberByUuid(memberUuid)).thenReturn(member);

        when(runningReader.findDayRunInfos(year, month, 7L)).thenReturn(List.of());
        when(mapper.toDayRunStatusResponses(List.of())).thenReturn(List.of());

        // when
        List<RunMonthlyStatusResponse> result = sut.findMonthlyDayRunStatus(year, month, memberUuid);

        // then
        assertThat(result).isNotNull().isEmpty();
    }

}
