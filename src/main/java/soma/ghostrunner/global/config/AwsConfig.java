package soma.ghostrunner.global.config;

import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.listener.acknowledgement.AcknowledgementOrdering;
import io.awspring.cloud.sqs.listener.acknowledgement.handler.AcknowledgementMode;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClientBuilder;

import java.net.URI;
import java.time.Duration;

@Configuration
public class AwsConfig {

    @Value("${cloud.aws.credentials.access-key}")
    private String accessKey;

    @Value("${cloud.aws.credentials.secret-key}")
    private String secretKey;

    @Value("${cloud.aws.region.static}")
    private String region;

    @Value("${cloud.aws.sqs.endpoint:#{null}}")
    private String sqsEndpoint;

    @Bean
    public S3Client s3Client() {
        AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKey, secretKey);

        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKey, secretKey);

        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
                .build();
    }

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(accessKey, secretKey);
        SqsAsyncClientBuilder builder = SqsAsyncClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(awsCredentials));
        // yml에 sqsEndpoint가 존재한다면 (= 테스트 환경이라면) 엔드포인트를 LocalStack 주소로 덮어쓴다.
        if (sqsEndpoint != null) {
            builder.endpointOverride(URI.create(sqsEndpoint));
        }
        return builder.build();
    }

    @Bean
    public SqsTemplate sqsTemplate(SqsAsyncClient sqsAsyncClient) {
        return SqsTemplate.newTemplate(sqsAsyncClient);
    }

    @Bean
    public SqsMessageListenerContainerFactory<Object> defaultSqsListenerContainerFactory(SqsAsyncClient sqsAsyncClient) {
        return SqsMessageListenerContainerFactory.builder()
                .sqsAsyncClient(sqsAsyncClient)
                .configure(options -> options
                        .acknowledgementMode(AcknowledgementMode.ON_SUCCESS)
                        .maxConcurrentMessages(10)
                        .maxMessagesPerPoll(10)
                        .pollTimeout(Duration.ofSeconds(20))
                        .acknowledgementOrdering(AcknowledgementOrdering.ORDERED)
                )
                .build();
    }

}
