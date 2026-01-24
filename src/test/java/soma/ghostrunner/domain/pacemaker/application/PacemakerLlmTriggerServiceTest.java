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

    @DisplayName("TX2 커밋 후: Redis 카운트 증가 및 LLM 비동기 호출이 실행된다")
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
        // Redis 카운트 증가 확인
        verify(rateLimitService).incrementCounter(memberUuid);

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

    @DisplayName("TX2 커밋 후: 일일 사용량 초과 시 InvalidRunningException을 던진다")
    @Test
    void triggerLlmAfterCommit_rateLimitExceeded_throwsException() {
        // given
        String memberUuid = "member-123";

        Member member = Member.of("러너", "url");
        member.setUuid(memberUuid);

        WorkoutDto workoutDto = WorkoutDto.of(RunningType.I, 10.0, List.of());
        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.I, memberUuid);

        PacemakerCreationResult result = PacemakerCreationResult.of(
                pacemaker, member, workoutDto, 45, 3, 25);

        when(rateLimitService.createRateLimitKey(memberUuid)).thenReturn("rate-limit-key");
        doThrow(new InvalidRunningException(soma.ghostrunner.global.error.ErrorCode.TOO_MANY_REQUESTS, "일일 사용량 초과"))
                .when(rateLimitService).incrementCounter(memberUuid);

        // when & then
        assertThatThrownBy(() -> triggerService.triggerLlmAfterCommit(result))
                .isInstanceOf(InvalidRunningException.class)
                .hasMessageContaining("일일 사용량");

        // LLM 서비스는 호출되지 않아야 함
        verifyNoInteractions(llmService);
    }

    @DisplayName("TX2 커밋 후: Redis 스크립트 오류 시 RuntimeException을 던진다")
    @Test
    void triggerLlmAfterCommit_redisError_throwsException() {
        // given
        String memberUuid = "member-123";

        Member member = Member.of("러너", "url");
        member.setUuid(memberUuid);

        WorkoutDto workoutDto = WorkoutDto.of(RunningType.I, 10.0, List.of());
        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, 1L, RunningType.I, memberUuid);

        PacemakerCreationResult result = PacemakerCreationResult.of(
                pacemaker, member, workoutDto, 45, 3, 25);

        when(rateLimitService.createRateLimitKey(memberUuid)).thenReturn("rate-limit-key");
        doThrow(new RuntimeException("Redis 스크립트 실행 오류"))
                .when(rateLimitService).incrementCounter(memberUuid);

        // when & then
        assertThatThrownBy(() -> triggerService.triggerLlmAfterCommit(result))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Redis 스크립트");

        verifyNoInteractions(llmService);
    }

}
