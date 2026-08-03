package soma.ghostrunner.domain.pacemaker.application.dto;

import soma.ghostrunner.domain.member.domain.Member;

/**
 * Pacemaker 복구에 필요한 데이터를 담는 컨텍스트
 */
public record RecoveryContext(
        Member member,
        int vdot,
        WorkoutDto workoutDto,
        int condition,
        int temperature,
        Long pacemakerId
) {
}
