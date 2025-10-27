package soma.ghostrunner;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

public class DatabaseCleanserExtension implements AfterEachCallback {

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        ApplicationContext context = SpringExtension.getApplicationContext(extensionContext);
        cleanup(context);
    }

    private void cleanup(ApplicationContext context) {
        EntityManager em = context.getBean(EntityManager.class);
        TransactionTemplate transactionTemplate = context.getBean(TransactionTemplate.class);

        transactionTemplate.execute(action -> {
            em.clear();
            truncateTables(em);
            return null;
        });
    }

    private void truncateTables(EntityManager em) {
        em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
        for (String tableName : findTableNames(em)) {
            em.createNativeQuery("TRUNCATE TABLE %s".formatted(tableName)).executeUpdate();
        }
        em.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
    }

    /* 현재 스키마의 테이블을 조회 **/
    @SuppressWarnings("unchecked")
    private List<String> findTableNames(EntityManager em) {
        String tableNameSelectQuery = """
                SELECT TABLE_NAME
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'
                """;
        return em.createNativeQuery(tableNameSelectQuery).getResultList();
    }

}
