package soma.ghostrunner.domain.running.dao;

import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.running.application.dto.response.QCourseInfo;
import soma.ghostrunner.domain.running.application.dto.response.QSoloRunInfo;
import soma.ghostrunner.domain.running.application.dto.response.SoloRunInfo;
import soma.ghostrunner.domain.running.domain.QRunning;

import java.util.Optional;

import static soma.ghostrunner.domain.course.domain.QCourse.course;
import static soma.ghostrunner.domain.running.domain.QRunning.running;

@Repository
@RequiredArgsConstructor
public class CustomRunningRepositoryImpl implements CustomRunningRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<SoloRunInfo> findSoloRunInfoById(long runningId) {
        QRunning subRunning = new QRunning("subRunning");

        return Optional.ofNullable(
                queryFactory
                        .select(new QSoloRunInfo(running, new QCourseInfo(course,
                                JPAExpressions.select(subRunning.count()).from(subRunning).where(subRunning.course.id.eq(course.id)))))
                        .from(running)
                        .join(running.course, course)
                        .where(running.id.eq(runningId))
                        .fetchOne());
    }
}
