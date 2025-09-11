package soma.ghostrunner.domain.running.application;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.domain.MemberVdot;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.member.infra.dao.MemberVdotRepository;
import soma.ghostrunner.domain.running.api.dto.response.PacemakerPollingResponse;
import soma.ghostrunner.domain.running.application.dto.WorkoutDto;
import soma.ghostrunner.domain.running.application.dto.request.CreatePacemakerCommand;
import soma.ghostrunner.domain.running.domain.Pacemaker;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;
import soma.ghostrunner.domain.running.infra.persistence.PacemakerRepository;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

@SpringBootTest
class PacemakerServiceTest extends IntegrationTestSupport {

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    MemberVdotRepository memberVdotRepository;

    @Autowired
    private PacemakerRepository pacemakerRepository;

    @Autowired
    private PaceMakerService pacemakerService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @MockitoBean
    PacemakerLlmService pacemakerLlmService;

    @DisplayName("AI 페이스메이커를 생성한다.")
    @Test
    void createPaceMaker() throws InterruptedException {
        // given
        Member member = createMember();
        memberRepository.save(member);

        MemberVdot memberVdot = createMemberVdot(member, 30);
        memberVdotRepository.save(memberVdot);

        // when
        Long id = pacemakerService.createPaceMaker(member.getUuid(), createCommand());

        // then
        Pacemaker pacemaker = pacemakerRepository.findById(id).get();
        Assertions.assertThat(pacemaker).isNotNull();

        verify(pacemakerLlmService, times(1)).requestLlmToCreatePacemaker(
                any(Member.class),
                any(WorkoutDto.class),
                anyInt(),
                anyInt(),
                anyInt(),
                anyLong(),
                anyString()
        );
    }

    private Member createMember() {
        return Member.of("이복둥", "프로필 URL");
    }

    private MemberVdot createMemberVdot(Member member, int vdot) {
        return MemberVdot.of(vdot, member);
    }

    private CreatePacemakerCommand createCommand() {
        return new CreatePacemakerCommand("MARATHON", 10.0, 5,
                30, 6.2, LocalDate.now());
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

        // when
        pacemakerService.createPaceMaker(memberUuid, createCommand());
        pacemakerService.createPaceMaker(memberUuid, createCommand());
        pacemakerService.createPaceMaker(memberUuid, createCommand());

        // then
        assertThatThrownBy(() -> pacemakerService.createPaceMaker(memberUuid, createCommand()))
                .isInstanceOf(InvalidRunningException.class)
                .hasMessage("일일 사용량을 초과했습니다.");
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
        pacemakerService.createPaceMaker(memberUuid, createCommand(today));
        pacemakerService.createPaceMaker(memberUuid, createCommand(today));
        pacemakerService.createPaceMaker(memberUuid, createCommand(today));

        // then
        assertThatThrownBy(() -> pacemakerService.createPaceMaker(memberUuid, createCommand(today)))
                .isInstanceOf(InvalidRunningException.class)
                .hasMessage("일일 사용량을 초과했습니다.");
        pacemakerService.createPaceMaker(memberUuid, createCommand(nextDay));
    }

    private CreatePacemakerCommand createCommand(LocalDate localDate) {
        return new CreatePacemakerCommand("MARATHON", 10.0, 5,
                30, 6.2, localDate);
    }

    @DisplayName("목표 거리가 3K 미만이라면 예외를 발생한다.")
    @Test
    void cannotCreateLowerThan3KPaceMakerRequest() {
        // given
        Member member = createMember();
        memberRepository.save(member);

        MemberVdot memberVdot = createMemberVdot(member, 30);
        memberVdotRepository.save(memberVdot);

        // when // then
        Assertions.assertThatThrownBy(() -> pacemakerService.createPaceMaker(member.getUuid(), createCommand(2.0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("3km 미만의 거리는 페이스메이커를 생성할 수 없습니다.");
    }

    private CreatePacemakerCommand createCommand(Double targetDistance) {
        return new CreatePacemakerCommand("MARATHON", targetDistance, 5,
                30, 6.2, LocalDate.now());
    }

     @DisplayName("VDOT가 이전에 기록되어 있지 않은데 요청값에 없다면 예외를 발생한다.")
     @Test
     void cannotCreatePacemakerWithoutVdotInfo() {
         // given
         Member member = createMember();
         memberRepository.save(member);

         // when // then
         Assertions.assertThatThrownBy(() -> pacemakerService.createPaceMaker(member.getUuid(), createNonePacePerKmCommand()))
                 .isInstanceOf(InvalidRunningException.class)
                 .hasMessage("기존 VDOT 기록이 없어 페이스메이커를 생성할 수 없습니다.");
     }

    private CreatePacemakerCommand createNonePacePerKmCommand() {
        return CreatePacemakerCommand.builder()
                .purpose("MARATHON")
                .targetDistance(5.0)
                .condition(1)
                .temperature(30)
                .localDate(LocalDate.now())
                .build();
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("여러 스레드가 동시에 페이스메이커 생성을 요청할 때, 분산 락에 의해 단 한 번만 실행된다")
    @Test
    void createOnlyOnePacemakerWhenConcurrentRequests() throws InterruptedException {
        // given
        Member member = createMember();
        memberRepository.save(member);

        MemberVdot memberVdot = createMemberVdot(member, 30);
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
                    pacemakerService.createPaceMaker(member.getUuid(), createCommand());
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
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(threadCount - 1);
        deleteData();
    }

    private void deleteData() {
        memberVdotRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
        redisTemplate.keys("pacemaker_api_lock:*").forEach(redisTemplate::delete);
        redisTemplate.keys("pacemaker_api_rate_limit:*").forEach(redisTemplate::delete);
    }

    @DisplayName("페이스메이커를 조회한다.")
    @Test
    void getPacemaker() {
        // given
        Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, "멤버 UUID");
        pacemaker.updateSucceedPacemaker("요약", 10.0, 50, "메세지");
        pacemakerRepository.save(pacemaker);

        // when
        PacemakerPollingResponse response = pacemakerService.getPacemaker(pacemaker.getId(), "멤버 UUID");

        // then
        Assertions.assertThat(response.getProcessingStatus()).isEqualTo("COMPLETED");
        Assertions.assertThat(response.getPacemaker().getGoalKm()).isEqualTo(10.0);
    }

     @DisplayName("페이스메이커를 조회할 때 실패하거나 가공되고 있는 페이스메이커를 조회해도 200이 응답한다. 대신, Status에 반영한다.")
     @Test
     void getProcessingOrFailedPacemaker() {
         // given
         Pacemaker processingPacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, "멤버 UUID");
         pacemakerRepository.save(processingPacemaker);

         // when
         PacemakerPollingResponse response = pacemakerService.getPacemaker(processingPacemaker.getId(), "멤버 UUID");

         // then
         Assertions.assertThat(response.getPacemaker()).isNull();
         Assertions.assertThat(response.getProcessingStatus()).isEqualTo("PROCEEDING");
     }

     @DisplayName("페이스메이커를 조회할 때 자신의 페이스메이커가 아니라면 4xx 에러를 발생한다.")
     @Test
     void getPacemakerNotMine() {
         // given
         Pacemaker pacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, "멤버 UUID");
         pacemakerRepository.save(pacemaker);

         // when // then
         Assertions.assertThatThrownBy(() -> pacemakerService.getPacemaker(pacemaker.getId(), "INVALID 멤버 UUID"))
                 .isInstanceOf(AccessDeniedException.class)
                 .hasMessage("접근할 수 없는 러닝 데이터입니다.");
     }

}
