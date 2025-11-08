package soma.ghostrunner.domain.notification.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.notification.domain.PushHistory;

@Repository
public interface PushHistoryRepository extends JpaRepository<PushHistory, Long> {
}
