package soma.ghostrunner.domain.notification.api.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationBroadcastRequest {
    private String title;
    private String body;
    private String data;
    private String version;
    private RangeType versionRange = RangeType.ALL_VERSIONS;

    public enum RangeType {
        ALL_VERSIONS,
        AT_LEAST,
        EXACTLY,
        AT_MOST
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();
    @SneakyThrows
    public Map<String, Object> dataMap() {
        if (this.data == null || this.data.isEmpty()) {
            return Map.of();
        }
        return objectMapper.readValue(this.data, new TypeReference<Map<String, Object>>() {});
    }
}
