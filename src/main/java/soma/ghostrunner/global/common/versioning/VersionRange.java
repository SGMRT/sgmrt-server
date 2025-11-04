package soma.ghostrunner.global.common.versioning;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(staticName = "of")
public class VersionRange {
    enum Operator {
        EQUALS,
        GREATER_THAN,
        GREATER_THAN_OR_EQUALS,
        LESS_THAN,
        LESS_THAN_OR_EQUALS
    }
    public static final VersionRange ALL_VERSIONS = VersionRange.of(SemanticVersion.of("0.0.0"), Operator.GREATER_THAN_OR_EQUALS);

    private final SemanticVersion version;
    private final Operator operator;

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
