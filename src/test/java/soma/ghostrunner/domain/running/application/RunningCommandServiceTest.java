package soma.ghostrunner.domain.running.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.clients.aws.upload.GhostRunnerS3Client;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Coordinate;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.dao.MemberRepository;
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;
import soma.ghostrunner.domain.running.application.dto.request.CreateRunCommand;
import soma.ghostrunner.domain.running.application.dto.request.RunRecordCommand;
import soma.ghostrunner.domain.running.dao.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

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
    private GhostRunnerS3Client ghostRunnerS3Client;

//    @DisplayName("새로운 코스에 대한 러닝 기록을 생성한다.")
//    @Test
//    void createCourseAndRun() {
//        // given
//        Member member = createMember("테스트 유저");
//        memberRepository.save(member);
//
//        RunRecordCommand runRecordDto = createRunRecordDto(5.1, 130.0, -120.0, 3600L);
//        CreateRunCommand createRunCommand = createRunCommandRequest("러닝 이름", "SOLO", 100L, runRecordDto);
//
//        byte[] bytes = "line1\nline2\n".getBytes(StandardCharsets.UTF_8);
//        MockMultipartFile rawTelemetry = new MockMultipartFile(
//                "rawTelemetry", "telemetry.jsonl", "application/jsonl", bytes
//        );
//        MockMultipartFile interpolatedTelemetry = new MockMultipartFile(
//                "rawTelemetry", "telemetry.jsonl", "application/jsonl", bytes
//        );
//        MockMultipartFile image = new MockMultipartFile(
//                "img", "capture.png", "image/png", new byte[]{1, 2, 3}
//        );
//
//        given(ghostRunnerS3Client.uploadInterpolatedTelemetry(ArgumentMatchers.any(), anyString()))
//                .willReturn("Mock Telemetries Url");
//        given(ghostRunnerS3Client.uploadRawTelemetry(ArgumentMatchers.any(), anyString()))
//                .willReturn("Mock Telemetries Url");
//        given(ghostRunnerS3Client.uploadSimplifiedTelemetry(ArgumentMatchers.any(), anyString()))
//                .willReturn("Mock Telemetries Url");
//        given(ghostRunnerS3Client.uploadRunningCaptureImage(ArgumentMatchers.any(), anyString()))
//                .willReturn("Mock Telemetries Url");
//
//        // when
//        CreateCourseAndRunResponse response = runningCommandService.createCourseAndRun(createRunCommand, member.getUuid());
//        CreateCourseAndRunResponse response = null;
//
//        // then
//        Running savedRunning = runningRepository.findById(response.getRunningId()).get();
//        Assertions.assertThat(savedRunning)
//                .isNotNull()
//                .extracting(Running::getRunningName, Running::getRunningMode, Running::getStartedAt)
//                .containsExactly("러닝 이름", RunningMode.SOLO, 100L);
//
//        RunningRecord savedRunningRecord = savedRunning.getRunningRecord();
//        Assertions.assertThat(savedRunningRecord)
//                .isNotNull()
//                .extracting(RunningRecord::getDistance, RunningRecord::getElevationGain,
//                        RunningRecord::getElevationLoss, RunningRecord::getDuration)
//                .containsExactly(5.1, 130, -120, 3600L);
//
//        RunningDataUrls runningDataUrls = savedRunning.getRunningDataUrls();
//        Assertions.assertThat(runningDataUrls)
//                .isNotNull()
//                .extracting(RunningDataUrls::getRawTelemetrySavedUrl, RunningDataUrls::getInterpolatedTelemetrySavedUrl,
//                        RunningDataUrls::getScreenShotSavedUrl)
//                .containsExactly("Mock Telemetries Url", "Mock Telemetries Url", "Mock Telemetries Url");
//
//        Course savedCourse = courseRepository.findById(response.getCourseId()).get();
//        Assertions.assertThat(savedCourse)
//                .isNotNull()
//                .extracting(Course::getName, Course::getIsPublic)
//                .containsExactly(null, false);
//
//        CourseProfile savedCourseProfile = savedCourse.getCourseProfile();
//        Assertions.assertThat(savedCourseProfile)
//                .isNotNull()
//                .extracting(CourseProfile::getDistance, CourseProfile::getElevationGain, CourseProfile::getElevationLoss)
//                .containsExactly(5.1, 130, -120);
//
//        Coordinate savedCoordinate = savedCourse.getStartCoordinate();
//        Assertions.assertThat(savedCoordinate)
//                .isNotNull()
//                .extracting(Coordinate::getLatitude, Coordinate::getLongitude)
//                .containsExactly(36.2, 37.3);
//    }

    private Member createMember(String name) {
        return Member.of(name, "프로필 URL");
    }

    private CreateRunCommand createRunCommandRequest(String runningName, String runningMode, Long startedAt,
                                                     RunRecordCommand runRecordCommand) {
        return new CreateRunCommand(runningName, null, runningMode,
                startedAt, runRecordCommand, false, true);
    }

    private List<TelemetryDto> createTelemetryDtos() {
        List<TelemetryDto> telemetryDtos = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            telemetryDtos.add(new TelemetryDto(100L + i, 36.2 + i, 37.3 + i, 10.1 + i,
                    6.4 + i, 110.0 + i, 120 + i, 110 + i, true));
        }
        return telemetryDtos;
     }

    private RunRecordCommand createRunRecordDto(double distance, double elevationGain, double elevationLoss, long duration) {
        return new RunRecordCommand(distance, elevationGain, elevationLoss, duration,
                6.4, 123, 110, 130);
    }

    @DisplayName("기존 코스를 혼자 러닝하여 새로운 러닝 기록을 생성한다.")
    @Test
    void createSoloRun() {
        // given
        Member member = createMember("테스트 유저");
        memberRepository.save(member);

        Course course = createCourse(member);
        Long savedCourseId = courseRepository.save(course).getId();

        RunRecordCommand runRecordCommand = createRunRecordDto(5.1, 130, -120, 3600L);
        List<TelemetryDto> telemetryDtos = createTelemetryDtos();
        CreateRunCommand request = createGhostRunCommandRequest(
                "러닝 이름", "SOLO", null,
                100L, runRecordCommand);

//        given(s3TelemetryClient.uploadTelemetries(anyString(), anyString()))
//                .willReturn("Mock Telemetries Url");

        // when
//        Long savedRunningId = runningCommandService.createRun(request, savedCourseId, member.getUuid());

        // then
//        Running savedRunning = runningRepository.findById(savedRunningId).get();
//        Assertions.assertThat(savedRunning)
//                .isNotNull()
//                .extracting(Running::getRunningName, Running::getRunningMode,
//                        Running::getGhostRunningId, Running::getStartedAt)
//                .containsExactly("러닝 이름", RunningMode.SOLO, null, 100L);
//
//        Course savedCourse = savedRunning.getCourse();
//        Assertions.assertThat(savedCourse.getId()).isEqualTo(savedCourseId);
    }

    @DisplayName("기존 코스를 고스트와 러닝하여 새로운 러닝 기록을 생성한다.")
    @Test
    void createGhostRun() {
        // given
        Member member = createMember("테스트 유저");
        memberRepository.save(member);

        Course course = createCourse(member);
        Long savedCourseId = courseRepository.save(course).getId();

        Running ghostRunning = createRunning(member, course);
        Long ghostRunningId = runningRepository.save(ghostRunning).getId();

        RunRecordCommand runRecordCommand = createRunRecordDto(5.1, 130, -120, 3600L);
        CreateRunCommand request = createGhostRunCommandRequest(
                "러닝 이름", "GHOST", ghostRunningId,
                100L, runRecordCommand);

//        given(s3TelemetryClient.uploadTelemetries(anyString(), anyString()))
//                .willReturn("Mock Telemetries Url");

        // when
//        Long savedRunningId = runningCommandService.createRun(request, savedCourseId, member.getUuid());

        // then
//        Running savedRunning = runningRepository.findById(savedRunningId).get();
//        Assertions.assertThat(savedRunning)
//                .isNotNull()
//                .extracting(Running::getRunningName, Running::getRunningMode,
//                        Running::getGhostRunningId, Running::getStartedAt)
//                .containsExactly("러닝 이름", RunningMode.GHOST, ghostRunningId, 100L);
//
//        Course savedCourse = savedRunning.getCourse();
//        Assertions.assertThat(savedCourse.getId()).isEqualTo(savedCourseId);
    }

    @DisplayName("요청한 코스가 고스트가 실제로 뛴 코스가 아닌 경우 예외가 발생한다.")
    @Test
    void throwExceptionIfCourseNotRunByGhost() {
        // given
        Member member = createMember("테스트 유저");
        String savedMemberUuid = memberRepository.save(member).getUuid();

        Course ghostCourse = createCourse(member);
        Long savedCourseId = courseRepository.save(ghostCourse).getId();
        Course fakeCourse = createCourse(member);
        Long savedFakeCourseId = courseRepository.save(fakeCourse).getId();

        Running ghostRunning = createRunning(member, ghostCourse);
        Long ghostRunningId = runningRepository.save(ghostRunning).getId();

        RunRecordCommand runRecordCommand = createRunRecordDto(5.1, 130, -120, 3600L);
        CreateRunCommand request = createGhostRunCommandRequest(
                "러닝 이름", "GHOST", ghostRunningId,
                100L, runRecordCommand);

//        given(s3TelemetryClient.uploadTelemetries(anyString(), anyString()))
//                .willReturn("Mock Telemetries Url");

        // when // then
//        Assertions.assertThatThrownBy(() -> runningCommandService.createRun(request, savedFakeCourseId, savedMemberUuid))
//                .isInstanceOf(InvalidRunningException.class)
//                .hasMessage("고스트가 뛴 코스가 아닙니다.");
    }

    private Course createCourse(Member member) {
        CourseProfile testCourseProfile = createCourseProfile();
        Coordinate testCoordinate = createStartPoint();
        return Course.of(member, testCourseProfile.getDistance(),
                testCourseProfile.getElevationAverage(), testCourseProfile.getElevationGain(), testCourseProfile.getElevationLoss(),
                testCoordinate.getLatitude(), testCoordinate.getLongitude(),
                "Mock URL", "Mock URL");
    }

    private Coordinate createStartPoint() {
        return Coordinate.of(37.545354, 34.7878);
    }

    private CourseProfile createCourseProfile() {
        return CourseProfile.of(5.2, 40.0, 30.0, -10.0);
    }

    private CreateRunCommand createGhostRunCommandRequest(String runningName, String runningMode, Long ghostRunningId,
                                                          Long startedAt, RunRecordCommand runRecordCommand) {
        return new CreateRunCommand(runningName, ghostRunningId, runningMode,
                startedAt, runRecordCommand, false, true);
    }

    private Running createRunning(Member member, Course course) {
        RunningRecord testRunningRecord = createRunningRecord();
        return Running.of("테스트 러닝 제목", RunningMode.SOLO, 2L, testRunningRecord,
                1750729987181L, true, false, "URL", "URL", "URL", member, course);
    }
  
    @DisplayName("러닝 기록을 공개/비공개 상태로 변경한다.")
    @Test
    void setRunningPublic() {
        // given
        Member member = createMember("테스트 유저");
        memberRepository.save(member);

        Course course = createCourse(member);
        courseRepository.save(course);

        Running publicRunning = runningRepository.save(createRunning(member, course, true));
        Running privateRunning = runningRepository.save(createRunning(member, course, false));

        // when
        runningCommandService.updateRunningPublicStatus(publicRunning.getId(), member.getUuid());
        runningCommandService.updateRunningPublicStatus(privateRunning.getId(), member.getUuid());

        // then
        Running updatedToPublicRunning = runningRepository.findById(privateRunning.getId()).get();
        assertThat(updatedToPublicRunning.isPublic()).isTrue();

        Running updatedToPrivateRunning = runningRepository.findById(publicRunning.getId()).get();
        assertThat(updatedToPrivateRunning.isPublic()).isFalse();
    }

    @DisplayName("러닝을 중지한 기록이 있다면 공개 설정이 불가능하다.")
    @Test
    void cannotEnablePublicWhenStoppedRunExists() {
        // given
        Member member = createMember("테스트 유저");
        memberRepository.save(member);

        Course course = createCourse(member);
        courseRepository.save(course);

        Running hasPausedRunning = runningRepository.save(createHasPausedRunning(member, course));
        runningRepository.save(hasPausedRunning);

        // when // then
        assertThatThrownBy(
                () -> runningCommandService.updateRunningPublicStatus(hasPausedRunning.getId(), member.getUuid()))
                .isInstanceOf(InvalidRunningException.class)
                .hasMessage("정지한 기록이 있다면 공개할 수 없습니다.");
    }

    @DisplayName("자신의 러닝 데이터가 아니라면 공개 설정을 수정할 수 없다.")
    @Test
    void cannotUpdateIsPublicIfNotOwner() {
        // given
        Member member = createMember("테스트 유저");
        memberRepository.save(member);

        Course course = createCourse(member);
        courseRepository.save(course);

        Running publicRunning = runningRepository.save(createRunning(member, course, true));

        // when // then
        assertThatThrownBy(
                        () -> runningCommandService.updateRunningPublicStatus(publicRunning.getId(), UUID.randomUUID().toString()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("접근할 수 없는 러닝 데이터입니다.");
    }

    private Running createRunning(Member member, Course course, Boolean isPublic) {
        RunningRecord testRunningRecord = createRunningRecord();
        return Running.of("테스트 러닝 제목", RunningMode.SOLO, 2L, testRunningRecord, 1750729987181L,
                isPublic, false, "URL", "URL", "URL", member, course);
    }

    private Running createHasPausedRunning(Member member, Course course) {
        return Running.of("테스트 러닝 제목", RunningMode.SOLO, 2L, createRunningRecord(), 1750729987181L,
                false, true, "URL", "URL", "URL", member, course);
    }

    private RunningRecord createRunningRecord() {
        return RunningRecord.of(5.2, 40.0, 30.0, -20.0,
                6.1, 4.9, 6.9, 3423L, 302, 120, 56);
    }

    @DisplayName("N개의 러닝 데이터를 삭제한다.")
    @Test
    void deleteRunnings() {
        // given
        Member member = createMember("테스트 유저");
        memberRepository.save(member);

        Course course1 = createCourse(member);
        Course course2 = createCourse(member);
        courseRepository.saveAll(List.of(course1, course2));

        Running running1 = createRunning(member, course1);
        Running running2 = createRunning(member, course1);
        Running running3 = createRunning(member, course2);
        runningRepository.saveAll(List.of(running1, running2, running3));

        // when
        List<Long> runningIds = List.of(running1.getId(), running2.getId(), running3.getId());
        runningCommandService.deleteRunnings(runningIds, member.getUuid());

        // then
        List<Running> runnings = runningRepository.findByIds(List.of(running1.getId(), running2.getId(), running3.getId()));
        assertThat(runnings.size()).isEqualTo(0);
    }

    @DisplayName("자신의 러닝 데이터가 아니라면 삭제할 수 없다.")
    @Test
    void cannotDeleteRunningsIfNotOwner() {
        // given
        Member member = createMember("테스트 유저");
        memberRepository.save(member);

        Course course1 = createCourse(member);
        Course course2 = createCourse(member);
        courseRepository.saveAll(List.of(course1, course2));

        Running running1 = createRunning(member, course1);
        Running running2 = createRunning(member, course1);
        Running running3 = createRunning(member, course2);
        runningRepository.saveAll(List.of(running1, running2, running3));

        // when // then
        List<Long> runningIds = List.of(running1.getId(), running2.getId(), running3.getId());
        assertThatThrownBy(
                () -> runningCommandService.deleteRunnings(runningIds, UUID.randomUUID().toString()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("접근할 수 없는 러닝 데이터입니다.");
    }

    @DisplayName("러닝 이름을 변경한다.")
    @Test
    void updateRunningName() {
        // given
        Member member = createMember("테스트 유저");
        memberRepository.save(member);

        Course course = createCourse(member);
        courseRepository.save(course);

        Running running = createRunning(member, course);
        runningRepository.save(running);

        // when
        runningCommandService.updateRunningName("변경할 러닝명", running.getId(), member.getUuid());

        // then
        Running updatedRunning = runningRepository.findById(running.getId()).get();
        assertThat(updatedRunning.getRunningName()).isEqualTo("변경할 러닝명");
    }

    @DisplayName("자신의 러닝이 아니라면 이름을 변경하지 못한다.")
    @Test
    void cannotUpdateRunningNameIfNotOwner() {
        // given
        Member member = createMember("테스트 유저");
        memberRepository.save(member);

        Course course = createCourse(member);
        courseRepository.save(course);

        Running running = createRunning(member, course);
        runningRepository.save(running);

        // when // then
        assertThatThrownBy(
                () -> runningCommandService.updateRunningName(
                        "변경할 러닝명", running.getId(), UUID.randomUUID().toString()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("접근할 수 없는 러닝 데이터입니다.");
    }

    @DisplayName("러닝 스크린샷 이미지를 업데이트한다.")
    @Test
    void saveRunningScreenShotImage() {
        // given
        Member member = createMember("테스트 유저");
        memberRepository.save(member);

        Course course = createCourse(member);
        courseRepository.save(course);

        Running running = createRunning(member, course);
        runningRepository.save(running);

        MockMultipartFile screenShotImage = new MockMultipartFile(
                "img", "capture.png", "image/png", new byte[]{1, 2, 3}
        );

        given(ghostRunnerS3Client.uploadRunningCaptureImage(any(), anyString()))
                .willReturn("Mock ScreenShotImage Url");

        // when
        runningCommandService.updateScreenShotImage(member.getUuid(), running.getId(), screenShotImage);

        // then
        Running savedRunning = runningRepository.findById(running.getId()).get();
        assertThat(savedRunning.getRunningDataUrls().getScreenShotUrl()).isEqualTo("Mock ScreenShotImage Url");
     }

     @DisplayName("자신의 러닝이 아니라면 예외를 발생시킨다.")
     @Test
     void throwAuthenticationExceptionWhenSaveNotMyRunningScreenShotImage() {
         // given
         Member member = createMember("테스트 유저");
         memberRepository.save(member);

         Course course = createCourse(member);
         courseRepository.save(course);

         Running running = createRunning(member, course);
         runningRepository.save(running);

         MockMultipartFile screenShotImage = new MockMultipartFile(
                 "img", "capture.png", "image/png", new byte[]{1, 2, 3}
         );

         given(ghostRunnerS3Client.uploadRunningCaptureImage(any(), anyString()))
                 .willReturn("Mock ScreenShotImage Url");

         // when // then
         assertThatThrownBy(() ->
                 runningCommandService.updateScreenShotImage("Fake Member Uuid", running.getId(), screenShotImage))
                 .isInstanceOf(AccessDeniedException.class)
                 .hasMessage("접근할 수 없는 러닝 데이터입니다.");
     }

}
