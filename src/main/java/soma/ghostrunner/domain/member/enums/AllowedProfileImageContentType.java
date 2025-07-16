package soma.ghostrunner.domain.member.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AllowedProfileImageContentType {
    IMAGE_JPEG("image/jpeg"),
    IMAGE_PNG("image/png");

    private final String mimeType;

    public static AllowedProfileImageContentType fromMimeType(String mimeType) {
        for (AllowedProfileImageContentType type : AllowedProfileImageContentType.values()) {
            if (type.mimeType.equalsIgnoreCase(mimeType)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid mime type: " + mimeType);
    }
}
