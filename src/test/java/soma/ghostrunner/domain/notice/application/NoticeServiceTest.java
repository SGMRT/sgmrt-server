package soma.ghostrunner.domain.notice.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MultipartFile;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.notice.api.dto.request.NoticeActivationRequest;
import soma.ghostrunner.domain.notice.exceptions.NoticeTypeDeprecatedException;
import soma.ghostrunner.global.clients.aws.s3.GhostRunnerS3Client;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.notice.api.dto.request.NoticeCreationRequest;
import soma.ghostrunner.domain.notice.api.dto.request.NoticeDismissRequest;
import soma.ghostrunner.domain.notice.api.dto.request.NoticeUpdateRequest;
import soma.ghostrunner.domain.notice.api.dto.response.NoticeDetailedResponse;
import soma.ghostrunner.domain.notice.dao.NoticeDismissalRepository;
import soma.ghostrunner.domain.notice.dao.NoticeRepository;
import soma.ghostrunner.domain.notice.domain.Notice;
import soma.ghostrunner.domain.notice.domain.NoticeDismissal;
import soma.ghostrunner.domain.notice.domain.enums.NoticeType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class NoticeServiceTest extends IntegrationTestSupport {

    @Autowired private NoticeService noticeService;
    @Autowired private NoticeRepository noticeRepository;
    @Autowired private NoticeDismissalRepository noticeDismissalRepository;
    @Autowired private MemberRepository memberRepository;
    @MockitoBean private GhostRunnerS3Client s3Client; // S3 클라이언트는 모킹 처리

    private final LocalDateTime NOW = LocalDateTime.of(2025, 8, 8, 12, 0);

    @DisplayName("새로운 공지사항을 성공적으로 생성한다. 시작일과 종료일은 null로 설정된다.")
    @Test
    void saveNotice_success() {
        // given
        NoticeCreationRequest request = createNoticeCreationRequest("공지 제목", "공지 내용", null);

        // when
        Long noticeId = noticeService.saveNotice(request);

        // then
        assertThat(noticeId).isNotNull();
        Optional<Notice> foundNotice = noticeRepository.findById(noticeId);
        assertThat(foundNotice).isPresent();
        assertThat(foundNotice.get().getTitle()).isEqualTo("공지 제목");
        assertThat(foundNotice.get().getContent()).isEqualTo("공지 내용");
        assertThat(foundNotice.get().getStartAt()).isNull();
        assertThat(foundNotice.get().getEndAt()).isNull();
    }

    @DisplayName("이미지와 함께 새로운 공지사항을 성공적으로 생성한다.")
    @Test
    void saveNotice_withImage_success() {
        // given
        MockMultipartFile image = new MockMultipartFile("image", "test.png", "image/png", "test".getBytes());
        NoticeCreationRequest request = createNoticeCreationRequest("이미지 공지", "이미지 내용", image);
        given(s3Client.uploadMultipartFile(any(MultipartFile.class), any(String.class))).willReturn("http://s3-test-url/image.png");

        // when
        Long noticeId = noticeService.saveNotice(request);

        // then
        assertThat(noticeId).isNotNull();
        Notice foundNotice = noticeRepository.findById(noticeId).orElseThrow();
        assertThat(foundNotice.getImageUrl()).isEqualTo("http://s3-test-url/image.png");
        verify(s3Client, times(1)).uploadMultipartFile(any(MultipartFile.class), any(String.class));
    }

    @DisplayName("Deprecated된 타입의 공지사항 생성을 시도하면 예외가 발생한다.")
    @ParameterizedTest
    @EnumSource(value = NoticeType.class, names = {"GENERAL", "EVENT"}, mode = EnumSource.Mode.INCLUDE)
    void saveNotice_deprecatedTypes(NoticeType deprecatedType) {
        // given
        NoticeCreationRequest request = NoticeCreationRequest.builder()
                .title("제목")
                .content("내용")
                .type(deprecatedType)
                .priority(1)
                .build();

        // when & then
        assertThatThrownBy(() -> noticeService.saveNotice(request))
                .isInstanceOf(NoticeTypeDeprecatedException.class);
    }

    @DisplayName("유효하지 않은 파일 확장자로 공지사항 생성을 시도하면 예외가 발생한다.")
    @Test
    void saveNotice_withInvalidFileExtension_fail() {
        // given
        MockMultipartFile invalidFile = new MockMultipartFile("file", "test.txt", "text/plain", "test".getBytes());
        NoticeCreationRequest request = createNoticeCreationRequest("제목", "내용", invalidFile);

        // when & then
        assertThatThrownBy(() -> noticeService.saveNotice(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("허용되지 않은 확장자입니다.");
    }

    @DisplayName("활성 공지사항 조회 시 숨김 처리되지 않은 공지를 조회한다.")
    @Test
    void findActiveNotices_success() {
        // given
        Member member1 = createAndSaveMember("user1");
        Member member2 = createAndSaveMember("user2");

        Notice activeNotice = createAndSaveNotice("활성 공지", NOW.minusDays(1), NOW.plusDays(1));
        Notice futureNotice = createAndSaveNotice("미래 공지", NOW.plusDays(1), NOW.plusDays(2));
        Notice expiredNotice = createAndSaveNotice("만료 공지", NOW.minusDays(2), NOW.minusDays(1));
        Notice dismissedNotice = createAndSaveNotice("숨김 공지", NOW.minusDays(1), NOW.plusDays(1));
        Notice permanentlyDismissedNotice = createAndSaveNotice("영구 숨김 공지", NOW.minusDays(1), NOW.plusDays(1));

        createAndSaveDismissal(member1, dismissedNotice, NOW.plusDays(3)); // 아직 숨김 기간임
        createAndSaveDismissal(member1, permanentlyDismissedNotice, null); // 영구 숨김

        // when
        List<NoticeDetailedResponse> noticesForMember1 = noticeService.findActiveNotices(member1.getUuid(), NOW, null);
        List<NoticeDetailedResponse> noticesForMember2 = noticeService.findActiveNotices(member2.getUuid(), NOW, null);

        // then
        assertThat(noticesForMember1).hasSize(1).extracting("title").containsExactly("활성 공지");
        assertThat(noticesForMember2).hasSize(3).extracting("title").containsExactlyInAnyOrder("활성 공지", "숨김 공지", "영구 숨김 공지");
    }

    @DisplayName("활성 공지사항 조회 시 비활성 상태인 공지사항은 포함되지 않는다. (시작일과 종료일이 null인 공지)")
    @Test
    void findActiveNotices_excludesDeactivatedNotices() {
        // given
        Member member = createAndSaveMember("멋쟁이 토마토");
        Notice deactivatedNotice = createAndSaveNotice("비활성 공지", null, null);
        Notice activeNotice = createAndSaveNotice("활성 공지", NOW.minusDays(1), NOW.plusDays(1));
        // when
        List<NoticeDetailedResponse> notices = noticeService.findActiveNotices(member.getUuid(), NOW, null);
        // then
        assertThat(notices).hasSize(1).extracting("title").containsExactly("활성 공지");
    }

    @DisplayName("공지사항을 활성화하면 시작기간 및 종료기간이 설정된다.")
    @Test
    void activateNotices_success() {
        // given
        Notice notice1 = createAndSaveNotice("비활성 공지 1", null, null);
        Notice notice2 = createAndSaveNotice("비활성 공지 2", null, null);

        // when
        List<Long> activatedIds = noticeService.activateNotices(List.of(notice1.getId(), notice2.getId()), NOW, NOW.plusDays(7));

        // then
        assertThat(activatedIds).containsExactlyInAnyOrder(notice1.getId(), notice2.getId());
        List<Notice> activatedNotices = noticeRepository.findAllById(activatedIds);
        assertThat(activatedNotices).allMatch(n -> n.getStartAt().isEqual(NOW) && n.getEndAt().isEqual(NOW.plusDays(7)));
    }

    @DisplayName("공지사항을 비활성화하면 시작기간 및 종료기간이 null로 설정된다.")
    @Test
    void deactivateNotices_success() {
        // given
        Notice notice1 = createAndSaveNotice("활성 공지 1", NOW.minusDays(1), NOW.plusDays(1));
        Notice notice2 = createAndSaveNotice("활성 공지 2", NOW.minusDays(2), NOW.plusDays(2));

        // when
        List<Long> deactivatedIds = noticeService.deactivateNotices(List.of(notice1.getId(), notice2.getId()));

        // then
        assertThat(deactivatedIds).containsExactlyInAnyOrder(notice1.getId(), notice2.getId());
        List<Notice> deactivatedNotices = noticeRepository.findAllById(deactivatedIds);
        assertThat(deactivatedNotices).allMatch(n -> n.getStartAt() == null && n.getEndAt() == null);
    }

    @DisplayName("활성 공지사항 조회 시 특정 타입의 공지만 조회한다.")
    @ParameterizedTest
    @EnumSource(NoticeType.class)
    void findActiveNotices_byType(NoticeType type) {
        // given
        Member member = createAndSaveMember("user-type");
        createAndSaveNotice("일반 공지", NoticeType.GENERAL, NOW.minusDays(1), NOW.plusDays(1));
        createAndSaveNotice("이벤트 공지", NoticeType.EVENT, NOW.minusDays(1), NOW.plusDays(1));
        createAndSaveNotice("일반 공지 V2", NoticeType.GENERAL_V2, NOW.minusDays(1), NOW.plusDays(1));
        createAndSaveNotice("이벤트 공지 V2", NoticeType.EVENT_V2, NOW.minusDays(1), NOW.plusDays(1));
        Notice hiddenNotice = createAndSaveNotice("숨긴 공지", NoticeType.EVENT_V2, NOW.minusDays(1), NOW.plusDays(1));
        createAndSaveDismissal(member, hiddenNotice, null);

        // when
        List<NoticeDetailedResponse> notices = noticeService.findActiveNotices(member.getUuid(), NOW, type);

        // then
        assertThat(notices).hasSize(1);
        assertThat(notices).allMatch(n -> n.type() == type);
    }

    @DisplayName("모든 공지사항을 페이지네이션하여 조회한다.")
    @Test
    void findAllNotices_success() {
        // given
        createAndSaveNotice("일반 공지", NoticeType.GENERAL_V2, NOW.minusDays(1), NOW.plusDays(1));
        createAndSaveNotice("이벤트 공지", NoticeType.EVENT_V2, NOW.minusDays(1), NOW.plusDays(2));

        // when
        Page<NoticeDetailedResponse> notices = noticeService.findAllNotices(0, 10, null);

        // then
        assertThat(notices.getTotalElements()).isEqualTo(2);
        assertThat(notices.getContent()).extracting("title").contains("일반 공지", "이벤트 공지");
    }

    @DisplayName("특정 공지사항을 ID로 조회한다.")
    @ParameterizedTest
    @EnumSource(NoticeType.class)
    void findAllNotices_byType(NoticeType type) {
        // given
        createAndSaveNotice("일반 공지", NoticeType.GENERAL, NOW, NOW.plusDays(1));
        createAndSaveNotice("이벤트 공지", NoticeType.EVENT, NOW, NOW.plusDays(2));
        createAndSaveNotice("일반 공지 V2", NoticeType.GENERAL_V2, NOW, NOW.plusDays(1));
        createAndSaveNotice("이벤트 공지 V2", NoticeType.EVENT_V2, NOW, NOW.plusDays(2));

        // when
        Page<NoticeDetailedResponse> notices = noticeService.findAllNotices(0, 10, type);

        // then
        assertThat(notices.getTotalElements()).isEqualTo(1);
        assertThat(notices.getContent()).allMatch(n -> n.type() == type);
    }

    @DisplayName("비활성화된 공지사항을 조회한다.")
    @Test
    void findDeactivatedNotices_success() {
        // given
        createAndSaveNotice("활성 공지", NOW.minusDays(1), NOW.plusDays(1));
        createAndSaveNotice("비활성 공지 1", null, null);
        createAndSaveNotice("비활성 공지 2", null, null);

        // when
        List<NoticeDetailedResponse> deactivatedNotices = noticeService.findDeactivatedNotices();

        // then
        assertThat(deactivatedNotices).hasSize(2);
        assertThat(deactivatedNotices).extracting("title").containsExactlyInAnyOrder("비활성 공지 1", "비활성 공지 2");
    }

    @DisplayName("공지사항을 '오늘 하루 보지 않기'로 설정한다.")
    @Test
    void dismiss_forOneDay_success() {
        // given
        Member member = createAndSaveMember("user-dismiss");
        Notice notice = createAndSaveNotice("숨길 공지", NOW, NOW.plusDays(1));
        NoticeDismissRequest request = new NoticeDismissRequest(1);

        // when
        noticeService.dismiss(notice.getId(), request, member.getUuid());

        // then
        Optional<NoticeDismissal> dismissal = noticeDismissalRepository.findByNoticeIdAndMemberUuid(notice.getId(), member.getUuid());
        assertThat(dismissal).isPresent();
        assertThat(dismissal.get().getDismissUntil().toLocalDate()).isEqualTo(LocalDate.now().plusDays(1));
    }

    @DisplayName("공지사항을 '앞으로 보지 않기'로 설정한다.")
    @Test
    void dismiss_forever_success() {
        // given
        Member member = createAndSaveMember("user-dismiss");
        Notice notice = createAndSaveNotice("숨길 공지", NOW, NOW.plusDays(1));
        NoticeDismissRequest request = new NoticeDismissRequest(null);

        // when
        noticeService.dismiss(notice.getId(), request, member.getUuid());

        // then
        Optional<NoticeDismissal> dismissal = noticeDismissalRepository.findByNoticeIdAndMemberUuid(notice.getId(), member.getUuid());
        assertThat(dismissal).isPresent();
        assertThat(dismissal.get().getDismissUntil()).isNull();
    }

    @DisplayName("이미 숨김 처리한 공지사항의 숨김 기간을 연장한다.")
    @Test
    void dismiss_updateExisting_success() {
        // given
        Member member = createAndSaveMember("user-dismiss-update");
        Notice notice = createAndSaveNotice("숨김 연장할 공지", NOW, NOW.plusDays(10));

        NoticeDismissal existingDismissal = createAndSaveDismissal(member, notice, NOW.plusDays(1));
        NoticeDismissRequest request = new NoticeDismissRequest(7);

        // when
        noticeService.dismiss(notice.getId(), request, member.getUuid());

        // then
        Optional<NoticeDismissal> updatedDismissal = noticeDismissalRepository.findById(existingDismissal.getId());
        assertThat(updatedDismissal).isPresent();
        assertThat(updatedDismissal.get().getDismissUntil().toLocalDate()).isEqualTo(LocalDate.now().plusDays(7));
    }

    @DisplayName("이미 숨김 처리한 공지사항의 숨김 기간을 무제한으로 연장한다.")
    @Test
    void dismiss_updateExisting_forever_success() {
        // given
        Member member = createAndSaveMember("user-dismiss-update");
        Notice notice = createAndSaveNotice("숨김 연장할 공지", NOW, NOW.plusDays(10));

        NoticeDismissal existingDismissal = createAndSaveDismissal(member, notice, NOW.plusDays(1));
        NoticeDismissRequest request = new NoticeDismissRequest(null);

        // when
        noticeService.dismiss(notice.getId(), request, member.getUuid());

        // then
        Optional<NoticeDismissal> updatedDismissal = noticeDismissalRepository.findById(existingDismissal.getId());
        assertThat(updatedDismissal).isPresent();
        assertThat(updatedDismissal.get().getDismissUntil()).isNull();
    }

    @DisplayName("공지사항의 제목과 내용을 수정한다.")
    @Test
    void updateNotice_titleAndContent_success() {
        // given
        Notice notice = createAndSaveNotice("기존 제목", NOW, NOW.plusDays(7));
        NoticeUpdateRequest request = NoticeUpdateRequest.builder()
                .title("새로운 제목")
                .content("새로운 내용")
                .updateAttrs(Set.of(NoticeUpdateRequest.UpdateAttrs.TITLE, NoticeUpdateRequest.UpdateAttrs.CONTENT))
                .build();

        // when
        noticeService.updateNotice(notice.getId(), request);

        // then
        Notice updatedNotice = noticeRepository.findById(notice.getId()).orElseThrow();
        assertThat(updatedNotice.getTitle()).isEqualTo("새로운 제목");
        assertThat(updatedNotice.getContent()).isEqualTo("새로운 내용");
    }

    @DisplayName("공지 수정 시 수정 요청 필드 목록이 비어있으면 아무것도 변경되지 않는다.")
    @Test
    void updateNotice_emptyUpdateAttrs_noChange() {
        // given
        String originalTitle = "원본 제목";
        Notice notice = createAndSaveNotice(originalTitle, NOW, NOW.plusDays(7));
        NoticeUpdateRequest request = NoticeUpdateRequest.builder()
                .title("바꾸려는 제목")
                .updateAttrs(Set.of()) // 비어있는 Set
                .build();

        // when
        noticeService.updateNotice(notice.getId(), request);

        // then
        Notice notUpdatedNotice = noticeRepository.findById(notice.getId()).orElseThrow();
        assertThat(notUpdatedNotice.getTitle()).isEqualTo(originalTitle);
    }

    @DisplayName("공지사항을 성공적으로 삭제한다.")
    @Test
    void deleteById_success() {
        // given
        Notice notice = createAndSaveNotice("삭제될 공지", NOW, NOW.plusDays(1));

        // when
        noticeService.deleteById(notice.getId());

        // then
        Optional<Notice> deletedNotice = noticeRepository.findById(notice.getId());
        assertThat(deletedNotice).isEmpty();
    }


    // --- Helper Methods ---

    private Member createAndSaveMember(String nickname) {
        return memberRepository.save(Member.of(nickname, "profile.jpg"));
    }

    private Notice createAndSaveNotice(String title, NoticeType type, LocalDateTime start, LocalDateTime end) {
        return noticeRepository.save(Notice.of(title, "내용", type, null, 1, start, end));
    }

    private Notice createAndSaveNotice(String title, LocalDateTime start, LocalDateTime end) {
        return noticeRepository.save(Notice.of(title, "내용", NoticeType.GENERAL_V2, null, 1, start, end));
    }

    private NoticeDismissal createAndSaveDismissal(Member member, Notice notice, LocalDateTime dismissUntil) {
        return noticeDismissalRepository.save(NoticeDismissal.of(member, notice, dismissUntil));
    }

    private NoticeCreationRequest createNoticeCreationRequest(String title, String content, MultipartFile image) {
        return NoticeCreationRequest.builder()
                .title(title)
                .content(content)
                .image(image)
                .priority(1)
                .build();
    }

}