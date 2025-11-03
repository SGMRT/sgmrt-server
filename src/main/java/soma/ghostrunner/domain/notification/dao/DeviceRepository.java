package soma.ghostrunner.domain.notification.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.notification.domain.Device;

import java.util.List;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    boolean existsByMemberIdAndToken(Long memberId, String token);

    List<Device> findByMemberIdIn(List<Long> userIds);

}
