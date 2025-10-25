package soma.ghostrunner.domain.running.infra;

import jakarta.persistence.EntityManager;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.running.domain.Pacemaker;
import soma.ghostrunner.domain.running.domain.PacemakerSet;
import soma.ghostrunner.domain.running.infra.persistence.PacemakerRepository;
import soma.ghostrunner.domain.running.infra.persistence.PacemakerSetRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class PacemakerRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private PacemakerRepository pacemakerRepository;

    @Autowired
    private PacemakerSetRepository pacemakerSetRepository;

    @Autowired
    private EntityManager em;

    @DisplayName("페이스메이커를 삭제하면 자식의 페이스메이커 세트들까지 모두 삭제된다.")
    @Test
    void deletePacemakers() {
        // given
        Pacemaker pacemaker = createPacemaker();
        pacemakerRepository.save(pacemaker);

        List<PacemakerSet> pacemakerSets = createPacemakerSets(pacemaker);
        pacemakerSetRepository.saveAll(pacemakerSets);

        // when
        pacemakerRepository.softDelete(pacemaker.getId());
        pacemakerRepository.softDeleteAllByPacemakerId(pacemaker.getId());

        // then
        Assertions.assertThat(pacemakerRepository.findById(pacemaker.getId())).isEmpty();
        Assertions.assertThat(pacemakerSetRepository.findByPacemakerIdOrderBySetNumAsc(pacemaker.getId())).isEmpty();
     }

    private Pacemaker createPacemaker() {
        return Pacemaker.of(Pacemaker.Norm.DISTANCE, 15.0, 1L, "MEMBER UUID");
    }

    private List<PacemakerSet> createPacemakerSets(Pacemaker pacemaker) {
        return List.of(
                PacemakerSet.of(1, "첫번째 세트", 0.0, 2.0, 7.0, pacemaker),
                PacemakerSet.of(2, "두번째 세트", 2.0, 8.0, 4.0, pacemaker),
                PacemakerSet.of(3, "세번째 세트", 8.0, 15.0, 10.0, pacemaker)
        );
    }

    @DisplayName("코스의 가장 최근에 만들어졌으며 + 아직 뛰지 않은 페이스메이커를 조회한다.")
    @Test
    void findMostRecentNotRunPacemaker() throws Exception {
        // given
        String member = "MEMBER-A";
        Long courseId = 100L;

        // 오래된 것
        Pacemaker p1 = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, courseId, member);
        pacemakerRepository.save(p1);
        em.flush(); em.clear();

        // 생성시각 차이를 위해 약간 대기 (createdAt 순서 보장)
        Thread.sleep(5);

        // 최신 것
        Pacemaker p2 = Pacemaker.of(Pacemaker.Norm.DISTANCE, 15.0, courseId, member);
        pacemakerRepository.save(p2);
        em.flush(); em.clear();

        // when
        Optional<Pacemaker> found = pacemakerRepository.findByCourseId(courseId, member);

        // then: 최신(p2)을 반환해야 함
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(p2.getId());
    }

    @DisplayName("hasRunWith = true(이미 함께 뛴 세션)는 제외되고, false 중 최신만 반환된다.")
    @Test
    void excludeHasRunWithTrue() throws Exception {
        // given
        String member = "MEMBER-A";
        Long courseId = 200L;

        Pacemaker oldValid = Pacemaker.of(Pacemaker.Norm.DISTANCE, 8.0, courseId, member);
        pacemakerRepository.save(oldValid);
        em.flush(); em.clear();

        Thread.sleep(5);

        Pacemaker latestButRan = Pacemaker.of(Pacemaker.Norm.DISTANCE, 12.0, courseId, member);
        latestButRan.updateAfterRunning(1L);
        pacemakerRepository.save(latestButRan);

        // when
        Optional<Pacemaker> found = pacemakerRepository.findByCourseId(courseId, member);

        // then: 최신이지만 이미 뛴 세션(latestButRan)은 제외 → oldValid가 반환
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(oldValid.getId());
    }

    @DisplayName("다른 멤버의 페이스메이커는 조회되지 않는다.")
    @Test
    void notReturnOtherMember() {
        // given
        Long courseId = 300L;
        pacemakerRepository.save(Pacemaker.of(Pacemaker.Norm.DISTANCE, 5.0, courseId, "MEMBER-A"));

        // when
        Optional<Pacemaker> found = pacemakerRepository.findByCourseId(courseId, "MEMBER-B");

        // then
        assertThat(found).isEmpty();
    }

    @DisplayName("다른 코스의 페이스메이커는 조회되지 않는다.")
    @Test
    void notReturnOtherCourse() {
        // given
        String member = "MEMBER-A";
        pacemakerRepository.save(Pacemaker.of(Pacemaker.Norm.DISTANCE, 5.0, 400L, member));

        // when
        Optional<Pacemaker> found = pacemakerRepository.findByCourseId(401L, member);

        // then
        assertThat(found).isEmpty();
    }

    @DisplayName("소프트 삭제된 페이스메이커는 조회에서 자동으로 제외된다.")
    @Test
    void softDeletedExcluded() {
        // given
        String member = "MEMBER-A";
        Long courseId = 500L;
        Pacemaker p = pacemakerRepository.save(Pacemaker.of(Pacemaker.Norm.DISTANCE, 7.0, courseId, member));
        em.flush(); em.clear();

        // 소프트 삭제
        pacemakerRepository.softDelete(p.getId());
        em.flush(); em.clear();

        // when
        Optional<Pacemaker> found = pacemakerRepository.findByCourseId(courseId, member);

        // then
        assertThat(found).isEmpty();
    }

    @DisplayName("여러 개 중 조건을 만족하는 것이 하나도 없으면 빈 Optional을 반환한다.")
    @Test
    void returnEmptyWhenNoCandidate() {
        // given
        String member = "MEMBER-A";
        Long courseId = 600L;

        Pacemaker failedRun = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, courseId, member);
        failedRun.updateStatus(Pacemaker.Status.FAILED);
        Pacemaker ran = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, courseId, member);
        ran.updateAfterRunning(1L);
        pacemakerRepository.saveAll(List.of(failedRun, ran));

        // when
        Optional<Pacemaker> found = pacemakerRepository.findByCourseId(courseId, member);

        // then
        assertThat(found).isEmpty();
    }

    @DisplayName("가공중인 페이스메이커가 두 개라면 최신 가공중인 페이스메이커가 조회된다.")
    @Test
    void returnLatestProceedingPacemaker() throws InterruptedException {
        // given
        String member = "MEMBER-A";
        Long courseId = 600L;

        Pacemaker ran1 = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, courseId, member);
        ran1.updateAfterRunning(1L);
        Pacemaker ran2 = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, courseId, member);
        ran2.updateAfterRunning(1L);
        pacemakerRepository.saveAll(List.of(ran1, ran2));

        Pacemaker notYetRun1 = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, courseId, member);
        pacemakerRepository.save(notYetRun1);

        Thread.sleep(5);

        Pacemaker notYetRun2 = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, courseId, member);
        pacemakerRepository.save(notYetRun2);

        // when
        Optional<Pacemaker> found = pacemakerRepository.findByCourseId(courseId, member);

        // then
        assertThat(found.get().getId()).isEqualTo(notYetRun2.getId());
    }

    @DisplayName("가장 최근 페이스메이커가 가공에 실패했다면 이전의 가공되어 있는 페이스메이커가 나온다.")
    @Test
    void returnNotFailedPacemaker() throws InterruptedException {
        // given
        String member = "MEMBER-A";
        Long courseId = 600L;

        Pacemaker ran1 = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, courseId, member);
        ran1.updateAfterRunning(1L);
        Pacemaker ran2 = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, courseId, member);
        ran2.updateAfterRunning(1L);
        pacemakerRepository.saveAll(List.of(ran1, ran2));

        Pacemaker succeedPacemaker = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, courseId, member);
        pacemakerRepository.save(succeedPacemaker);

        Thread.sleep(5);

        Pacemaker failedRun = Pacemaker.of(Pacemaker.Norm.DISTANCE, 10.0, courseId, member);
        failedRun.updateStatus(Pacemaker.Status.FAILED);
        pacemakerRepository.save(failedRun);

        // when
        Optional<Pacemaker> found = pacemakerRepository.findByCourseId(courseId, member);

        // then
        assertThat(found.get().getId()).isEqualTo(succeedPacemaker.getId());
    }

}
