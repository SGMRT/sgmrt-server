package soma.ghostrunner.domain.notification.dao;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.notification.domain.Notification;

@Deprecated(since = "푸시 알림 전송 내역 DB 저장은 보류. 향후 필요 시 구현 예정")
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
