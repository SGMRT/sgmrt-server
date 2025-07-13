package soma.ghostrunner.domain.member;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {
    boolean existsByAuthUid(String authUid);

    Optional<Member> findByAuthUid(String authUid);
}
