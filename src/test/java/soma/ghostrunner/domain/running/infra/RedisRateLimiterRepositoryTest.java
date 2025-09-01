package soma.ghostrunner.domain.running.infra;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.domain.MemberVdot;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.member.infra.dao.MemberVdotRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class RedisRateLimiterRepositoryTest extends IntegrationTestSupport {

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    MemberVdotRepository memberVdotRepository;

    @Autowired
    private RedisRateLimiterRepository redisRateLimiterRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Test
    @DisplayName("일일 제한 횟수를 카운팅한다.")
    void createRateLimitExceededPaceMakerRequest() {
        // given
        Member member = createMember();
        String memberUuid = member.getUuid();
        memberRepository.save(member);

        MemberVdot memberVdot = createMemberVdot(member, 30);
        memberVdotRepository.save(memberVdot);

        LocalDate localDate = LocalDate.of(2024, 8, 26);
        String rateLimitKey = "ratelimit:" + memberUuid + ":" + localDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

        // when
        redisRateLimiterRepository.incrementAndGet(rateLimitKey, 3, 86400);
        redisRateLimiterRepository.incrementAndGet(rateLimitKey, 3, 86400);
        redisRateLimiterRepository.incrementAndGet(rateLimitKey, 3, 86400);

        // then
        Assertions.assertThat(redisRateLimiterRepository.incrementAndGet(rateLimitKey, 3, 86400))
                .isEqualTo(4);
    }

    private Member createMember() {
        return Member.of("이복둥", "프로필 URL");
    }

    private MemberVdot createMemberVdot(Member member, int vdot) {
        return MemberVdot.of(vdot, member);
    }

    @Test
    @DisplayName("동시에 여러 개의 스레드에서 요청하더라도 Racing Condition 없이 모두 차감되어야 한다")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void createConcurrentPacemakerRequests() throws InterruptedException {
        // given
        Member member = createMember();
        String memberUuid = member.getUuid();
        memberRepository.save(member);

        MemberVdot memberVdot = createMemberVdot(member, 30);
        memberVdotRepository.save(memberVdot);

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        LocalDate localDate = LocalDate.of(2024, 8, 26);
        String rateLimitKey = "ratelimit:" + memberUuid + ":" + localDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                redisRateLimiterRepository.incrementAndGet(rateLimitKey, 3, 86400);
                latch.countDown();
            });
        }
        latch.await();

        // then
        Assertions.assertThat(redisRateLimiterRepository.incrementAndGet(rateLimitKey, 3, 86400))
                .isEqualTo(11);
        deleteData();
    }

    private void deleteData() {
        memberVdotRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
        redisTemplate.keys("ratelimit:*").forEach(redisTemplate::delete);
        redisTemplate.keys("pacemaker_api_lock:*").forEach(redisTemplate::delete);
    }

}
