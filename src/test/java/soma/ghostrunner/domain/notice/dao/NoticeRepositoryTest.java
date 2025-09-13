package soma.ghostrunner.domain.notice.dao;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

import static org.assertj.core.api.Assertions.*;


class NoticeRepositoryTest extends IntegrationTestSupport {

    @Autowired
    NoticeRepository noticeRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    NoticeDismissalRepository noticeDismissalRepository;

    private Member defaultMember;

    @BeforeEach
    void setUp() {
        defaultMember = memberRepository.save(Member.of("카리나", "test-url"));
    }


    @Nested
    @DisplayName("시간 기준 활성화 여부 검증")
    class ActivationByTimeTest {

        @Test
        @DisplayName("현재 활성화된 공지는 조회되어야 한다.")
        void should_find_active_notice() {
            // given
            LocalDateTime baseDateTime = LocalDateTime.of(2025, 9, 15, 10, 0);
            Notice activeNotice = createNotice("활성 공지", baseDateTime.minusDays(1), baseDateTime.plusDays(1));
            noticeRepository.save(activeNotice);

            // when
            List<Notice> result = noticeRepository.findActiveNoticesForMember(baseDateTime, defaultMember.getUuid(), null);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTitle()).isEqualTo("활성 공지");
        }

        @Test
        @DisplayName("아직 시작되지 않은 공지는 조회되지 않아야 한다.")
        void should_not_find_scheduled_notice() {
            // given
            LocalDateTime baseDateTime = LocalDateTime.of(2025, 9, 15, 10, 0);
            Notice scheduledNotice = createNotice("예정 공지", baseDateTime.plusDays(1), baseDateTime.plusDays(2));
            noticeRepository.save(scheduledNotice);

            // when
            List<Notice> result = noticeRepository.findActiveNoticesForMember(baseDateTime, defaultMember.getUuid(), null);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("이미 종료된 공지는 조회되지 않아야 한다.")
        void should_not_find_expired_notice() {
            // given
            LocalDateTime baseDateTime = LocalDateTime.of(2025, 9, 15, 10, 0);
            Notice expiredNotice = createNotice("종료 공지", baseDateTime.minusDays(2), baseDateTime.minusDays(1));
            noticeRepository.save(expiredNotice);

            // when
            List<Notice> result = noticeRepository.findActiveNoticesForMember(baseDateTime, defaultMember.getUuid(), null);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("종료 시간이 없는 영구 활성 공지는 조회되어야 한다.")
        void should_find_permanent_active_notice() {
            // given
            LocalDateTime baseDateTime = LocalDateTime.of(2025, 9, 15, 10, 0);
            Notice permanentNotice = createNotice("영구 공지", baseDateTime.minusDays(1), null);
            noticeRepository.save(permanentNotice);

            // when
            List<Notice> result = noticeRepository.findActiveNoticesForMember(baseDateTime, defaultMember.getUuid(), null);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTitle()).isEqualTo("영구 공지");
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
    }

    @Nested
    @DisplayName("사용자 숨김 여부 검증")
    class DismissalStatusTest {

        @Test
        @DisplayName("사용자가 숨기지 않은 활성 공지는 조회되어야 한다.")
        void should_find_notice_when_not_dismissed() {
            // given
            LocalDateTime baseDateTime = LocalDateTime.of(2025, 9, 15, 10, 0);
            Notice notice = createNotice("닫지 않은 공지", baseDateTime.minusDays(1), baseDateTime.plusDays(1));
            noticeRepository.save(notice);

            // when
            List<Notice> result = noticeRepository.findActiveNoticesForMember(baseDateTime, defaultMember.getUuid(), null);

            // then
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("사용자가 영구적으로 숨긴 활성 공지는 조회되지 않아야 한다.")
        void should_not_find_notice_when_permanently_dismissed() {
            // given
            LocalDateTime baseDateTime = LocalDateTime.of(2025, 9, 15, 10, 0);
            Notice notice = createNotice("영구 닫음 공지", baseDateTime.minusDays(1), baseDateTime.plusDays(1));
            noticeRepository.save(notice);
            noticeDismissalRepository.save(createDismissal(defaultMember, notice, null)); // dismissUntil = null (영구)

            // when
            List<Notice> result = noticeRepository.findActiveNoticesForMember(baseDateTime, defaultMember.getUuid(), null);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("사용자가 숨겼으나 숨김 기간이 만료된 활성 공지는 다시 조회되어야 한다.")
        void should_find_notice_when_dismissal_is_expired() {
            // given
            LocalDateTime baseDateTime = LocalDateTime.of(2025, 9, 15, 10, 0);
            Notice notice = createNotice("닫음 만료 공지", baseDateTime.minusDays(1), baseDateTime.plusDays(1));
            noticeRepository.save(notice);
            // '닫음' 기간이 어제로 만료됨
            noticeDismissalRepository.save(createDismissal(defaultMember, notice, baseDateTime.minusDays(1)));

            // when
            List<Notice> result = noticeRepository.findActiveNoticesForMember(baseDateTime, defaultMember.getUuid(), null);

            // then
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("공지 타입 필터링 검증")
    class NoticeTypeFilterTest {

        @Test
        @DisplayName("특정 타입으로 조회 시 해당 타입의 활성 공지만 조회되어야 한다.")
        void should_find_only_specific_type_notices() {
            // given
            LocalDateTime baseDateTime = LocalDateTime.of(2025, 9, 15, 10, 0);
            Notice eventNotice = createNotice("이벤트 공지", baseDateTime.minusDays(1), baseDateTime.plusDays(1), NoticeType.EVENT);
            Notice updateNotice = createNotice("업데이트 공지", baseDateTime.minusDays(1), baseDateTime.plusDays(1), NoticeType.GENERAL);
            noticeRepository.saveAll(List.of(eventNotice, updateNotice));

            // when
            List<Notice> result = noticeRepository.findActiveNoticesForMember(baseDateTime, defaultMember.getUuid(), NoticeType.EVENT);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTitle()).isEqualTo("이벤트 공지");
        }

        @Test
        @DisplayName("타입을 null로 조회 시 모든 타입의 활성 공지가 조회되어야 한다.")
        void should_find_all_type_notices_when_type_is_null() {
            // given
            LocalDateTime baseDateTime = LocalDateTime.of(2025, 9, 15, 10, 0);
            Notice eventNotice = createNotice("이벤트 공지", baseDateTime.minusDays(1), baseDateTime.plusDays(1), NoticeType.EVENT);
            Notice updateNotice = createNotice("업데이트 공지", baseDateTime.minusDays(1), baseDateTime.plusDays(1), NoticeType.GENERAL);
            noticeRepository.saveAll(List.of(eventNotice, updateNotice));

            // when
            List<Notice> result = noticeRepository.findActiveNoticesForMember(baseDateTime, defaultMember.getUuid(), null);

            // then
            assertThat(result).hasSize(2)
                    .extracting(Notice::getTitle)
                    .containsExactlyInAnyOrder("이벤트 공지", "업데이트 공지");
        }
    }

    private Notice createNotice(String name) {
        return Notice.of(name, "dummy content", "dummy-url");
    }

    private Notice createNotice(String name, LocalDateTime startAt, LocalDateTime endAt) {
        return Notice.of(name , "dummy content", NoticeType.GENERAL, "dummy-url", 0, startAt, endAt);
    }

    private Notice createNotice(String name, LocalDateTime startAt, LocalDateTime endAt, NoticeType type) {
        return Notice.of(name , "dummy content", type, "dummy-url", 0, startAt, endAt);
    }

    private Notice createNotice(String name, Integer priority) {
        return Notice.of(name , "dummy content", NoticeType.GENERAL, "dummy-url", priority, null, null);
    }

    private Member createMember(String name) {
        return Member.of(name, "test-url");
    }

    private NoticeDismissal createDismissal(Member member, Notice notice, LocalDateTime dismissUntil) {
        return NoticeDismissal.of(member, notice, dismissUntil);
    }



    // ---------------------------------------------------------------


}