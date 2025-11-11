package soma.ghostrunner;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
public abstract class IntegrationTestSupport {

    private static final String MYSQL_IMAGE = "mysql:8.0";
    private static final String REDIS_IMAGE = "redis:7-alpine";
    private static final String LOCALSTACK_IMAGE = "localstack/localstack:3.0";

    protected static final MySQLContainer<?> MYSQL_CONTAINER;
    protected static final GenericContainer<?> REDIS_CONTAINER;
    protected static final LocalStackContainer SQS_CONTAINER;

    private static boolean CONTAINERS_STARTED = false;
    private static Exception INITIALIZATION_ERROR = null;

    static {
        try {
            System.out.println("🚀 Starting test containers...");

            // MySQL 시작
            MYSQL_CONTAINER = new MySQLContainer<>(MYSQL_IMAGE)
                    .withDatabaseName("ghostrunner_test")
                    .withUsername("test")
                    .withPassword("test")
                    .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci")
                    .withStartupTimeout(Duration.ofMinutes(3))
                    .withStartupAttempts(3);

            System.out.println("⏳ Starting MySQL...");
            MYSQL_CONTAINER.start();
            System.out.println("✅ MySQL started: " + MYSQL_CONTAINER.getJdbcUrl());

            // Redis 시작
            REDIS_CONTAINER = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
                    .withExposedPorts(6379)
                    .withStartupTimeout(Duration.ofMinutes(2))
                    .withStartupAttempts(3);

            System.out.println("⏳ Starting Redis...");
            REDIS_CONTAINER.start();
            System.out.println("✅ Redis started: " + REDIS_CONTAINER.getHost() + ":" + REDIS_CONTAINER.getMappedPort(6379));

            // LocalStack 시작 (선택적 - SQS 사용 안 하는 테스트도 있을 수 있음)
            SQS_CONTAINER = new LocalStackContainer(DockerImageName.parse(LOCALSTACK_IMAGE))
                    .withServices(SQS)
                    .withEnv("SERVICES", "sqs")
                    .withEnv("EAGER_SERVICE_LOADING", "0")
                    .withStartupTimeout(Duration.ofMinutes(5))
                    .withStartupAttempts(2);

            System.out.println("⏳ Starting LocalStack...");
            SQS_CONTAINER.start();
            System.out.println("✅ LocalStack started");

            // LocalStack 안정화 대기
            System.out.println("⏳ Waiting for LocalStack to be ready...");
            Thread.sleep(3000);  // CI 환경을 위해 3초로 증가

            // SQS 큐 생성 (실패해도 테스트는 계속 진행)
            try {
                createSQSQueuesWithRetry();
            } catch (Exception e) {
                System.err.println("⚠️ Warning: Failed to create SQS queues, but continuing: " + e.getMessage());
                // SQS 큐 생성 실패는 치명적이지 않을 수 있으므로 경고만 출력
            }

            CONTAINERS_STARTED = true;
            System.out.println("🎉 All containers started successfully");

            // Shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("🛑 Stopping test containers...");
                try {
                    if (SQS_CONTAINER != null && SQS_CONTAINER.isRunning()) {
                        SQS_CONTAINER.stop();
                    }
                    if (REDIS_CONTAINER != null && REDIS_CONTAINER.isRunning()) {
                        REDIS_CONTAINER.stop();
                    }
                    if (MYSQL_CONTAINER != null && MYSQL_CONTAINER.isRunning()) {
                        MYSQL_CONTAINER.stop();
                    }
                    System.out.println("✅ All containers stopped");
                } catch (Exception e) {
                    System.err.println("⚠️ Error stopping containers: " + e.getMessage());
                }
            }));

        } catch (Exception e) {
            INITIALIZATION_ERROR = e;
            System.err.println("❌ FATAL: Failed to initialize test containers");
            e.printStackTrace();
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * SQS 큐 생성 (재시도 로직)
     */
    private static void createSQSQueuesWithRetry() throws Exception {
        int maxRetries = 5;  // CI 환경을 위해 재시도 횟수 증가
        int retryDelay = 3000;  // 3초로 증가

        for (int i = 0; i < maxRetries; i++) {
            try {
                if (!SQS_CONTAINER.isRunning()) {
                    throw new IllegalStateException("LocalStack container is not running");
                }

                System.out.println("🔄 Attempting to create SQS queues (attempt " + (i + 1) + "/" + maxRetries + ")");

                var result1 = SQS_CONTAINER.execInContainer("awslocal", "sqs", "create-queue", "--queue-name", "TEST_QUEUE_NAME");
                var result2 = SQS_CONTAINER.execInContainer("awslocal", "sqs", "create-queue", "--queue-name", "TEST_DLQ_NAME");

                if (result1.getExitCode() != 0) {
                    throw new RuntimeException("Queue creation failed: " + result1.getStderr());
                }
                if (result2.getExitCode() != 0) {
                    throw new RuntimeException("DLQ creation failed: " + result2.getStderr());
                }

                System.out.println("✅ SQS queues created successfully");
                System.out.println("   - TEST_QUEUE_NAME: " + result1.getStdout().trim());
                System.out.println("   - TEST_DLQ_NAME: " + result2.getStdout().trim());
                return;

            } catch (Exception e) {
                System.err.println("⚠️ Attempt " + (i + 1) + " failed: " + e.getMessage());

                if (i < maxRetries - 1) {
                    System.out.println("   Retrying in " + retryDelay + "ms...");
                    Thread.sleep(retryDelay);
                } else {
                    throw new RuntimeException("Failed to create SQS queues after " + maxRetries + " attempts", e);
                }
            }
        }
    }

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        if (!CONTAINERS_STARTED) {
            throw new IllegalStateException(
                    "Containers failed to start. Original error: " +
                            (INITIALIZATION_ERROR != null ? INITIALIZATION_ERROR.getMessage() : "Unknown"));
        }

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
