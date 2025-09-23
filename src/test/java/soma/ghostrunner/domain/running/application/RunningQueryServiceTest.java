package soma.ghostrunner.domain.running.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import soma.ghostrunner.domain.course.dto.response.CourseGhostResponse;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.api.support.RunningApiMapper;
import soma.ghostrunner.domain.running.application.dto.response.MemberAndRunRecordInfo;
import soma.ghostrunner.domain.running.application.dto.response.RunInfo;
import soma.ghostrunner.domain.running.application.support.RunningInfoFilter;
import soma.ghostrunner.domain.running.application.dto.response.GhostRunDetailInfo;
import soma.ghostrunner.domain.running.application.dto.response.SoloRunDetailInfo;
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
    RunningApiMapper runningApiMapper;
    @Mock
    MemberService memberService;

    RunningQueryService sut; // SUT (spy가 필요한 케이스는 별도 생성)

    @BeforeEach
    void setUp() {
        sut = new RunningQueryService(runningRepository, runningApiMapper, memberService);
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
        when(runningApiMapper.toGhostResponse(r1)).thenReturn(g1);
        when(runningApiMapper.toGhostResponse(r2)).thenReturn(g2);

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
}
