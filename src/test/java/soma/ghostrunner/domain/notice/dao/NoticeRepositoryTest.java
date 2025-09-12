package soma.ghostrunner.domain.notice.dao;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.notice.domain.Notice;
import soma.ghostrunner.domain.notice.domain.NoticeDismissal;
import soma.ghostrunner.domain.notice.domain.enums.NoticeType;

import java.time.LocalDateTime;
import java.util.List;


class NoticeRepositoryTest extends IntegrationTestSupport {

    @Autowired
    NoticeRepository noticeRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    NoticeDismissalRepository noticeDismissalRepository;

    @DisplayName("회원이 숨김처리하지 않은 공지를 조회한다.")
    @Test
    void findActiveNoticesForMember() {
        // given
        Member member = createMember("카리나");
        memberRepository.save(member);

        Notice notice1 = createNotice("공지1");
        Notice notice2 = createNotice("공지2");
        noticeRepository.saveAll(List.of(notice1, notice2));

        NoticeDismissal dismissal = NoticeDismissal.of(member, notice2); // 공지2 숨김처리
        noticeDismissalRepository.save(dismissal);

        // when
        List<Notice> activeNotices = noticeRepository.findActiveNoticesForMember(LocalDateTime.now(), member.getUuid(), null);

        // then
        Assertions.assertThat(activeNotices).hasSize(1);
        Assertions.assertThat(activeNotices)
                .extracting("title")
                .containsExactlyInAnyOrder("공지1");
    }

    @DisplayName("공지사항 노출기간 내의 공지만 조회한다.")
    @Test
    void findActiveNotices() {
        // given
        Member member = createMember("카리나");
        memberRepository.save(member);

        LocalDateTime noticeRegisteredTime = LocalDateTime.now().minusHours(2);
        Notice notice1 = createNotice("옛날 공지", noticeRegisteredTime, noticeRegisteredTime.plusHours(1));
        Notice notice2 = createNotice("현재 공지", noticeRegisteredTime, noticeRegisteredTime.plusHours(3));
        noticeRepository.saveAll(List.of(notice1, notice2));

        // when
        List<Notice> activeNotices = noticeRepository.findActiveNoticesForMember(LocalDateTime.now(), member.getUuid(), null);

        // then
        Assertions.assertThat(activeNotices).hasSize(1);
        Assertions.assertThat(activeNotices)
                .extracting("title")
                .containsExactly("현재 공지");
    }

    @DisplayName("공지사항은 우선순위 내림차순으로 정렬되어 출력된다.")
    @Test
    public void activeNotices_OrderedByPriority() {
        // given
        Member member = createMember("카리나");
        memberRepository.save(member);

        Notice notice1 = createNotice("안 중요한 공지", 0);
        Notice notice2 = createNotice("제일 중요한 공지", 2);
        Notice notice3 = createNotice("약간 중요한 공지", 1);
        noticeRepository.saveAll(List.of(notice1, notice2, notice3));

        // when
        List<Notice> activeNotices = noticeRepository.findActiveNoticesForMember(LocalDateTime.now(), member.getUuid(), null);

        // then
        Assertions.assertThat(activeNotices).hasSize(3);
        Assertions.assertThat(activeNotices)
                .extracting("title")
                .containsExactly("제일 중요한 공지", "약간 중요한 공지", "안 중요한 공지");
    }

    private Notice createNotice(String name) {
        return Notice.of(name, "dummy content", "dummy-url");
    }

    private Notice createNotice(String name, LocalDateTime startAt, LocalDateTime endAt) {
        return Notice.of(name , "dummy content", NoticeType.GENERAL, "dummy-url", 0, startAt, endAt);
    }

    private Notice createNotice(String name, Integer priority) {
        return Notice.of(name , "dummy content", NoticeType.GENERAL, "dummy-url", priority, null, null);
    }

    private Member createMember(String name) {
        return Member.of(name, "test-url");
    }

}