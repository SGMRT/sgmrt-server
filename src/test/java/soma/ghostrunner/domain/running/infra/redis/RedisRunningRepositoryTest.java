package soma.ghostrunner.domain.running.infra.redis;

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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class RedisRunningRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private RedisRunningRepository repository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    MemberVdotRepository memberVdotRepository;
    @Autowired
    private RedisRunningRepository redisRunningRepository;

    @DisplayName("레디스에 Key-Value(String) 을 저장한다.")
    @Test
    void save() {
        // given
        String key = "test:running:session";
        String value = "payload";
        long timeout = 2L;
        TimeUnit unit = TimeUnit.SECONDS;

        // when
        repository.save(key, value, unit, timeout);

        // then
        String saved = redisTemplate.opsForValue().get(key);
        assertThat(saved).isEqualTo(value);
    }

    @DisplayName("만료 기한 후 제거됐는지 검사한다.")
    @Test
    void saveWithExpiration() throws InterruptedException {
        // given
        String key = "test:running:session";
        String value = "payload-expire";
        long timeout = 1L;
        TimeUnit unit = TimeUnit.SECONDS;

        // when
        repository.save(key, value, unit, timeout);

        // then
        Thread.sleep(1200);
        assertThat(redisTemplate.opsForValue().get(key)).isNull();
    }

    @Test
    @DisplayName("일일 제한 횟수를 카운팅한다.")
    void incrementRateLimitCounter() {
        // given
        Member member = createMember();
        String memberUuid = member.getUuid();
        memberRepository.save(member);

        MemberVdot memberVdot = createMemberVdot(member, 30);
        memberVdotRepository.save(memberVdot);

        LocalDate localDate = LocalDate.of(2024, 8, 26);
        String rateLimitKey = "ratelimit:" + memberUuid + ":" + localDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

        // when // then
        Assertions.assertThat(repository.incrementRateLimitCounter(rateLimitKey, 3, 86400))
                .isEqualTo(1);
        redisTemplate.delete(rateLimitKey);
    }

    private Member createMember() {
        return Member.of("이복둥", "프로필 URL");
    }

    private MemberVdot createMemberVdot(Member member, int vdot) {
        return MemberVdot.of(vdot, member);
    }

    @Test
    @DisplayName("일일 제한 횟수를 초과했다면 -1을 반환한다.")
    void returnMinusWhenExceedRateLimitCounter() {
        // given
        Member member = createMember();
        String memberUuid = member.getUuid();
        memberRepository.save(member);

        MemberVdot memberVdot = createMemberVdot(member, 30);
        memberVdotRepository.save(memberVdot);

        LocalDate localDate = LocalDate.of(2024, 8, 26);
        String rateLimitKey = "ratelimit:" + memberUuid + ":" + localDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

        // when
        repository.incrementRateLimitCounter(rateLimitKey, 3, 86400);
        repository.incrementRateLimitCounter(rateLimitKey, 3, 86400);
        repository.incrementRateLimitCounter(rateLimitKey, 3, 86400);

        // then
        Assertions.assertThat(repository.incrementRateLimitCounter(rateLimitKey, -1, 86400))
                .isEqualTo(-1);
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

        int threadCount = 3;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        LocalDate localDate = LocalDate.of(2024, 8, 26);
        String rateLimitKey = "ratelimit:" + memberUuid + ":" + localDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                repository.incrementRateLimitCounter(rateLimitKey, 3, 86400);
                latch.countDown();
            });
        }
        latch.await();

        // then
        Assertions.assertThat(redisRunningRepository.get(rateLimitKey)).isEqualTo("3");
        deleteData();
    }

    private void deleteData() {
        memberVdotRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
        redisTemplate.keys("ratelimit:*").forEach(redisTemplate::delete);
        redisTemplate.keys("pacemaker_api_lock:*").forEach(redisTemplate::delete);
    }

    @Test
    @DisplayName("일일 제한 횟수를 감소시킨다.")
    void decrementRateLimitCounter() {
        // given
        Member member = createMember();
        String memberUuid = member.getUuid();
        memberRepository.save(member);

        MemberVdot memberVdot = createMemberVdot(member, 30);
        memberVdotRepository.save(memberVdot);

        LocalDate localDate = LocalDate.of(2024, 8, 26);
        String rateLimitKey = "ratelimit:" + memberUuid + ":" + localDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

        repository.incrementRateLimitCounter(rateLimitKey, 3, 86400);
        repository.incrementRateLimitCounter(rateLimitKey, 3, 86400);

        // when // then
        Assertions.assertThat(repository.decrementRateLimitCounter(rateLimitKey))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("일일 제한 횟수를 감소시킬 때, 0이라면 값을 없앤다.")
    void decrementAndRemoveRateLimitCounter() {
        // given
        Member member = createMember();
        String memberUuid = member.getUuid();
        memberRepository.save(member);

        MemberVdot memberVdot = createMemberVdot(member, 30);
        memberVdotRepository.save(memberVdot);

        LocalDate localDate = LocalDate.of(2024, 8, 26);
        String rateLimitKey = "ratelimit:" + memberUuid + ":" + localDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

        repository.incrementRateLimitCounter(rateLimitKey, 3, 86400);

        // when
        repository.decrementRateLimitCounter(rateLimitKey);

        // then
        Assertions.assertThat(redisTemplate.opsForValue().get(rateLimitKey)).isNull();
    }

}
