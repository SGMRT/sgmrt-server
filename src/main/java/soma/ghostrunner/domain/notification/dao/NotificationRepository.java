package soma.ghostrunner.domain.notification.dao;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.notification.domain.Notification;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
