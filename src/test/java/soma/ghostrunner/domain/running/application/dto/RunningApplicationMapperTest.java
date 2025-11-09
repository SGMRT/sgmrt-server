package soma.ghostrunner.domain.running.application.dto;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.dto.CourseRunDto;
import soma.ghostrunner.domain.course.dto.response.CourseGhostResponse;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.running.application.dto.request.RunRecordCommand;
import soma.ghostrunner.domain.running.application.support.RunningApplicationMapper;
import soma.ghostrunner.domain.running.domain.*;
import soma.ghostrunner.domain.running.domain.events.PacemakerCreatedEvent;
import soma.ghostrunner.domain.running.domain.path.Coordinates;
import soma.ghostrunner.domain.running.domain.path.Telemetry;
import soma.ghostrunner.domain.running.domain.path.TelemetryStatistics;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
        TelemetryStatistics telemetryStatistics =
                createProcessedTelemetriesDto(relativeTelemetries, startPointCoordinates, 5.0);

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
            List<Telemetry> relativeTelemetries, Coordinates startPointCoordinates, Double distance) {
        return new TelemetryStatistics(relativeTelemetries, startPointCoordinates, 6.5, 5.2, 120.2, distance);
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
        TelemetryStatistics telemetryStatistics =
                createProcessedTelemetriesDto(relativeTelemetries, startPointCoordinates, 5.0);

        RunningDataUrlsDto runningDataUrlsDto = new RunningDataUrlsDto(
                "RAW URL", "INTERPOLATED URL",
                "PATH_DATA_URL", "CHECKPOINT_URL", "SCREEN_SHOT_URL");

        // when
        Course course = mapper.toCourse(member, createRunCommand, telemetryStatistics, runningDataUrlsDto);

        // then
        assertThat(course.getName()).isNull();
        assertThat(course.getCourseProfile().getElevationAverage()).isEqualTo(telemetryStatistics.avgElevation());
        assertThat(course.getCourseProfile().getDistance()).isEqualTo(5.0);
        assertThat(course.getCourseProfile().getElevationLoss()).isEqualTo(runRecordCommand.getElevationLoss());
        assertThat(course.getStartCoordinate().getLatitude()).isEqualTo(37.2);
        assertThat(course.getCourseDataUrls().getRouteUrl()).isEqualTo("PATH_DATA_URL");
        assertThat(course.getCourseDataUrls().getThumbnailUrl()).isEqualTo("SCREEN_SHOT_URL");
     }

    @DisplayName("CourseRunDto에서 CourseGhostResponse로 변환한다.")
    @Test
    void toCourseGhostResponse() {
        // given
        LocalDateTime now = LocalDateTime.of(2025, 6, 1, 12, 0, 0);
        CourseRunDto courseRunDto = new CourseRunDto(
            "member-uuid", "profile-url",  "호나우지뉴",
                1L, 2L, "러닝 이름", 180.5, 180, 180,
                3600L, true, now.toEpochSecond(ZoneOffset.UTC)
        );

        // when
        CourseGhostResponse response = mapper.toGhostResponse(courseRunDto);

        // then
        assertThat(response.runnerUuid()).isEqualTo(courseRunDto.runnerUuid());
        assertThat(response.runnerProfileUrl()).isEqualTo(courseRunDto.runnerProfileUrl());
        assertThat(response.runnerNickname()).isEqualTo(courseRunDto.runnerNickname());
        assertThat(response.runningId()).isEqualTo(courseRunDto.runningId());
        assertThat(response.runningName()).isEqualTo(courseRunDto.runningName());
        assertThat(response.averagePace()).isEqualTo(courseRunDto.averagePace());
        assertThat(response.cadence()).isEqualTo(courseRunDto.cadence());
        assertThat(response.bpm()).isEqualTo(courseRunDto.bpm());
        assertThat(response.duration()).isEqualTo(courseRunDto.duration());
        assertThat(response.isPublic()).isEqualTo(courseRunDto.isPublic());
        assertThat(response.startedAt()).isEqualTo(now);
     }

    @DisplayName("러닝, 코스, 회원 엔티티를 CourseRunEvent로 변환한다.")
    @Test
    void toCourseRunEvent() {
        // given
        Member runner = Member.of("손흥민", "profile-url-1");
        Member courseOwner = Member.of("코스 주인", "profile-url-2");
        Course course = createCourse(courseOwner);
        Running running = createPublicSoloRunning(runner, course);

        // when
        var event = mapper.toCourseRunEvent(running, course, runner);

        // then
        assertThat(event.courseId()).isEqualTo(course.getId());
        assertThat(event.courseName()).isEqualTo(course.getName());
        assertThat(event.courseOwnerId()).isEqualTo(courseOwner.getId());
        assertThat(event.runningId()).isEqualTo(running.getId());
        assertThat(event.runStartedAt()).isEqualTo(running.getStartedAt());
        assertThat(event.runDuration()).isEqualTo(running.getRunningRecord().getDuration());
        assertThat(event.runnerId()).isEqualTo(runner.getId());
        assertThat(event.runnerNickname()).isEqualTo(runner.getNickname());
    }

    @DisplayName("페이스메이커 엔티티를 PacemakerCreatedEvent로 변환한다.")
    @Test
    void toPacemakerCreatedEvent() {
        // given
        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 500d, 1L, RunningType.E, "uuid");
        setPacemakerId(pacemaker, 100L);

        // when
        PacemakerCreatedEvent event = mapper.toPacemakerCreatedEvent(pacemaker);
        // then
        assertThat(event.pacemakerId()).isEqualTo(pacemaker.getId());
        assertThat(event.courseId()).isEqualTo(pacemaker.getCourseId());
        assertThat(event.memberUuid()).isEqualTo(pacemaker.getMemberUuid());
    }

    private void setPacemakerId(Pacemaker pacemaker, long id) {
        try {
            java.lang.reflect.Field idField = Pacemaker.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(pacemaker, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Running createPublicSoloRunning(Member member, Course course) {
        return Running.of(
                "테스트 러닝",
                RunningMode.SOLO,
                null,
                RunningRecord.of(5.5, 100.0, 100.0, 23D,
                        5.4, 100D, 100D, 100L, 100, 180, 180),
                1234567L,
                true, false,
                "RAW_URL", "INTERPOLATED_URL", "SCREENSHOT_URL",
                member,
                course
        );
    }

}
