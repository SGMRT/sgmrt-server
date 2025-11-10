package soma.ghostrunner.domain.device.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.device.domain.Device;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long>, CustomDeviceRepository {

    boolean existsByMemberIdAndToken(Long memberId, String token);

    Optional<Device> findByToken(String pushToken);

    List<Device> findByMemberIdIn(List<Long> userIds);

    Optional<Device> findByUuid(String uuid);

    boolean existsByToken(String pushToken);

    void deleteByToken(String token);

    void deleteAllByTokenIn(List<String> token);

}
