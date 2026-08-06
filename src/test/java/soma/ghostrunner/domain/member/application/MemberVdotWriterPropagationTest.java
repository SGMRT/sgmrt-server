package soma.ghostrunner.domain.member.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import soma.ghostrunner.DatabaseCleanserExtension;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.domain.MemberVdot;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.member.infra.dao.MemberVdotRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link MemberVdotWriter}의 <b>전파 계약</b> 통합 테스트.
 *
 * <p>전파는 프록시가 있어야 성립하므로 Mockito 단위 테스트({@code MemberVdotWriterTest})로는 아예 검증되지 않는다.
 * 여기서 고정하는 계약은 두 메서드가 <b>서로 다르다</b>는 점이다.
 * <ul>
 *   <li>{@code updateFromRun} — 러닝 저장이 커밋된 뒤 트랜잭션 <b>밖에서</b> 호출된다. 자체 트랜잭션을 열어야 한다.
 *       여기에 MANDATORY 를 되돌리면 러닝 종료 API 가 통째로 터진다.</li>
 *   <li>{@code initializeFromRunningLevel} — 회원 온보딩 경로. 앞선 회원 정보 변경과 원자적으로 커밋돼야 하므로
 *       MANDATORY 를 유지한다. 트랜잭션 없이 호출해 조용히 반영되지 않는 상황을 런타임에 막는다.</li>
 * </ul>
 * (패턴 출처: {@code CourseReadModelWriterTest}의 MANDATORY 계약 테스트)
 */
@DisplayName("MemberVdotWriter 전파 계약 통합 테스트")
@ExtendWith(DatabaseCleanserExtension.class)
class MemberVdotWriterPropagationTest extends IntegrationTestSupport {

    @Autowired
    private MemberVdotWriter writer;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private MemberVdotRepository memberVdotRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @DisplayName("updateFromRun 은 활성 트랜잭션 없이 호출해도 자체 트랜잭션으로 커밋된다")
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)  // 테스트 트랜잭션 비활성화
    void updateFromRun_withoutTransaction_commits() {
        // given : 커밋된 회원 하나
        String memberUuid = newTransaction().execute(status ->
                memberRepository.save(Member.of("러너", "https://example.com/p.jpg")).getUuid());

        // when : 러닝 저장 트랜잭션이 이미 끝난 뒤의 호출을 그대로 재현한다
        assertThatCode(() -> writer.updateFromRun(memberUuid, 6.0))
                .doesNotThrowAnyException();

        // then : 다른 트랜잭션에서 읽히므로 실제로 커밋된 것이다
        Optional<MemberVdot> committed =
                newTransaction().execute(status -> memberVdotRepository.findByMemberUuid(memberUuid));
        assertThat(committed).isPresent();
    }

    @DisplayName("initializeFromRunningLevel 은 트랜잭션 없이 호출하면 예외를 던진다 — 온보딩은 원자성을 유지한다")
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void initializeFromRunningLevel_withoutTransaction_throws() {
        // given : 프록시 인터셉터가 본문 실행 전에 막으므로 인자는 쓰이지 않는다

        // when & then
        assertThatThrownBy(() -> writer.initializeFromRunningLevel("no-such-uuid", "입문자"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private TransactionTemplate newTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}
