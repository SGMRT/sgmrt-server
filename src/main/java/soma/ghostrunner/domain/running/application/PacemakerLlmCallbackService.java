package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    private final String PACEMAKER_PROCESSING_STATE_KEY_PREFIX = "pacemaker_processing_state:";
    private final String PACEMAKER_SUCCEED_STATE = "SUCCEED";
    private final String PACEMAKER_FAILED_STATE = "FAILED";

    @Transactional
    public void handleSuccess(Long pacemakerId, String workoutDtoStr) {
        WorkoutDto workoutDto = WorkoutDto.fromVoiceGuidanceGeneratedWorkoutDto(workoutDtoStr);

        Pacemaker pacemaker = findPacemaker(pacemakerId);
        pacemaker.updateSucceedPacemaker(workoutDto);
        pacemakerRepository.save(pacemaker);

        List<PacemakerSet> sets = PacemakerSet.createPacemakerSets(workoutDto.getSets(), pacemaker);
        pacemakerSetRepository.saveAll(sets);

        updatePacemakerStatusInRedis(pacemakerId, COMPLETED);
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
        updatePacemakerStatusInRedis(pacemakerId, status);
    }

    private Pacemaker findPacemaker(Long pacemakerId) {
        return pacemakerRepository.findById(pacemakerId)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, pacemakerId));
    }

    private void updatePacemakerStatusInRedis(Long pacemakerId, Status status) {
        String key = PACEMAKER_PROCESSING_STATE_KEY_PREFIX + pacemakerId;
        if (status == COMPLETED) {
            redisRunningRepository.save(key, PACEMAKER_SUCCEED_STATE, TimeUnit.DAYS, 1);
        } else {
            redisRunningRepository.save(key, PACEMAKER_FAILED_STATE, TimeUnit.DAYS, 1);
        }
    }

}
