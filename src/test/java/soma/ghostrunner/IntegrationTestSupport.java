package soma.ghostrunner;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.time.Duration;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
public abstract class IntegrationTestSupport {

    private static final String MYSQL_IMAGE = "mysql:8.0";
    private static final String REDIS_IMAGE = "redis:7-alpine";
    private static final String LOCALSTACK_IMAGE = "localstack/localstack:3.0";

    // static 블록으로 단 한 번만 시작
    private static final MySQLContainer<?> MYSQL_CONTAINER;
    private static final GenericContainer<?> REDIS_CONTAINER;
    private static final LocalStackContainer SQS_CONTAINER;

    static {

        // MySQL 컨테이너 초기화 및 시작
        MYSQL_CONTAINER = new MySQLContainer<>(MYSQL_IMAGE)
                .withDatabaseName("ghostrunner_test")
                .withUsername("test")
                .withPassword("test")
                .withStartupTimeout(Duration.ofMinutes(3));
        MYSQL_CONTAINER.start();

        // Redis 컨테이너 초기화 및 시작
        REDIS_CONTAINER = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
                .withExposedPorts(6379)
                .withStartupTimeout(Duration.ofMinutes(2));
        REDIS_CONTAINER.start();

        // LocalStack 컨테이너 초기화 및 시작
        SQS_CONTAINER = new LocalStackContainer(DockerImageName.parse(LOCALSTACK_IMAGE))
                .withServices(SQS)
                .withEnv("SERVICES", "sqs")  // SQS만 활성화
                .withStartupTimeout(Duration.ofMinutes(5))
                .waitingFor(Wait.forLogMessage(".*Ready.*", 1)
                        .withStartupTimeout(Duration.ofMinutes(3)));
        SQS_CONTAINER.start();

        // LocalStack 완전 초기화 대기 및 SQS 큐 생성
        try {
            Thread.sleep(5000);  // 5초 추가 대기
            createSQSQueues();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SQS queues", e);
        }

        // JVM 종료 시 컨테이너 정리
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            SQS_CONTAINER.stop();
            REDIS_CONTAINER.stop();
            MYSQL_CONTAINER.stop();
        }));

    }

    /**
     * SQS 큐 생성 (재시도 로직 포함)
     */
    private static void createSQSQueues() throws IOException, InterruptedException {
        int maxRetries = 3;
        int retryDelay = 2000;

        for (int i = 0; i < maxRetries; i++) {
            try {
                // 컨테이너 실행 상태 확인
                if (!SQS_CONTAINER.isRunning()) {
                    throw new IllegalStateException("LocalStack container is not running");
                }

                // SQS 큐 생성
                var result1 = SQS_CONTAINER.execInContainer(
                        "awslocal", "sqs", "create-queue", "--queue-name", "TEST_QUEUE_NAME");
                var result2 = SQS_CONTAINER.execInContainer(
                        "awslocal", "sqs", "create-queue", "--queue-name", "TEST_DLQ_NAME");

                // 결과 확인
                if (result1.getExitCode() == 0 && result2.getExitCode() == 0) {
                    System.out.println("✅ SQS queues created successfully");
                    return;
                } else {
                    System.err.println("⚠️ Queue creation returned non-zero exit code");
                    System.err.println("STDOUT: " + result1.getStdout() + result2.getStdout());
                    System.err.println("STDERR: " + result1.getStderr() + result2.getStderr());
                }

            } catch (Exception e) {
                System.err.println("⚠️ Attempt " + (i + 1) + " failed: " + e.getMessage());

                if (i < maxRetries - 1) {
                    System.out.println("Retrying in " + retryDelay + "ms...");
                    Thread.sleep(retryDelay);
                } else {
                    throw new RuntimeException("Failed to create SQS queues after " + maxRetries + " attempts", e);
                }
            }
        }
    }

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        // MySQL
        registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
        registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");

        // Redis
        registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));
        registry.add("spring.data.redis.ssl.enabled", () -> "false");

        // SQS
        registry.add("cloud.aws.sqs.endpoint",
                () -> SQS_CONTAINER.getEndpointOverride(SQS).toString());
        registry.add("cloud.aws.credentials.access-key", SQS_CONTAINER::getAccessKey);
        registry.add("cloud.aws.credentials.secret-key", SQS_CONTAINER::getSecretKey);
        registry.add("cloud.aws.region.static", SQS_CONTAINER::getRegion);
    }

}
