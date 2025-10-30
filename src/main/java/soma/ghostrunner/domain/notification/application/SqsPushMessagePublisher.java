package soma.ghostrunner.domain.notification.application;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import soma.ghostrunner.domain.notification.application.dto.PushMessageDto;

@Component
@RequiredArgsConstructor
public class SqsPushMessagePublisher implements PushMessagePublisher {

    @Value("${cloud.aws.sqs.push-queue-name}")
    private String queueName;

    private final SqsTemplate sqsTemplate;

    @Override
    public void send(final PushMessageDto message) {
        sqsTemplate.send(to -> to
                .queue(queueName)
                .payload(message));
    }

}
