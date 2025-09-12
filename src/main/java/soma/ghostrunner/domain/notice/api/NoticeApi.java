package soma.ghostrunner.domain.notice.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.web.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import soma.ghostrunner.domain.notice.application.NoticeService;
import soma.ghostrunner.domain.notice.api.dto.request.NoticeCreationRequest;
import soma.ghostrunner.domain.notice.api.dto.response.NoticeDetailedResponse;
import soma.ghostrunner.domain.notice.api.dto.request.NoticeDismissRequest;
import soma.ghostrunner.domain.notice.api.dto.request.NoticeUpdateRequest;
import soma.ghostrunner.domain.notice.domain.enums.NoticeType;
import soma.ghostrunner.global.security.jwt.JwtUserDetails;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/notices")
public class NoticeApi {

    private final NoticeService noticeService;

    @GetMapping
    public PagedModel<NoticeDetailedResponse> getAllNotices(
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "type", required = false) String type) {
        NoticeType noticeType = validateAndConvertToNoticeType(type);
        return new PagedModel<>(noticeService.findAllNotices(page, size, noticeType));
    }

    @GetMapping("/active")
    public List<NoticeDetailedResponse> getActiveNotices(
            @AuthenticationPrincipal JwtUserDetails userDetails,
            @RequestParam(value = "type", required = false) String type) {
        NoticeType noticeType = validateAndConvertToNoticeType(type);
        return noticeService.findActiveNotices(userDetails.getUserId(), LocalDateTime.now(), noticeType);
    }

    @GetMapping("/{noticeId}")
    public NoticeDetailedResponse getNotice(@PathVariable("noticeId") Long id) {
        return noticeService.findNotice(id);
    }

    @PostMapping("/{noticeId}/dismissal")
    public void dismiss(@PathVariable("noticeId") Long id,
                        @RequestBody NoticeDismissRequest request,
                        @AuthenticationPrincipal JwtUserDetails userDetails) {
        noticeService.dismiss(id, request, userDetails.getUserId());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Long createNotice(@ModelAttribute @Valid NoticeCreationRequest request) {
        // todo admin만 사용 가능
        return noticeService.saveNotice(request);
    }

    @PatchMapping(value = "/{noticeId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void updateNotice(@PathVariable("noticeId") Long id,
                             @ModelAttribute NoticeUpdateRequest request) {
        // todo admin만 사용 가능
        noticeService.updateNotice(id, request);
    }

    @DeleteMapping("/{noticeId}")
    public void deleteNotice(@PathVariable("noticeId") Long id) {
        // todo admin만 사용 가능
        noticeService.deleteById(id);
    }

    private NoticeType validateAndConvertToNoticeType(String noticeType) {
        if (noticeType == null) return null;
        try {
            return NoticeType.valueOf(noticeType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid notice type: " + noticeType);
        }
    }

}
