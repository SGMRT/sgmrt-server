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
import soma.ghostrunner.domain.running.application.dto.CourseCoordinateDto;
import soma.ghostrunner.domain.running.dao.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;

import java.util.List;

import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.*;

class RunningTelemetryQueryServiceTest extends IntegrationTestSupport {

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

    @DisplayName("코스에서 가장 처음 뛴 러너의 시계열 위치 데이터를 조회하여 코스의 위/경도 시계열을 반환한다.")
    @Test
    void findCoordinateTelemetries() {
        // given
        Member member1 = createMember("이복둥");
        Member member2 = createMember("이복둥2");
        memberRepository.saveAll(List.of(member1, member2));

        Course course = createCourse();
        courseRepository.save(course);

        Running firstRunning = createRunning("첫 번째 러닝", course, member1, "첫 번째 러닝의 URL");
        Running secondRunning = createRunning("두 번째 러닝", course, member1, "두 번째 러닝의 URL");
        Running thirdRunning = createRunning("세 번째 러닝", course, member2, "세 번째 러닝의 URL");
        runningRepository.saveAll(List.of(firstRunning, secondRunning, thirdRunning));

        List<String> downloadedStringTelemetries = List.of(
                "{\"timeStamp\":0,\"lat\":37.2,\"lng\":37.3,\"dist\":110.0,\"pace\":6.0,\"alt\":100,\"cadence\":120,\"bpm\":110,\"isRunning\":true}",
                "{\"timeStamp\":1,\"lat\":37.3,\"lng\":37.6,\"dist\":110.0,\"pace\":6.0,\"alt\":100,\"cadence\":120,\"bpm\":110,\"isRunning\":true}",
                "{\"timeStamp\":2,\"lat\":37.4,\"lng\":37.7,\"dist\":110.0,\"pace\":6.0,\"alt\":100,\"cadence\":120,\"bpm\":110,\"isRunning\":true}",
                "{\"timeStamp\":3,\"lat\":37.5,\"lng\":37.8,\"dist\":110.0,\"pace\":6.0,\"alt\":100,\"cadence\":120,\"bpm\":110,\"isRunning\":true}"
        );
        given(telemetryClient.downloadTelemetryFromUrl("첫 번째 러닝의 URL")).willReturn(downloadedStringTelemetries);

        // when
        List<CourseCoordinateDto> courseCoordinateDtos = runningTelemetryQueryService.findCoordinateTelemetries(course.getId());

        // then
        Assertions.assertThat(courseCoordinateDtos)
                .hasSize(4)
                .extracting("lat", "lng")
                .containsExactly(
                        tuple(37.2, 37.3), tuple(37.3, 37.6), tuple(37.4, 37.7), tuple(37.5, 37.8)
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
        return StartPoint.fromCoordinates(37.545354, 34.7878);
    }

    private CourseProfile createCourseProfile() {
        return CourseProfile.of(5.2, 40, -20);
    }

    private RunningRecord createRunningRecord() {
        return RunningRecord.of(5.2, 40, -20, 6.1, 3423.2, 302.2, 120L, 56, 100, 120);
    }

}
