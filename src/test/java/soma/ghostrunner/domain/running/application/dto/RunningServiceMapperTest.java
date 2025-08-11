package soma.ghostrunner.domain.running.application.dto;

import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.running.application.dto.request.RunRecordDto;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RunningServiceMapperTest {

    private final RunningServiceMapper mapper = Mappers.getMapper(RunningServiceMapper.class);

    @DisplayName("Runnging 엔티티로 변환한다.")
    @Test
    void toRunning() {
        // given
        Member member = createMember();
        Course course = createCourse(member);

        RunRecordDto runRecordDto = createRunRecordDto();
        CreateRunCommand createRunCommand = createRunCommand(runRecordDto);

        List<TelemetryDto> relativeTelemetries = createTelemetryDtos();
        CoordinateDto startPointCoordinateDto = new CoordinateDto(37.2, 37.5);
        List<CoordinateDto> coordinateDtos = createCoordinateDtos();
        ProcessedTelemetriesDto processedTelemetriesDto =
                createProcessedTelemetriesDto(relativeTelemetries, startPointCoordinateDto, coordinateDtos);

        // when
        Running running = mapper.toRunning(
                createRunCommand, processedTelemetriesDto,
                "RAW URL", "SIMPLIFIED URL", "SCREEN SHOT URL",
                member, course
        );

        // then
        assertThat(running.getRunningName()).isEqualTo(createRunCommand.runningName());
        assertThat(running.getRunningMode()).isEqualTo(RunningMode.SOLO);
        assertThat(running.getGhostRunningId()).isNull();
        assertThat(running.getStartedAt()).isEqualTo(1000L);

        assertThat(running.getRunningRecord().getDistance()).isEqualTo(5.5);
        assertThat(running.getRunningRecord().getCadence()).isEqualTo(100L);

        assertThat(running.getRunningDataUrls().getRawTelemetrySavedUrl()).isEqualTo("RAW URL");
        assertThat(running.getRunningDataUrls().getSimplifiedTelemetrySavedUrl()).isEqualTo("SIMPLIFIED URL");
        assertThat(running.getRunningDataUrls().getScreenShotSavedUrl()).isEqualTo("SCREEN SHOT URL");
    }

    private @NotNull ProcessedTelemetriesDto createProcessedTelemetriesDto(List<TelemetryDto> relativeTelemetries, CoordinateDto startPointCoordinateDto, List<CoordinateDto> coordinateDtos) {
        return new ProcessedTelemetriesDto(relativeTelemetries, startPointCoordinateDto, coordinateDtos, 6.5, 5.2);
    }

    private @NotNull List<CoordinateDto> createCoordinateDtos() {
        return List.of(
                new CoordinateDto(37.2, 37.5),
                new CoordinateDto(37.3, 37.6),
                new CoordinateDto(37.4, 37.7),
                new CoordinateDto(37.5, 37.8)
        );
    }

    private @NotNull List<TelemetryDto> createTelemetryDtos() {
        return List.of(
                new TelemetryDto(0L, 37.2, 37.5, 110.0, 6.0, 100, 120, 110, true),
                new TelemetryDto(1L, 37.3, 37.6, 110.1, 6.1, 101, 121, 111, true),
                new TelemetryDto(2L, 37.4, 37.7, 110.2, 6.2, 102, 122, 112, true),
                new TelemetryDto(3L, 37.5, 37.8, 110.3, 6.3, 103, 123, 113, false)
        );
    }

    private @NotNull CreateRunCommand createRunCommand(RunRecordDto runRecordDto) {
        return new CreateRunCommand(
                "테스트 러닝 이름", null, "SOLO", 1000L,
                runRecordDto, true, true);
    }

    private @NotNull RunRecordDto createRunRecordDto() {
        return new RunRecordDto(5.5, 100, 100, 23L,
                5.4, 100, 100, 100);
    }

    private Course createCourse(Member member) {
        return Course.of(member, 5.6, 110, 120, 37.2, 37.3, "PATH_DATA_URL");
    }

    private Member createMember() {
        return Member.of("이복둥", "Profile Picture URL");
    }

    @DisplayName("코스 엔티티로 변환한다.")
    @Test
    void toCourse() {
        // given
        Member member = createMember();
        RunRecordDto runRecordDto = createRunRecordDto();
        CreateRunCommand createRunCommand = createRunCommand(runRecordDto);
        CoordinateDto startPointCoordinateDto = new CoordinateDto(37.2, 37.5);

        // when
        Course course = mapper.toCourse(member, createRunCommand, startPointCoordinateDto, "PATH_DATA_URL");

        // then
        assertThat(course.getName()).isNull();
        assertThat(course.getCourseProfile().getDistance()).isEqualTo(runRecordDto.distance());
        assertThat(course.getCourseProfile().getElevationLoss()).isEqualTo(runRecordDto.elevationLoss());
        assertThat(course.getStartCoordinate().getLatitude()).isEqualTo(37.2);
        assertThat(course.getPathDataSavedUrl()).isEqualTo("PATH_DATA_URL");
     }

}
