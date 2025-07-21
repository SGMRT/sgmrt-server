package soma.ghostrunner.domain.member.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.member.domain.TermsAgreement;

@Repository
public interface TermsAgreementRepository extends JpaRepository<TermsAgreement, Long> {

    TermsAgreement findTopByMemberIdOrderByAgreedAtDesc(Long id);

}
