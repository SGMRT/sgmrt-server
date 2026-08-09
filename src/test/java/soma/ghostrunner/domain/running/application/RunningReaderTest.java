package soma.ghostrunner.domain.running.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.data.domain.*;
import org.springframework.data.util.Pair;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.running.application.dto.response.*;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningRecord;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Reader는 "리포지토리 호출 + 없을 때의 예외 변환 + 키 기준 정규화"까지만 책임진다.
 * 조합·매핑·다른 도메인 조회의 검증은 {@link RunningQueryServiceTest}와 CourseFacade 쪽에 있다.
 */
@ExtendWith(SpringExtension.class)
class RunningReaderTest {

    @Mock
    RunningRepository runningRepository;

    RunningReader sut;

    @BeforeEach
    void setUp() {
        sut = new RunningReader(runningRepository);
    }

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

    @Test
    @DisplayName("findGhostRunInfo: 존재하면 반환, 없으면 RunningNotFoundException")
    void findGhostRunInfo_foundOrNotFound() {
        Long id = 1L;
        String memberUuid = "u-1";
        GhostRunDetailInfo info = mock(GhostRunDetailInfo.class);

        when(runningRepository.findGhostRunInfoById(id, memberUuid)).thenReturn(Optional.of(info));
        assertThat(sut.findGhostRunInfo(id, memberUuid)).isSameAs(info);

        when(runningRepository.findGhostRunInfoById(id, memberUuid)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> sut.findGhostRunInfo(id, memberUuid))
                .isInstanceOf(RunningNotFoundException.class);
    }

    @Test
    @DisplayName("findMemberAndRunRecordInfo: 존재하면 반환, 없으면 RunningNotFoundException")
    void findMemberAndRunRecordInfo_foundOrNotFound() {
        Long id = 2L;
        MemberAndRunRecordInfo info = mock(MemberAndRunRecordInfo.class);

        when(runningRepository.findMemberAndRunRecordInfoById(id)).thenReturn(Optional.of(info));
        assertThat(sut.findMemberAndRunRecordInfo(id)).isSameAs(info);

        when(runningRepository.findMemberAndRunRecordInfoById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> sut.findMemberAndRunRecordInfo(id))
                .isInstanceOf(RunningNotFoundException.class);
    }

    @Test
    @DisplayName("findInterpolatedTelemetryUrl: URL 있으면 반환, 없으면(= 남의 러닝) AccessDeniedException")
    void findInterpolatedTelemetryUrl_foundOrDenied() {
        Long id = 5L; String u = "u";
        when(runningRepository.findInterpolatedTelemetryUrlByIdAndMemberUuid(id, u))
                .thenReturn(Optional.of("s3://telemetry"));
        assertThat(sut.findInterpolatedTelemetryUrl(id, u)).isEqualTo("s3://telemetry");

        when(runningRepository.findInterpolatedTelemetryUrlByIdAndMemberUuid(id, u))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> sut.findInterpolatedTelemetryUrl(id, u))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("findPublicGhostRuns: 매핑 없이 엔티티 페이지를 그대로 반환한다")
    void findPublicGhostRuns_returnsEntityPage() {
        Long courseId = 9L;
        Pageable pageable = PageRequest.of(0, 3, Sort.by("runningRecord.averagePace"));
        Running r1 = mock(Running.class), r2 = mock(Running.class);
        Page<Running> page = new PageImpl<>(List.of(r1, r2), pageable, 2);
        when(runningRepository.findByCourse_IdAndIsPublicTrue(courseId, pageable)).thenReturn(page);

        assertThat(sut.findPublicGhostRuns(courseId, pageable)).isSameAs(page);
    }

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

    @DisplayName("findMemberBestRunBefore: 특정 시간 이전의 회원 베스트 러닝 기록 조회")
    @Test
    void findMemberBestRunBefore() {
        // given
        String memberUuid = "uuid-11";
        Long courseId = 20L;
        Long beforeStartedAt = 1_600_000L;
        Running bestRun = mock(Running.class);
        when(runningRepository.findBestRunByCourseIdAndMemberUuidBefore(courseId, memberUuid, beforeStartedAt))
                .thenReturn(Optional.of(bestRun));
        // when
        Optional<Running> result = sut.findMemberBestRunBefore(courseId, memberUuid, beforeStartedAt);
        // then
        assertThat(result).contains(bestRun);
        verify(runningRepository).findBestRunByCourseIdAndMemberUuidBefore(courseId, memberUuid, beforeStartedAt);
    }

    @Test
    @DisplayName("findBestRunningRecordsForCourses: duration->id 기준으로 코스별 1건만 남긴다 (중복 들어와도 정제)")
    void findBestRunningRecordsForCourses_dedup() {
        // given
        List<Long> courseIds = List.of(1L, 2L, 3L);
        String memberUuid = "멤버 UUID";

        Running r1 = mock(Running.class);         // course=1, dur=140, id=10
        Running r2 = mock(Running.class);         // course=2, dur=95 , id=20
        Running r3 = mock(Running.class);         // course=2, dur=90 , id=30  -> 더 좋음(선정)
        // course=3은 레코드 없음 → null 기대

        Course c1 = mock(Course.class);
        Course c2 = mock(Course.class);

        when(c1.getId()).thenReturn(1L);
        when(c2.getId()).thenReturn(2L);

        when(r1.getCourse()).thenReturn(c1);
        when(r2.getCourse()).thenReturn(c2);
        when(r3.getCourse()).thenReturn(c2);

        RunningRecord rr1 = mock(RunningRecord.class);
        RunningRecord rr2 = mock(RunningRecord.class);
        RunningRecord rr3 = mock(RunningRecord.class);
        when(r1.getRunningRecord()).thenReturn(rr1);
        when(r2.getRunningRecord()).thenReturn(rr2);
        when(r3.getRunningRecord()).thenReturn(rr3);

        when(r1.getId()).thenReturn(10L);
        when(r2.getId()).thenReturn(20L);
        when(r3.getId()).thenReturn(30L);

        when(runningRepository.findBestRunningRecordsByMemberIdAndCourseIds(memberUuid, courseIds))
                .thenReturn(List.of(r1, r2, r3));

        // when
        Map<Long, Running> map = sut.findBestRunningRecordsForCourses(courseIds, memberUuid);

        // then
        assertThat(map).hasSize(3);
        assertThat(map.get(1L)).isSameAs(r1);
        assertThat(map.get(2L)).isSameAs(r2);
        assertThat(map.get(3L)).isNull();       // 해당 코스 레코드 없음 → null
        verify(runningRepository).findBestRunningRecordsByMemberIdAndCourseIds(memberUuid, courseIds);
    }

}
