package soma.ghostrunner.domain.notice.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import soma.ghostrunner.domain.member.domain.Member;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class NoticeDismissalTest {

    @DisplayName("of 정적 팩토리 메소드로 NoticeDismissal 엔티티를 생성한다. (만료일 지정)")
    @Test
    void createEntity_withDismissUntil_success() {
        // given
        Member member = mock(Member.class);
        Notice notice = mock(Notice.class);
        LocalDateTime dismissUntil = LocalDateTime.now().plusDays(7);

        // when
        NoticeDismissal dismissal = NoticeDismissal.of(member, notice, dismissUntil);

        // then
        assertThat(dismissal.getMember()).isEqualTo(member);
        assertThat(dismissal.getNotice()).isEqualTo(notice);
        assertThat(dismissal.getDismissUntil()).isEqualTo(dismissUntil);
        assertThat(dismissal.getId()).isNull(); // ID는 영속화 시점에 할당되므로 null
    }

    @DisplayName("of 정적 팩토리 메소드로 NoticeDismissal 엔티티를 생성한다. (영구 숨김)")
    @Test
    void createEntity_withPermanentDismiss_success() {
        // given
        Member member = mock(Member.class);
        Notice notice = mock(Notice.class);

        // when
        NoticeDismissal dismissal = NoticeDismissal.of(member, notice);

        // then
        assertThat(dismissal.getMember()).isEqualTo(member);
        assertThat(dismissal.getNotice()).isEqualTo(notice);
        assertThat(dismissal.getDismissUntil()).isNull();
    }

    @DisplayName("updateDismissUntil 메소드로 숨김 만료일을 성공적으로 변경한다.")
    @Test
    void updateDismissUntil_success() {
        // given
        Member member = mock(Member.class);
        Notice notice = mock(Notice.class);
        LocalDateTime initialDismissUntil = LocalDateTime.now().plusDays(1);
        NoticeDismissal dismissal = NoticeDismissal.of(member, notice, initialDismissUntil);

        LocalDateTime newDismissUntil = LocalDateTime.now().plusDays(30);

        // when
        dismissal.updateDismissUntil(newDismissUntil);

        // then
        assertThat(dismissal.getDismissUntil()).isEqualTo(newDismissUntil);
    }

    @DisplayName("updateDismissUntil 메소드에 null을 전달하여 영구 숨김으로 변경한다.")
    @Test
    void updateDismissUntil_toNull_success() {
        // given
        Member member = mock(Member.class);
        Notice notice = mock(Notice.class);
        LocalDateTime initialDismissUntil = LocalDateTime.now().plusDays(1);
        NoticeDismissal dismissal = NoticeDismissal.of(member, notice, initialDismissUntil);

        // when
        dismissal.updateDismissUntil(null);

        // then
        assertThat(dismissal.getDismissUntil()).isNull();
    }
}