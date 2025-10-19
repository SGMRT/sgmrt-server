package soma.ghostrunner.domain.notification.listener;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import soma.ghostrunner.domain.notification.application.NotificationService;
import soma.ghostrunner.domain.notification.domain.event.NotificationEvent;
import soma.ghostrunner.domain.running.domain.events.CourseRunEvent;

import java.util.List;


/** 외부 이벤트를 리스닝하여 NotificationEvent로 변환해주는 클래스 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final ApplicationEventPublisher applicationEventPublisher;

    /** 본인 코스를 다른 러너가 달린 경우 */
    @TransactionalEventListener
    public void notifyCourseRunEvent(CourseRunEvent courseEvent) {
        // todo 트랜잭션 내부가 아닌 경우?
        if (courseEvent.courseOwnerId().equals(courseEvent.runnerId())) {
            // 러너가 코스 주인 본인인 경우 무시
            return;
        }
        NotificationEvent notificationEvent = new NotificationEvent(
                List.of(courseEvent.courseOwnerId()),
                "누군가 내 코스를 달렸어요!",
                courseEvent.runnerNickname() + " 님이 회원님의 \"" + courseEvent.courseName() + "\" 코스를 완주했습니다.",
                null
        );
        applicationEventPublisher.publishEvent(notificationEvent);
    }

    /** 코스의 본인 최고 기록을 갱신한 경우 */
    public void notifyPersonalTopRecordUpdateOnCourse() {

    }

    /** 새로운 공지사항이 게시된 경우 */
    public void notifyNoticePosted() {

    }

    /** 새로운 이벤트가 게시된 경우 */
    public void notifyEventPosted() {

    }

}
