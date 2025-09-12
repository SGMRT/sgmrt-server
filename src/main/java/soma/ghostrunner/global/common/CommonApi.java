package soma.ghostrunner.global.common;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import soma.ghostrunner.global.clients.aws.s3.PresignUrlDto;
import soma.ghostrunner.global.clients.aws.s3.PresignUrlType;
import soma.ghostrunner.global.clients.aws.s3.GhostRunnerS3PresignUrlClient;

@RestController
@RequiredArgsConstructor
public class CommonApi {

    private final GhostRunnerS3PresignUrlClient ghostRunnerS3PresignUrlClient;

    @GetMapping("/v1/common/presign-url")
    public PresignUrlDto generatePresignUrl(PresignUrlType type, @NotNull String fileName) {
        if (type == PresignUrlType.MEMBER_PROFILE) {
            return ghostRunnerS3PresignUrlClient.getMemberProfilePresignUrl(fileName);
        }
        throw new IllegalArgumentException("Unsupported type " + type);
    }

}
