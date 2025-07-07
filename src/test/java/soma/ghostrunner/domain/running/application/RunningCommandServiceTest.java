package soma.ghostrunner.domain.running.application;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.clients.aws.TelemetryClient;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.course.domain.StartPoint;
import soma.ghostrunner.domain.member.Member;
import soma.ghostrunner.domain.member.MemberRepository;
import soma.ghostrunner.domain.running.api.dto.response.CreateCourseAndRunResponse;
import soma.ghostrunner.domain.running.application.dto.ProcessedTelemetriesDto;
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.running.application.dto.request.RunRecordDto;
import soma.ghostrunner.domain.running.dao.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.BDDAssertions.tuple;
import static org.mockito.BDDMockito.*;

class RunningCommandServiceTest extends IntegrationTestSupport {

    @Autowired
    private RunningCommandService runningCommandService;

    @Autowired
    private RunningRepository runningRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CourseRepository courseRepository;

    @MockitoBean
    private TelemetryClient telemetryClient;

    @DisplayName("새로운 코스에 대한 러닝 기록을 생성한다.")
    @Test
    void createCourseAndRun() {
        // given
        Member member = createMember("테스트 유저");
        memberRepository.save(member);

        RunRecordDto runRecordDto = createRunRecordDto(5.1, 130, -120, 3600L);
        List<TelemetryDto> telemetryDtos = createTelemetryDtos();
        CreateRunCommand request = createRunCommandRequest("러닝 이름", "SOLO", 100L,
                runRecordDto, telemetryDtos);

        given(telemetryClient.uploadTelemetries(anyString(), anyLong()))
                .willReturn("Mock Telemetries Url");

        // when
        CreateCourseAndRunResponse response = runningCommandService.createCourseAndRun(request, member.getId());

        // then
        Running savedRunning = runningRepository.findById(response.getRunningId()).get();
        Assertions.assertThat(savedRunning)
                .isNotNull()
                .extracting(Running::getRunningName, Running::getRunningMode,
                        Running::getStartedAt, Running::getTelemetryUrl)
                .containsExactly("러닝 이름", RunningMode.SOLO, 100L, "Mock Telemetries Url");

        RunningRecord savedRunningRecord = savedRunning.getRunningRecord();
        Assertions.assertThat(savedRunningRecord)
                .isNotNull()
                .extracting(RunningRecord::getDistance, RunningRecord::getElevationGain,
                        RunningRecord::getElevationLoss, RunningRecord::getDuration)
                .containsExactly(5.1, 130, -120, 3600L);

        Course savedCourse = courseRepository.findById(response.getCourseId()).get();
        Assertions.assertThat(savedCourse)
                .isNotNull()
                .extracting(Course::getName, Course::getIsPublic)
                .containsExactly(null, false);

        StartPoint savedStartPoint = savedCourse.getStartPoint();
        Assertions.assertThat(savedStartPoint)
                .isNotNull()
                .extracting(StartPoint::getLatitude, StartPoint::getLongitude)
                .containsExactly(36.2, 37.3);
     }

    private Member createMember(String name) {
        return Member.of(name, "프로필 URL");
    }

    private CreateRunCommand createRunCommandRequest(String runningName, String runningMode, Long startedAt,
                                                     RunRecordDto runRecordDto, List<TelemetryDto> telemetryDtos) {
        return new CreateRunCommand(runningName, null, runningMode,
                startedAt, runRecordDto, false, true, telemetryDtos);
    }

