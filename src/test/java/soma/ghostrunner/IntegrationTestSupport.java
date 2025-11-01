package soma.ghostrunner;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
public abstract class IntegrationTestSupport {

    private static final String MYSQL_IMAGE = "mysql:8.0";
    private static final String REDIS_IMAGE = "redis:latest";
    private static final String LOCALSTACK_IMAGE = "localstack/localstack:latest";

    private static final MySQLContainer<?> MYSQL_CONTAINER = new MySQLContainer<>(MYSQL_IMAGE);
    private static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE)).withExposedPorts(6379);
    private static final LocalStackContainer SQS_CONTAINER = new LocalStackContainer(DockerImageName.parse(LOCALSTACK_IMAGE))
            .withServices(SQS);

    @BeforeAll
    public static void startContainers() throws IOException, InterruptedException {
        MYSQL_CONTAINER.start();
        REDIS_CONTAINER.start();
        SQS_CONTAINER.start();
        SQS_CONTAINER.execInContainer("awslocal", "sqs", "create-queue", "--queue-name", "TEST_QUEUE_NAME"); // yml의 큐 이름과 동일하게 생성
        SQS_CONTAINER.execInContainer("awslocal", "sqs", "create-queue", "--queue-name", "TEST_DLQ_NAME");
    }

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        // MySQL
        registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
        registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);

        // Redis
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));

        // SQS
        registry.add("cloud.aws.sqs.endpoint",   // SQS 엔드포인트를 LocalStack의 SQS 엔드포인트로 강제 지정
                () -> SQS_CONTAINER.getEndpointOverride(LocalStackContainer.Service.SQS).toString());
        registry.add("cloud.aws.credentials.access-key", SQS_CONTAINER::getAccessKey);
        registry.add("cloud.aws.credentials.secret-key", SQS_CONTAINER::getSecretKey);
        registry.add("cloud.aws.region.static", SQS_CONTAINER::getRegion);
    }

}
