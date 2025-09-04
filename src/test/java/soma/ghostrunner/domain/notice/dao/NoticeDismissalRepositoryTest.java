package soma.ghostrunner.domain.notice.dao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.notice.domain.Notice;
import soma.ghostrunner.domain.notice.domain.NoticeDismissal;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.*;

class NoticeDismissalRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private NoticeDismissalRepository noticeDismissalRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private NoticeRepository noticeRepository;

    @DisplayName("공지 ID와 멤버 UUID로 공지 숨김 정보를 조회한다.")
    @Test
    void findByNoticeIdAndMemberUuid_success() {
        // given
        Member member = createAndSaveMember("윤아", "profile.jpg");
        Notice notice = createAndSaveNotice("테스트 공지", "내용");
        NoticeDismissal dismissal = createAndSaveDismissal(member, notice);

        // when
        Optional<NoticeDismissal> foundDismissal = noticeDismissalRepository.findByNoticeIdAndMemberUuid(notice.getId(), member.getUuid());

        // then
        assertThat(foundDismissal).isPresent();
        assertThat(foundDismissal.get().getId()).isEqualTo(dismissal.getId());
        assertThat(foundDismissal.get().getMember().getUuid()).isEqualTo(member.getUuid());
        assertThat(foundDismissal.get().getNotice().getId()).isEqualTo(notice.getId());
    }

    @DisplayName("존재하지 않는 공지 ID로 조회하면 빈 Optional을 반환한다.")
    @Test
    void findByNoticeIdAndMemberUuid_fail_withNonExistingNoticeId() {
        // given
        Member member = createAndSaveMember("김다미", "profile.jpg");
        Long nonExistingNoticeId = 999L;

        // when
        Optional<NoticeDismissal> foundDismissal = noticeDismissalRepository.findByNoticeIdAndMemberUuid(nonExistingNoticeId, member.getUuid());

        // then
        assertThat(foundDismissal).isEmpty();
    }

    @DisplayName("존재하지 않는 멤버 UUID로 조회하면 빈 Optional을 반환한다.")
    @Test
    void findByNoticeIdAndMemberUuid_fail_withNonExistingMemberUuid() {
        // given
        Notice notice = createAndSaveNotice("아이유", "내용");
        String nonExistingMemberUuid = UUID.randomUUID().toString();

        // when
        Optional<NoticeDismissal> foundDismissal = noticeDismissalRepository.findByNoticeIdAndMemberUuid(notice.getId(), nonExistingMemberUuid);

        // then
        assertThat(foundDismissal).isEmpty();
    }

    @DisplayName("다른 멤버가 숨김 처리한 공지는 조회되지 않는다.")
    @Test
    void findByNoticeIdAndMemberUuid_edge_notReturnOtherMembersDismissal() {
        // given
        Member member1 = createAndSaveMember("카리나", "profile1.jpg");
        Member member2 = createAndSaveMember("윈터", "profile2.jpg");
        Notice notice = createAndSaveNotice("테스트 공지", "내용");
        createAndSaveDismissal(member2, notice); // member2가 공지를 숨김

        // when
        // member1의 UUID로 조회
        Optional<NoticeDismissal> foundDismissal = noticeDismissalRepository.findByNoticeIdAndMemberUuid(notice.getId(), member1.getUuid());

        // then
        assertThat(foundDismissal).isEmpty();
    }

    private Member createAndSaveMember(String nickname, String profileUrl) {
        Member member = Member.of(nickname, profileUrl);
        return memberRepository.save(member);
    }

    private Notice createAndSaveNotice(String title, String content) {
        Notice notice = Notice.of(title, content, null, 1, LocalDateTime.now(), LocalDateTime.now().plusDays(7));
        return noticeRepository.save(notice);
    }

    private NoticeDismissal createAndSaveDismissal(Member member, Notice notice) {
        NoticeDismissal dismissal = NoticeDismissal.of(member, notice, LocalDateTime.now().plusDays(1));
        return noticeDismissalRepository.save(dismissal);
    }
}