package soma.ghostrunner.domain.member.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import soma.ghostrunner.domain.member.domain.MemberVdot;

import java.util.Optional;

public interface MemberVdotRepository extends JpaRepository<MemberVdot, Long> {

    @Query("select mv from MemberVdot mv where mv.member.id = :memberId")
    Optional<MemberVdot> findByMemberId(Long memberId);

}
