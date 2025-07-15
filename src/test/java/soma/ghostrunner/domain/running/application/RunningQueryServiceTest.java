package soma.ghostrunner.domain.running.application;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;
import soma.ghostrunner.domain.running.dao.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;

import java.util.List;

import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.*;

class RunningQueryServiceTest extends IntegrationTestSupport {

    @Autowired
    private RunningQueryService runningQueryService;

    @Autowired
    private RunningTelemetryQueryService runningTelemetryQueryService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    RunningRepository runningRepository;

    @MockitoBean
    TelemetryClient telemetryClient;

    @DisplayName("러닝의 전체 시계열을 조회한다.")
    @Test
    void findRunningTelemetries() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse();
        courseRepository.save(course);

        Running running = createRunning("러닝", course, member, "러닝의 URL");
        runningRepository.save(running);

        List<String> downloadedStringTelemetries = List.of(
                "{\"timeStamp\":0,\"lat\":37.2,\"lng\":37.5,\"dist\":110.0,\"pace\":6.0,\"alt\":100,\"cadence\":120,\"bpm\":110,\"isRunning\":true}",
                "{\"timeStamp\":1,\"lat\":37.3,\"lng\":37.6,\"dist\":110.1,\"pace\":6.1,\"alt\":101,\"cadence\":121,\"bpm\":111,\"isRunning\":true}",
                "{\"timeStamp\":2,\"lat\":37.4,\"lng\":37.7,\"dist\":110.2,\"pace\":6.2,\"alt\":102,\"cadence\":122,\"bpm\":112,\"isRunning\":true}",
                "{\"timeStamp\":3,\"lat\":37.5,\"lng\":37.8,\"dist\":110.3,\"pace\":6.3,\"alt\":103,\"cadence\":123,\"bpm\":113,\"isRunning\":false}"
        );

        given(telemetryClient.downloadTelemetryFromUrl("러닝의 URL")).willReturn(downloadedStringTelemetries);

        // when
        List<TelemetryDto> telemetries = runningQueryService.findRunningTelemetries(running.getId());

        // then
        Assertions.assertThat(telemetries)
                .hasSize(4)
                .extracting("timeStamp", "lat", "lng", "dist", "pace", "alt", "cadence", "bpm", "isRunning")
                .containsExactly(
                        tuple(0L, 37.2, 37.5, 110.0, 6.0, 100, 120, 110, true),
                        tuple(  1L, 37.3, 37.6, 110.1, 6.1, 101, 121, 111, true),
                        tuple(2L, 37.4, 37.7, 110.2, 6.2, 102, 122, 112, true),
                        tuple(3L, 37.5, 37.8, 110.3, 6.3, 103, 123, 113, false)
                );
    }

    private Running createRunning(String runningName, Course course, Member member, String telemetryUrl) {
        return Running.of(runningName, RunningMode.SOLO, null, createRunningRecord(), 1750729987181L,
                true, false, telemetryUrl, member, course);
    }

    private Member createMember(String name) {
        return Member.of(name, "프로필 URL");
    }

    private Course createCourse() {
        return Course.of(createCourseProfile(), createStartPoint(), "[{'lat':37.123, 'lng':32.123}, {'lat':37.123, 'lng':32.123}, {'lat':37.123, 'lng':32.123}]");
    }

    private StartPoint createStartPoint() {
        return StartPoint.of(37.545354, 34.7878);
    }

    private CourseProfile createCourseProfile() {
        return CourseProfile.of(5.2, 40, -20);
    }

    private RunningRecord createRunningRecord() {
        return RunningRecord.of(5.2, 40, -20, 6.1, 3423.2, 302.2, 120L, 56, 100, 120);
    }

}
