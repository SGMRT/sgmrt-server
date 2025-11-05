package soma.ghostrunner.domain.notification.dao;

import soma.ghostrunner.domain.notification.domain.Device;
import soma.ghostrunner.global.common.versioning.VersionRange;

import java.util.List;

public interface CustomDeviceRepository {
    List<Device> findAllByMemberIdsAndAppVersionRange(List<Long> memberIds, VersionRange appVersionRange);

    List<Device> findAllByAppVersionRange(VersionRange versionRange);

}
