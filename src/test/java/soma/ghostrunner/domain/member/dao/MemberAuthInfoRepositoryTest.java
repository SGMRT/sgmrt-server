package soma.ghostrunner.domain.member.dao;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.domain.MemberAuthInfo;

import java.util.Optional;

class MemberAuthInfoRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberAuthInfoRepository memberAuthInfoRepository;

    @DisplayName("파이어베이스로 부터 검증 받은 Uid를 갖는 회원이 있는지 검증한다.")
    @Test
    void existsByExternalAuthUid() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        MemberAuthInfo memberAuthInfo = createMemberAuthInfo(member, "외부 UID");
        memberAuthInfoRepository.save(memberAuthInfo);

        // when
        boolean isMemberExist = memberAuthInfoRepository.existsByExternalAuthUid("외부 UID");
        boolean isNoneMemberExist = memberAuthInfoRepository.existsByExternalAuthUid("외부 없는 UID");

        // then
        Assertions.assertThat(isMemberExist).isTrue();
        Assertions.assertThat(isNoneMemberExist).isFalse();
    }

    private Member createMember(String name) {
        return Member.of(name, "프로필 URL");
    }

    private MemberAuthInfo createMemberAuthInfo(Member member, String externalUid) {
        return MemberAuthInfo.of(member, externalUid);
    }

    @DisplayName("파이어베이스로 부터 검증 받은 Uid를 갖는 회원의 UUID를 조회한다.")
    @Test
    void findMemberUuidByExternalAuthUid() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        MemberAuthInfo memberAuthInfo = createMemberAuthInfo(member, "외부 UID");
        memberAuthInfoRepository.save(memberAuthInfo);

        // when
        String memberUuid = memberAuthInfoRepository.findMemberUuidByExternalAuthUid("외부 UID").get();
        Optional<String> nonExistMemberUuid = memberAuthInfoRepository.findMemberUuidByExternalAuthUid("없는 UUID");

        // then
        Assertions.assertThat(memberUuid).isEqualTo(member.getUuid());
        Assertions.assertThat(nonExistMemberUuid).isEmpty();
     }

}
