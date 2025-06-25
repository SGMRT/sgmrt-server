package soma.ghostrunner.domain.course;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseMetaInfo;
import soma.ghostrunner.domain.course.domain.StartPoint;

class CourseTest {

    private Course testCourse;
    private CourseMetaInfo testCourseMetaInfo;
    private StartPoint testStartPoint;

    @BeforeEach
    void setUp() {
        testCourseMetaInfo = CourseMetaInfo.of(5.2, 40);
        testStartPoint = StartPoint.fromCoordinates(37.545354, 34.7878);
        testCourse = Course.of(testCourseMetaInfo, testStartPoint, "[{'lat':37.123, 'lng':32.123}, {'lat':37.123, 'lng':32.123}, {'lat':37.123, 'lng':32.123}]");
    }

    @Test
    void of() {
        Assertions.assertThat(testCourse.getName()).isNull();
        Assertions.assertThat(testCourse.getCourseMetaInfo().getAltitude()).isEqualTo(40);
    }
}
