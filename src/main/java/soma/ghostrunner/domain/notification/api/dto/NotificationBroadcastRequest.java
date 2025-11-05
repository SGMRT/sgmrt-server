package soma.ghostrunner.domain.notification.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationBroadcastRequest {
    private String title;
    private String body;
    private Map<String, Object> data;
    private String version;
    private RangeType versionRange = RangeType.ALL_VERSIONS;

    public enum RangeType {
        ALL_VERSIONS,
        AT_LEAST,
        EXACTLY,
        AT_MOST
    }
}
