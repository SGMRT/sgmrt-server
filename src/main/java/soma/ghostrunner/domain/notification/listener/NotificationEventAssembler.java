package soma.ghostrunner.domain.notification.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.notice.domain.event.NoticeActivatedEvent;
import soma.ghostrunner.domain.notification.domain.event.NotificationEvent;
import soma.ghostrunner.domain.running.domain.events.CourseRunEvent;

import java.util.List;

@Component
@RequiredArgsConstructor
public class NotificationEventAssembler {

    private final MemberService memberService;

    public NotificationEvent buildCourseRunEvent(CourseRunEvent runEvent) {
        return new NotificationEvent(
                List.of(runEvent.courseOwnerId()),
                "누군가 내 코스를 달렸어요!",
                runEvent.runnerNickname() + " 님이 회원님의 " + determineCourseName(runEvent.courseName()) + "를 완주했습니다.",
                null
        );
    }

    public NotificationEvent buildPacemakerCreatedEvent(Member member, Course course) {
        return new NotificationEvent(
                List.of(member.getId()),
                "고스티가 완성됐어요",
                determineCourseName(course.getName()) + "에 고스티가 생성됐어요!",
                null
        );
    }

    public NotificationEvent buildSingleEventNoticeEvent(List<NoticeActivatedEvent.NoticeRecord> eventNotices) {
        var notice = eventNotices.get(0);
        return new NotificationEvent(
                allMemberIds(),
                "새로운 이벤트 공지가 등록되었어요.",
                notice.title(),
                null
        );
    }

    public NotificationEvent buildMultiEventNoticeEvent(List<NoticeActivatedEvent.NoticeRecord> eventNotices) {
        return new NotificationEvent(
                allMemberIds(),
                "새로운 이벤트 " + eventNotices.size() + "건이 등록되었어요",
                buildMultiNoticeNotificationContent(eventNotices),
                null
        );
    }

    public NotificationEvent buildSingleNoticeEvent(NoticeActivatedEvent.NoticeRecord notice) {
        return new NotificationEvent(
                allMemberIds(),
                "새로운 공지가 등록되었어요.",
                notice.title(),
                null
        );
    }

    public NotificationEvent buildNoticeEvent(List<NoticeActivatedEvent.NoticeRecord> generalNotices) {
        if (generalNotices.size() == 1) {
            return buildSingleNoticeEvent(generalNotices.get(0));
        } else {
            return buildMultiNoticeEvent(generalNotices);
        }
    }

    public NotificationEvent buildMultiNoticeEvent(List<NoticeActivatedEvent.NoticeRecord> generalNotices) {
        return new NotificationEvent(
                allMemberIds(),
                "새로운 공지 " + generalNotices.size() + "건이 등록되었어요",
                buildMultiNoticeNotificationContent(generalNotices),
                null
        );
    }

    public NotificationEvent buildEventNoticeEvent(List<NoticeActivatedEvent.NoticeRecord> eventNotices) {
        if (eventNotices.size() == 1) {
            return buildSingleEventNoticeEvent(eventNotices);
        } else {
            return buildMultiEventNoticeEvent(eventNotices);
        }
    }

    public String buildMultiNoticeNotificationContent(List<NoticeActivatedEvent.NoticeRecord> generalNotices) {
        StringBuilder contentBuilder = new StringBuilder();
        for(var notice: generalNotices) {
            contentBuilder.append("- ").append(notice.title()).append("\n");
        }
        return contentBuilder.toString().trim();
    }

    public NotificationEvent buildTopRecordUpdatedEvent(CourseRunEvent runEvent) {
        return new NotificationEvent(
                List.of(runEvent.runnerId()),
                "개인 기록 갱신!",
                "축하해요! " + determineCourseName(runEvent.courseName()) + "에서 개인 최고 기록을 갱신했어요!",
                null
        );
    }


    // --- 헬퍼 메소드 ---

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
