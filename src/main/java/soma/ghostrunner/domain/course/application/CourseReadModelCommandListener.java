package soma.ghostrunner.domain.course.application;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import soma.ghostrunner.domain.course.dao.CourseReadModelRepository;
import soma.ghostrunner.domain.course.domain.*;
import soma.ghostrunner.domain.course.dto.CourseRankInfo;
import soma.ghostrunner.domain.running.domain.events.RunFinishedEvent;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseReadModelCommandListener {

    private final CourseReadModelRepository courseReadModelRepository;
    private final CourseRankFinder courseRankFinder;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void createReadModel(CourseRegisteredEvents event) {
        Course course = event.getCourse();
        CourseReadModel readModel = CourseReadModel.of(course);

        CourseRankInfo rankInfo = courseRankFinder.findFirstRunnerByCourseId(course.getId());
        readModel.updateFirstRunner(toRankSlot(rankInfo));

        courseReadModelRepository.save(readModel);
    }

    private RankSlot toRankSlot(CourseRankInfo rankInfo) {
        return RankSlot.of(rankInfo.getMemberId(), rankInfo.getMemberProfileUrl());
    }

    @EventListener
    public void updateReadModel(RunFinishedEvent event) {
        Course course = event.course();

        if (courseIsPublic(course)) {
            CourseReadModel courseReadModel = findReadModel(course);

            Long runnersCount = courseRankFinder.countRunnersByCourseId(course.getId());
            updateRunnersCount(courseReadModel, runnersCount);

            if (!event.hasPaused()) {
                List<CourseRankInfo> top4Runners = courseRankFinder.findCourseTop4RankInfoByCourseId(course.getId());
                updateTop4Runners(top4Runners, courseReadModel);
            }

            courseReadModelRepository.save(courseReadModel);
        }
    }

    private Boolean courseIsPublic(Course course) {
        return course.validateIsPublic();
    }

    private CourseReadModel findReadModel(Course course) {
        return courseReadModelRepository.findByCourseId(course.getId())
                .orElseThrow(() -> new IllegalArgumentException("공개되지 않은 코스를 조회하여 리드모델이 없는 경우"));
    }

    private void updateRunnersCount(CourseReadModel courseReadModel, Long runnersCount) {
        courseReadModel.updateRunnersCount(runnersCount);
    }

    private void updateTop4Runners(List<CourseRankInfo> top4Runners, CourseReadModel courseReadModel) {
        List<RankSlot> slots = toRankSlots(top4Runners);
        courseReadModel.updateRanking(slots);
    }

    private List<RankSlot> toRankSlots(List<CourseRankInfo> top4Runners) {
        return top4Runners.stream()
                .map(this::toRankSlot)
                .toList();
    }

}