    private List<TelemetryDto> createTelemetryDtos() {
        List<TelemetryDto> telemetryDtos = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            telemetryDtos.add(new TelemetryDto(100L + i, 36.2 + i, 37.3 + i, 10.1 + i,
                    6.4 + i, 110 + i, 120 + i, 110 + i, true));
        }
        return telemetryDtos;
     }

    private RunRecordDto createRunRecordDto(double distance, int elevationGain, int elevationLoss, long duration) {
        return new RunRecordDto(distance, elevationGain, elevationLoss, duration,
                6.4, 123, 110, 130);
    }

    @DisplayName("기존 코스를 혼자 러닝하여 새로운 러닝 기록을 생성한다.")
    @Test
    void createSoloRun() {
        // given
        Member member = createMember("테스트 유저");
        memberRepository.save(member);

        Course course = createCourse();
        Long savedCourseId = courseRepository.save(course).getId();

        RunRecordDto runRecordDto = createRunRecordDto(5.1, 130, -120, 3600L);
        List<TelemetryDto> telemetryDtos = createTelemetryDtos();
        CreateRunCommand request = createGhostRunCommandRequest("러닝 이름", "SOLO", null,
                100L, runRecordDto, telemetryDtos);

        given(telemetryClient.uploadTelemetries(anyString(), anyLong()))
                .willReturn("Mock Telemetries Url");

        // when
        Long savedRunningId = runningCommandService.createRun(request, savedCourseId, member.getId());

        // then
        Running savedRunning = runningRepository.findById(savedRunningId).get();
        Assertions.assertThat(savedRunning)
                .isNotNull()
                .extracting(Running::getRunningName, Running::getRunningMode, Running::getGhostRunningId,
                        Running::getStartedAt, Running::getTelemetryUrl)
                .containsExactly("러닝 이름", RunningMode.SOLO, null, 100L, "Mock Telemetries Url");

        Course savedCourse = savedRunning.getCourse();
        Assertions.assertThat(savedCourse.getId()).isEqualTo(savedCourseId);
    }

    @DisplayName("기존 코스를 고스트와 러닝하여 새로운 러닝 기록을 생성한다.")
    @Test
    void createGhostRun() {
        // given
        Member member = createMember("테스트 유저");
        memberRepository.save(member);

        Course course = createCourse();
        Long savedCourseId = courseRepository.save(course).getId();

        Running ghostRunning = createRunning(member, course);
        Long ghostRunningId = runningRepository.save(ghostRunning).getId();

        RunRecordDto runRecordDto = createRunRecordDto(5.1, 130, -120, 3600L);
        List<TelemetryDto> telemetryDtos = createTelemetryDtos();
        CreateRunCommand request = createGhostRunCommandRequest("러닝 이름", "GHOST", ghostRunningId,
                100L, runRecordDto, telemetryDtos);

        given(telemetryClient.uploadTelemetries(anyString(), anyLong()))
                .willReturn("Mock Telemetries Url");

        // when
        Long savedRunningId = runningCommandService.createRun(request, savedCourseId, member.getId());

        // then
        Running savedRunning = runningRepository.findById(savedRunningId).get();
        Assertions.assertThat(savedRunning)
                .isNotNull()
                .extracting(Running::getRunningName, Running::getRunningMode, Running::getGhostRunningId,
                        Running::getStartedAt, Running::getTelemetryUrl)
                .containsExactly("러닝 이름", RunningMode.GHOST, ghostRunningId, 100L, "Mock Telemetries Url");

        Course savedCourse = savedRunning.getCourse();
        Assertions.assertThat(savedCourse.getId()).isEqualTo(savedCourseId);
    }

    @DisplayName("요청한 코스가 고스트가 실제로 뛴 코스가 아닌 경우 예외가 발생한다.")
    @Test
    void throwExceptionIfCourseNotRunByGhost() {
        // given
        Member member = createMember("테스트 유저");
        Long savedMemberId = memberRepository.save(member).getId();

        Course ghostCourse = createCourse();
        Long savedCourseId = courseRepository.save(ghostCourse).getId();
        Course fakeCourse = createCourse();
        Long savedFakeCourseId = courseRepository.save(fakeCourse).getId();

        Running ghostRunning = createRunning(member, ghostCourse);
        Long ghostRunningId = runningRepository.save(ghostRunning).getId();

        RunRecordDto runRecordDto = createRunRecordDto(5.1, 130, -120, 3600L);
        List<TelemetryDto> telemetryDtos = createTelemetryDtos();
        CreateRunCommand request = createGhostRunCommandRequest("러닝 이름", "GHOST", ghostRunningId,
                100L, runRecordDto, telemetryDtos);

        given(telemetryClient.uploadTelemetries(anyString(), anyLong()))
                .willReturn("Mock Telemetries Url");

        // when // then
        Assertions.assertThatThrownBy(() -> runningCommandService.createRun(request, savedFakeCourseId, savedMemberId))
                .isInstanceOf(InvalidRunningException.class)
                .hasMessage("고스트가 뛴 코스가 아닙니다.");
    }

    private Course createCourse() {
        return Course.of(createCourseProfile(), createStartPoint(),
                "[{'lat':37.123, 'lng':32.123}, {'lat':37.123, 'lng':32.123}, {'lat':37.123, 'lng':32.123}]");
    }

    private StartPoint createStartPoint() {
        return StartPoint.fromCoordinates(37.545354, 34.7878);
    }

    private CourseProfile createCourseProfile() {
        return CourseProfile.of(5.2, 40, -10);
    }

    private CreateRunCommand createGhostRunCommandRequest(String runningName, String runningMode, Long ghostRunningId,
                                                          Long startedAt, RunRecordDto runRecordDto, List<TelemetryDto> telemetryDtos) {
        return new CreateRunCommand(runningName, ghostRunningId, runningMode,
                startedAt, runRecordDto, false, true, telemetryDtos);
    }

    private Running createRunning(Member member, Course course) {
        RunningRecord testRunningRecord = createRunningRecord();
        return Running.of("테스트 러닝 제목", RunningMode.SOLO, 2L, testRunningRecord,
                1750729987181L, true, false, "URL", member, course);
    }

    @DisplayName("러닝 기록을 공개/비공개 상태로 변경한다.")
    @Test
    void setRunningPublic() {
        // given
        Member member = createMember("테스트 유저");
        memberRepository.save(member);

        Course course = createCourse();
        courseRepository.save(course);

        Running publicRunning = runningRepository.save(createRunning(member, course, true));
        Running privateRunning = runningRepository.save(createRunning(member, course, false));

        // when
        runningCommandService.updateRunningPublicStatus(publicRunning.getId());
        runningCommandService.updateRunningPublicStatus(privateRunning.getId());

        // then
        Running updatedToPublicRunning = runningRepository.findById(privateRunning.getId()).get();
        Assertions.assertThat(updatedToPublicRunning.isPublic()).isTrue();

        Running updatedToPrivateRunning = runningRepository.findById(publicRunning.getId()).get();
        Assertions.assertThat(updatedToPrivateRunning.isPublic()).isFalse();
    }

    @DisplayName("러닝을 중지한 기록이 있다면 공개 설정이 불가능하다.")
    @Test
    void cannotEnablePublicWhenStoppedRunExists() {
        // given
        Member member = createMember("테스트 유저");
        memberRepository.save(member);

        Course course = createCourse();
        courseRepository.save(course);

        Running hasPausedRunning = runningRepository.save(createHasPausedRunning(member, course));
        runningRepository.save(hasPausedRunning);

        // when // then
        Assertions.assertThatThrownBy(() -> runningCommandService.updateRunningPublicStatus(hasPausedRunning.getId()))
                .isInstanceOf(InvalidRunningException.class)
                .hasMessage("정지한 기록이 있다면 공개할 수 없습니다.");
     }

    private Running createRunning(Member member, Course course, Boolean isPublic) {
        RunningRecord testRunningRecord = createRunningRecord();
        return Running.of("테스트 러닝 제목", RunningMode.SOLO, 2L, testRunningRecord, 1750729987181L,
                isPublic, false, "URL", member, course);
    }

    private Running createHasPausedRunning(Member member, Course course) {
        return Running.of("테스트 러닝 제목", RunningMode.SOLO, 2L, createRunningRecord(), 1750729987181L,
                false, true, "URL", member, course);
    }

    private RunningRecord createRunningRecord() {
        return RunningRecord.of(5.2, 40, -20, 6.1, 4.9, 6.9, 3423L, 302, 120, 56);
    }

}
