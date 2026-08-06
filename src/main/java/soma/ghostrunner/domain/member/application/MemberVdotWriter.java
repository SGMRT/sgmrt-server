package soma.ghostrunner.domain.member.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.domain.member.application.dto.MemberMapper;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.domain.MemberVdot;
import soma.ghostrunner.domain.member.exception.InvalidMemberException;
import soma.ghostrunner.domain.member.exception.MemberNotFoundException;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.member.infra.dao.MemberVdotRepository;
import soma.ghostrunner.domain.pacemaker.application.VdotService;
import soma.ghostrunner.global.error.ErrorCode;

import java.util.Optional;

/**
 * 회원 VDOT의 <b>유일한 쓰기 진입점</b>. (CourseReadModelWriter와 같은 패턴)
 *
 * <ul>
 *   <li><b>단일 진입점</b> — VDOT를 저장/갱신하는 모든 유즈케이스(러닝 종료 upsert, 러닝 레벨 초기 설정)는
 *       이 클래스를 통한다. 조회는 MemberService 담당.</li>
 *   <li><b>전파는 메서드마다 다르다</b> — 유즈케이스마다 "원본과 원자적이어야 하는가"의 답이 다르기 때문이다.
 *       {@link #updateFromRun}은 자체 트랜잭션(REQUIRED), {@link #initializeFromRunningLevel}은
 *       {@link Propagation#MANDATORY}다. 아래 참고.</li>
 *   <li>과거에는 러닝 완주 이벤트를 구독해 갱신했으나 직접 호출로 전환했다 — 같은 트랜잭션 동기 로직은
 *       이벤트의 실질 이득이 없어 호출 흐름이 드러나는 직접 호출로 통일한다. (설계 04 §6)</li>
 * </ul>
 *
 * <p><b>러닝 저장과의 원자성은 의도적으로 포기했다.</b> 예전에는 클래스 레벨 {@code MANDATORY}로 러닝 저장
 * 트랜잭션에 얹혀 갱신했지만, 그러면 VDOT 계산 실패가 <b>러닝 저장 자체를 롤백</b>시킨다. 사용자가 방금 뛴
 * 기록이 통째로 사라지는 손해가, VDOT가 한 번 낡는 손해보다 훨씬 크다. VDOT는 다음 러닝에서 다시 계산되는
 * 값이기도 하다. 대신 실패를 조용히 삼키지 않도록 호출자({@code RunningCommandService})가 ERROR 로그로
 * 남긴다 — prod 프로파일에서는 {@code SentryAppender}가 root 에 물려 있어 그대로 알럿이 된다.</p>
 */
@Service
@RequiredArgsConstructor
public class MemberVdotWriter {

    private final MemberMapper mapper;
    private final MemberVdotRepository memberVdotRepository;
    private final MemberRepository memberRepository;
    private final VdotService vdotService;

    /**
     * 러닝 종료 — 평균 페이스로 계산한 VDOT를 upsert 한다.
     *
     * <p>러닝 저장 트랜잭션이 커밋된 <b>뒤에</b> 트랜잭션 밖에서 호출되므로 자체 트랜잭션을 연다(REQUIRED).
     * 여기서 실패해도 러닝은 이미 커밋돼 있고, 되돌리지 않는 것이 이 설계의 요지다.</p>
     */
    @Transactional
    public void updateFromRun(String memberUuid, Double averagePace) {
        Member member = findMember(memberUuid);
        int vdot = vdotService.calculateVdot(averagePace);
        upsertMemberVdot(member, vdot);
    }

    /**
     * 러닝 레벨(입문/중급/상급) 기반 초기 설정 — 이미 VDOT가 있으면 예외. (기존 정책 유지)
     *
     * <p>회원 온보딩({@code MemberService}) 경로다. 실패를 사용자에게 그대로 알려야 하는 성격이고 앞선
     * 회원 정보 변경과 함께 커밋돼야 하므로 {@link Propagation#MANDATORY}를 유지한다 — 트랜잭션 없이
     * 호출해 조용히 반영되지 않는 상황을 런타임에 막는다.</p>
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void initializeFromRunningLevel(String memberUuid, String level) {
        Member member = findMember(memberUuid);
        rejectIfVdotAlreadyExists(memberUuid);
        int vdot = vdotService.calculateVdotFromRunningLevel(level);
        memberVdotRepository.save(mapper.toMemberVdot(member, vdot));
    }

    private Member findMember(String memberUuid) {
        return memberRepository.findByUuid(memberUuid)
                .orElseThrow(() -> new MemberNotFoundException(ErrorCode.MEMBER_NOT_FOUND,
                        "cannot find member, uuid: " + memberUuid));
    }

    private void upsertMemberVdot(Member member, int vdot) {
        Optional<MemberVdot> optionalMemberVdot = memberVdotRepository.findByMemberUuid(member.getUuid());
        if (optionalMemberVdot.isPresent()) {
            optionalMemberVdot.get().updateVdot(vdot);
        } else {
            memberVdotRepository.save(mapper.toMemberVdot(member, vdot));
        }
    }

    private void rejectIfVdotAlreadyExists(String memberUuid) {
        if (memberVdotRepository.existsByMemberUuid(memberUuid)) {
            throw new InvalidMemberException(ErrorCode.VDOT_ALREADY_EXIST, "이미 VDOT가 저장되어 있음.");
        }
    }
}
