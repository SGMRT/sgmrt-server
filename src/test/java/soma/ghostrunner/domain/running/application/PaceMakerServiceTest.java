package soma.ghostrunner.domain.running.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.domain.MemberVdot;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.member.infra.dao.MemberVdotRepository;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;

import java.time.LocalDate;

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

    @DisplayName("목표 거리가 3K 미만이라면 예외를 발생한다.")
    @Test
    void throwExceptionWhenCreatePacemakerWithLowerThan3KDistance() {
        // given

        // when

        // then

     }

}
