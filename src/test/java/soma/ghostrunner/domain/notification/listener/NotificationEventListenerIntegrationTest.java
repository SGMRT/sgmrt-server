package soma.ghostrunner.domain.notification.listener;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import soma.ghostrunner.DatabaseCleanserExtension;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.notice.domain.Notice;
import soma.ghostrunner.domain.notice.domain.enums.NoticeType;
import soma.ghostrunner.domain.notice.domain.event.NoticeActivatedEvent;
import soma.ghostrunner.domain.notification.application.NotificationService;
import soma.ghostrunner.domain.notification.domain.event.NotificationCommand;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;
import soma.ghostrunner.domain.running.domain.events.CourseRunEvent;
import soma.ghostrunner.domain.running.domain.events.PacemakerCreatedEvent;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(DatabaseCleanserExtension.class)
@RecordApplicationEvents
class NotificationEventListenerIntegrationTest extends IntegrationTestSupport {

    @Autowired ApplicationEvents applicationEvents;

    @Autowired MemberRepository memberRepository;
    @Autowired RunningRepository runningRepository;
    @Autowired CourseRepository courseRepository;

    @MockitoBean NotificationService notificationService; // 외부 전송 막기 위해 목으로 대체

    @Autowired ApplicationEventPublisher eventPublisher;
    @Autowired PlatformTransactionManager transactionManager;
    private TransactionTemplate transactionTemplate;

    private Member defaultMember;

    @BeforeEach
    void setUp() {
        applicationEvents.clear();
        defaultMember = createMember("햄부기");
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        transactionTemplate.execute(status -> memberRepository.save(defaultMember));
    }

    @DisplayName("본인 코스를 다른 러너가 완주한 경우 올바른 알림 이벤트를 발행한다.")
    @Test
    void notifyCourseRunEvent() {
        // given
        // 코스 생성, 새로운 멤버 생성, 새로운 멤버의 러닝 생성
        Member courseOwner = defaultMember;
        Course course = createCourse(courseOwner);
        Member runner = createMember("맥도날드");
        Running running = createRunning(runner, course);
        courseRepository.save(course);
        memberRepository.saveAll(List.of(courseOwner, runner));
        runningRepository.save(running);
        CourseRunEvent runEvent = new CourseRunEvent(
                course.getId(),
                "테스트 코스",
                courseOwner.getId(),
                running.getId(),
                running.getStartedAt(),
                running.getRunningRecord().getDuration(),
                runner.getId(),
                runner.getNickname()
        );

        // when - CourseRunEvent 발행 및 트랜잭션 커밋
        transactionTemplate.execute(status -> {
            eventPublisher.publishEvent(runEvent);
            return null;
        });

        // then
        // NotificationEvent 이벤트가 발생 여부 검증
        List<NotificationCommand> events = applicationEvents.stream(NotificationCommand.class).filter(
                event -> event.title().equals("누군가 내 코스를 달렸어요!"))
                .toList();
        assertThat(events.size()).isEqualTo(1);
        // 이벤트 내용 검증
        NotificationCommand event = events.get(0);
        assertThat(event.userIds()).hasSize(1).contains(defaultMember.getId());
        assertThat(event.title()).isEqualTo("누군가 내 코스를 달렸어요!");
        assertThat(event.body()).isEqualTo("맥도날드 님이 회원님의 테스트 코스를 완주했습니다.");
    }

