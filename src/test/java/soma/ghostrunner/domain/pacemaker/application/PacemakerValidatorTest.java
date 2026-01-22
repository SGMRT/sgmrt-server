package soma.ghostrunner.domain.pacemaker.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import soma.ghostrunner.domain.course.application.CourseService;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.member.exception.MemberNotFoundException;
import soma.ghostrunner.global.error.ErrorCode;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PacemakerValidatorTest {

    @Mock
    CourseService courseService;

    @InjectMocks
    PacemakerValidator validator;

    @DisplayName("코스가 존재하면 검증 성공")
    @Test
    void validateCreationRequest_success() {
        // given
        Long courseId = 1L;
        when(courseService.findCourseById(courseId)).thenReturn(mock(Course.class));

        // when & then
        assertThatCode(() -> validator.validateCreationRequest(courseId))
                .doesNotThrowAnyException();

        verify(courseService).findCourseById(courseId);
    }

    @DisplayName("코스가 존재하지 않으면 예외 발생")
    @Test
    void validateCreationRequest_courseNotFound_throwsException() {
        // given
        Long courseId = 999L;
        when(courseService.findCourseById(courseId))
                .thenThrow(new MemberNotFoundException(ErrorCode.ENTITY_NOT_FOUND, "cannot find course"));

        // when & then
        assertThatThrownBy(() -> validator.validateCreationRequest(courseId))
                .isInstanceOf(MemberNotFoundException.class)
                .hasMessageContaining("cannot find course");

        verify(courseService).findCourseById(courseId);
    }

}
