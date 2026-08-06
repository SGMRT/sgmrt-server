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
 *   <li><b>호출자 트랜잭션 필수</b> — {@link Propagation#MANDATORY}. VDOT 갱신은 원본 변경(러닝 저장,
 *       회원 요청 처리)과 원자적으로 커밋돼야 하며, 트랜잭션 없는 호출은 런타임에 차단된다.</li>
 *   <li>과거에는 러닝 완주 이벤트를 구독해 갱신했으나 직접 호출로 전환했다 — 같은 트랜잭션 동기 로직은
 *       이벤트의 실질 이득이 없어 호출 흐름이 드러나는 직접 호출로 통일한다. (설계 04 §6)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class MemberVdotWriter {

    private final MemberMapper mapper;
    private final MemberVdotRepository memberVdotRepository;
    private final MemberRepository memberRepository;
    private final VdotService vdotService;

    /** 러닝 종료 — 평균 페이스로 계산한 VDOT를 upsert 한다. */
    public void updateFromRun(String memberUuid, Double averagePace) {
        Member member = findMember(memberUuid);
        int vdot = vdotService.calculateVdot(averagePace);
        upsertMemberVdot(member, vdot);
    }

    /** 러닝 레벨(입문/중급/상급) 기반 초기 설정 — 이미 VDOT가 있으면 예외. (기존 정책 유지) */
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
