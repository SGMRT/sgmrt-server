package soma.ghostrunner.domain.notification.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import soma.ghostrunner.domain.course.application.CourseService;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.notice.domain.enums.NoticeType;
import soma.ghostrunner.domain.notice.domain.event.NoticeActivatedEvent;
import soma.ghostrunner.domain.notification.domain.event.NotificationEvent;
import soma.ghostrunner.domain.running.application.RunningQueryService;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.events.CourseRunEvent;
import soma.ghostrunner.domain.running.domain.events.PacemakerCreatedEvent;

import java.util.List;


/** 외부 이벤트를 리스닝하여 NotificationEvent로 변환해주는 클래스 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final MemberService memberService;
    private final CourseService courseService;
    private final RunningQueryService runningQueryService;

    private final NotificationEventAssembler notificationEventAssembler;
    private final ApplicationEventPublisher applicationEventPublisher;

    /** 본인 코스를 다른 러너가 달린 경우 */
    @TransactionalEventListener
    public void notifyCourseRunEvent(CourseRunEvent runEvent) {
        // 러너가 코스 주인 본인인 경우 무시
        if (runEvent.courseOwnerId().equals(runEvent.runnerId())) {
            return;
        }
        NotificationEvent notificationEvent = notificationEventAssembler.buildCourseRunEvent(runEvent);
        log.info("알림 이벤트 전송 - 회원 '{}'가 회원 '{}'의 코스 '{}'를 달림 (event={})",
                runEvent.runnerId(), runEvent.courseOwnerId(), runEvent.courseId(), notificationEvent);
        applicationEventPublisher.publishEvent(notificationEvent);
    }

    /** 코스의 본인 최고 기록을 갱신한 경우 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    public void notifyCourseTopPersonalRecordUpdate(CourseRunEvent runEvent) {
        // todo: 만약 러닝 이벤트 제출 이후 이 트랜잭션이 시작되기 직전에 새로운 기록이 추가된다면 문제 발생 가능. (단, 현실적으로 거의 불가능한 시나리오)
        // 본인의 이전 기록을 조회한다
        Member member = memberService.findMemberById(runEvent.runnerId());
        List<Running> runHistory = runningQueryService.findLatestRunningsByMember(runEvent.courseId(), member.getUuid(), 2);
        // 이번 기록이 첫 기록인 경우 알림을 보내지 않는다
        if(runHistory.size() < 2) {
            return;
        }
        runHistory.sort((a, b) -> Long.compare(b.getStartedAt(), a.getStartedAt()));
        sendNotificationIfTopRecordUpdated(runEvent, runHistory.get(0), runHistory.get(1));
    }

    private void sendNotificationIfTopRecordUpdated(CourseRunEvent runEvent, Running currentRun, Running previousRun) {
        if(currentRun.getRunningRecord().getDuration() < previousRun.getRunningRecord().getDuration()) {
            // 기록이 개선된 경우 알림을 전송한다
            NotificationEvent notificationEvent = notificationEventAssembler.buildTopRecordUpdatedEvent(runEvent);
            log.info("알림 이벤트 전송 - 회원 '{}'가 코스 '{}'에서 개인 기록 갱신 (event={})",
                    runEvent.runnerId(), runEvent.courseId(), notificationEvent);
            applicationEventPublisher.publishEvent(notificationEvent);
        }
    }

    @TransactionalEventListener
    public void handleNoticeActivatedEvent(NoticeActivatedEvent event) {
        // 공지사항 타입에 따라 일반 공지사항 or 이벤트 공지사항 푸시 알람으로 분기
        List<NoticeActivatedEvent.NoticeRecord> generalNotices = filterNoticeRecords(event, List.of(NoticeType.GENERAL_V2, NoticeType.GENERAL));
        List<NoticeActivatedEvent.NoticeRecord> eventNotices = filterNoticeRecords(event, List.of(NoticeType.EVENT_V2, NoticeType.EVENT));

        if (!generalNotices.isEmpty()) {
            notifyGeneralNotice(generalNotices);
        }
        if (!eventNotices.isEmpty()) {
            notifyEventNotice(eventNotices);
        }
    }

    private List<NoticeActivatedEvent.NoticeRecord> filterNoticeRecords(NoticeActivatedEvent event, List<NoticeType> filterTypes) {
        return event.activatedNotices().stream()
                .filter(notice -> filterTypes.contains(notice.noticeType()))
                .toList();
    }

    /** 새로운 일반 공지사항이 게시된 경우 (즉, 일반 공지사항의 시작시간이 도래한 경우) */
    public void notifyGeneralNotice(List<NoticeActivatedEvent.NoticeRecord> generalNotices) {
        if (generalNotices.isEmpty()) return;
        NotificationEvent notificationEvent = notificationEventAssembler.buildNoticeEvent(generalNotices);
        log.info("알림 이벤트 전송 - 새로운 공지 사항 {}건 게시 (event={})", generalNotices.size(), notificationEvent);
        applicationEventPublisher.publishEvent(notificationEvent);
    }

    /** 새로운 이벤트 공지사항이 게시된 경우 (즉, 이벤트 공지사항의 시작시간이 도래한 경우) */
    public void notifyEventNotice(List<NoticeActivatedEvent.NoticeRecord> eventNotices) {
        if (eventNotices.isEmpty()) return;
        NotificationEvent notificationEvent = notificationEventAssembler.buildEventNoticeEvent(eventNotices);
        log.info("알림 이벤트 전송 - 새로운 이벤트 공지 {}건 게시 (event={})", eventNotices.size(), notificationEvent);
        applicationEventPublisher.publishEvent(notificationEvent);
    }

    /** 페이스메이커가 생성된 경우 */
    @TransactionalEventListener
    public void handlePacemakerCreationEvent(PacemakerCreatedEvent event) {
        Member member = memberService.findMemberByUuid(event.memberUuid());
        Course course = courseService.findCourseById(event.courseId());
        NotificationEvent notificationEvent = notificationEventAssembler.buildPacemakerCreatedEvent(member, course);
        log.info("알림 이벤트 전송 - 회원 '{}'에 코스 '{}'에 페이스메이커 '{}' 생성 완료 (event={})",
                member.getId(), course.getId(), event.pacemakerId(), notificationEvent);
        applicationEventPublisher.publishEvent(notificationEvent);
    }

}
