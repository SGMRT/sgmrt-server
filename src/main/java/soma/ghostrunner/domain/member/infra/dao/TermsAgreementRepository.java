package soma.ghostrunner.domain.member.infra.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.member.domain.TermsAgreement;

import java.util.Optional;

@Repository
public interface TermsAgreementRepository extends JpaRepository<TermsAgreement, Long> {

    TermsAgreement findTopByMemberIdOrderByAgreedAtDesc(Long id);

    Optional<TermsAgreement> findByMemberId(Long id);

    @Query("select ta from TermsAgreement ta join fetch Member m where m.uuid = :uuid")
    Optional<TermsAgreement> findByMemberUuid(String uuid);

}
