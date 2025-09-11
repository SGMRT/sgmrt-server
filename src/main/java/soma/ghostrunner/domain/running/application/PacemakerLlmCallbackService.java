package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.application.dto.WorkoutDto;
import soma.ghostrunner.domain.running.domain.Pacemaker;
import soma.ghostrunner.domain.running.domain.Pacemaker.Status;
import soma.ghostrunner.domain.running.domain.PacemakerSet;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;
import soma.ghostrunner.domain.running.infra.persistence.PacemakerRepository;
import soma.ghostrunner.domain.running.infra.persistence.PacemakerSetRepository;
import soma.ghostrunner.domain.running.infra.redis.RedisRunningRepository;
import soma.ghostrunner.global.error.ErrorCode;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static soma.ghostrunner.domain.running.domain.Pacemaker.Status.*;

@Service
@RequiredArgsConstructor
public class PacemakerLlmCallbackService {

    private final PacemakerRepository pacemakerRepository;
    private final PacemakerSetRepository pacemakerSetRepository;
    private final RedisRunningRepository redisRunningRepository;

    @Transactional
    public void handleSuccess(Long pacemakerId, String workoutDtoStr) {
        WorkoutDto workoutDto = WorkoutDto.fromVoiceGuidanceGeneratedWorkoutDto(workoutDtoStr);

        Pacemaker pacemaker = findPacemaker(pacemakerId);
        updatePacemaker(pacemaker, workoutDto);
        pacemakerRepository.save(pacemaker);

        List<PacemakerSet> sets = PacemakerSet.createPacemakerSets(workoutDto.getSets(), pacemaker);
        pacemakerSetRepository.saveAll(sets);
    }

    private Pacemaker findPacemaker(Long pacemakerId) {
        return pacemakerRepository.findById(pacemakerId)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, pacemakerId));
    }

    private void updatePacemaker(Pacemaker pacemaker, WorkoutDto workoutDto) {
        pacemaker.updateSucceedPacemaker(workoutDto.getSummary(), workoutDto.getGoalKm(),
                workoutDto.getExpectedMinutes(), workoutDto.getInitialMessage());
    }

    @Transactional
    public void handleError(String rateLimitKey, Long pacemakerId) {
        compensateRateLimitCount(rateLimitKey);
        updatePacemakerStatus(pacemakerId, FAILED);
    }

    private void compensateRateLimitCount(String rateLimitKey) {
        redisRunningRepository.decrementRateLimitCounter(rateLimitKey);
    }

    private void updatePacemakerStatus(Long pacemakerId, Status status) {
        Pacemaker pacemaker = findPacemaker(pacemakerId);
        pacemaker.updateStatus(status);
    }

}
