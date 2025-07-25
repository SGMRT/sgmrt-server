package soma.ghostrunner.clients.aws.presign;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PresignUrlDto {

    private String presignUrl;
    private String objectKey;

    private PresignUrlDto(String presignUrl, String objectKey) {
        this.presignUrl = presignUrl;
        this.objectKey = objectKey;
    }

    public static PresignUrlDto of(String presignUrl, String objectKey) {
        return new PresignUrlDto(presignUrl, objectKey);
    }

}
