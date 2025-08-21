package soma.ghostrunner.domain.member.infra.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import soma.ghostrunner.domain.member.domain.MemberSettings;

import java.util.Optional;

public interface MemberSettingsRepository extends JpaRepository<MemberSettings, Long> {

    Optional<MemberSettings> findByMember_Uuid(String memberUuid);

}
