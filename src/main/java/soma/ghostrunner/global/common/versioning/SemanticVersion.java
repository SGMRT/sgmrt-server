package soma.ghostrunner.global.common.versioning;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Getter
@Embeddable
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SemanticVersion implements Comparable<SemanticVersion>{

    @Column(name = "version_major", nullable = false)
    private int major;

    @Column(name = "version_minor", nullable = false)
    private int minor;

    @Column(name = "version_patch", nullable = false)
    private int patch;

    public static SemanticVersion of(String version) {
        String[] parts = version.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("잘못된 버전 형식 (X.Y.Z 형태여야 함) : " + version);
        }
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = Integer.parseInt(parts[2]);
            if (major < 0 || minor < 0 || patch < 0) {
                throw new IllegalArgumentException("잘못된 버전 형식 (각 필드는 음수일 수 없음) : " + version);
            }
            return new SemanticVersion(major, minor, patch);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("잘못된 버전 형식 (각 필드가 정수여야 함) : " + version, e);
        }
    }

    @Override
    public int compareTo(SemanticVersion other) {
        if (this.major != other.major) {
            return Integer.compare(this.major, other.major);
        }
        if (this.minor != other.minor) {
            return Integer.compare(this.minor, other.minor);
        }
        return Integer.compare(this.patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }

}