    @DisplayName("코스에서 본인의 기존 신기록을 갱신한 경우 올바른 알림 이벤트를 발행한다.")
    @Test
    void notifyCourseTopPersonalRecordUpdate() {
        // given
        Course course = createCourse(defaultMember);
        Member runner = createMember("버거킹");
        Running prevRunning = createRunningWithDuration(runner, course, 3600L);
        Running newRunning = createRunningWithDuration(runner, course, 1000L);
        transactionTemplate.execute(status -> {
            // 새 트랜잭션에서 데이터 저장 eventListener에서 볼 수 있게 하기 위함 (notifyCourseTopPersonalRecordUpdate의 트랜잭션 전파 속성은 REQUIRES_NEW임)
            courseRepository.save(course);
            memberRepository.save(runner);
            runningRepository.saveAll(List.of(prevRunning, newRunning));
            return null;
        });

        CourseRunEvent runEvent = new CourseRunEvent(
                course.getId(),
                course.getName(),
                defaultMember.getId(),
                newRunning.getId(),
                newRunning.getStartedAt(),
                newRunning.getRunningRecord().getDuration(),
                runner.getId(),
                runner.getNickname()
        );

        // when
        transactionTemplate.execute(status -> {
            eventPublisher.publishEvent(runEvent);
            return null;
        });

        // then
        List<NotificationCommand> events = applicationEvents.stream(NotificationCommand.class).filter(
                event -> event.title().equals("개인 기록 갱신!"))
                .toList();
        assertThat(events).hasSize(1);
        // 이벤트 내용 검증
        NotificationCommand event = events.get(0);
        assertThat(event.userIds()).hasSize(1).contains(runner.getId());
        assertThat(event.title()).isEqualTo("개인 기록 갱신!");
        assertThat(event.body()).isEqualTo("축하해요! 테스트 코스에서 개인 최고 기록을 갱신했어요!");
    }

    @DisplayName("공지사항 활성화 이벤트를 수신하면 공지사항 유형에 따라 올바른 알림 이벤트를 전송한다.")
    @ParameterizedTest
    @MethodSource("singleNoticeEventTestCases")
    void handleNoticeActivatedEvent_singleNotice(Notice notice) {
        // given
        Member member1 = createMember("피자헛");
        Member member2 = createMember("도미노피자");
        transactionTemplate.execute(status -> memberRepository.saveAll(List.of(member1, member2)));
        NoticeActivatedEvent noticeEvent = new NoticeActivatedEvent(
                List.of(new NoticeActivatedEvent.NoticeRecord(
                        notice.getId(),
                        notice.getType(),
                        notice.getTitle(),
                        notice.getContent(),
                        notice.getStartAt(),
                        notice.getEndAt()
                ))
        );

        // when
        transactionTemplate.execute(status -> {
            eventPublisher.publishEvent(noticeEvent);
            return null;
        });

        // then
        String expectedTitle = notice.getType() == NoticeType.GENERAL_V2 ?
                "새로운 공지가 등록되었어요." : "새로운 이벤트 공지가 등록되었어요.";
        List<NotificationCommand> events = applicationEvents.stream(NotificationCommand.class).filter(
                event -> event.title().equals(expectedTitle))
                .toList();
        assertThat(events).hasSize(1);
        var event = events.get(0);
        assertThat(event.userIds()).contains(member1.getId(), member2.getId());
        assertThat(event.title()).isEqualTo(expectedTitle);
        assertThat(event.body()).isEqualTo(notice.getTitle());
    }

    private static Stream<Arguments> singleNoticeEventTestCases() {
        return Stream.of(
                Arguments.of(createGeneralNotice("[공지] 고스트러너 업데이트 안내")),
                Arguments.of(createEventNotice("[이벤트] 대박 이벤트가 왔어용"))
        );
    }


    @DisplayName("다중 공지사항 활성화 이벤트를 수신하면 올바른 푸시 알람 전송을 요청한다.")
    @ParameterizedTest
    @MethodSource("multipleNoticeEventTestCases")
    void handleNoticeActivatedEvent_MultipleNotices(List<Notice> notices, String expectedTitle) {
        // given
        Member member1 = createMember("맘스터치");
        Member member2 = createMember("KFC");
        transactionTemplate.execute(status -> memberRepository.saveAll(List.of(member1, member2)));
        List<NoticeActivatedEvent.NoticeRecord> noticeRecords = notices.stream().map(
                notice -> new NoticeActivatedEvent.NoticeRecord(
                        notice.getId(),
                        notice.getType(),
                        notice.getTitle(),
                        notice.getContent(),
                        notice.getStartAt(),
                        notice.getEndAt()
                )).toList();
        NoticeActivatedEvent noticeEvent = new NoticeActivatedEvent(noticeRecords);

        // when
        transactionTemplate.execute(status -> {
            eventPublisher.publishEvent(noticeEvent);
            return null;
        });

        // then
        List<NotificationCommand> events = applicationEvents.stream(NotificationCommand.class).filter(
                event -> event.title().equals(expectedTitle))
                .toList();
        assertThat(events).hasSize(1);
        var event = events.get(0);
        assertThat(event.userIds()).contains(member1.getId(), member2.getId());
        assertThat(event.title()).isEqualTo(expectedTitle);
        assertThat(event.body()).isEqualTo(notices.stream().map(Notice::getTitle)
                .map(title -> "- " + title)
                .collect(Collectors.joining("\n")));
    }

