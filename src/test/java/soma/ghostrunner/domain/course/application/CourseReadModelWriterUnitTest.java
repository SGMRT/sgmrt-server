package soma.ghostrunner.domain.course.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import soma.ghostrunner.domain.course.dao.CourseReadModelRepository;
import soma.ghostrunner.domain.course.dao.CourseRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * CourseReadModelWriter 단위 테스트 — 락 획득 순서 규약.
 *
 * 여러 코스를 재계산할 때 X락({@code findByCourseIdForUpdate})을 항상 courseId 오름차순으로 잡아야 한다.
 * 서로 다른 트랜잭션이 같은 코스 집합을 다른 순서로 잠그면 InnoDB 데드락이 발생하며,
 * 그 순서는 클라이언트가 보낸 러닝 ID 배열에 종속되므로 재현 가능한 조건이 된다.
 * (실제 데드락은 재현이 어려워 안전망이 되지 못하므로, 여기서는 호출 순서 자체를 고정한다)
 */
@DisplayName("CourseReadModelWriter 단위 테스트 - 락 순서")
@ExtendWith(MockitoExtension.class)
class CourseReadModelWriterUnitTest {

    @Mock
    private CourseReadModelRepository readModelRepository;

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private CourseReadModelWriter writer;

    @DisplayName("재계산: 코스 ID가 뒤섞여 들어와도 오름차순으로 X락을 잡는다")
    @Test
    void recalculate_acquiresLocksInAscendingCourseIdOrder() {
        // given : 클라이언트가 보낸 순서 그대로인 뒤섞인 코스 ID들
        given(readModelRepository.findByCourseIdForUpdate(anyLong()))
                .willReturn(Optional.empty());

        // when
        writer.recalculate(List.of(30L, 10L, 20L));

        // then
        InOrder lockOrder = inOrder(readModelRepository);
        lockOrder.verify(readModelRepository).findByCourseIdForUpdate(10L);
        lockOrder.verify(readModelRepository).findByCourseIdForUpdate(20L);
        lockOrder.verify(readModelRepository).findByCourseIdForUpdate(30L);
        lockOrder.verifyNoMoreInteractions();
    }

    @DisplayName("재계산: 중복된 코스 ID와 null은 걸러내고 코스마다 한 번만 X락을 잡는다")
    @Test
    void recalculate_skipsDuplicatesAndNulls() {
        // given
        given(readModelRepository.findByCourseIdForUpdate(anyLong()))
                .willReturn(Optional.empty());

        // when
        writer.recalculate(Arrays.asList(20L, null, 10L, 20L, null));

        // then
        InOrder lockOrder = inOrder(readModelRepository);
        lockOrder.verify(readModelRepository).findByCourseIdForUpdate(10L);
        lockOrder.verify(readModelRepository).findByCourseIdForUpdate(20L);
        lockOrder.verifyNoMoreInteractions();
    }

    @DisplayName("재계산: 코스 ID가 없으면 락을 잡지 않는다")
    @Test
    void recalculate_withoutCourseIds_acquiresNoLock() {
        // when
        writer.recalculate(List.of());
        writer.recalculate(null);

        // then
        verify(readModelRepository, never()).findByCourseIdForUpdate(anyLong());
    }
}
