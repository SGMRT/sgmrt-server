package soma.ghostrunner.domain.running.application;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.domain.MemberVdot;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.member.infra.dao.MemberVdotRepository;
import soma.ghostrunner.domain.running.api.support.PacemakerType;
import soma.ghostrunner.domain.running.application.dto.request.CreatePacemakerCommand;
import soma.ghostrunner.domain.running.infra.persistence.PacemakerRepository;
import soma.ghostrunner.domain.running.infra.persistence.PacemakerSetRepository;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;


@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PacemakerServiceLockTest extends IntegrationTestSupport {

    @Autowired
    PacemakerRepository pacemakerRepository;

    @Autowired
    PacemakerSetRepository pacemakerSetRepository;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    MemberVdotRepository memberVdotRepository;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    private PacemakerService pacemakerService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    PacemakerLlmService pacemakerLlmService;

    @Transactional(propagation = Propagation.NOT_SUPPORTED) // 트랜잭션 끄기
    @DisplayName("여러 스레드가 동시에 생성 요청 시, 분산 락으로 단 1회만 성공")
    @Test
    void createOnlyOnePacemakerWhenConcurrentRequests() throws InterruptedException {
        // given - DB/Redis 정리
        deleteData();

        Member member = Member.of("이복둥", "프로필 URL");
        memberRepository.save(member);
        memberVdotRepository.save(MemberVdot.of(30, member));

        // ✅ 실제로 사용할 코스 생성 및 저장
        Course course = Course.of(
                member,          // 혹은 코스 생성 주체와 무관하다면 다른 멤버여도 무방
                10.0, 0.0, 0.0, 0.0,
                37.5, 127.0,
                "route", "thumb", "name"
        );
        courseRepository.save(course);
        Long courseId = course.getId();

        int threadCount = 10;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    pacemakerService.createPaceMaker(
                            member.getUuid(),
                            new CreatePacemakerCommand(PacemakerType.MARATHON, 10.0, 5, 30, courseId) // ✅ 기존 1L → courseId
                    );
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // then
        Assertions.assertThat(successCount.get()).isEqualTo(1);
        Assertions.assertThat(failureCount.get()).isEqualTo(threadCount - 1);

        deleteData();
    }


    private void deleteData() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");

        // 자식 → 부모 순으로 TRUNCATE (TRUNCATE는 순서 덜 민감하지만 안전하게)
        jdbc.execute("TRUNCATE TABLE pacemaker_set");
        jdbc.execute("TRUNCATE TABLE pacemaker");
        jdbc.execute("TRUNCATE TABLE course");
        jdbc.execute("TRUNCATE TABLE member_vdot");
        jdbc.execute("TRUNCATE TABLE member");

        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");

        // Redis 키 정리
        redisTemplate.keys("pacemaker_api_lock:*").forEach(redisTemplate::delete);
        redisTemplate.keys("pacemaker_api_rate_limit:*").forEach(redisTemplate::delete);
    }

}
