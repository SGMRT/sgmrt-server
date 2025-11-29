package soma.ghostrunner.domain.member.dao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.domain.MemberVdot;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.member.infra.dao.MemberVdotRepository;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class MemberVdotRepositoryTest extends IntegrationTestSupport {

    @Autowired
    MemberVdotRepository memberVdotRepository;

    @Autowired
    private MemberRepository memberRepository;

    @DisplayName("멤버 ID로 MemberVdot를 찾는다.")
    @Test
    void findByMemberId() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        MemberVdot memberVdot = createMemberVdot(member, 30);
        memberVdotRepository.save(memberVdot);

        // when // then
        assertThat(memberVdotRepository.findByMemberUuid(member.getUuid()).get().getVdot()).isEqualTo(30);
     }

    private Member createMember(String name) {
        return Member.of(name, "프로필 URL");
    }

    private MemberVdot createMemberVdot(Member member, int vdot) {
        return MemberVdot.of(vdot, member);
    }

    @DisplayName("MemberVdot가 없다면 NULL을 응답한다.")
    @Test
    void findNoneVdotThenReturnNull() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        // when // then
        assertThat(memberVdotRepository.findByMemberUuid(member.getUuid())).isEmpty();
    }

}
