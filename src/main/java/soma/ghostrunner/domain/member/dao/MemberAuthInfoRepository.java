package soma.ghostrunner.domain.member.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.member.domain.MemberAuthInfo;

import java.util.Optional;

@Repository
public interface MemberAuthInfoRepository extends JpaRepository<MemberAuthInfo, Long> {

    @Query("select mai.member.uuid from MemberAuthInfo mai where mai.externalAuthUid = :authUid")
    Optional<String> findMemberUuidByExternalAuthUid(String authUid);

    boolean existsByExternalAuthUid(String authUid);

    Optional<MemberAuthInfo> findByMemberId(Long id);

}
