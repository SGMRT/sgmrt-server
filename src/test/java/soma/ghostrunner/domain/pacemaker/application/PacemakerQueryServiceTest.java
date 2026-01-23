package soma.ghostrunner.domain.pacemaker.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import soma.ghostrunner.domain.pacemaker.api.dto.response.PacemakerPollingResponse;
import soma.ghostrunner.domain.pacemaker.application.support.PacemakerApplicationMapper;
import soma.ghostrunner.domain.pacemaker.domain.Pacemaker;
import soma.ghostrunner.domain.pacemaker.domain.PacemakerSet;
import soma.ghostrunner.domain.pacemaker.domain.RunningType;
import soma.ghostrunner.domain.pacemaker.domain.formula.RunningTipsProvider;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerRepository;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerSetRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PacemakerQueryServiceTest {

    @Mock
    PacemakerRepository pacemakerRepository;
    @Mock
    PacemakerSetRepository pacemakerSetRepository;
    @Mock
    PacemakerApplicationMapper mapper;
    @Mock
    RunningTipsProvider runningTipsProvider;

    PacemakerQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new PacemakerQueryService(
                pacemakerRepository,
                pacemakerSetRepository,
                mapper,
                runningTipsProvider
        );
    }

    @Test
    @DisplayName("COMPLETED 상태 페이스메이커는 세트를 조회하고 mapper.toResponse(pacemaker, sets)로 응답한다")
    void getPacemaker_completed_success() {
        // given
        String owner = "owner-uuid";
        Long id = 100L;

        Pacemaker completed = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.M, owner);
        completed.proceed();
        completed.complete("요약", 10.0, 50, "메세지");

        List<PacemakerSet> sets = List.of(
                PacemakerSet.of(1, "메세지1", null, null, null, null),
                PacemakerSet.of(2, "메세지1", null, null, null, null)
        );
        PacemakerPollingResponse expected = new PacemakerPollingResponse();
        when(runningTipsProvider.getRandomTip()).thenReturn("Mock Tip");
        when(pacemakerRepository.findById(id)).thenReturn(Optional.of(completed));
        when(pacemakerSetRepository.findByPacemakerIdOrderBySetNumAsc(id)).thenReturn(sets);
        when(mapper.toPacemakerPollingResponse(completed, sets, "Mock Tip")).thenReturn(expected);

        // when
        PacemakerPollingResponse actual = queryService.getPacemaker(id, owner);

        // then
        assertThat(actual).isSameAs(expected);

        InOrder inOrder = inOrder(pacemakerRepository, pacemakerSetRepository, mapper);
        inOrder.verify(pacemakerRepository).findById(id);
        inOrder.verify(pacemakerSetRepository).findByPacemakerIdOrderBySetNumAsc(id);
        inOrder.verify(mapper).toPacemakerPollingResponse(completed, sets, "Mock Tip");

        verify(mapper, never()).toPacemakerPollingResponse(completed);
    }

    @Test
    @DisplayName("FALLBACK 상태 페이스메이커도 완료로 간주하여 세트를 조회한다")
    void getPacemaker_fallback_returnsWithSets() {
        // given
        String owner = "owner-uuid";
        Long id = 100L;

        Pacemaker fallback = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.M, owner);
        fallback.proceed();
        fallback.fallback();

        List<PacemakerSet> sets = List.of(
                PacemakerSet.of(1, null, 0.0, 2.0, 5.0, null)
        );
        PacemakerPollingResponse expected = new PacemakerPollingResponse();
        when(runningTipsProvider.getRandomTip()).thenReturn("Mock Tip");
        when(pacemakerRepository.findById(id)).thenReturn(Optional.of(fallback));
        when(pacemakerSetRepository.findByPacemakerIdOrderBySetNumAsc(id)).thenReturn(sets);
        when(mapper.toPacemakerPollingResponse(fallback, sets, "Mock Tip")).thenReturn(expected);

        // when
        PacemakerPollingResponse actual = queryService.getPacemaker(id, owner);

        // then
        assertThat(actual).isSameAs(expected);
        verify(pacemakerSetRepository).findByPacemakerIdOrderBySetNumAsc(id);
    }

    @Test
    @DisplayName("INIT/PROCEEDING 상태면 세트 조회 없이 mapper.toResponse(status)로 응답한다")
    void getPacemaker_processing_returnsStatusOnly() {
        // given
        String owner = "owner-uuid";
        Long id = 101L;

        Pacemaker init = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.M, owner);

        PacemakerPollingResponse expected = new PacemakerPollingResponse();
        when(pacemakerRepository.findById(id)).thenReturn(Optional.of(init));
        when(mapper.toPacemakerPollingResponse(init)).thenReturn(expected);

        // when
        PacemakerPollingResponse actual = queryService.getPacemaker(id, owner);

        // then
        assertThat(actual).isSameAs(expected);

        verify(pacemakerSetRepository, never()).findByPacemakerIdOrderBySetNumAsc(anyLong());
        verify(mapper, never()).toPacemakerPollingResponse(eq(init), anyList(), anyString());

        InOrder inOrder = inOrder(pacemakerRepository, mapper);
        inOrder.verify(pacemakerRepository).findById(id);
        inOrder.verify(mapper).toPacemakerPollingResponse(init);
    }

    @Test
    @DisplayName("본인 소유가 아니면 AccessDeniedException을 던진다")
    void getPacemaker_notMine_throwsAccessDenied() {
        // given
        String owner = "owner-uuid";
        String other = "other-uuid";
        Long id = 102L;

        Pacemaker entity = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.M, owner);
        when(pacemakerRepository.findById(id)).thenReturn(Optional.of(entity));

        // when/then
        assertThatThrownBy(() -> queryService.getPacemaker(id, other))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("접근할 수 없는 러닝 데이터입니다.");

        verify(pacemakerSetRepository, never()).findByPacemakerIdOrderBySetNumAsc(anyLong());
    }

    @Test
    @DisplayName("Pacemaker ID가 없으면 RunningNotFoundException을 던진다")
    void getPacemaker_notFound_throws() {
        // given
        Long id = 999L;
        when(pacemakerRepository.findById(id)).thenReturn(Optional.empty());

        // when/then
        assertThatThrownBy(() -> queryService.getPacemaker(id, "any-uuid"))
                .isInstanceOf(RunningNotFoundException.class);

        verifyNoInteractions(pacemakerSetRepository, mapper);
    }

    @Test
    @DisplayName("findPacemaker는 ID로 페이스메이커를 조회한다")
    void findPacemaker_success() {
        // given
        Long id = 100L;
        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.M, "owner");
        when(pacemakerRepository.findById(id)).thenReturn(Optional.of(pacemaker));

        // when
        Pacemaker result = queryService.findPacemaker(id);

        // then
        assertThat(result).isSameAs(pacemaker);
    }

    @Test
    @DisplayName("findPacemaker는 ID가 없으면 RunningNotFoundException을 던진다")
    void findPacemaker_notFound() {
        // given
        Long id = 999L;
        when(pacemakerRepository.findById(id)).thenReturn(Optional.empty());

        // when/then
        assertThatThrownBy(() -> queryService.findPacemaker(id))
                .isInstanceOf(RunningNotFoundException.class);
    }

}
