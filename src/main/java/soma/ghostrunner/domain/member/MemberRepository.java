package soma.ghostrunner.domain.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.member.domain.Member;

import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {

    @Query("select m from Member m " +
            "inner join MemberAuthInfo mai on m.id = mai.member.id where mai.externalAuthUid = :authUid")
    Optional<Member> findByExternalAuthUid(String authUid);

    boolean existsByNickname(String nickname);

    Optional<Member> findByUuid(String uuid);

}
