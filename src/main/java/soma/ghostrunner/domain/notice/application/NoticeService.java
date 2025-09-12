package soma.ghostrunner.domain.notice.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import soma.ghostrunner.clients.aws.upload.GhostRunnerS3Client;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.notice.api.dto.*;
import soma.ghostrunner.domain.notice.api.dto.request.NoticeCreationRequest;
import soma.ghostrunner.domain.notice.api.dto.request.NoticeDismissRequest;
import soma.ghostrunner.domain.notice.api.dto.request.NoticeUpdateRequest;
import soma.ghostrunner.domain.notice.api.dto.response.NoticeDetailedResponse;
import soma.ghostrunner.domain.notice.dao.NoticeDismissalRepository;
import soma.ghostrunner.domain.notice.dao.NoticeRepository;
import soma.ghostrunner.domain.notice.domain.Notice;
import soma.ghostrunner.domain.notice.domain.NoticeDismissal;
import soma.ghostrunner.domain.notice.domain.enums.NoticeType;
import soma.ghostrunner.domain.notice.exceptions.NoticeNotFoundException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final MemberService memberService;
    private final GhostRunnerS3Client s3Client;
    private final NoticeRepository noticeRepository;
    private final NoticeDismissalRepository noticeDismissalRepository;
    private final NoticeMapper noticeMapper;

    @Transactional(readOnly = true)
    public Notice findNoticeById(Long id) {
        return noticeRepository.findById(id).orElseThrow((NoticeNotFoundException::new));
    }

    @Transactional
    public Long saveNotice(NoticeCreationRequest request) {
        Assert.notNull(request, "공지 생성 request는 null일 수 없음");

        Notice notice = Notice.of(request.getTitle(),
                request.getContent(),
                request.getType(),
                null,
                request.getPriority(),
                request.getStartAt(),
                request.getEndAt()
                );
        Notice savedNotice = noticeRepository.save(notice);
        Long noticeId =  savedNotice.getId();

        // 파일이 존재하는 경우 공지사항 id에 맞게 S3에 업로드
        if(request.getImage() != null) {
            validateFile(request.getImage());
            String imageUrl = s3Client.uploadNoticeImage(request.getImage(), noticeId);
            savedNotice.updateImageUrl(imageUrl); // dirty checking 으로 DB에 반영
        }

        return noticeId;
    }

    @Transactional(readOnly = true)
    public List<NoticeDetailedResponse> findActiveNotices(String memberUuid, NoticeType noticeType) {
        // 노출 기간 내의 공지사항을 숨김 처리 여부와 공지 타입으로 필터링하여 조회
        List<Notice> filteredNotices = noticeRepository.findActiveNoticesForMember(LocalDateTime.now(), memberUuid, noticeType);
        return filteredNotices.stream().map(noticeMapper::toDetailedResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<NoticeDetailedResponse> findAllNotices(int page, int size, NoticeType noticeType) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return noticeRepository.findAllByType(noticeType, pageable).map(noticeMapper::toDetailedResponse);
    }

    @Transactional(readOnly = true)
    public NoticeDetailedResponse findNotice(Long id) {
        Notice notice = findNoticeById(id);
        return noticeMapper.toDetailedResponse(notice);
    }

    @Transactional
    public void dismiss(Long noticeId, NoticeDismissRequest request, String memberUuid) {
        Notice notice = findNoticeById(noticeId);
        Member member = memberService.findMemberByUuid(memberUuid);
        LocalDateTime dismissUntil = calculateDismissalDate(LocalDateTime.now(), request.getDismissDays());

        // 이미 숨김 기록이 존재한다면 INSERT 대신 UPDATE
        Optional<NoticeDismissal> dismissal = noticeDismissalRepository.findByNoticeIdAndMemberUuid(noticeId, memberUuid);
        if (dismissal.isPresent()) {
            dismissal.get().updateDismissUntil(dismissUntil);
        } else {
            noticeDismissalRepository.save(NoticeDismissal.of(member, notice, dismissUntil));
        }
    }

    @Transactional
    public void updateNotice(Long noticeId, NoticeUpdateRequest request) {
        Notice notice = findNoticeById(noticeId);
        for(NoticeUpdateRequest.UpdateAttrs attr : request.getUpdateAttrs()) {
            switch (attr) {
                case TITLE -> notice.updateTitle(request.getTitle());
                case CONTENT -> notice.updateContent(request.getContent());
                case PRIORITY -> notice.updatePriority(request.getPriority());
                case START_AT -> notice.updateStartAt(request.getStartAt());
                case END_AT -> notice.updateEndAt(request.getEndAt());
                case IMAGE -> {
                    validateFile(request.getImage());
                    String imageUrl = s3Client.uploadNoticeImage(request.getImage(), noticeId);
                    notice.updateImageUrl(imageUrl);
                }
            }
        }
    }

    @Transactional
    public void deleteById(Long id) {
        noticeRepository.deleteById(id);
    }

    private LocalDateTime calculateDismissalDate(LocalDateTime now, Integer dismissDays) {
        if(dismissDays == null) return null; // 숨김 기한을 무제한으로 설정한 경우
        LocalDate date = now.toLocalDate();
        LocalDate dismissDate = date.plusDays(dismissDays);
        return dismissDate.atStartOfDay();
    }

    private void validateFile(MultipartFile file) {
        long MAX_FILE_SIZE = 100 * 1024 * 1024; // 100MB
        List<String> allowedExtensions = List.of("jpg", "png", "jpeg");

        Assert.notNull(file, "업로드할 파일이 없습니다.");
        Assert.notNull(file.isEmpty(), "업로드할 파일이 없습니다.");
        if(file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("파일 크기는 " + (int) (MAX_FILE_SIZE / 1024 / 1024) + "MB 이하여야 합니다.");
        }

        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if(ext == null || !allowedExtensions.contains(ext)) {
            throw new IllegalArgumentException("'" + ext + "'는 허용되지 않은 확장자입니다.");
        }
    }

}
