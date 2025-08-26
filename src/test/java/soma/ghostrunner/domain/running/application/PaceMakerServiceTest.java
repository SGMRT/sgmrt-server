package soma.ghostrunner.domain.running.application;

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
import soma.ghostrunner.domain.running.exception.InvalidRunningException;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class PaceMakerServiceTest extends IntegrationTestSupport {

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    MemberVdotRepository memberVdotRepository;

    @Autowired
    private PaceMakerService paceMakerService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @DisplayName("AI 페이스메이커를 생성한다.")
    @Test
    void createPaceMaker() throws InterruptedException {
        // given
        Member member = createMember();
        memberRepository.save(member);

        MemberVdot memberVdot = createMemberVdot(member, 30);
        memberVdotRepository.save(memberVdot);

        LocalDate localDate = LocalDate.of(2024, 8, 26);

        // when // then
        paceMakerService.createPaceMaker(member.getUuid(), localDate);
     }

    private Member createMember() {
        return Member.of("이복둥", "프로필 URL");
    }

    private MemberVdot createMemberVdot(Member member, int vdot) {
        return MemberVdot.of(vdot, member);
    }

    @Test
    @DisplayName("일일 제한 횟수(3회)를 초과하면 예외가 발생해야 한다.")
    void createRateLimitExceededPaceMakerRequest() throws InterruptedException {
        // given
        Member member = createMember();
        String memberUuid = member.getUuid();
        memberRepository.save(member);

        MemberVdot memberVdot = createMemberVdot(member, 30);
        memberVdotRepository.save(memberVdot);

        LocalDate localDate = LocalDate.of(2024, 8, 26);

        // when
        paceMakerService.createPaceMaker(memberUuid, localDate);
        paceMakerService.createPaceMaker(memberUuid, localDate);
        paceMakerService.createPaceMaker(memberUuid, localDate);

        // then
        assertThatThrownBy(() -> paceMakerService.createPaceMaker(memberUuid, localDate))
                .isInstanceOf(InvalidRunningException.class)
                .hasMessage("일일 사용량을 초과했습니다.");
    }

    @Test
    @DisplayName("동시에 여러 요청이 발생해도 1번만 성공하고, 횟수도 1번만 차감되어야 한다")
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
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        LocalDate localDate = LocalDate.of(2024, 8, 26);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    paceMakerService.createPaceMaker(memberUuid, localDate);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        // then
        // 처음 스레드만 성공하고, 해당 스레드가 처리하는 동안 다른 스레드 요청들은 거부
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(threadCount - 1);

        // 횟수가 1번만 차감되었는지 확인하기 위해 2번 더 호출
        Thread.sleep(1000);
        paceMakerService.createPaceMaker(memberUuid, localDate); // 2회째 성공
        paceMakerService.createPaceMaker(memberUuid, localDate); // 3회째 성공

        // 4번째 호출은 실패해야 함
        assertThatThrownBy(() -> paceMakerService.createPaceMaker(memberUuid, localDate))
                .isInstanceOf(InvalidRunningException.class)
                .hasMessage("일일 사용량을 초과했습니다.");

        deleteData();
    }

    private void deleteData() {
        memberVdotRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
        redisTemplate.keys("ratelimit:*").forEach(redisTemplate::delete);
        redisTemplate.keys("pacemaker_api_lock:*").forEach(redisTemplate::delete);
    }

    @DisplayName("다음 날이 되면 다시 페이스메이커를 생성할 수 있다.")
    @Test
    void canCreatePacemakerNextDay() throws InterruptedException {
        // given
        Member member = createMember();
        String memberUuid = member.getUuid();
        memberRepository.save(member);

        MemberVdot memberVdot = createMemberVdot(member, 30);
        memberVdotRepository.save(memberVdot);

        LocalDate today = LocalDate.of(2024, 8, 26);
        LocalDate nextDay = today.plusDays(1);

        // when
        paceMakerService.createPaceMaker(memberUuid, today);
        paceMakerService.createPaceMaker(memberUuid, today);
        paceMakerService.createPaceMaker(memberUuid, today);

        // then
        assertThatThrownBy(() -> paceMakerService.createPaceMaker(memberUuid, today))
                .isInstanceOf(InvalidRunningException.class)
                .hasMessage("일일 사용량을 초과했습니다.");
        paceMakerService.createPaceMaker(memberUuid, nextDay);
     }

}
