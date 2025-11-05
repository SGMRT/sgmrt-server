package soma.ghostrunner.domain.notification.dao;

import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.notification.domain.Device;
import soma.ghostrunner.global.common.versioning.VersionRange;

import java.util.List;

import static soma.ghostrunner.domain.notification.domain.QDevice.device;

@Repository
@RequiredArgsConstructor
public class CustomDeviceRepositoryImpl implements CustomDeviceRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Device> findAllByMemberIdsAndAppVersionRange(List<Long> memberIds, VersionRange appVersionRange) {
        return queryFactory.selectFrom(device)
                .where(device.member.id.in(memberIds),
                       appVersionWithinRange(appVersionRange))
                .fetch();
    }

    @Override
    public List<Device> findAllByAppVersionRange(VersionRange versionRange) {
        return queryFactory.selectFrom(device)
                .where(appVersionWithinRange(versionRange))
                .fetch();
    }

    private Predicate appVersionWithinRange(VersionRange versionRange) {
        if (versionRange == null) return null;
        var version = versionRange.getVersion();
        var newVersionPath = device.appVersion;
        return switch (versionRange.getOperator()) {
            case EQUALS -> newVersionPath.major.eq(version.getMajor())
                    .and(newVersionPath.minor.eq(version.getMinor()))
                    .and(newVersionPath.patch.eq(version.getPatch()));
            case GREATER_THAN_OR_EQUALS -> newVersionPath.major.gt(version.getMajor())
                    .or(newVersionPath.major.eq(version.getMajor())
                            .and(newVersionPath.minor.gt(version.getMinor())))
                    .or(newVersionPath.major.eq(version.getMajor())
                            .and(newVersionPath.minor.eq(version.getMinor()))
                            .and(newVersionPath.patch.goe(version.getPatch())));
            case LESS_THAN_OR_EQUALS -> newVersionPath.major.lt(version.getMajor())
                    .or(newVersionPath.major.eq(version.getMajor())
                            .and(newVersionPath.minor.lt(version.getMinor())))
                    .or(newVersionPath.major.eq(version.getMajor())
                            .and(newVersionPath.minor.eq(version.getMinor()))
                            .and(newVersionPath.patch.loe(version.getPatch())));
            case GREATER_THAN -> newVersionPath.major.gt(version.getMajor())
                    .or(newVersionPath.major.eq(version.getMajor())
                            .and(newVersionPath.minor.gt(version.getMinor())))
                    .or(newVersionPath.major.eq(version.getMajor())
                            .and(newVersionPath.minor.eq(version.getMinor()))
                            .and(newVersionPath.patch.gt(version.getPatch())));
            case LESS_THAN -> newVersionPath.major.lt(version.getMajor())
                    .or(newVersionPath.major.eq(version.getMajor())
                            .and(newVersionPath.minor.lt(version.getMinor())))
                    .or(newVersionPath.major.eq(version.getMajor())
                            .and(newVersionPath.minor.eq(version.getMinor()))
                            .and(newVersionPath.patch.lt(version.getPatch())));
            default -> throw new UnsupportedOperationException("지원되지 않는 연산자입니다.");
        };
    }
}
