package soma.ghostrunner.domain.notification.application;

import io.awspring.cloud.sqs.operations.SendResult;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.sentry.spring.jakarta.tracing.SentrySpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import soma.ghostrunner.domain.notification.application.dto.PushMessageDto;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@SentrySpan
public class SqsPushMessagePublisher implements PushMessagePublisher {

    @Value("${cloud.aws.sqs.push-queue-name}")
    private String queueName;

    private final SqsTemplate sqsTemplate;
    private static final int SQS_BATCH_MAX_SIZE = 10;

    @Override
    public void send(final PushMessageDto message) {
        SendResult<PushMessageDto> result = sqsTemplate.send(to -> to
                .queue(queueName)
                .payload(message));
    }

    public void sendMany(final List<PushMessageDto> messages) {
        List<Message<PushMessageDto>> sqsMessages = messages.stream()
                .map(m -> MessageBuilder.withPayload(m).build())
                .toList();

        try {
            for (int i = 0; i < sqsMessages.size(); i += SQS_BATCH_MAX_SIZE) {
                int end = Math.min(sqsMessages.size(), i + SQS_BATCH_MAX_SIZE);
                SendResult.Batch<PushMessageDto> results = sqsTemplate.sendMany(queueName, sqsMessages.subList(i, end));
                if (!results.failed().isEmpty()) {
                    log.error("SQS 배치 발행 부분 실패 (메시지 {}건 중 {}건 실패)", messages.size(), results.failed().size());
                }
            }
        } catch (Exception ex) {
            log.error("SQS 배치 발행 실패 (메시지 {}건)", messages.size(), ex);
        }

    }

}
