package soma.ghostrunner.domain.pacemaker.application.support;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.pacemaker.api.dto.response.*;
import soma.ghostrunner.domain.pacemaker.application.dto.request.PacemakerCreateCommand;
import soma.ghostrunner.domain.pacemaker.domain.Pacemaker;
import soma.ghostrunner.domain.pacemaker.domain.PacemakerSet;
import soma.ghostrunner.domain.pacemaker.domain.RunningType;
import soma.ghostrunner.domain.pacemaker.domain.events.PacemakerCreatedEvent;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PacemakerApplicationMapper {

    PacemakerApplicationMapper INSTANCE = Mappers.getMapper(PacemakerApplicationMapper.class);

    default PacemakerPollingResponse toPacemakerPollingResponse(Pacemaker p) {
        return PacemakerPollingResponse.builder()
                .processingStatus(p.getStatus().name())
                .build();
    }

    default PacemakerPollingResponse toPacemakerPollingResponse(Pacemaker p, List<PacemakerSet> sets, String runningTip) {

        List<PacemakerSetResponse> setResponses = sets.stream()
                .map(s -> PacemakerSetResponse.builder()
                        .setNum(s.getSetNum())
                        .message(s.getMessage())
                        .startPoint(s.getStartPoint())
                        .endPoint(s.getEndPoint())
                        .pace(s.getPace())
                        .build())
                .toList();

        PacemakerTimeTableResponse timeTable = new PacemakerTimeTableResponse(setResponses, p.getExpectedTime());

        PacemakerResponse pacemakerResponse = PacemakerResponse.builder()
                .id(p.getId())
                .runningType(p.getRunningType().toWorkoutWord())
                .norm(p.getNorm().name())
                .summary(p.getSummary())
                .goalKm(p.getGoalDistance())
                .expectedMinutes(p.getExpectedTime())
                .initialMessage(p.getInitialMessage())
                .sets(setResponses)
                .timeTable(timeTable)
                .runningTip(runningTip)
                .build();

        return PacemakerPollingResponse.builder()
                .processingStatus(p.getStatus().name())
                .pacemakerResponse(pacemakerResponse)
                .build();
    }

    default PacemakerInCourseViewPollingResponse toPacemakerInCourseViewPollingResponse(Pacemaker p) {
        return PacemakerInCourseViewPollingResponse.builder()
                .processingStatus(p.getStatus().name())
                .build();
    }

    default PacemakerInCourseViewPollingResponse toPacemakerInCourseViewPollingResponse(Pacemaker p, List<PacemakerSet> sets) {

        List<PacemakerSetResponse> setResponses = sets.stream()
                .map(s -> PacemakerSetResponse.builder()
                        .setNum(s.getSetNum())
                        .message(s.getMessage())
                        .startPoint(s.getStartPoint())
                        .endPoint(s.getEndPoint())
                        .pace(s.getPace())
                        .build())
                .toList();

        PacemakerSummaryResponse pacemakerSummaryResponse = PacemakerSummaryResponse.builder()
                .id(p.getId())
                .pacemaker(p)
                .sets(setResponses)
                .build();

        return PacemakerInCourseViewPollingResponse.builder()
                .processingStatus(p.getStatus().name())
                .pacemakerSummaryResponse(pacemakerSummaryResponse)
                .build();
    }

    @Mapping(source = "id", target = "pacemakerId")
    @Mapping(source = "courseId", target = "courseId")
    @Mapping(source = "memberUuid", target = "memberUuid")
    PacemakerCreatedEvent toPacemakerCreatedEvent(Pacemaker pacemaker);

}
