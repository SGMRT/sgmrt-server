package soma.ghostrunner.domain.running.application.dto;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.running.application.dto.request.RunRecordCommand;
import soma.ghostrunner.domain.running.application.support.RunningApplicationMapper;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.path.Coordinates;
import soma.ghostrunner.domain.running.domain.path.Telemetry;
import soma.ghostrunner.domain.running.domain.path.TelemetryStatistics;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RunningApplicationMapperTest {

    private final RunningApplicationMapper mapper = Mappers.getMapper(RunningApplicationMapper.class);

    @DisplayName("Runnging 엔티티로 변환한다.")
    @Test
    void toRunning() {
        // given
        Member member = createMember();
        Course course = createCourse(member);

        RunRecordCommand runRecordCommand = createRunRecordDto();
        CreateRunCommand createRunCommand = createRunCommand(runRecordCommand);

        List<Telemetry> relativeTelemetries = createTelemetryDtos();
        Coordinates startPointCoordinates = new Coordinates(37.2, 37.5);
        List<Coordinates> coordinates = createCoordinateDtos();
        TelemetryStatistics telemetryStatistics =
                createProcessedTelemetriesDto(relativeTelemetries, startPointCoordinates, coordinates);

        // when
        Running running = mapper.toRunning(
                createRunCommand, telemetryStatistics,
                new RunningDataUrlsDto(
                        "RAW URL", "INTERPOLATED URL",
                        "SIMPLIFIED URL", "CHECKPOINT URL", "SCREEN SHOT URL"), member, course);

        // then
        assertThat(running.getRunningName()).isEqualTo(createRunCommand.getRunningName());
        assertThat(running.getRunningMode()).isEqualTo(RunningMode.SOLO);
        assertThat(running.getGhostRunningId()).isNull();
        assertThat(running.getStartedAt()).isEqualTo(1000L);

        assertThat(running.getRunningRecord().getElevationAverage()).isEqualTo(120.2);
        assertThat(running.getRunningRecord().getDistance()).isEqualTo(5.5);
        assertThat(running.getRunningRecord().getCadence()).isEqualTo(100L);

        assertThat(running.getRunningDataUrls().getRawTelemetryUrl()).isEqualTo("RAW URL");
        assertThat(running.getRunningDataUrls().getInterpolatedTelemetryUrl()).isEqualTo("INTERPOLATED URL");
        assertThat(running.getRunningDataUrls().getScreenShotUrl()).isEqualTo("SCREEN SHOT URL");
    }

    private TelemetryStatistics createProcessedTelemetriesDto(
            List<Telemetry> relativeTelemetries, Coordinates startPointCoordinates, List<Coordinates> coordinates) {
        return new TelemetryStatistics(relativeTelemetries, startPointCoordinates, 6.5, 5.2, 120.2);
    }

    private List<Coordinates> createCoordinateDtos() {
        return List.of(
                new Coordinates(37.2, 37.5),
                new Coordinates(37.3, 37.6),
                new Coordinates(37.4, 37.7),
                new Coordinates(37.5, 37.8)
        );
    }

    private @NotNull List<Telemetry> createTelemetryDtos() {
        return List.of(
                new Telemetry(0L, 37.2, 37.5, 110.0, 6.0, 100.0, 120, 110, true),
                new Telemetry(1L, 37.3, 37.6, 110.1, 6.1, 101.0, 121, 111, true),
                new Telemetry(2L, 37.4, 37.7, 110.2, 6.2, 102.0, 122, 112, true),
                new Telemetry(3L, 37.5, 37.8, 110.3, 6.3, 103.0, 123, 113, false)
        );
    }

    private @NotNull CreateRunCommand createRunCommand(RunRecordCommand runRecordCommand) {
        return new CreateRunCommand(
                "테스트 러닝 이름", null, "SOLO", 1000L,
                runRecordCommand, true, true);
    }

    private RunRecordCommand createRunRecordDto() {
        return new RunRecordCommand(5.5, 100.0, 100.0, 23L,
                5.4, 100, 100, 100);
    }

    private Course createCourse(Member member) {
        return Course.of(member, 5.6,
                110.0, 110.0, 120.0,
                37.2, 37.3,
                "PATH_DATA_URL", "CHECKPOINT_URL", "PATH_DATA_URL");
    }

    private Member createMember() {
        return Member.of("이복둥", "Profile Picture URL");
    }

    @DisplayName("코스 엔티티로 변환한다.")
    @Test
    void toCourse() {
        // given
        Member member = createMember();
        RunRecordCommand runRecordCommand = createRunRecordDto();
        CreateRunCommand createRunCommand = createRunCommand(runRecordCommand);

        List<Telemetry> relativeTelemetries = createTelemetryDtos();
        Coordinates startPointCoordinates = new Coordinates(37.2, 37.5);
        List<Coordinates> coordinates = createCoordinateDtos();
        TelemetryStatistics telemetryStatistics =
                createProcessedTelemetriesDto(relativeTelemetries, startPointCoordinates, coordinates);

        RunningDataUrlsDto runningDataUrlsDto = new RunningDataUrlsDto(
                "RAW URL", "INTERPOLATED URL",
                "PATH_DATA_URL", "CHECKPOINT_URL", "SCREEN_SHOT_URL");

        // when
        Course course = mapper.toCourse(member, createRunCommand, telemetryStatistics, runningDataUrlsDto);

        // then
        assertThat(course.getName()).isNull();
        assertThat(course.getCourseProfile().getElevationAverage()).isEqualTo(telemetryStatistics.avgElevation());
        assertThat(course.getCourseProfile().getDistance()).isEqualTo(runRecordCommand.getDistance());
        assertThat(course.getCourseProfile().getElevationLoss()).isEqualTo(runRecordCommand.getElevationLoss());
        assertThat(course.getStartCoordinate().getLatitude()).isEqualTo(37.2);
        assertThat(course.getCourseDataUrls().getRouteUrl()).isEqualTo("PATH_DATA_URL");
        assertThat(course.getCourseDataUrls().getThumbnailUrl()).isEqualTo("SCREEN_SHOT_URL");
     }

}
