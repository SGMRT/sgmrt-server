package soma.ghostrunner.domain.notice.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.web.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import soma.ghostrunner.domain.notice.api.dto.request.*;
import soma.ghostrunner.domain.notice.application.NoticeService;
import soma.ghostrunner.domain.notice.api.dto.response.NoticeDetailedResponse;
import soma.ghostrunner.domain.notice.domain.enums.NoticeType;
import soma.ghostrunner.global.common.validator.auth.AdminOnly;
import soma.ghostrunner.global.security.jwt.JwtUserDetails;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1")
public class NoticeApi {

    private final NoticeService noticeService;

    @GetMapping("/notices")
    public PagedModel<NoticeDetailedResponse> getAllNotices(
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "type", required = false) String type) {
        NoticeType noticeType = validateAndConvertToNoticeType(type);
        return new PagedModel<>(noticeService.findAllNotices(page, size, noticeType));
    }

    @GetMapping("/notices/active")
    public List<NoticeDetailedResponse> getActiveNotices(
            @AuthenticationPrincipal JwtUserDetails userDetails,
            @RequestParam(value = "type", required = false) String type) {
        NoticeType noticeType = validateAndConvertToNoticeType(type);
        return noticeService.findActiveNotices(userDetails.getUserId(), LocalDateTime.now(), noticeType);
    }

    @GetMapping("/notices/{noticeId}")
    public NoticeDetailedResponse getNotice(@PathVariable("noticeId") Long id) {
        return noticeService.findNotice(id);
    }

    @PostMapping("/notices/{noticeId}/dismissal")
    public void dismiss(@PathVariable("noticeId") Long id,
                        @RequestBody NoticeDismissRequest request,
                        @AuthenticationPrincipal JwtUserDetails userDetails) {
        noticeService.dismiss(id, request, userDetails.getUserId());
    }

    /** * * * * * * * *
     *   어드민용 API   *
     * * * * * * * * **/

    @Operation(summary = "공지사항 저장 (어드민 전용)")
    @AdminOnly
    @PostMapping(value = "/admin/notices", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Long createNotice(@ModelAttribute @Valid NoticeCreationRequest request) {
        return noticeService.saveNotice(request);
    }

    @Operation(summary = "비활성화 상태인 공지사항 조회 (어드민 전용)")
    @AdminOnly
    @GetMapping("/admin/notices/deactivated")
    public List<NoticeDetailedResponse> getDeactivatedNotices() {
        return noticeService.findDeactivatedNotices();
    }

    @Operation(summary = "공지사항 활성화 (어드민 전용)")
    @AdminOnly
    @PostMapping(value = "/admin/notices/activate")
    public List<Long> activateNotices(@RequestBody NoticeActivationRequest request) {
        return noticeService.activateNotices(request.getNoticeIds(), LocalDateTime.now(), request.getEndAt());
    }

    @Operation(summary = "공지사항 비활성화 (어드민 전용)")
    @AdminOnly
    @PostMapping(value = "/admin/notices/deactivate")
    public List<Long> deactivateNotices(@RequestBody NoticeDeactivationRequest request) {
        return noticeService.deactivateNotices(request.getNoticeIds());
    }

    @Operation(summary = "공지사항 수정 (어드민 전용)")
    @AdminOnly
    @PatchMapping(value = "/admin/notices/{noticeId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void updateNotice(@PathVariable("noticeId") Long id,
                             @ModelAttribute NoticeUpdateRequest request) {
        noticeService.updateNotice(id, request);
    }

    @Operation(summary = "공지사항 삭제 (어드민 전용)")
    @AdminOnly
    @DeleteMapping("/admin/notices/{noticeId}")
    public void deleteNotice(@PathVariable("noticeId") Long id) {
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
