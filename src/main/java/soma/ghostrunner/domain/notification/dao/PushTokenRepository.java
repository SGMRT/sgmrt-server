package soma.ghostrunner.domain.notification.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.notification.domain.PushToken;

import java.util.List;

@Repository
public interface PushTokenRepository extends JpaRepository<PushToken, Long> {

    boolean existsByMemberIdAndToken(Long memberId, String token);

    List<PushToken> findByMemberIdIn(List<Long> userIds);

    void deleteByToken(String token);

}
