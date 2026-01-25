package soma.ghostrunner.domain.pacemaker.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.pacemaker.application.dto.PacemakerCreationResult;
import soma.ghostrunner.domain.pacemaker.application.dto.WorkoutDto;
import soma.ghostrunner.domain.pacemaker.domain.Pacemaker;
import soma.ghostrunner.domain.pacemaker.domain.RunningType;
import soma.ghostrunner.domain.pacemaker.infra.persistence.PacemakerRepository;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PacemakerLlmTriggerServiceTest {

    @Mock
    PacemakerRepository pacemakerRepository;
    @Mock
    PacemakerRateLimitService rateLimitService;
    @Mock
    PacemakerLlmService llmService;

    PacemakerLlmTriggerService triggerService;

    @BeforeEach
    void setUp() {
        triggerService = new PacemakerLlmTriggerService(
                pacemakerRepository,
                rateLimitService,
                llmService
        );
    }

    @DisplayName("TX2: INIT 상태의 Pacemaker를 PROCEEDING으로 업데이트한다")
    @Test
    void updateToProceeding_success() {
        // given
        Long pacemakerId = 100L;
        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.I, "member-uuid");
        // 현재 INIT 상태

        when(pacemakerRepository.findById(pacemakerId)).thenReturn(Optional.of(pacemaker));

        // when
        triggerService.updateToProceeding(pacemakerId);

        // then
        assertThat(pacemaker.getStatus()).isEqualTo(Pacemaker.Status.PROCEEDING);
    }

    @DisplayName("TX2 커밋 후: LLM 비동기 호출이 실행된다 (카운트 증가는 Facade에서 처리)")
    @Test
    void triggerLlmAfterCommit_success() {
        // given
        String memberUuid = "member-123";
        Long pacemakerId = 100L;
        int vdot = 45;
        int condition = 3;
        int temperature = 25;
        String rateLimitKey = "pacemaker_api_rate_limit:member-123:2024-01-01";

        Member member = Member.of("러너", "url");
        member.setUuid(memberUuid);

        WorkoutDto workoutDto = WorkoutDto.of(RunningType.I, 10.0, List.of());

        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.I, memberUuid);

        PacemakerCreationResult result = PacemakerCreationResult.of(
                pacemaker, member, workoutDto, vdot, condition, temperature);

        when(rateLimitService.createRateLimitKey(memberUuid)).thenReturn(rateLimitKey);

        // when
        triggerService.triggerLlmAfterCommit(result);

        // then
        // 카운트 증가는 Facade에서 선카운트로 처리되므로 여기서는 호출되지 않음
        verify(rateLimitService, never()).incrementCounter(any());

        // LLM 서비스 호출 확인
        verify(llmService).requestLlmToCreatePacemaker(
                eq(member),
                eq(workoutDto),
                eq(vdot),
                eq(condition),
                eq(temperature),
                eq(pacemaker.getId()),
                eq(rateLimitKey)
        );
    }

}
