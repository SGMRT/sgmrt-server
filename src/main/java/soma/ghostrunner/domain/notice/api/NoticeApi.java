package soma.ghostrunner.domain.notice.api;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
import java.util.Collections;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class NoticeApi {

    private final NoticeService noticeService;

    /** * * * * * * * *
     *   사용자용 API   *
     * * * * * * * * **/
    @GetMapping("/v2/notices")
    public PagedModel<NoticeDetailedResponse> getAllNotices(
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "type", required = false) String type) {
        NoticeType noticeType = validateAndConvertToNoticeType(type);
        return new PagedModel<>(noticeService.findAllNotices(page, size, noticeType));
    }

    @GetMapping("/v2/notices/active")
    public List<NoticeDetailedResponse> getActiveNotices(
            @AuthenticationPrincipal JwtUserDetails userDetails,
            @RequestParam(value = "type", required = false) String type) {
        NoticeType noticeType = validateAndConvertToNoticeType(type);
        return noticeService.findActiveNotices(userDetails.getUserId(), LocalDateTime.now(), noticeType);
    }

    @GetMapping("/v2/notices/{noticeId}")
    public NoticeDetailedResponse getNotice(@PathVariable("noticeId") Long id) {
        return noticeService.findNotice(id);
    }

    @PostMapping("/v2/notices/{noticeId}/dismissal")
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
    @PostMapping(value = "/v1/admin/notices", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Long createNotice(@ModelAttribute @Valid NoticeCreationRequest request) {
        return noticeService.saveNotice(request);
    }

    @Operation(summary = "비활성화 상태인 공지사항 조회 (어드민 전용)")
    @AdminOnly
    @GetMapping("/v1/admin/notices/deactivated")
    public List<NoticeDetailedResponse> getDeactivatedNotices() {
        return noticeService.findDeactivatedNotices();
    }

    @Operation(summary = "공지사항 활성화 (어드민 전용)")
    @AdminOnly
    @PostMapping(value = "/v1/admin/notices/activate")
    public List<Long> activateNotices(@RequestBody NoticeActivationRequest request) {
        return noticeService.activateNotices(request.getNoticeIds(), LocalDateTime.now(), request.getEndAt());
    }

    @Operation(summary = "공지사항 비활성화 (어드민 전용)")
    @AdminOnly
    @PostMapping(value = "/v1/admin/notices/deactivate")
    public List<Long> deactivateNotices(@RequestBody NoticeDeactivationRequest request) {
        return noticeService.deactivateNotices(request.getNoticeIds());
    }

    @Operation(summary = "공지사항 수정 (어드민 전용)")
    @AdminOnly
    @PatchMapping(value = "/v1/admin/notices/{noticeId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void updateNotice(@PathVariable("noticeId") Long id,
                             @ModelAttribute NoticeUpdateRequest request) {
        noticeService.updateNotice(id, request);
    }

    @Operation(summary = "공지사항 삭제 (어드민 전용)")
    @AdminOnly
    @DeleteMapping("/v1/admin/notices/{noticeId}")
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

    /** * * * * * * * * *
     *  Deprecated API  *
     * * * * * * * * * **/

//    @Hidden
    @Deprecated(since = "클라 v1.0.3 이하 호환을 위해 남겨둠")
    @GetMapping("/v1/notices")
    public PagedModel<NoticeDetailedResponse> getAllNoticesV1(
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "type", required = false) String type) {
        return dummyNoticePage(page, size);
    }

    private PagedModel<NoticeDetailedResponse> dummyNoticePage(int page, int size) {
        // 고정된 공지사항 페이지 반환
        List<NoticeDetailedResponse> noticeList = Collections.singletonList(dummyNoticeDetailedResponse());
        Page<NoticeDetailedResponse> noticePage = new PageImpl<>(noticeList, PageRequest.of(page, size), 1);
        return new PagedModel<>(noticePage);
    }

//    @Hidden
    @Deprecated(since = "클라 v1.0.3 이하 호환을 위해 남겨둠")
    @GetMapping("/v1/notices/active")
    public List<NoticeDetailedResponse> getActiveNoticesV1(
            @AuthenticationPrincipal JwtUserDetails userDetails,
            @RequestParam(value = "type", required = false) String type) {
        return dummyActiveNoticeList();
    }

    private List<NoticeDetailedResponse> dummyActiveNoticeList() {
        // 고정된 공지사항 리스트 반환
        return Collections.singletonList(dummyNoticeDetailedResponse());
    }

//    @Hidden
    @Deprecated(since = "클라 v1.0.3 이하 호환을 위해 남겨둠")
    @GetMapping("/v1/notices/{noticeId}")
    public NoticeDetailedResponse getNoticeV1(@PathVariable("noticeId") Long id) {
        // 고정된 공지사항 반환
        return dummyNoticeDetailedResponse(id);
    }

    @Hidden
    @Deprecated(since = "클라 v1.0.3 이하 호환을 위해 남겨둠")
    @PostMapping("/v1/notices/{noticeId}/dismissal")
    public void dismissV1(@PathVariable("noticeId") Long id,
                          @RequestBody NoticeDismissRequest request,
                          @AuthenticationPrincipal JwtUserDetails userDetails) {
        // 아무 일도 하지 않음
    }

    private NoticeDetailedResponse dummyNoticeDetailedResponse() {
        return dummyNoticeDetailedResponse(1L);
    }

    private NoticeDetailedResponse dummyNoticeDetailedResponse(long id) {
        return new NoticeDetailedResponse(
                id,
                "앱을 업데이트해주세요!",
                NoticeType.GENERAL,
                null,
                "고스트러너 업데이트가 필요합니다. 앱스토어에서 최신 버전 업데이트 후 이용해주세요!",
                0,
                null,
                null
        );
    }

}
