package soma.ghostrunner.domain.notice.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
        NoticeType type = NoticeType.EVENT_V2;
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

    @DisplayName("of 메소드 호출 시 endAt이 주어지면 startAt도 반드시 주어져야 한다.")
    @Test
    void createEntity_withEndAtWithoutStartAt_fail() {
        // given
        LocalDateTime endAt = LocalDateTime.now().plusDays(1);
        // when & then
        assertThatThrownBy(() -> Notice.of("제목", "내용", NoticeType.GENERAL_V2, null, 1, null, endAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("endAt이 지정된 경우 startAt은 null일 수 없습니다.");
    }

    @DisplayName("of 메소드 호출 시 startAt이 endAt보다 나중이면 예외가 발생한다.")
    @Test
    void createEntity_withStartAtAfterEndAt_fail() {
        // given
        LocalDateTime startAt = LocalDateTime.now().plusDays(2);
        LocalDateTime endAt = LocalDateTime.now().plusDays(1);

        // when & then
        assertThatThrownBy(() -> Notice.of("제목", "내용", NoticeType.GENERAL_V2, null, 1, startAt, endAt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("startAt이 endAt보다 이후일 수 없습니다.");
    }

    @DisplayName("of 메소드 호출 시 startAt과 endAt이 같아도 정상 생성된다.")
    @Test
    void createEntity_withStartAtEqualsEndAt_edge() {
        // given
        LocalDateTime sameTime = LocalDateTime.now().plusHours(1);

        // when
        Notice notice = Notice.of("제목", "내용", NoticeType.GENERAL_V2, null, 1, sameTime, sameTime);

        // then
        assertThat(notice.getStartAt()).isEqualTo(sameTime);
        assertThat(notice.getEndAt()).isEqualTo(sameTime);
    }


    @DisplayName("Setter 메소드로 필드 값을 정상적으로 변경할 수 있다.")
    @Test
    void setters_updateFields_success() {
        // given
        var now = LocalDateTime.now();
        Notice notice = Notice.of("초기 제목", "초기 내용", NoticeType.GENERAL_V2, "initial.png", 1, now, now.plusDays(1));
        LocalDateTime newStartAt = LocalDateTime.now().plusHours(1);
        LocalDateTime newEndAt = LocalDateTime.now().plusDays(1);

        // when
        notice.updateTitle("수정된 제목");
        notice.updateContent("수정된 내용");
        notice.updateImageUrl("http://www.updated.png");
        notice.updatePriority(5);
        notice.updateType(NoticeType.EVENT_V2);
        notice.updateStartAt(newStartAt);
        notice.updateEndAt(newEndAt);

        // then
        assertThat(notice.getTitle()).isEqualTo("수정된 제목");
        assertThat(notice.getContent()).isEqualTo("수정된 내용");
        assertThat(notice.getImageUrl()).isEqualTo("http://www.updated.png");
        assertThat(notice.getPriority()).isEqualTo(5);
        assertThat(notice.getType()).isEqualTo(NoticeType.EVENT_V2);
        assertThat(notice.getStartAt()).isEqualTo(newStartAt);
        assertThat(notice.getEndAt()).isEqualTo(newEndAt);
    }

    @DisplayName("노출 시작일 변경 시도 시 노출 종료일이 null이면 예외를 발생시킨다. (startAt과 endAt은 둘 다 null이거나 둘 다 null이 아니여야 한다.")
    @Test
    void updateStartAt_withNull() {
        // given
        Notice notice = Notice.of("제목", "내용", NoticeType.GENERAL_V2, null, 1,
                null, null);
        // when & then
        assertThatThrownBy(() -> notice.updateStartAt(LocalDateTime.now().minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("공지 노출 종료기간이 null인 상태에서 시작기간을 변경할 수 없습니다.");
    }

    @DisplayName("노출 종료일 변경 시도 시 노출 시작일 null이면 예외를 발생시킨다. (startAt과 endAt은 둘 다 null이거나 둘 다 null이 아니여야 한다.")
    @Test
    void updateEndAt_withNull() {
        // given
        Notice notice = Notice.of("제목", "내용", NoticeType.GENERAL_V2, null, 1,
                null, null);
        // when & then
        assertThatThrownBy(() -> notice.updateEndAt(LocalDateTime.now().plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("공지 노출 시작기간이 null인 상태에서 종료기간을 변경할 수 없습니다.");
    }

    @DisplayName("공지사항을 노출 설정할 수 있다. 노출 기간이 올바르게 설정된다..")
    @Test
    void updateEndAt_success() {
        // given
        Notice notice = Notice.of("제목", "내용", NoticeType.GENERAL_V2, null, 1);
        // when
        LocalDateTime startAt = LocalDateTime.now().plusDays(1);
        LocalDateTime endAt = LocalDateTime.now().plusDays(10);
        notice.activate(startAt, endAt);

        // then
        assertThat(notice.getStartAt()).isEqualTo(startAt);
        assertThat(notice.getEndAt()).isEqualTo(endAt);
    }

    @DisplayName("공지사항을 비노출 설정할 수 있다. 시작일과 종료일이 모두 null로 변경된다.")
    @Test
    void deactivate_success() {
        // given
        Notice notice = Notice.of("제목", "내용", NoticeType.GENERAL_V2, null, 1,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(10));
        // when
        notice.deactivate();

        // then
        assertThat(notice.getStartAt()).isNull();
        assertThat(notice.getEndAt()).isNull();
    }


}