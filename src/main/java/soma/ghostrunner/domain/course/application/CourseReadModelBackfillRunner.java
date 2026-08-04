package soma.ghostrunner.domain.course.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import soma.ghostrunner.domain.course.dao.CourseRepository;

import java.util.List;

/**
 * 리드모델 일회성 백필 러너. (설계 04 §4 — PR-2)
 *
 * <p>대상: 리드모델이 없는 기존 공개 코스(리드모델 도입 전에 공개된 코스들). 조회 경로를 리드모델로
 * 전환하기 전에 실행하지 않으면 해당 코스들이 지도에서 사라진다.</p>
 *
 * <p>실행 절차: {@code course.read-model.backfill=true} 프로퍼티를 켜고 기동 → 완료 로그 확인 →
 * 프로퍼티 제거. 멱등이므로 재실행해도 안전하다(있는 리드모델은 재계산으로 보정만 된다).
 * SQL 대안: docs/refactoring/course-read-model/ddl/migration-backfill.sql</p>
 *
 * <p>코스별 개별 트랜잭션으로 처리한다 — Writer 가 {@code MANDATORY} 라 트랜잭션이 필수이고,
 * 전체를 한 트랜잭션으로 묶으면 실패 시 전량 롤백 + 장시간 락 보유가 되기 때문.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "course.read-model.backfill", havingValue = "true")
@RequiredArgsConstructor
public class CourseReadModelBackfillRunner implements ApplicationRunner {

    private final CourseRepository courseRepository;
    private final CourseReadModelWriter readModelWriter;
    private final PlatformTransactionManager transactionManager;

    @Override
    public void run(ApplicationArguments args) {
        List<Long> publicCourseIds = courseRepository.findAllPublicCourseIds();
        log.info("Read model backfill started. targetCourses={}", publicCourseIds.size());

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        int failures = 0;
        for (Long courseId : publicCourseIds) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    readModelWriter.syncPublicity(courseId, true);   // 없으면 생성 + 전체 재계산
                    readModelWriter.recalculate(List.of(courseId));  // 있던 리드모델도 원본 기준으로 보정
                });
            } catch (Exception e) {
                failures++;
                log.error("Read model backfill failed for course={}", courseId, e);
            }
        }
        log.info("Read model backfill finished. total={}, failed={}", publicCourseIds.size(), failures);
    }
}
