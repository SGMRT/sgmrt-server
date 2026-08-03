package soma.ghostrunner;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

@TestConfiguration(proxyBeanMethods = false)
public class LocalContainersConfig {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ghostrunner_local")
            .withUsername("local")
            .withPassword("local")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static final LocalStackContainer LOCALSTACK = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(SQS);

    static {
        Startables.deepStart(MYSQL, REDIS, LOCALSTACK).join();
        createSqsQueues();
    }

    private static void createSqsQueues() {
        try {
            LOCALSTACK.execInContainer("awslocal", "sqs", "create-queue", "--queue-name", "test-main");
            LOCALSTACK.execInContainer("awslocal", "sqs", "create-queue", "--queue-name", "test-dlq");
        } catch (Exception e) {
            throw new RuntimeException("SQS 큐 생성 실패", e);
        }
    }

    @Bean
    DynamicPropertyRegistrar containerProperties() {
        return registry -> {
            registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
            registry.add("spring.datasource.username", MYSQL::getUsername);
            registry.add("spring.datasource.password", MYSQL::getPassword);
            registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
            registry.add("spring.data.redis.host", REDIS::getHost);
            registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
            registry.add("spring.data.redis.ssl.enabled", () -> "false");
            registry.add("cloud.aws.sqs.endpoint", () -> LOCALSTACK.getEndpointOverride(SQS).toString());
            registry.add("cloud.aws.credentials.access-key", LOCALSTACK::getAccessKey);
            registry.add("cloud.aws.credentials.secret-key", LOCALSTACK::getSecretKey);
            registry.add("cloud.aws.region.static", LOCALSTACK::getRegion);
        };
    }
}
