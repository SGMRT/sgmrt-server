package soma.ghostrunner.domain.member.infra.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import soma.ghostrunner.domain.member.domain.MemberVdot;

import java.util.Optional;

public interface MemberVdotRepository extends JpaRepository<MemberVdot, Long> {

    @Query("select mv from MemberVdot mv inner join Member m on mv.member.uuid = :memberUuid")
    Optional<MemberVdot> findByMemberUuid(String memberUuid);

}
