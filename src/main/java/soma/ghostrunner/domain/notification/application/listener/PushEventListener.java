package soma.ghostrunner.domain.notification.application.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import soma.ghostrunner.domain.course.application.CourseService;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.member.application.MemberService;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.notice.domain.enums.NoticeType;
import soma.ghostrunner.domain.notice.domain.event.NoticeActivatedEvent;
import soma.ghostrunner.domain.notification.application.PushContentAssembler;
import soma.ghostrunner.domain.notification.application.PushService;
import soma.ghostrunner.domain.notification.application.dto.PushContent;
import soma.ghostrunner.domain.running.application.RunningQueryService;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.events.CourseRunEvent;
import soma.ghostrunner.domain.running.domain.events.PacemakerCreatedEvent;

import java.util.List;
import java.util.Optional;


/** 외부 이벤트를 리스닝하여 PushNotificationService를 호출하는 클래스 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushEventListener {

    private final PushService pushService;
    private final PushContentAssembler pushContentAssembler;

    private final MemberService memberService;
    private final CourseService courseService;
    private final RunningQueryService runningQueryService;

    /** 본인 코스를 다른 러너가 달린 경우 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyCourseRunEvent(CourseRunEvent runEvent) {
        // 러너가 코스 주인 본인인 경우 무시
        if (runEvent.courseOwnerId().equals(runEvent.runnerId())) {
            return;
        }
        PushContent pushContent = pushContentAssembler.buildCourseRunEvent(runEvent);
        log.info("알림 이벤트 전송 - 회원 '{}'가 회원 '{}'의 코스 '{}'를 달림 (event={})",
                runEvent.runnerId(), runEvent.courseOwnerId(), runEvent.courseId(), pushContent);
        pushService.push(runEvent.courseOwnerId(), pushContent);
    }

    /** 코스의 본인 최고 기록을 갱신한 경우 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyCourseTopPersonalRecordUpdate(CourseRunEvent runEvent) {
        // 본인의 이전 기록을 조회한다
        Member member = memberService.findMemberById(runEvent.runnerId());
        Optional<Running> previousBestRun = runningQueryService.findMemberBestRunBefore(runEvent.courseId(), member.getUuid(), runEvent.runStartedAt());
        // 이번 기록이 첫 기록인 경우 알림을 보내지 않는다
        if(previousBestRun.isEmpty()) return;
        if(topRecordUpdated(previousBestRun, runEvent.runDuration())) {
            // 기록이 개선된 경우 알림을 전송한다
            PushContent command = pushContentAssembler. buildTopRecordUpdatedEvent(runEvent);
            log.info("알림 이벤트 전송 - 회원 '{}'가 코스 '{}'에서 개인 기록 갱신 (event={})",
                    runEvent.runnerId(), runEvent.courseId(), command);
            pushService.push(runEvent.runnerId(), command);
        }
    }

    private boolean topRecordUpdated(Optional<Running> prevRunning, Long newDuration) {
        return prevRunning.orElseThrow().getRunningRecord().getDuration() > newDuration;
    }

    /** 새로운 공지사항이 공개된 경우 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
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
    private void notifyGeneralNotice(List<NoticeActivatedEvent.NoticeRecord> generalNotices) {
        if (generalNotices.isEmpty()) return;
        PushContent pushContent = pushContentAssembler.buildNoticeEvent(generalNotices);
        log.info("알림 이벤트 전송 - 새로운 공지 사항 {}건 게시 (event={})", generalNotices.size(), pushContent);
        pushService.broadcast(pushContent);
    }

    /** 새로운 이벤트 공지사항이 게시된 경우 (즉, 이벤트 공지사항의 시작시간이 도래한 경우) */
    private void notifyEventNotice(List<NoticeActivatedEvent.NoticeRecord> eventNotices) {
        if (eventNotices.isEmpty()) return;
        PushContent pushContent = pushContentAssembler.buildEventNoticeEvent(eventNotices);
        log.info("알림 이벤트 전송 - 새로운 이벤트 공지 {}건 게시 (event={})", eventNotices.size(), pushContent);
        pushService.broadcast(pushContent);
    }

    /** 페이스메이커가 생성된 경우 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePacemakerCreationEvent(PacemakerCreatedEvent event) {
        Member member = memberService.findMemberByUuid(event.memberUuid());
        Course course = courseService.findCourseById(event.courseId());
        if (course.getName() == null) return; // 이름 없는 코스인 경우 알림을 보내지 않음 (공개 코스 (= 이름 설정 필수)에만 페이스메이커 생성 가능하므로 사실 발생할 일은 거의 없음)
        PushContent pushContent = pushContentAssembler.buildPacemakerCreatedEvent(course);
        log.info("알림 이벤트 전송 - 회원 '{}'에 코스 '{}'에 페이스메이커 '{}' 생성 완료 (event={})",
                member.getId(), course.getId(), event.pacemakerId(), pushContent);
        pushService.push(member.getId(), pushContent);
    }

}
