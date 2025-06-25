package soma.ghostrunner.domain.running.domain;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseMetaInfo;
import soma.ghostrunner.domain.course.domain.StartPoint;
import soma.ghostrunner.domain.member.Member;

class RunningTest {

    private RunningRecord testRunningRecord;
    private Running testRunning;

    @BeforeEach
    void setUp() {
        // Member, Course
        Member testMember = Member.of("이복둥", "프로필 URL");
        CourseMetaInfo testCourseMetaInfo = CourseMetaInfo.of(5.2, 40);
        StartPoint testStartPoint = StartPoint.fromCoordinates(37.545354, 34.7878);
        Course testCourse = Course.of(testCourseMetaInfo, testStartPoint, "[{'lat':37.123, 'lng':32.123}, {'lat':37.123, 'lng':32.123}, {'lat':37.123, 'lng':32.123}]");

        // RunningRecord
        testRunningRecord = RunningRecord.of(5.2, 40, 6.1, 3423, 302, 120, 56);

        // Running
        testRunning = Running.of("테스트 러닝제목", RunningMode.SOLO, 2L, testRunningRecord, 1750729987181L,
                true, false, "URL", testMember, testCourse);
    }

    @Test
    void of() {
        System.out.println(testRunning.getTelemetryUrl());
        Assertions.assertThat(testRunning.getRunningSummary()).isNull();
        Assertions.assertThat(testRunning.getGhostRunningId()).isEqualTo(2L);
    }

    @DisplayName("RunningMode Enum 테스트")
    @Test
    void testRunningMode() {
        Assertions.assertThat(RunningMode.valueOf("SOLO")).isEqualTo(RunningMode.SOLO);
    }
}
