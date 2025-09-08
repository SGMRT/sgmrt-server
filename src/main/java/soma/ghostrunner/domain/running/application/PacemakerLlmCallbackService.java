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

    private final String PACEMAKER_PROCESSING_STATE_KEY_PREFIX = "pacemaker_processing_state:";
    private final String PACEMAKER_SUCCEED_STATE = "SUCCEED";
    private final String PACEMAKER_FAILED_STATE = "FAILED";

    @Transactional
    public void handleSuccess(Long pacemakerId, String workoutDtoStr, Member member) {
        WorkoutDto workoutDto = WorkoutDto.fromVoiceGuidanceGeneratedWorkoutDto(workoutDtoStr);

        Pacemaker pacemaker = findPacemaker(pacemakerId);
        pacemaker.updateSucceedPacemaker(workoutDto);
        pacemakerRepository.save(pacemaker);

        List<PacemakerSet> sets = PacemakerSet.createPacemakerSets(workoutDto.getSets(), pacemaker);
        pacemakerSetRepository.saveAll(sets);

        updatePacemakerStatusInRedis(pacemakerId, COMPLETED, member);
    }

    @Transactional
    public void handleError(String rateLimitKey, Long pacemakerId, Member member) {
        compensateRateLimitCount(rateLimitKey);
        updatePacemakerStatus(pacemakerId, FAILED, member);
    }

    private void compensateRateLimitCount(String rateLimitKey) {
        redisRunningRepository.decrementRateLimitCounter(rateLimitKey);
    }

    private void updatePacemakerStatus(Long pacemakerId, Status status, Member member) {
        Pacemaker pacemaker = findPacemaker(pacemakerId);
        pacemaker.updateStatus(status);
        updatePacemakerStatusInRedis(pacemakerId, status, member);
    }

    private Pacemaker findPacemaker(Long pacemakerId) {
        return pacemakerRepository.findById(pacemakerId)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, pacemakerId));
    }

    private void updatePacemakerStatusInRedis(Long pacemakerId, Status status, Member member) {
        String key = PACEMAKER_PROCESSING_STATE_KEY_PREFIX + pacemakerId;
        if (status == COMPLETED) {
            String value = member.getUuid() + ":" + PACEMAKER_SUCCEED_STATE;
            redisRunningRepository.save(key, value, TimeUnit.DAYS, 1);
        } else {
            String value = member.getUuid() + ":" + PACEMAKER_FAILED_STATE;
            redisRunningRepository.save(key, value, TimeUnit.DAYS, 1);
        }
    }

}