    private static Stream<Arguments> multipleNoticeEventTestCases() {
        return Stream.of(
                Arguments.of(
                        List.of(
                                createGeneralNotice("[공지1] 고스트러너 업데이트 안내"),
                                createGeneralNotice("[공지2] 서버 점검 안내"),
                                createGeneralNotice("[공지3] 신규 기능 안내")
                        ),
                        "새로운 공지 3건이 등록되었어요"
                ),
                Arguments.of(
                        List.of(
                                createEventNotice("[이벤트1] 대박 이벤트가 왔어요"),
                                createEventNotice("[이벤트2] 코스 완주 시 아파트를 드립니다!")
                        ),
                        "새로운 이벤트 2건이 등록되었어요"
                )
        );
    }

    @DisplayName("페이스메이커 생성 이벤트를 수신하면 올바른 푸시 알람 전송을 요청한다.")
    @Test
    void handlePacemakerCreationEvent() {
        // given
        Member member1 = createMember("스타벅스");
        Course course = createCourse(member1);
        transactionTemplate.execute(status -> {
            memberRepository.save(member1);
            courseRepository.save(course);
            return null;
        });
        var pacemakerEvent = new PacemakerCreatedEvent(
                member1.getId(),
                course.getId(),
                member1.getUuid()
        );

        // when
        transactionTemplate.execute(status -> {
            eventPublisher.publishEvent(pacemakerEvent);
            return null;
        });

        // then
        List<NotificationCommand> events = applicationEvents.stream(NotificationCommand.class).filter(
                event -> event.title().equals("고스티가 완성됐어요"))
                .toList();
        assertThat(events).hasSize(1);
        var event = events.get(0);
        assertThat(event.userIds()).contains(member1.getId());
        assertThat(event.title()).isEqualTo("고스티가 완성됐어요");
        assertThat(event.body()).isEqualTo(course.getName() + "에 고스티가 생성됐어요!");
    }


    // --- 헬퍼 메소드 ---

    private static Notice createGeneralNotice(String title) {
        final LocalDateTime NOW = LocalDateTime.of(2025, 8, 8, 10, 0);
        return Notice.of(title, "내용", NoticeType.GENERAL_V2, "image-url", 1,
                NOW.minusDays(1), NOW.plusDays(1));
    }

    private static Notice createEventNotice(String title) {
        final LocalDateTime NOW = LocalDateTime.of(2025, 8, 8, 10, 0);
        return Notice.of(title, "내용", NoticeType.EVENT_V2, "image-url", 1,
                NOW.minusDays(1), NOW.plusDays(1));
    }

    private static Member createMember(String name) {
        return Member.of(name, "profile-url");
    }

    private static Course createCourse(Member member) {
        final double DEFAULT_LAT = 37, DEFAULT_LNG = 129;
        Course course = Course.of(member, 1000d, 15d, 15d, -5d,
                DEFAULT_LAT, DEFAULT_LNG, "url", "url", "url");
        course.setName("테스트 코스");
        course.setIsPublic(true);
        return course;
    }

    private static Running createRunningWithDuration(Member runner, Course course, Long duration) {
        return Running.of("러닝이에용", RunningMode.SOLO, 2L, createRunningRecord(duration), 1750729987181L,
                true, false, "URL", "URL", "URL", runner, course);
    }

    private static Running createRunning(Member runner, Course course) {
        return Running.of("러닝이에용", RunningMode.SOLO, 2L, createRunningRecord(), 1750729987181L,
                true, false, "URL", "URL", "URL", runner, course);
    }

    private static RunningRecord createRunningRecord() {
        return createRunningRecord(300L);
    }

    private static RunningRecord createRunningRecord(Long duration) {
        return RunningRecord.of(
                5.2, 40.0, 30.0, -20.0,
                6.1, 3423.2, 302.2, duration, 56, 100, 120);
    }


}