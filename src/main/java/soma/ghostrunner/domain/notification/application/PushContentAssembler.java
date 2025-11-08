package soma.ghostrunner.domain.notification.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.notice.domain.event.NoticeActivatedEvent;
import soma.ghostrunner.domain.notification.domain.deeplink.DeepLinkUrls;
import soma.ghostrunner.domain.notification.application.dto.PushContent;
import soma.ghostrunner.domain.running.domain.events.CourseRunEvent;
import soma.ghostrunner.global.common.versioning.VersionRange;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PushContentAssembler {

    private final MemberService memberService;
    private static final String DEEPLINK_ITEM_KEY = "urls";
    private static final VersionRange NOTICE_AVAILABLE_VERSION_RANGE = VersionRange.ALL_VERSIONS; // 임시 허용
//    private static final VersionRange NOTICE_AVAILABLE_VERSION_RANGE = VersionRange.of(
//            // 공지사항 관련 푸쉬는 앱 버전 1.0.3 이상만 전송
//            SemanticVersion.of("1.0.3"),
//            VersionRange.Operator.GREATER_THAN_OR_EQUALS);

    /** 본인 코스를 다른 러너가 달린 경우 */
    public PushContent buildCourseRunEvent(CourseRunEvent runEvent) {
        return PushContent.of(
                "누군가 내 코스를 달렸어요!",
                runEvent.runnerNickname() + " 님이 회원님의 " + determineCourseName(runEvent.courseName()) + "를 완주했습니다.",
                Map.of(DEEPLINK_ITEM_KEY, DeepLinkUrls.courseRunUrlItems(runEvent.courseId()))
        );
    }

    public PushContent buildPacemakerCreatedEvent(Course course) {
        return PushContent.of(
                "고스티가 완성됐어요",
                determineCourseName(course.getName()) + "에 고스티가 생성됐어요!",
                Map.of(DEEPLINK_ITEM_KEY, DeepLinkUrls.pacemakerCreationUrlItems(course.getId()))
        );
    }

    /** 코스의 본인 최고 기록을 갱신한 경우 */
    public PushContent buildTopRecordUpdatedEvent(CourseRunEvent runEvent) {
        return PushContent.of(
                "개인 기록 갱신!",
                "축하해요! " + determineCourseName(runEvent.courseName()) + "에서 개인 최고 기록을 갱신했어요!",
                Map.of(DEEPLINK_ITEM_KEY, DeepLinkUrls.topRecordUpdateUrlItems(runEvent.courseId()))
        );
    }

    /** 새로운 이벤트가 공개된 경우 (1개) */
    public PushContent buildSingleEventNoticeEvent(List<NoticeActivatedEvent.NoticeRecord> eventNotices) {
        var notice = eventNotices.get(0);
        return PushContent.of(
                "새로운 이벤트 공지가 등록되었어요",
                notice.title(),
                Map.of(DEEPLINK_ITEM_KEY, DeepLinkUrls.eventNoticeUrlItems(notice.noticeId())),
                NOTICE_AVAILABLE_VERSION_RANGE
        );
    }

    /** 새로운 이벤트가 공개된 경우 (2개 이상) */
    public PushContent buildMultiEventNoticeEvent(List<NoticeActivatedEvent.NoticeRecord> eventNotices) {
        return PushContent.of(
                "새로운 이벤트 " + eventNotices.size() + "건이 등록되었어요",
                buildMultiNoticeNotificationContent(eventNotices),
                Map.of(DEEPLINK_ITEM_KEY, DeepLinkUrls.multiNoticeUrlItems()),
                NOTICE_AVAILABLE_VERSION_RANGE
        );
    }

    /** 새로운 공지사항이 공개된 경우 (1개) */
    public PushContent buildSingleNoticeEvent(NoticeActivatedEvent.NoticeRecord notice) {
        return PushContent.of(
                "새로운 공지가 등록되었어요",
                notice.title(),
                Map.of(DEEPLINK_ITEM_KEY, DeepLinkUrls.generalNoticeUrlItems(notice.noticeId())),
                NOTICE_AVAILABLE_VERSION_RANGE
        );
    }

    /** 새로운 공지사항이 공개된 경우 (2개 이상) */
    public PushContent buildMultiNoticeEvent(List<NoticeActivatedEvent.NoticeRecord> generalNotices) {
        return PushContent.of(
                "새로운 공지 " + generalNotices.size() + "건이 등록되었어요",
                buildMultiNoticeNotificationContent(generalNotices),
                Map.of(DEEPLINK_ITEM_KEY, DeepLinkUrls.multiNoticeUrlItems()),
                NOTICE_AVAILABLE_VERSION_RANGE
        );
    }

    public PushContent buildNoticeEvent(List<NoticeActivatedEvent.NoticeRecord> generalNotices) {
        if (generalNotices.size() == 1) {
            return buildSingleNoticeEvent(generalNotices.get(0));
        } else {
            return buildMultiNoticeEvent(generalNotices);
        }
    }

    public PushContent buildEventNoticeEvent(List<NoticeActivatedEvent.NoticeRecord> eventNotices) {
        if (eventNotices.size() == 1) {
            return buildSingleEventNoticeEvent(eventNotices);
        } else {
            return buildMultiEventNoticeEvent(eventNotices);
        }
    }

    // --- 헬퍼 메소드 ---

    private String buildMultiNoticeNotificationContent(List<NoticeActivatedEvent.NoticeRecord> generalNotices) {
        StringBuilder contentBuilder = new StringBuilder();
        for(var notice: generalNotices) {
            contentBuilder.append("- ").append(notice.title()).append("\n");
        }
        return contentBuilder.toString().trim();
    }

    /** courseName이 '~코스'로 끝나지 않는다면 뒤에 코스를 붙인다. */
    private static String determineCourseName(String courseName) {
        if (courseName.endsWith("코스")) {
            return courseName;
        } else {
            return courseName + " 코스";
        }
    }

    private List<Long> allMemberIds() {
        return memberService.findAllMemberIds();
    }

}
