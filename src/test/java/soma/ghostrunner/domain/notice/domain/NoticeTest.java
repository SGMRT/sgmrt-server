package soma.ghostrunner.domain.notice.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import soma.ghostrunner.domain.notice.domain.enums.NoticeType;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class NoticeTest {

    @DisplayName("of(title, content, imageUrl) 메소드로 기본값이 적용된 Notice 엔티티를 생성한다.")
    @Test
    void createEntity_withDefaultValues_success() {
        // given
        String title = "기본 공지";
        String content = "기본 내용";
        String imageUrl = "http://example.com/image.png";

        // when
        Notice notice = Notice.of(title, content, imageUrl);

        // then
        assertThat(notice.getTitle()).isEqualTo(title);
        assertThat(notice.getContent()).isEqualTo(content);
        assertThat(notice.getImageUrl()).isEqualTo(imageUrl);
        assertThat(notice.getPriority()).isZero();
        assertThat(notice.getStartAt()).isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(notice.getEndAt()).isNull();
    }

    @DisplayName("of(모든 필드) 메소드로 모든 필드가 지정된 Notice 엔티티를 생성한다.")
    @Test
    void createEntity_withAllFields_success() {
        // given
        String title = "전체 필드 공지";
        String content = "전체 내용";
        String imageUrl = "http://example.com/image.png";
        Integer priority = 10;
        NoticeType type = NoticeType.EVENT;
        LocalDateTime startAt = LocalDateTime.now().plusDays(1);
        LocalDateTime endAt = LocalDateTime.now().plusDays(10);

        // when
        Notice notice = Notice.of(title, content, type, imageUrl, priority, startAt, endAt);

        // then
        assertThat(notice.getTitle()).isEqualTo(title);
        assertThat(notice.getContent()).isEqualTo(content);
        assertThat(notice.getType()).isEqualTo(type);
        assertThat(notice.getImageUrl()).isEqualTo(imageUrl);
        assertThat(notice.getPriority()).isEqualTo(priority);
        assertThat(notice.getStartAt()).isEqualTo(startAt);
        assertThat(notice.getEndAt()).isEqualTo(endAt);
    }

    @DisplayName("of 메소드 호출 시 필수 인자가 모두 null이면 예외가 발생한다.")
    @Test
    void createEntity_withAllNullArguments_fail() {
        // when & then
        assertThatThrownBy(() -> Notice.of(null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("of 메소드 호출 시 startAt이 endAt보다 나중이면 예외가 발생한다.")
    @Test
    void createEntity_withStartAtAfterEndAt_fail() {
        // given
        LocalDateTime startAt = LocalDateTime.now().plusDays(2);
        LocalDateTime endAt = LocalDateTime.now().plusDays(1);

        // when & then
        assertThatThrownBy(() -> Notice.of("제목", "내용", NoticeType.GENERAL, null, 1, startAt, endAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("startAt cannot be after endAt");
    }

    @DisplayName("of 메소드 호출 시 priority나 startAt이 null이면 기본값으로 생성된다.")
    @Test
    void createEntity_withNullPriorityAndStartAt_edge() {
        // given
        String title = "일부 null 공지";
        LocalDateTime endAt = LocalDateTime.now().plusDays(5);

        // when
        Notice notice = Notice.of(title, "내용", NoticeType.GENERAL, "url", null, null, endAt);

        // then
        assertThat(notice.getPriority()).isZero(); // priority 기본값 0
        assertThat(notice.getStartAt()).isNotNull(); // startAt 기본값 now()
        assertThat(notice.getEndAt()).isEqualTo(endAt);
    }

    @DisplayName("of 메소드 호출 시 startAt과 endAt이 같아도 정상 생성된다.")
    @Test
    void createEntity_withStartAtEqualsEndAt_edge() {
        // given
        LocalDateTime sameTime = LocalDateTime.now().plusHours(1);

        // when
        Notice notice = Notice.of("제목", "내용", NoticeType.GENERAL, null, 1, sameTime, sameTime);

        // then
        assertThat(notice.getStartAt()).isEqualTo(sameTime);
        assertThat(notice.getEndAt()).isEqualTo(sameTime);
    }


    @DisplayName("Setter 메소드로 필드 값을 정상적으로 변경할 수 있다.")
    @Test
    void setters_updateFields_success() {
        // given
        Notice notice = Notice.of("초기 제목", "초기 내용", "initial.png");
        LocalDateTime newStartAt = LocalDateTime.now().plusHours(1);
        LocalDateTime newEndAt = LocalDateTime.now().plusDays(1);

        // when
        notice.updateTitle("수정된 제목");
        notice.updateContent("수정된 내용");
        notice.updateImageUrl("http://www.updated.png");
        notice.updatePriority(5);
        notice.updateStartAt(newStartAt);
        notice.updateEndAt(newEndAt);

        // then
        assertThat(notice.getTitle()).isEqualTo("수정된 제목");
        assertThat(notice.getContent()).isEqualTo("수정된 내용");
        assertThat(notice.getImageUrl()).isEqualTo("http://www.updated.png");
        assertThat(notice.getPriority()).isEqualTo(5);
        assertThat(notice.getStartAt()).isEqualTo(newStartAt);
        assertThat(notice.getEndAt()).isEqualTo(newEndAt);
    }
}