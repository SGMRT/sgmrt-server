package soma.ghostrunner.domain.notification.dao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.notification.domain.PushToken;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.*;

@DisplayName("PushTokenRepository 통합 테스트")
class PushTokenRepositoryTest extends IntegrationTestSupport {
    @Autowired
    private PushTokenRepository pushTokenRepository;

    @Autowired
    private MemberRepository memberRepository;

    // 테스트용 Member 생성 헬퍼 메서드
    private Member createMember(String nickname) {
        return Member.of(nickname, "test_profile_url");
    }

    // 테스트용 PushToken 생성 헬퍼 메서드
    private PushToken createPushToken(Member member, String token) {
        return new PushToken(member, token);
    }

    @DisplayName("특정 회원의 푸시 토큰이 존재하는지 확인한다.")
    @Test
    void existsByMemberIdAndToken_success() {
        // given
        Member member = createMember("신짱구");
        memberRepository.save(member);
        PushToken pushToken = createPushToken(member, "token-123");
        pushTokenRepository.save(pushToken);

        // when
        boolean exists = pushTokenRepository.existsByMemberIdAndToken(member.getId(), "token-123");

        // then
        assertThat(exists).isTrue();
    }

    @DisplayName("특정 회원의 푸시 토큰이 존재하지 않으면 false를 반환한다.")
    @Test
    void existsByMemberIdAndToken_notExists() {
        // given
        Member member = createMember("신짱구");
        memberRepository.save(member);
        PushToken pushToken = createPushToken(member, "token-123");
        pushTokenRepository.save(pushToken);

        // when
        boolean exists = pushTokenRepository.existsByMemberIdAndToken(member.getId(), "non-existent-token");

        // then
        assertThat(exists).isFalse();
    }

    @DisplayName("다른 회원의 푸시 토큰 저장에 대해 조회하면 false를 반환한다.")
    @Test
    void existsByMemberIdAndToken_failure_byMember() {
        // given
        Member member1 = createMember("신짱구");
        Member member2 = createMember("나훈아");
        memberRepository.saveAll(List.of(member1, member2));
        PushToken pushToken1 = createPushToken(member1, "token-123");
        pushTokenRepository.save(pushToken1);

        // when
        boolean exists = pushTokenRepository.existsByMemberIdAndToken(member2.getId(), "token-123");

        // then
        assertThat(exists).isFalse();
    }

    @DisplayName("여러 회원의 ID로 푸시 토큰 목록을 조회한다.")
    @Test
    void findByMemberIdIn_success() {
        // given
        Member member1 = createMember("짱구");
        Member member2 = createMember("흰둥이");
        Member member3 = createMember("짱아");
        memberRepository.saveAll(List.of(member1, member2, member3));

        // member1은 두 개의 토큰을 갖는다.
        PushToken token1 = createPushToken(member1, "token-1");
        PushToken token2 = createPushToken(member1, "token-2");
        PushToken token3 = createPushToken(member2, "token-3");
        PushToken token4 = createPushToken(member3, "token-4");
        pushTokenRepository.saveAll(List.of(token1, token2, token3, token4));

        // when
        List<Long> targetMemberIds = List.of(member1.getId(), member2.getId()); // member1과 member2의 토큰을 조회한다.
        List<PushToken> foundTokens = pushTokenRepository.findByMemberIdIn(targetMemberIds);

        // then
        assertThat(foundTokens).hasSize(3)
                .extracting("member.nickname", "token")
                .containsExactlyInAnyOrder(
                        tuple("짱구", "token-1"),
                        tuple("짱구", "token-2"),
                        tuple("흰둥이", "token-3")
                );
    }

    @DisplayName("존재하지 않는 회원 ID를 포함하여 조회하면 존재하는 토큰만 반환한다.")
    @Test
    void findByMemberIdIn_mixedExistence() {
        // given
        Member member1 = createMember("신형만");
        Member member2 = createMember("봉미선");
        memberRepository.saveAll(List.of(member1, member2));

        PushToken token1 = createPushToken(member1, "token-1");
        PushToken token2 = createPushToken(member2, "token-2");
        pushTokenRepository.saveAll(List.of(token1, token2));

        // when
        List<Long> targetMemberIds = List.of(member1.getId(), 9999L); // 9999L는 존재하지 않는 ID
        List<PushToken> foundTokens = pushTokenRepository.findByMemberIdIn(targetMemberIds);

        // then
        // user1만 조회된다.
        assertThat(foundTokens).hasSize(1)
                .extracting("member.nickname", "token")
                .containsExactly(tuple("신형만", "token-1"));
    }

    @DisplayName("푸쉬 토큰 조회에 빈 ID 리스트를 인자로 넘기면 빈 결과가 반환된다.")
    @Test
    void findByMemberIdIn_emptyInput() {
        // given
        List<Long> emptyList = Collections.emptyList();

        // when
        List<PushToken> foundTokens = pushTokenRepository.findByMemberIdIn(emptyList);

        // then
        assertThat(foundTokens).isEmpty();
    }
}