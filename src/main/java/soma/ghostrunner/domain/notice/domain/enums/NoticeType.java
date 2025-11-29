package soma.ghostrunner.domain.notice.domain.enums;

import java.util.List;

public enum NoticeType {
    // DEPRECATED TYPES
    GENERAL,
    EVENT,

    // NEW TYPES
    GENERAL_V2,
    EVENT_V2;

    public static List<NoticeType> getDeprecatedTypes() {
        return List.of(GENERAL, EVENT);
    }
}
