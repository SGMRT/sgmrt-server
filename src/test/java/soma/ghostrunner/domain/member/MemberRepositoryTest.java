package soma.ghostrunner.domain.member;

import org.assertj.core.api.Assertions;
import org.checkerframework.checker.units.qual.A;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.member.dao.MemberAuthInfoRepository;
import soma.ghostrunner.domain.member.domain.MemberAuthInfo;

class MemberRepositoryTest extends IntegrationTestSupport {

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    MemberAuthInfoRepository memberAuthInfoRepository;

    @DisplayName("파이어베이스의 UID를 갖는 회원을 조회한다.")
    @Test
    void findByExternalAuthUid() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        MemberAuthInfo memberAuthInfo = createMemberAuthInfo(member, "외부 UID");
        memberAuthInfoRepository.save(memberAuthInfo);

        // when
        Member savedMember = memberRepository.findByExternalAuthUid("외부 UID").get();

        // then
        Assertions.assertThat(savedMember.getNickname()).isEqualTo("이복둥");
    }

    private Member createMember(String name) {
        return Member.of(name, "프로필 URL");
    }

    private MemberAuthInfo createMemberAuthInfo(Member member, String externalUid) {
        return MemberAuthInfo.of(member, externalUid);
    }

}
