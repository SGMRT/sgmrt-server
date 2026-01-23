package soma.ghostrunner.domain.pacemaker.application.dto;

import lombok.Builder;
import lombok.Getter;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.pacemaker.domain.Pacemaker;

@Getter
@Builder
public class PacemakerCreationResult {

    private final Long pacemakerId;
    private final Member member;
    private final WorkoutDto workoutDto;
    private final int vdot;
    private final int condition;
    private final int temperature;

    public static PacemakerCreationResult of(Pacemaker pacemaker, Member member,
                                              WorkoutDto workoutDto, int vdot,
                                              int condition, int temperature) {
        return PacemakerCreationResult.builder()
                .pacemakerId(pacemaker.getId())
                .member(member)
                .workoutDto(workoutDto)
                .vdot(vdot)
                .condition(condition)
                .temperature(temperature)
                .build();
    }

}
