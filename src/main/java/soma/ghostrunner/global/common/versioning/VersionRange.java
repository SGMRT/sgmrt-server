package soma.ghostrunner.global.common.versioning;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(staticName = "of")
public class VersionRange {
    public enum Operator {
        EQUALS,
        GREATER_THAN,
        GREATER_THAN_OR_EQUALS,
        LESS_THAN,
        LESS_THAN_OR_EQUALS
    }
    public static final VersionRange ALL_VERSIONS = VersionRange.of(SemanticVersion.of("0.0.0"), Operator.GREATER_THAN_OR_EQUALS);

    private final SemanticVersion version;
    private final Operator operator;

    /** 정확히 해당 버전만 포함하는 버전 범위 생성 */
    public static VersionRange exactly(SemanticVersion version) {
        return VersionRange.of(version, Operator.EQUALS);
    }

    public static VersionRange exactly(String version) {
        return VersionRange.of(SemanticVersion.of(version), Operator.EQUALS);
    }

    /** 해당 버전 이상을 포함하는 버전 범위 생성 */
    public static VersionRange atLeast(SemanticVersion version) {
        return VersionRange.of(version, Operator.GREATER_THAN_OR_EQUALS);
    }

    public static VersionRange atLeast(String version) {
        return VersionRange.of(SemanticVersion.of(version), Operator.GREATER_THAN_OR_EQUALS);
    }

    /** 해당 버전 이하를 포함하는 버전 범위 생성 */
    public static VersionRange atMost(SemanticVersion version) {
        return VersionRange.of(version, Operator.LESS_THAN_OR_EQUALS);
    }

    public static VersionRange atMost(String version) {
        return VersionRange.of(SemanticVersion.of(version), Operator.LESS_THAN_OR_EQUALS);
    }

    /** 문자열로부터 버전 범위를 파싱 (X.Y.Z[^|v| ] 형식) */
    public static VersionRange parse(String versionRangeStr) {
        versionRangeStr = versionRangeStr.trim();
        String versionStr = versionRangeStr.substring(0, versionRangeStr.length() - 1).trim();
        if (versionRangeStr.endsWith("^")) {
            return VersionRange.of(SemanticVersion.of(versionStr), Operator.GREATER_THAN_OR_EQUALS);
        } else if (versionRangeStr.endsWith("v")) {
            return VersionRange.of(SemanticVersion.of(versionStr), Operator.LESS_THAN_OR_EQUALS);
        } else {
            return VersionRange.of(SemanticVersion.of(versionRangeStr), Operator.EQUALS);
        }
    }

    /** otherVersion이 이 버전 범위에 포함되는지 여부를 반환 */
    public boolean includes(SemanticVersion otherVersion) {
        int comparison = otherVersion.compareTo(this.version);
        return switch (this.operator) {
            case EQUALS -> comparison == 0;
            case GREATER_THAN -> comparison > 0;
            case GREATER_THAN_OR_EQUALS -> comparison >= 0;
            case LESS_THAN -> comparison < 0;
            case LESS_THAN_OR_EQUALS -> comparison <= 0;
        };
    }

}
