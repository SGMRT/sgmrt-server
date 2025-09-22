package soma.ghostrunner.domain.running.application;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.domain.MemberVdot;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.member.infra.dao.MemberVdotRepository;
import soma.ghostrunner.domain.running.application.dto.request.CreatePacemakerCommand;
import soma.ghostrunner.domain.running.infra.persistence.PacemakerRepository;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS) // 실행 끝나면 컨텍스트/DB/Redis 초기화
class PacemakerServiceLockTest extends IntegrationTestSupport {

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    MemberVdotRepository memberVdotRepository;

    @Autowired
    private PacemakerService pacemakerService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @MockitoBean
    PacemakerLlmService pacemakerLlmService;

    @Transactional(propagation = Propagation.NOT_SUPPORTED) // 트랜잭션 끄기
    @DisplayName("여러 스레드가 동시에 페이스메이커 생성을 요청할 때, 분산 락에 의해 단 한 번만 실행된다")
    @Test
    void createOnlyOnePacemakerWhenConcurrentRequests() throws InterruptedException {
        // given - DB/Redis 정리
        deleteData();

        Member member = Member.of("이복둥", "프로필 URL");
        memberRepository.save(member);

        MemberVdot memberVdot = MemberVdot.of(30, member);
        memberVdotRepository.save(memberVdot);

        int threadCount = 10;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    pacemakerService.createPaceMaker(member.getUuid(),
                            new CreatePacemakerCommand("MARATHON", 10.0, 5, 30, 6.2, LocalDate.now()));
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
        memberVdotRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
        redisTemplate.keys("pacemaker_api_lock:*").forEach(redisTemplate::delete);
        redisTemplate.keys("pacemaker_api_rate_limit:*").forEach(redisTemplate::delete);
    }
}
