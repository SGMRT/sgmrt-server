package soma.ghostrunner.domain.running.infra;

import jakarta.persistence.EntityManager;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Coordinate;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.course.dto.CourseRunDto;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.running.application.dto.response.*;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class RunningRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private RunningRepository runningRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    EntityManager em;

    @DisplayName("특정 코스 ID에 해당하는 모든 러닝 ID 목록을 조회한다")
    @Test
    void findIdsByCourseId() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse(member);
        courseRepository.save(course);

        Running running1 = createRunning(member, course);
        Running running2 = createRunning(member, course);
        Running running3 = createRunning(member, course);
        runningRepository.saveAll(List.of(running1, running2, running3));

        // when
        List<Long> runningIds = runningRepository.findIdsByCourseId(course.getId());

        // then
        assertThat(runningIds)
                .hasSize(3)
                .containsExactly(running1.getId(), running2.getId(), running3.getId());
    }

    private Running createRunning(Member testMember, Course testCourse) {
        RunningRecord testRunningRecord = RunningRecord.of(5.2, 30.0, 40.0, -20.0,
                6.1, 4.9, 6.9, 3423L, 302, 120, 56);
        return Running.of("테스트 러닝 제목", RunningMode.SOLO, null, testRunningRecord, 1750729987181L,
                true, false, "URL", "URL", "URL", testMember, testCourse);
    }

    private Member createMember(String name) {
        return Member.of(name, "프로필 URL");
    }

    private Course createCourse(Member testMember) {
        CourseProfile testCourseProfile = createCourseProfile();
        Coordinate testCoordinate = createStartPoint();
        Course course = Course.of(testMember, testCourseProfile.getDistance(),
                testCourseProfile.getElevationAverage(), testCourseProfile.getElevationGain(), testCourseProfile.getElevationLoss(),
                testCoordinate.getLatitude(), testCoordinate.getLongitude(),
                "URL", "URL", "URL");
        course.setIsPublic(true);
        return course;
    }

    private Coordinate createStartPoint() {
        return Coordinate.of(37.545354, 34.7878);
    }

    private CourseProfile createCourseProfile() {
        return CourseProfile.of(5.2, 30.0, 40.0, -10.0);
    }

    @DisplayName("러닝 ID와 멤버 ID로 러닝을 조회한다.")
    @Test
    void findByIdAndMemberId() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse(member);
        courseRepository.save(course);

        Running running = createRunning(member, course);
        runningRepository.save(running);

        // when
        Running savedRunning = runningRepository.findByIdAndMemberId(running.getId(), member.getId()).get();

        // then
        assertThat(savedRunning.getId()).isEqualTo(running.getId());
    }

    @DisplayName("러닝 ID에 해당하지 않은 멤버 ID로 조회하는 경우 Optional.Empty를 반환한다.")
    @Test
    void findByIdAndFakeMemberId() {
        // given
        Member member = createMember("이복둥");
        Member fakeMember = createMember("페이크 이복둥");
        memberRepository.saveAll(List.of(member, fakeMember));

        Course course = createCourse(member);
        courseRepository.save(course);

        Running running = createRunning(member, course);
        runningRepository.save(running);

        // when
        Optional<Running> savedRunning = runningRepository.findByIdAndMemberId(running.getId(), fakeMember.getId());

        // then
        assertThat(savedRunning).isEmpty();
    }

    @DisplayName("러닝 ID로 시계열 URL을 조회한다.")
    @Test
    void findRunningUrlByRunningId() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse(member);
        courseRepository.save(course);

        Running running = createRunning(member, course);
        runningRepository.save(running);

        // when
        String url = runningRepository.findById(running.getId()).get().getRunningDataUrls().getInterpolatedTelemetryUrl();

        // then
        assertThat(url)
                .isEqualTo(running.getRunningDataUrls().getInterpolatedTelemetryUrl());
    }

    @DisplayName("혼자 뛴 러닝에 대한 상세 정보를 조회한다. 코스는 공개/비공개 유무에 상관 없이 코스 정보를 함께 응답한다.")
    @Test
    void findSoloRunInfoById() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse(member, "테스트 코스");
        courseRepository.save(course);

        Running running1 = createSoloRunning(member, course);
        Running running2 = createSoloRunning(member, course);
        runningRepository.saveAll(List.of(running1, running2));

        // when
        SoloRunDetailInfo soloRunDetailInfo = runningRepository.findSoloRunInfoById(running1.getId(), member.getUuid()).get();

        // then
        assertThat(soloRunDetailInfo.getStartedAt()).isEqualTo(running1.getStartedAt());
        assertThat(soloRunDetailInfo.getRunningName()).isEqualTo(running1.getRunningName());
        assertThat(soloRunDetailInfo.getCourseInfo().getId()).isEqualTo(running1.getCourse().getId());
        assertThat(soloRunDetailInfo.getCourseInfo().getName()).isEqualTo(running1.getCourse().getName());
        assertThat(soloRunDetailInfo.getTelemetryUrl())
                .isEqualTo(running1.getRunningDataUrls().getInterpolatedTelemetryUrl());
        assertThat(soloRunDetailInfo.getRecordInfo().getDistance())
                .isEqualTo(running1.getRunningRecord().getDistance());
        assertThat(soloRunDetailInfo.getRecordInfo().getDuration())
                .isEqualTo(running1.getRunningRecord().getDuration());
    }

    private Running createSoloRunning(Member testMember, Course testCourse) {
        RunningRecord testRunningRecord = RunningRecord.of(5.2, 30.0, 40.0, -20.0,
                6.1, 4.9, 6.9, 3423L, 302, 120, 56);
        return Running.of("테스트 러닝 제목", RunningMode.SOLO, null, testRunningRecord, 1750729987181L,
                true, false, "URL", "URL", "URL", testMember, testCourse);
    }

    private Course createCourse(Member testMember, String courseName) {
        CourseProfile testCourseProfile = createCourseProfile();
        Coordinate testCoordinate = createStartPoint();
        Course course = Course.of(testMember, testCourseProfile.getDistance(),
                testCourseProfile.getElevationAverage(), testCourseProfile.getElevationGain(), testCourseProfile.getElevationLoss(),
                testCoordinate.getLatitude(), testCoordinate.getLongitude(),
                "URL", "URL", "URL");
        course.setName(courseName);
        course.setIsPublic(true);
        return course;
    }

    @DisplayName("기존 코스를 기반으로 혼자 뛴 러닝에 대한 상세 정보를 조회한다. 비공개 코스라도 정보를 모두 응답한다.")
    @Test
    void findSoloRunInfoByIdWithUnPublicCourse() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createUnPublicCourse(member);
        courseRepository.save(course);

        Running running1 = createSoloRunning(member, course);
        Running running2 = createSoloRunning(member, course);
        runningRepository.saveAll(List.of(running1, running2));

        // when
        SoloRunDetailInfo soloRunDetailInfo = runningRepository.findSoloRunInfoById(running1.getId(), member.getUuid()).get();

        // then
        assertThat(soloRunDetailInfo.getCourseInfo().getId()).isEqualTo(course.getId());
        assertThat(soloRunDetailInfo.getCourseInfo().getIsPublic()).isFalse();
    }

    private Course createUnPublicCourse(Member testMember) {
        CourseProfile testCourseProfile = createCourseProfile();
        Coordinate testCoordinate = createStartPoint();
        Course course = Course.of(testMember, testCourseProfile.getDistance(),
                testCourseProfile.getElevationAverage(), testCourseProfile.getElevationGain(), testCourseProfile.getElevationLoss(),
                testCoordinate.getLatitude(), testCoordinate.getLongitude(),
                "URL", "URL", "URL");
        course.setIsPublic(false);
        return course;
    }

    @DisplayName("기존 코스를 기반으로 혼자 뛴 러닝에 대한 상세 정보를 조회할 때 기록이 없다면 Empty가 조회된다.")
    @Test
    void findSoloRunInfoByInvalidRunningId() {
        // then
        assertThat(
                runningRepository.findSoloRunInfoById(Long.MAX_VALUE, UUID.randomUUID().toString())).isEmpty();
    }

    @DisplayName("기존 코스를 기반으로 고스트와 달린 러닝에 대한 코스와 나의 러닝 상세 정보를 조회한다. 고스트의 정보는 바로 조회되지 않는다.")
    @Test
    void findGhostRunInfoById() {
        // given
        Member member = createMember("이복둥");
        Member ghostMember = createMember("고스트 이복둥");
        memberRepository.saveAll(List.of(member, ghostMember));

        Course course = createCourse(member, "테스트 코스");
        courseRepository.save(course);

        Running ghostRunning = createRunning(ghostMember, course);
        runningRepository.save(ghostRunning);
        Running running = createGhostRunning(member, course, ghostRunning.getGhostRunningId());
        runningRepository.save(running);

        // when
        GhostRunDetailInfo ghostRunDetailInfo = runningRepository.findGhostRunInfoById(running.getId(), member.getUuid()).get();

        // then
        assertThat(ghostRunDetailInfo.getStartedAt()).isEqualTo(running.getStartedAt());
        assertThat(ghostRunDetailInfo.getRunningName()).isEqualTo(running.getRunningName());

        assertThat(ghostRunDetailInfo.getMyRunInfo().getNickname()).isEqualTo("이복둥");
        assertThat(ghostRunDetailInfo.getMyRunInfo().getProfileUrl()).isEqualTo("프로필 URL");

        assertThat(ghostRunDetailInfo.getMyRunInfo().getRecordInfo().getDistance()).isEqualTo(running.getRunningRecord().getDistance());
        assertThat(ghostRunDetailInfo.getMyRunInfo().getRecordInfo().getDuration()).isEqualTo(running.getRunningRecord().getDuration());
        assertThat(ghostRunDetailInfo.getTelemetryUrl()).isEqualTo(running.getRunningDataUrls().getInterpolatedTelemetryUrl());

        assertThat(ghostRunDetailInfo.getCourseInfo().getName()).isEqualTo("테스트 코스");

        assertThat(ghostRunDetailInfo.getGhostRunInfo()).isNull();
    }

    private Running createGhostRunning(Member testMember, Course testCourse, Long ghostRunningId) {
        RunningRecord testRunningRecord = RunningRecord.of(5.2, 30.0, 40.0, -20.0,
                6.1, 4.9, 6.9, 3423L, 302, 120, 56);
        return Running.of("테스트 러닝 제목", RunningMode.GHOST, ghostRunningId, testRunningRecord, 1750729987181L,
                true, false, "URL", "URL", "URL", testMember, testCourse);
    }

    @DisplayName("나의 닉네임, 프로필 URL, 러닝 상세정보를 조회한다.")
    @Test
    void findMemberAndRunRecordInfoById() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse(member, "테스트 코스");
        courseRepository.save(course);

        Running running = createSoloRunning(member, course);
        runningRepository.save(running);

        // when
        MemberAndRunRecordInfo memberAndRunRecordInfo = runningRepository.findMemberAndRunRecordInfoById(running.getId()).get();

        // then
        assertThat(memberAndRunRecordInfo.getNickname()).isEqualTo("이복둥");
        assertThat(memberAndRunRecordInfo.getProfileUrl()).isEqualTo("프로필 URL");
        assertThat(memberAndRunRecordInfo.getRecordInfo().getCadence()).isEqualTo(running.getRunningRecord().getCadence());
        assertThat(memberAndRunRecordInfo.getRecordInfo().getDuration()).isEqualTo(running.getRunningRecord().getDuration());
        assertThat(memberAndRunRecordInfo.getRecordInfo().getAveragePace()).isEqualTo(running.getRunningRecord().getAveragePace());
    }

    @DisplayName("러닝 시계열 url을 조회한다.")
    @Test
    void testGetRunningTelemetriesUrl() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse(member, "테스트 코스");
        courseRepository.save(course);

        Running running = createSoloRunning(member, course);
        runningRepository.save(running);

        // when
        String url = runningRepository.findInterpolatedTelemetryUrlByIdAndMemberUuid(running.getId(), member.getUuid()).get();

        // then
        assertThat(url).isEqualTo(running.getRunningDataUrls().getInterpolatedTelemetryUrl());
    }

    @DisplayName("코스에 대해 첫 번째 러닝 기록을 조회한다.")
    @Test
    void findFirstRunningByCourseId() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse(member, "테스트 코스");
        courseRepository.save(course);

        Running running1 = createSoloRunning(member, course);
        Running running2 = createSoloRunning(member, course);
        Running running3 = createSoloRunning(member, course);
        runningRepository.saveAll(List.of(running1, running2, running3));

        // when
        Running firstRunning = runningRepository.findFirstRunningByCourseId(course.getId()).get();

        // then
        assertThat(firstRunning.getId()).isEqualTo(running1.getId());
    }

    @DisplayName("러닝ID 리스트에 있는 러닝 기록을 조회한다.")
    @Test
    void findByIds() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse(member, "테스트 코스");
        courseRepository.save(course);

        Running running1 = createRunning(member, course, "러닝 제목1");
        Running running2 = createRunning(member, course, "러닝 제목2");
        Running running3 = createRunning(member, course, "러닝 제목3");
        Running running4 = createRunning(member, course, "러닝 제목4");
        runningRepository.saveAll(List.of(running1, running2, running3, running4));

        // when
        List<Running> runnings = runningRepository.findByIds(List.of(running1.getId(), running2.getId(), running3.getId(), Long.MAX_VALUE));

        // then
        assertThat(runnings)
                .hasSize(3)
                .extracting("id", "runningName")
                .containsExactlyInAnyOrder(
                        tuple(running1.getId(), "러닝 제목1"),
                        tuple(running2.getId(), "러닝 제목2"),
                        tuple(running3.getId(), "러닝 제목3")
                );
    }

    private Running createRunning(Member testMember, Course testCourse, String runningName) {
        RunningRecord testRunningRecord = RunningRecord.of(5.2, 30.0, 40.0, -20.0,
                6.1, 4.9, 6.9, 3423L, 302, 120, 56);
        return Running.of(runningName, RunningMode.SOLO, null, testRunningRecord, 1750729987181L,
                true, false, "URL","URL","URL", testMember, testCourse);
    }

    @DisplayName("러닝 기록을 삭제한다.")
    @Test
    void deleteRunnings() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse(member, "테스트 코스");
        courseRepository.save(course);

        Running running1 = createRunning(member, course, "러닝 제목1");
        Running running2 = createRunning(member, course, "러닝 제목2");
        Running running3 = createRunning(member, course, "러닝 제목3");
        Running running4 = createRunning(member, course, "러닝 제목4");
        runningRepository.saveAll(List.of(running1, running2, running3, running4));

        // when
        runningRepository.deleteInRunningIds(List.of(running1.getId(), running2.getId(), running3.getId(), running4.getId()));

        // then
        List<Running> runnings = runningRepository.findByIds(List.of(running1.getId(), running2.getId(),
                running3.getId(), running4.getId()));
        assertThat(runnings).hasSize(0);

        List<Running> deletedRunnings = runningRepository.findByIdsNoMatterDeleted(List.of(running1.getId(),
                running2.getId(), running3.getId(), running4.getId()));
        assertThat(deletedRunnings)
                .hasSize(4)
                .extracting("id", "runningName")
                .containsExactlyInAnyOrder(
                        tuple(running1.getId(), "러닝 제목1"),
                        tuple(running2.getId(), "러닝 제목2"),
                        tuple(running3.getId(), "러닝 제목3"),
                        tuple(running4.getId(), "러닝 제목4")
                );
    }

    @DisplayName("시간, 러닝 ID를 기준으로 커서 페이징(DESC)으로 러닝을 조회한다.")
    @Test
    void findRunInfosByCursorIds() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course c1 = createCourse(member);
        Course c2 = createCourse(member);
        Course c3 = createCourse(member);
        courseRepository.saveAll(List.of(c1, c2, c3));
        List<Course> courses = List.of(c1, c2, c3);

        Random rnd = new Random(42);
        List<Running> runnings = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            long startedAt = Math.abs(rnd.nextLong() % 1_000_000L);
            runnings.add(createRunning("러닝" + i, courses.get(rnd.nextInt(3)),
                    member, startedAt, RunningMode.SOLO));
        }
        for (int i = 100; i < 200; i++) {
            long startedAt = Math.abs(rnd.nextLong() % 1_000_000L);
            runnings.add(createRunning("러닝" + i, courses.get(rnd.nextInt(3)),
                    member, startedAt, RunningMode.GHOST));
        }
        runningRepository.saveAll(runnings);

        // 기대값: DESC 정렬(동률 시 id DESC)
        Comparator<Running> desc = Comparator.comparing(Running::getStartedAt, Comparator.reverseOrder())
                .thenComparing(Running::getId, Comparator.reverseOrder());
        List<Running> expected = runnings.stream()
                .sorted(desc)
                .toList();

        long startEpoch = 0L;
        long endEpoch   = 1_000_000L;

        // when
        List<RunInfo> page1 = runningRepository.findRunInfosFilteredByDate(
                null, null, startEpoch, endEpoch, member.getId());

        RunInfo cursor = page1.get(page1.size() - 1);
        List<RunInfo> page2 = runningRepository.findRunInfosFilteredByDate(
                cursor.getStartedAt(), cursor.getRunningId(),
                startEpoch, endEpoch, member.getId());

        // then
        // 페이지 크기
        int page1Size = page1.size();
        assertThat(page1Size).isEqualTo(20);

        // 데이터 검증
        for (int i = 0; i < page1.size(); i++) {
            RunInfo a = page1.get(i);
            Running e = expected.get(i);
            assertThat(a.getRunningId()).isEqualTo(e.getId());
            assertThat(a.getName()).isEqualTo(e.getRunningName());
            assertThat(a.getStartedAt()).isEqualTo(e.getStartedAt());
            assertThat(a.getGhostRunningId()).isEqualTo(e.getGhostRunningId());
        }

        for (int i = page1.size(); i < page2.size(); i++) {
            RunInfo a = page2.get(i - page1.size());
            Running e = expected.get(page1Size + i);
            assertThat(a.getRunningId()).isEqualTo(e.getId());
            assertThat(a.getName()).isEqualTo(e.getRunningName());
            assertThat(a.getStartedAt()).isEqualTo(e.getStartedAt());
            assertThat(a.getGhostRunningId()).isNull();
        }

        // 중복 체크
        var ids1 = page1.stream().map(RunInfo::getRunningId).toList();
        var ids2 = page2.stream().map(RunInfo::getRunningId).toList();
        assertThat(Collections.disjoint(ids1, ids2)).isTrue();

        // 경계값 확인
        if (!page2.isEmpty()) {
            RunInfo firstOfPage2 = page2.get(0);
            boolean strictlyBefore =
                    (firstOfPage2.getStartedAt() < cursor.getStartedAt()) ||
                            (Objects.equals(firstOfPage2.getStartedAt(), cursor.getStartedAt()) &&
                                    firstOfPage2.getRunningId() < cursor.getRunningId());
            assertThat(strictlyBefore).isTrue();
        }

        //startedAt DESC, id DESC 로 정렬되어 있는지
        assertSortedByStartedAtThenIdDesc(page1);
        assertSortedByStartedAtThenIdDesc(page2);
    }

    private static void assertSortedByStartedAtThenIdDesc(List<RunInfo> list) {
        for (int i = 1; i < list.size(); i++) {
            RunInfo prev = list.get(i - 1);
            RunInfo curr = list.get(i);
            boolean ok = (curr.getStartedAt() < prev.getStartedAt()) ||
                    (Objects.equals(curr.getStartedAt(), prev.getStartedAt())
                            && curr.getRunningId() < prev.getRunningId());
            assertThat(ok)
                    .as("must be sorted by startedAt DESC, then id DESC at index " + i)
                    .isTrue();
        }
    }

    @DisplayName("기본/기간별 보기 방식으로 러닝 기록을 조회할 때 시작 시간이 같다면 ID 내림차순으로 정렬된다.")
    @Test
    void sortByIdWhenStartTimesAreEqual() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse(member);
        courseRepository.save(course);

        Running r1 = createRunning("A", course, member, 1000L, RunningMode.SOLO);
        Running r2 = createRunning("B", course, member, 2000L, RunningMode.SOLO);
        Running r3 = createRunning("C", course, member, 2000L, RunningMode.SOLO);
        Running r4 = createRunning("D", course, member, 2000L, RunningMode.SOLO);
        Running r5 = createRunning("E", course, member, 3000L, RunningMode.SOLO);

        runningRepository.saveAll(List.of(r1, r2, r3, r4, r5));

        long startEpoch = 0L;
        long endEpoch   = 10_000L;

        // when
        List<RunInfo> result = runningRepository.findRunInfosFilteredByDate(
                null, null, startEpoch, endEpoch, member.getId()
        );

        // then
        assertSortedByStartedAtThenIdDesc(result);

        List<RunInfo> at2000 = result.stream()
                .filter(r -> Objects.equals(r.getStartedAt(), 2000L))
                .toList();

        assertThat(at2000).hasSize(3);
        for (int i = 1; i < at2000.size(); i++) {
            Long prev = at2000.get(i - 1).getRunningId();
            Long curr = at2000.get(i).getRunningId();
            assertThat(curr).isLessThan(prev);
        }
    }

    @DisplayName("기본/기간별 보기 방식으로 러닝 기록을 조회할 때 삭제된 데이터는 조회되지 않는다.")
    @Test
    void excludeDeletedRunsFromResults() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse(member);
        courseRepository.save(course);

        Running r1 = createRunning("R1", course, member, 1000L, RunningMode.SOLO);
        Running r2 = createRunning("R2", course, member, 2000L, RunningMode.SOLO);
        Running r3 = createRunning("R3", course, member, 3000L, RunningMode.SOLO);
        runningRepository.saveAll(List.of(r1, r2, r3));

        runningRepository.deleteInRunningIds(List.of(r2.getId()));

        long startEpoch = 0L;
        long endEpoch   = 10_000L;

        // when
        List<RunInfo> result = runningRepository.findRunInfosFilteredByDate(
                null, null, startEpoch, endEpoch, member.getId()
        );

        // then
        List<Long> ids = result.stream().map(RunInfo::getRunningId).toList();
        assertThat(ids).contains(r1.getId(), r3.getId());
        assertThat(ids).doesNotContain(r2.getId());
        assertSortedByStartedAtThenIdDesc(result);
    }

    @DisplayName("기본/기간별 보기 방식으로 러닝 기록을 조회할 때 필터링 조건에서 시작/끝이 완전히 같아도 조회된다.")
    @Test
    void includeRunsWhenStartAndEndTimesAreEqual() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse(member);
        courseRepository.save(course);

        // 1000, 2000(2건), 3000
        Running r1 = createRunning("R1", course, member, 1000L, RunningMode.SOLO);
        Running r2 = createRunning("R2", course, member, 2000L, RunningMode.SOLO);
        Running r3 = createRunning("R3", course, member, 2000L, RunningMode.SOLO);
        Running r4 = createRunning("R4", course, member, 3000L, RunningMode.SOLO);
        runningRepository.saveAll(List.of(r1, r2, r3, r4));

        long startEpoch = 2000L;
        long endEpoch   = 2000L;  // start == end (between inclusive)

        // when
        List<RunInfo> result = runningRepository.findRunInfosFilteredByDate(
                null, null, startEpoch, endEpoch, member.getId()
        );

        // then: startedAt == 2000 인 것들만 전부 포함(동률 여러 건도 포함)
        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(r -> Objects.equals(r.getStartedAt(), 2000L));
        // 동률 집합 내에서도 id DESC
        assertSortedByStartedAtThenIdDesc(result);
        // 결과 아이디 집합이 정확히 r2, r3 로만 구성되었는지(순서는 DESC)
        List<Long> gotIds = result.stream().map(RunInfo::getRunningId).toList();
        assertThat(gotIds).containsExactlyElementsOf(
                List.of(Math.max(r2.getId(), r3.getId()), Math.min(r2.getId(), r3.getId()))
        );
    }

    @DisplayName("기본/기간별 보기 방식으로 러닝 기록을 조회할 때 비공개 코스라면 코스 정보는 Null로 조회된다.")
    @Test
    void returnNullCourseInfoForPrivateCourses() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course publicCourse1 = createCourse(member);
        Course publicCourse2 = createCourse(member);
        Course privateCourse = createCourse(member);
        privateCourse.setIsPublic(false);
        courseRepository.saveAll(List.of(publicCourse1, publicCourse2, privateCourse));
        List<Course> courses = List.of(publicCourse1, publicCourse2, privateCourse);

        Random rnd = new Random(42);
        List<Running> runnings = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            long startedAt = Math.abs(rnd.nextLong() % 1_000_000L);
            runnings.add(createRunning("러닝" + i, courses.get(rnd.nextInt(3)),
                    member, startedAt, RunningMode.SOLO));
        }
        runningRepository.saveAll(runnings);

        List<Running> runningWithPrivateCourse = runnings.stream()
                .filter(running -> running.getCourse().getIsPublic().equals(false))
                .toList();
        int runningWithPrivateCourseCount = runningWithPrivateCourse.size();

        long startEpoch = 0L;
        long endEpoch   = 1_000_000L;

        // when
        List<RunInfo> runInfos = runningRepository.findRunInfosFilteredByDate(
                null, null, startEpoch, endEpoch, member.getId());
        for (int i = 0; i < 4; i++) {
            RunInfo cursor = runInfos.get(runInfos.size() - 1);
            List<RunInfo> nextRunInfos = runningRepository.findRunInfosFilteredByDate(
                    cursor.getStartedAt(), cursor.getRunningId(),
                    startEpoch, endEpoch, member.getId());
            runInfos.addAll(nextRunInfos);
        }

        // then
        int nullCourseInfoCount = 0;
        for (RunInfo runInfo : runInfos) {
            if (runInfo.getCourseInfo() == null) {
                nullCourseInfoCount += 1;
            }
        }
        Assertions.assertThat(nullCourseInfoCount).isEqualTo(runningWithPrivateCourseCount);
    }

    private Running createRunningWithDuration(Course course, Member member, Long duration, Long startedAt) {
        return Running.of("러닝 제목", RunningMode.SOLO, null, createRunningRecord(duration), startedAt,
                true, false, "시계열 URL", "시계열 URL", "시계열 URL", member, course);
    }

    private Running createRunning(String runningName, Course course, Member member, Long startedAt, RunningMode runningMode) {
        if (runningMode.equals(RunningMode.SOLO)) {
            return Running.of(runningName, runningMode, null, createRunningRecord(), startedAt,
                    true, false, "시계열 URL", "시계열 URL", "시계열 URL", member, course);
        } else {
            Random random = new Random();
            return Running.of(runningName, runningMode, random.nextLong(), createRunningRecord(), startedAt,
                    true, false, "시계열 URL", "시계열 URL", "시계열 URL", member, course);
        }
    }

    private Running createRunning(String runningName, Course course, Member member, Long duration, Long startedAt) {
        return Running.of(runningName, RunningMode.SOLO, null, createRunningRecord(duration), startedAt,
                true, false, "시계열 URL", "시계열 URL", "시계열 URL", member, course);
    }

    private RunningRecord createRunningRecord() {
        return RunningRecord.of(5.2, 30.0, 40.0, -20.0,
                6.1, 3423.2, 302.2, 120L, 56, 100, 120);
    }

    private RunningRecord createRunningRecord(long duration) {
        return RunningRecord.of(5.2, 30.0, 40.0, -20.0,
                6.1, 3423.2, 302.2, duration, 56, 100, 120);
    }

    @DisplayName("코스명, 러닝ID(DESC) 커서로 기간 내 러닝 리스트를 조회한다 — 정렬·연속성·중복없음")
    @Test
    void findRunInfosFilteredByCoursesByCursorIds_basicPaging() {
        // given
        long START_MS = 0L;
        long END_MS   = 1_000_000L;

        Member member = createMember("이복둥");
        memberRepository.save(member);

        List<String> names = List.of("공덕역 코스", "반포 코스", "이대역 코스", "태화강 코스", "한강 코스");
        List<Course> courses = names.stream()
                .map(n -> { Course c = createCourse(member, n); c.setIsPublic(true); return c; })
                .toList();
        courseRepository.saveAll(courses);

        Random rnd = new Random(42);
        List<Running> all = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            long ts = Math.abs(rnd.nextLong() % END_MS);
            all.add(createRunning("러닝" + i, courses.get(rnd.nextInt(courses.size())),
                    member, ts, RunningMode.SOLO));
        }
        for (int i = 100; i < 200; i++) {
            long ts = Math.abs(rnd.nextLong() % END_MS);
            all.add(createRunning("러닝" + i, courses.get(rnd.nextInt(courses.size())),
                    member, ts, RunningMode.GHOST));
        }
        runningRepository.saveAll(all);

        // when
        List<RunInfo> page = runningRepository.findRunInfosFilteredByCourses(
                null, null, START_MS, END_MS, member.getId());
        int pageSize = page.size();
        assertThat(pageSize).isGreaterThan(0);

        // 커서로 누적 수집(여러 페이지)
        List<RunInfo> collected = new ArrayList<>(page);
        while (true) {
            RunInfo cursor = collected.get(collected.size() - 1);
            List<RunInfo> next = runningRepository.findRunInfosFilteredByCourses(
                    cursor.getCourseInfo().getName(), cursor.getRunningId(),
                    START_MS, END_MS, member.getId());
            if (next.isEmpty()) break;

            // 커서 경계
            RunInfo first = next.get(0);
            boolean strictlyAfter =
                    first.getCourseInfo().getName().compareTo(cursor.getCourseInfo().getName()) > 0
                            || (Objects.equals(first.getCourseInfo().getName(), cursor.getCourseInfo().getName())
                            && first.getRunningId() < cursor.getRunningId()); // 같으면 ID 더 작아야
            assertThat(strictlyAfter).isTrue();

            // 페이지 간 중복 없음
            Set<Long> prevIds = collected.stream().map(RunInfo::getRunningId).collect(Collectors.toSet());
            Set<Long> nextIds = next.stream().map(RunInfo::getRunningId).collect(Collectors.toSet());
            assertThat(Collections.disjoint(prevIds, nextIds)).isTrue();

            collected.addAll(next);
        }

        // then
        Comparator<Running> courseAscIdDesc = Comparator
                .comparing((Running r) -> r.getCourse().getName())
                .thenComparing(Running::getId, Comparator.reverseOrder());
        List<Running> expected = all.stream()
                .sorted(courseAscIdDesc)
                .toList();

        assertThat(collected).hasSize(expected.size());
        assertSortedByCourseAscThenIdDesc(collected);

        for (int i = 0; i < collected.size(); i++) {
            RunInfo a = collected.get(i);
            Running e = expected.get(i);
            assertThat(a.getCourseInfo().getName()).isEqualTo(e.getCourse().getName());
            assertThat(a.getRunningId()).isEqualTo(e.getId());
            assertThat(a.getName()).isEqualTo(e.getRunningName());
            assertThat(a.getStartedAt()).isEqualTo(e.getStartedAt());
            assertThat(a.getGhostRunningId()).isEqualTo(e.getGhostRunningId());
        }
    }

    @DisplayName("동일 코스명 다건이 있는 경우에도 ID 내림차순으로 정렬되고 커서 경계가 정확하다")
    @Test
    void sameCourseNameMany_rows_areStableAndSeekIsStrict() {
        // given
        long START_MS = 0L;
        long END_MS   = 1_000_000L;

        Member member = createMember("동률테스트");
        memberRepository.save(member);

        Course c = createCourse(member, "같은 이름 코스");
        c.setIsPublic(true);
        courseRepository.save(c);

        // 같은 코스명으로 SOLO 30건 — startedAt은 섞지만 정렬 키는 (name, id)
        List<Running> list = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            long ts = (i % 2 == 0) ? 1000L : 2000L; // 일부 동률 타임스탬프
            list.add(createRunning("S" + i, c, member, ts, RunningMode.SOLO));
        }
        runningRepository.saveAll(list);

        // when: 첫 페이지
        List<RunInfo> p1 = runningRepository.findRunInfosFilteredByCourses(
                null, null, START_MS, END_MS, member.getId());
        assertThat(p1).isNotEmpty();

        // 커서로 다음 페이지 조회
        RunInfo cursor = p1.get(p1.size() - 1);
        List<RunInfo> p2 = runningRepository.findRunInfosFilteredByCourses(
                cursor.getCourseInfo().getName(), cursor.getRunningId(),
                START_MS, END_MS, member.getId());

        // then
        assertSortedByCourseAscThenIdDesc(p1);
        assertSortedByCourseAscThenIdDesc(p2);

        if (!p2.isEmpty()) {
            RunInfo first = p2.get(0);
            boolean strictlyBefore =
                    first.getCourseInfo().getName().compareTo(cursor.getCourseInfo().getName()) < 0
                            || (Objects.equals(first.getCourseInfo().getName(), cursor.getCourseInfo().getName())
                            && first.getRunningId() < cursor.getRunningId());
            assertThat(strictlyBefore).isTrue();
        }

        // 중복 없음
        Set<Long> ids1 = p1.stream().map(RunInfo::getRunningId).collect(Collectors.toSet());
        Set<Long> ids2 = p2.stream().map(RunInfo::getRunningId).collect(Collectors.toSet());
        assertThat(Collections.disjoint(ids1, ids2)).isTrue();
    }

    @DisplayName("기간 필터: start==end(포함)일 때 해당 시각의 레코드만 반환한다 (between inclusive, 정렬 DESC)")
    @Test
    void range_inclusive_when_start_equals_end() {
        // given
        Member member = createMember("기간검증");
        memberRepository.save(member);

        Course c1 = createCourse(member, "A");
        Course c2 = createCourse(member, "B");
        c1.setIsPublic(true); c2.setIsPublic(true);
        courseRepository.saveAll(List.of(c1, c2));

        long target = 555_555L;
        Running in1  = createRunning("IN1", c1, member, target, RunningMode.SOLO);
        Running in2  = createRunning("IN2", c2, member, target, RunningMode.SOLO);
        Running out1 = createRunning("OUT_LOW",  c1, member, target - 1, RunningMode.SOLO);
        Running out2 = createRunning("OUT_HIGH", c2, member, target + 1, RunningMode.SOLO);
        runningRepository.saveAll(List.of(in1, in2, out1, out2));

        // when
        List<RunInfo> result = runningRepository.findRunInfosFilteredByCourses(
                null, null, target, target, member.getId());

        // then: target 시각만 포함, 정렬은 (코스명 ASC, id DESC)
        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(r -> Objects.equals(r.getStartedAt(), target));
        assertSortedByCourseAscThenIdDesc(result);

        List<Long> got = result.stream().map(RunInfo::getRunningId).toList();
        List<Running> expectOrder = List.of(in1, in2).stream()
                .sorted(Comparator
                        .comparing((Running r) -> r.getCourse().getName()) // ASC
                        .thenComparing(Running::getId, Comparator.reverseOrder())) // DESC
                .toList();
        List<Long> expect = expectOrder.stream().map(Running::getId).toList();
        assertThat(got).containsExactlyElementsOf(expect);
    }

    private void assertSortedByCourseAscThenIdDesc(List<RunInfo> list) {
        for (int i = 1; i < list.size(); i++) {
            RunInfo prev = list.get(i - 1);
            RunInfo curr = list.get(i);
            int nameCmp = curr.getCourseInfo().getName().compareTo(prev.getCourseInfo().getName());
            boolean ok = nameCmp > 0 // ASC
                    || (nameCmp == 0 && curr.getRunningId() < prev.getRunningId()); // 같으면 ID DESC
            assertThat(ok)
                    .as("List must be sorted by course.name ASC, then id DESC at index " + i)
                    .isTrue();
        }
    }

    @DisplayName("기간 밖이면 빈 결과를 반환한다")
    @Test
    void outOfRange_returnsEmpty() {
        // given
        Member member = createMember("기간빈");
        memberRepository.save(member);

        Course c = createCourse(member, "X");
        c.setIsPublic(true);
        courseRepository.save(c);

        runningRepository.save(createRunning("R", c, member, 1000L, RunningMode.SOLO));

        // when
        List<RunInfo> r = runningRepository.findRunInfosFilteredByCourses(
                null, null, 2000L, 3000L, member.getId());

        // then
        assertThat(r).isEmpty();
    }

    @DisplayName("코스에 대한 전체 러닝 갯수를 출력한다. 같은 사용자라도 중복 허용된다.")
    @Test
    void countTotalRunningsCount() {
        // given
        Member member1 = createMember("이복둥");
        Member member2 = createMember("이복둥 주인");
        memberRepository.saveAll(List.of(member1, member2));

        Course c1 = createCourse(member1);
        Course c2 = createCourse(member1);
        List<Course> courses = List.of(c1, c2);
        courseRepository.saveAll(courses);

        Random rnd = new Random(42);
        List<Running> runnings = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            long startedAt = Math.abs(rnd.nextLong() % 1_000_000L);
            runnings.add(createRunning("러닝" + i, courses.get(rnd.nextInt(2)),
                    member1, startedAt, RunningMode.SOLO));
        }
        for (int i = 51; i < 100; i++) {
            long startedAt = Math.abs(rnd.nextLong() % 1_000_000L);
            runnings.add(createRunning("러닝" + i, courses.get(rnd.nextInt(2)),
                    member2, startedAt, RunningMode.SOLO));
        }
        runningRepository.saveAll(runnings);

        List<Running> c1Runnings = runnings.stream()
                .filter(running -> running.getCourse().getId().equals(c1.getId()))
                .toList();

        // when
        long c1RunCounts = runningRepository.countTotalRunningsCount(c1.getId());

        // then
        assertThat(c1RunCounts).isEqualTo(c1Runnings.size());
    }

    @DisplayName("코스 ID를 기반으로 사용자가 해당 코스에서 뛴 러닝 기록을 최신순으로 조회한다.")
    @Test
    void findRunningsByCourseId() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course c = createCourse(member, "이복둥 러닝 코스");
        c.setIsPublic(true);
        courseRepository.save(c);

        List<Running> soloRunnings = new ArrayList<>();
        long ts = 0L;
        for (int i = 0; i < 30; i++) {
            ts += 1000L;
            soloRunnings.add(createRunning("S" + i, c, member, ts, RunningMode.SOLO));
        }
        runningRepository.saveAll(soloRunnings);

        List<Running> ghostRunnings = new ArrayList<>();
        ts = 0L;
        for (int i = 0; i < 30; i++) {
            ts += 500L;
            ghostRunnings.add(createRunning("G" + i, c, member, ts, RunningMode.GHOST));
        }
        runningRepository.saveAll(ghostRunnings);

        List<Running> runnings = new ArrayList<>();
        runnings.addAll(soloRunnings);
        runnings.addAll(ghostRunnings);
        runnings.sort(
                Comparator.comparing(Running::getStartedAt).reversed()
                        .thenComparing(Comparator.comparing(Running::getId).reversed())
        );

        // when
        List<Running> savedRunnings = runningRepository.findRunningsByCourseIdAndMemberId(c.getId(), member.getId());

        // then
        for (int i = 0; i < savedRunnings.size(); i++) {
            Assertions.assertThat(savedRunnings.get(i).getId()).isEqualTo(runnings.get(i).getId());
        }

    }

    @DisplayName("코스 ID를 기반으로 사용자가 해당 코스에서 뛴 러닝 기록을 최신순으로 조회할 때, 아무것도 없다면 리스트는 비어있다.")
    @Test
    void findNullRunningsByCourseId() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course c = createCourse(member, "이복둥 러닝 코스");
        c.setIsPublic(true);
        courseRepository.save(c);

        // when
        List<Running> savedRunnings = runningRepository.findRunningsByCourseIdAndMemberId(c.getId(), member.getId());

        // then
        Assertions.assertThat(savedRunnings).isEmpty();
    }

    @DisplayName("코스 ID를 기반으로 해당 코스의 상위 랭킹 러닝 기록을 조회한다. 이때 같은 사용자의 중복된 러닝 기록은 허용되지 않는다.")
    @Test
    void findTopRankingRunsByCourseIdWithDistinctMember() {
        // given
        // 러닝 4개, 러너는 3명
        Member member1 = createMember("이복둥");
        Member member2 = createMember("이복둥 주인");
        Member member3 = createMember("이복둥 주인의 주인");
        memberRepository.saveAll(List.of(member1, member2, member3));

        Course c = createCourse(member1, "이복둥 러닝 코스");
        courseRepository.save(c);

        List<Running> ghostRuns = new ArrayList<>();
        ghostRuns.add(createRunning("러닝 1", c, member1, 180L, 1000L)); // 1등
        ghostRuns.add(createRunning("러닝 2", c, member2, 210L, 2000L)); // 2등
        ghostRuns.add(createRunning("러닝 3", c, member2, 240L, 3000L));
        ghostRuns.add(createRunning("러닝 4", c, member3, 300L, 4000L)); // 3등
        runningRepository.saveAll(ghostRuns);

        // when
        List<CourseRunDto> top3 = runningRepository.findTopRankingRunsByCourseIdWithDistinctMember(c.getId(), 3);
        List<CourseRunDto> top5 = runningRepository.findTopRankingRunsByCourseIdWithDistinctMember(c.getId(), 5);

        // then
        assertThat(top3).hasSize(3);
        assertThat(top3).extracting("runningName")
                .containsExactly("러닝 1", "러닝 2", "러닝 4");
        assertThat(top5).isEqualTo(top3); // top5로 해도 3건만 나옴
    }

    @DisplayName("여러 코스에 대해 조회한 상위 랭커의 모든 필드가 DTO에 정확히 매핑된다.")
    @Test
    void findTopRankingRunsByCourseIdWithDistinctMember_testFieldMapping() {
        // given
        Member testRunner = memberRepository.save(Member.of("해적왕", "https://profile.url/test.jpg"));
        Course testCourse = courseRepository.save(createCourse(testRunner, "내 어린 시절 우연히"));
        Running testRunning = runningRepository.save(createDetailedRunning(
                testRunner, testCourse, 1234L, "잊지 못할 한마디", 5.5
        ));

        // when
        List<CourseRunDto> topRunners = runningRepository.findTopRankingRunsByCourseIdWithDistinctMember(testCourse.getId(), 1);

        // then
        assertThat(topRunners).hasSize(1);
        CourseRunDto result = topRunners.get(0);
        // 필드 검증
        assertThat(result.courseId()).isEqualTo(testCourse.getId());
        assertThat(result.runnerUuid()).isEqualTo(testRunner.getUuid());
        assertThat(result.runnerProfileUrl()).isEqualTo(testRunner.getProfilePictureUrl());
        assertThat(result.runnerNickname()).isEqualTo(testRunner.getNickname());
        assertThat(result.runningId()).isEqualTo(testRunning.getId());
        assertThat(result.duration()).isEqualTo(testRunning.getRunningRecord().getDuration());
        assertThat(result.runningName()).isEqualTo(testRunning.getRunningName());
        assertThat(result.averagePace()).isEqualTo(testRunning.getRunningRecord().getAveragePace());
        assertThat(result.cadence()).isEqualTo(testRunning.getRunningRecord().getCadence());
        assertThat(result.bpm()).isEqualTo(testRunning.getRunningRecord().getBpm());
        assertThat(result.isPublic()).isTrue();
        assertThat(result.startedAt()).isEqualTo(testRunning.getStartedAt());
    }

    @DisplayName("코스 ID 리스트를 입력 받아 각 코스별 상위 랭킹 러닝 기록을 조회한다. 같은 사용자의 중복된 러닝 기록은 허용되지 않는다.")
    @Test
    void findTopRankingRunsByCourseIdsWithDistinctMember() {
        // given
        // 러너 1이 A코스의 1, 2등 기록 보유
        Member runner1 = createMember("에이스");
        Member runner2 = createMember("루피");
        memberRepository.saveAll(List.of(runner1, runner2));

        Course courseA = createCourse(runner1);
        courseRepository.save(courseA);

        runningRepository.save(createRunning(runner1, courseA, 1000L));  // 에이스 1등 기록
        runningRepository.save(createRunning(runner1, courseA, 1100L));  // 에이스 2등 기록 (무시되어야 함)
        runningRepository.save(createRunning(runner2, courseA, 1200L)); // 일반러너 3등 기록

        // when
        List<CourseRunDto> topRunners = runningRepository
                .findTopRankingRunsByCourseIdsWithDistinctMember(List.of(courseA.getId()), 2);

        // then
        // 러너 1의 기록과 러너 2의 기록이 랭킹에 포함 (러너 1이 두 번 포함되지 않음)
        assertThat(topRunners).hasSize(2);
        assertThat(topRunners).extracting("runnerNickname", "duration")
                .containsExactly(
                        tuple("에이스", 1000L),
                        tuple("루피", 1200L)
                );
    }

    Running createRunning(Member member, Course course, Long duration) {
        return Running.of("테스트 러닝", RunningMode.SOLO, null,
                createRunningRecord(duration), System.currentTimeMillis(),
                true, false, "시계열 URL", "시계열 URL", "시계열 URL", member, course);
    }

    @DisplayName("여러 코스의 상위 러닝 기록 조회 시, 한 멤버의 다른 코스 기록이 더 좋아도 현재 코스 랭킹에서 누락되지 않아야 한다.")
    @Test
    void findTopRankingRuns_shouldNotOmitRunner() {
        // given
        Member runner1 = createMember("에이스");
        Member runner2 = createMember("루피");
        memberRepository.saveAll(List.of(runner1, runner2));

        Course courseA = createCourse(runner1, "A코스");
        Course courseB = createCourse(runner1, "B코스");
        courseRepository.saveAll(List.of(courseA, courseB));

        // 러너 1은 A코스 기록(1000초)이 B코스 기록(1200초)보다 좋다. 하지만 B코스 내에서는 러너1의 1200초가 1등 기록이다.
        runningRepository.save(createRunning(runner1, courseA, 1000L));
        runningRepository.save(createRunning(runner1, courseB, 1200L));
        runningRepository.save(createRunning(runner2, courseB, 1300L));

        // when
        List<CourseRunDto> allTopRunners = runningRepository.findTopRankingRunsByCourseIdsWithDistinctMember(List.of(courseA.getId(), courseB.getId()), 4);

        // then
        // 러너 1은 A 코스 1등, B 코스도 1등이여야 한다
        // 결과를 코스 ID별로 그룹핑하여 검증
        Map<Long, List<CourseRunDto>> resultsByCourse = allTopRunners.stream()
                .collect(Collectors.groupingBy(CourseRunDto::courseId));

        // A코스 결과 검증
        List<CourseRunDto> courseARanking = resultsByCourse.get(courseA.getId());
        assertThat(courseARanking).hasSize(1);
        assertThat(courseARanking.get(0).runnerNickname()).isEqualTo("에이스");

        // B코스 결과 검증
        List<CourseRunDto> courseBRanking = resultsByCourse.get(courseB.getId());
        assertThat(courseBRanking).hasSize(2);
        assertThat(courseBRanking).extracting("runnerNickname", "duration")
                .containsExactly(
                        tuple("에이스", 1200L),
                        tuple("루피", 1300L)
                );
    }

    private Running createDetailedRunning(Member member, Course course, long duration, String runningName, double avgPace) {
        RunningRecord record = RunningRecord.of(5.0, 0.0, 0.0, 0.0, avgPace, 4.0, 6.0, duration, 300, 180, 150);
        return Running.of(runningName, RunningMode.SOLO, null, record, 1750729987181L,
                true, false, "url", "url", "url", member, course);
    }

    @DisplayName("여러 코스에 대해 조회한 상위 랭커의 모든 필드가 DTO에 정확히 매핑된다.")
    @Test
    void findTopRankingRuns_shouldMapAllFieldsCorrectly() {
        // given
        Member testRunner = memberRepository.save(Member.of("해적왕", "https://profile.url/test.jpg"));
        Course testCourse = courseRepository.save(createCourse(testRunner, "내 어린 시절 우연히"));
        Running testRunning = runningRepository.save(createDetailedRunning(
                testRunner, testCourse, 1234L, "잊지 못할 한마디", 5.5
        ));

        // when
        List<CourseRunDto> topRunners = runningRepository.findTopRankingRunsByCourseIdsWithDistinctMember(List.of(testCourse.getId()), 1);

        // then
        assertThat(topRunners).hasSize(1);
        CourseRunDto result = topRunners.get(0);
        // 필드 검증
        assertThat(result.courseId()).isEqualTo(testCourse.getId());
        assertThat(result.runnerUuid()).isEqualTo(testRunner.getUuid());
        assertThat(result.runnerProfileUrl()).isEqualTo(testRunner.getProfilePictureUrl());
        assertThat(result.runnerNickname()).isEqualTo(testRunner.getNickname());
        assertThat(result.runningId()).isEqualTo(testRunning.getId());
        assertThat(result.duration()).isEqualTo(testRunning.getRunningRecord().getDuration());
        assertThat(result.runningName()).isEqualTo(testRunning.getRunningName());
        assertThat(result.averagePace()).isEqualTo(testRunning.getRunningRecord().getAveragePace());
        assertThat(result.cadence()).isEqualTo(testRunning.getRunningRecord().getCadence());
        assertThat(result.bpm()).isEqualTo(testRunning.getRunningRecord().getBpm());
        assertThat(result.isPublic()).isTrue();
        assertThat(result.startedAt()).isEqualTo(testRunning.getStartedAt());
    }

    @DisplayName("코스 ID를 기반으로 해당 코스에 공개 러닝 기록을 등록한 회원의 수를 조회한다.")
    @Test
    void countPublicRunnersInCourse() {
        // given
        Member member1 = createMember("이복둥");
        Member member2 = createMember("이복둥 주인");
        Member member3 = createMember("이복둥 주인의 주인");
        memberRepository.saveAll(List.of(member1, member2, member3));

        Course c = createCourse(member1, "이복둥 러닝 코스");
        courseRepository.save(c);

        List<Running> runs = new ArrayList<>();
        runs.add(createRunning(member1, c));
        runs.add(createRunning(member2, c));
        runs.add(createRunning(member2, c));
        runs.add(createRunning(member3, c));
        runs.add(createRunning(member3, c));
        runningRepository.saveAll(runs);

        // when
        long count = runningRepository.countPublicRunnersInCourse(c.getId());

        // then
        Assertions.assertThat(count).isEqualTo(3L);
    }

    @DisplayName("코스 ID로 러너 수 조회 시 코스가 존재하지 않으면 0을 반환한다.")
    @Test
    void countPublicRunnersInCourse_noRuns() {
        // when
        long count = runningRepository.countPublicRunnersInCourse(12345L);

        // then
        Assertions.assertThat(count).isEqualTo(0L);
    }

    @DisplayName("특정 연, 월에 저장되어 있는 러닝 카운트를 집계한다. 없는 날짜는 조회되지 않는다.")
    @Test
    void findDayRunInfosFilteredByDate() {
        // given
        Member member = memberRepository.save(createMember("이복둥"));
        Course course = courseRepository.save(createCourse(member, "한강 10K"));

        // 2025-10월에 다음 일자/횟수로 데이터 생성:
        // 1(1), 3(2), 4(1), 5(1), 10(1), 12(1), 20(2), 30(1)  → 총 10개
        List<Running> runs = new ArrayList<>();
        runs.add(createRunningAt(member, course, LocalDate.of(2025, 10, 1)));
        runs.add(createRunningAt(member, course, LocalDate.of(2025, 10, 3)));
        runs.add(createRunningAt(member, course, LocalDate.of(2025, 10, 3)));
        runs.add(createRunningAt(member, course, LocalDate.of(2025, 10, 4)));
        runs.add(createRunningAt(member, course, LocalDate.of(2025, 10, 5)));
        runs.add(createRunningAt(member, course, LocalDate.of(2025, 10, 10)));
        runs.add(createRunningAt(member, course, LocalDate.of(2025, 10, 12)));
        runs.add(createRunningAt(member, course, LocalDate.of(2025, 10, 20)));
        runs.add(createRunningAt(member, course, LocalDate.of(2025, 10, 20)));
        runs.add(createRunningAt(member, course, LocalDate.of(2025, 10, 30)));

        // 경계 검증용: 전월/익월 데이터 (집계 제외되어야 함)
        runs.add(createRunningAt(member, course, LocalDate.of(2025, 9, 30)));
        runs.add(createRunningAt(member, course, LocalDate.of(2025, 11, 1)));

        runningRepository.saveAll(runs);
        em.flush(); em.clear();

        // when
        List<DayRunInfo> dayRunInfos = runningRepository.findDayRunInfosFilteredByDate(2025, 10, member.getId());

        // then
        // 1) 행 수(집계된 일자 수)
        assertThat(dayRunInfos).hasSize(8);

        // 2) 연/월 일관성
        assertThat(dayRunInfos).extracting(DayRunInfo::getYear).containsOnly(2025);
        assertThat(dayRunInfos).extracting(DayRunInfo::getMonth).containsOnly(10);

        // 3) 기대 일자와 카운트 매칭
        Map<Integer, Integer> expected = new LinkedHashMap<>();
        expected.put(1, 1);
        expected.put(3, 2);
        expected.put(4, 1);
        expected.put(5, 1);
        expected.put(10, 1);
        expected.put(12, 1);
        expected.put(20, 2);
        expected.put(30, 1);

        // 일자 집합
        assertThat(dayRunInfos).extracting(DayRunInfo::getDay)
                .containsExactlyInAnyOrderElementsOf(expected.keySet());

        // 일자별 카운트
        Map<Integer, Integer> actual = dayRunInfos.stream()
                .collect(Collectors.toMap(DayRunInfo::getDay, DayRunInfo::getRunCount));
        expected.forEach((d, cnt) -> assertThat(actual).containsEntry(d, cnt));

        // 4) 총합(해당 월 개수)
        int total = dayRunInfos.stream().mapToInt(DayRunInfo::getRunCount).sum();
        assertThat(total).isEqualTo(10);

        // 5) (선택) day 오름차순 정렬 보장 시
        List<Integer> sortedDays = dayRunInfos.stream().map(DayRunInfo::getDay).toList();
        assertThat(sortedDays).isSorted();
    }

    private Running createRunningAt(Member m, Course c, LocalDate dateKst) {
        long ms = toEpochMsKst(dateKst);
        RunningRecord rr = RunningRecord.of(5.2, 30.0, 40.0, -20.0,
                6.1, 4.9, 6.9, 3423L, 302, 120, 56);
        return Running.of("테스트 러닝 제목", RunningMode.SOLO, null, rr,
                ms, true, false, "URL", "URL", "URL", m, c);
    }

    private long toEpochMsKst(LocalDate dateKst) {
        ZoneId KST = ZoneId.of("Asia/Seoul");
        return ZonedDateTime.of(dateKst, LocalTime.MIDNIGHT, KST)
                .toInstant().toEpochMilli();
    }

    @DisplayName("특정 연, 월에 저장되어 있는 러닝 카운트를 집계한다. 없는 날짜는 조회되지 않는다.")
    @Test
    void findDayRunInfosFilteredByDate_WithEmptyRunnings() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse(member);
        courseRepository.save(course);

        // when
        List<DayRunInfo> dayRunInfos = runningRepository.findDayRunInfosFilteredByDate(2025, 10, member.getId());

        // then
        assertThat(dayRunInfos).isEmpty();
      
    }
      
    @DisplayName("코스 ID 목록을 기반으로 각 코스에 공개 러닝 기록을 등록한 회원의 수를 조회한다.")
    @Test
    void findPublicRunnerCountsByCourseIds() {
        // given
        Member m1 = createMember("신짱구");
        Member m2 = createMember("신짱아");
        memberRepository.saveAll(List.of(m1, m2));
        // 코스 3개 생성
        List<Course> courses = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Course c = createCourse(m1, "코스" + i);
            courses.add(c);
        }
        courseRepository.saveAll(courses);
        List<Long> courseIds = courses.stream().map(Course::getId).toList();
        // 러닝 5개 생성 - 코스 1에 한 명, 코스 2에 두 명, 코스 3에는 한 명
        Running r1 = createRunning(m1, courses.get(0));
        Running r2 = createRunning(m1, courses.get(1));
        Running r3 = createRunning(m2, courses.get(1));
        Running r4 = createRunning(m2, courses.get(2));
        Running r5 = createRunning(m2, courses.get(2));
        runningRepository.saveAll(List.of(r1, r2, r3, r4, r5));

        // when
        List<Pair<Long, Long>> courseRunnerCounts = runningRepository.findPublicRunnerCountsByCourseIds(courseIds);

        // then
        assertThat(courseRunnerCounts).hasSize(3);
        assertThat(courseRunnerCounts).containsExactlyInAnyOrder(
                Pair.of(courses.get(0).getId(), 1L),
                Pair.of(courses.get(1).getId(), 2L),
                Pair.of(courses.get(2).getId(), 1L)
        );
    }

    @DisplayName("코스 ID와 멤버 ID를 기반으로 사용자가 해당 코스에서 뛴 최신 러닝 기록을 조회한다.")
    @Test
    void findLatestRunsByCourseIdAndMemberId() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course c = createCourse(member, "이복둥 러닝 코스");
        c.setIsPublic(true);
        courseRepository.save(c);

        List<Running> runnings = new ArrayList<>();
        long ts = 0L;
        for (int i = 0; i < 5; i++) {
            ts += 1000L;
            runnings.add(createRunning("러닝" + i, c, member, ts, RunningMode.SOLO));
        }
        runningRepository.saveAll(runnings);

        // when
        List<Running> latestRunningOpt = runningRepository.findLatestRunsByCourseIdAndMemberId(c.getId(), member.getUuid(), 2);

        // then
        assertThat(latestRunningOpt).hasSize(2);
        assertThat(latestRunningOpt.get(0).getStartedAt()).isEqualTo(5000L);
        assertThat(latestRunningOpt.get(1).getStartedAt()).isEqualTo(4000L);
    }

    @DisplayName("코스 ID와 멤버 UUID를 기반으로 사용자가 해당 코스에서 특정 시각 이전에 뛴 베스트 러닝 기록을 조회한다.")
    @Test
    void findBestRunByCourseIdAndMemberUuidBefore() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse(member, "이복둥 러닝 코스");
        course.setIsPublic(true);
        courseRepository.save(course);

        List<Running> runnings = new ArrayList<>();
        long ts = 0L;
        for (int i = 0; i < 5; i++) {
            ts += 1000L;
            runnings.add(createRunningWithDuration(course, member, ts, ts));
        }
        runningRepository.saveAll(runnings);

        // when
        Optional<Running> bestRunningOpt = runningRepository.findBestRunByCourseIdAndMemberUuidBefore(course.getId(), member.getUuid(), 4500L);

        // then
        assertThat(bestRunningOpt).isPresent();
        Running bestRunning = bestRunningOpt.get();
        assertThat(bestRunning.getStartedAt()).isEqualTo(1000L);
    }
  
}
