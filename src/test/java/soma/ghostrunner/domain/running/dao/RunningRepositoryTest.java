package soma.ghostrunner.domain.running.dao;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Coordinate;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.dao.MemberRepository;
import soma.ghostrunner.domain.running.application.dto.response.GhostRunDetailInfo;
import soma.ghostrunner.domain.running.application.dto.response.MemberAndRunRecordInfo;
import soma.ghostrunner.domain.running.application.dto.response.RunInfo;
import soma.ghostrunner.domain.running.application.dto.response.SoloRunDetailInfo;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;

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
                "URL", "URL");
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
        Member member = createMember("이복둥", UUID.randomUUID().toString());
        Member fakeMember = createMember("페이크 이복둥", UUID.randomUUID().toString());
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

    private Member createMember(String name, String externalAuthUuid) {
        return Member.of(name, "프로필 URL");
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

    @DisplayName("혼자 뛴 러닝에 대한 상세 정보를 조회한다. 공개한 코스가 있다면 코스 정보를 함께 응답한다.")
    @Test
    void findSoloRunInfoById() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createPublicCourse(member, "테스트 코스");
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

    private Course createPublicCourse(Member testMember, String courseName) {
        CourseProfile testCourseProfile = createCourseProfile();
        Coordinate testCoordinate = createStartPoint();
        Course course = Course.of(testMember, testCourseProfile.getDistance(),
                testCourseProfile.getElevationAverage(), testCourseProfile.getElevationGain(), testCourseProfile.getElevationLoss(),
                testCoordinate.getLatitude(), testCoordinate.getLongitude(),
                "URL", "URL");
        course.setName(courseName);
        course.setIsPublic(true);
        return course;
    }

    @DisplayName("기존 코스를 기반으로 혼자 뛴 러닝에 대한 상세 정보를 조회한다. 비공개 코스라면 코스 정보는 Null을 응답한다.")
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
        assertThat(soloRunDetailInfo.getCourseInfo()).isNull();
    }

    private Course createUnPublicCourse(Member testMember) {
        CourseProfile testCourseProfile = createCourseProfile();
        Coordinate testCoordinate = createStartPoint();
        Course course = Course.of(testMember, testCourseProfile.getDistance(),
                testCourseProfile.getElevationAverage(), testCourseProfile.getElevationGain(), testCourseProfile.getElevationLoss(),
                testCoordinate.getLatitude(), testCoordinate.getLongitude(),
                "URL", "URL");
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

        Course course = createPublicCourse(member, "테스트 코스");
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

        Course course = createPublicCourse(member, "테스트 코스");
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

        Course course = createPublicCourse(member, "테스트 코스");
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

        Course course = createPublicCourse(member, "테스트 코스");
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

        Course course = createPublicCourse(member, "테스트 코스");
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

        Course course = createPublicCourse(member, "테스트 코스");
        courseRepository.save(course);

        Running running1 = createRunning(member, course, "러닝 제목1");
        Running running2 = createRunning(member, course, "러닝 제목2");
        Running running3 = createRunning(member, course, "러닝 제목3");
        Running running4 = createRunning(member, course, "러닝 제목4");
        runningRepository.saveAll(List.of(running1, running2, running3, running4));

        // when
        runningRepository.deleteAllByIdIn(List.of(running1.getId(), running2.getId(), running3.getId(), running4.getId()));

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

    @DisplayName("시간, 러닝 ID를 기준으로 커서 페이징(ASC)으로 러닝을 조회한다.")
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

        // 재현성을 위한 고정 시드 + 양수 epoch (0~999_999)
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

        // 기대값: ASC 정렬(동률 시 id ASC)
        Comparator<Running> asc = Comparator.comparing(Running::getStartedAt)
                .thenComparing(Running::getId);

        List<Running> expectedSolo = runnings.stream()
                .filter(r -> r.getRunningMode() == RunningMode.SOLO)
                .sorted(asc)
                .toList();

        List<Running> expectedGhost = runnings.stream()
                .filter(r -> r.getRunningMode() == RunningMode.GHOST)
                .sorted(asc)
                .toList();

        // 기간: 우리가 생성한 범위를 정확히 커버
        long startEpoch = 0L;
        long endEpoch   = 1_000_000L;

        // when: 첫 페이지(커서 없음)
        List<RunInfo> page1Solo = runningRepository.findRunInfosFilteredByDate(
                RunningMode.SOLO, null, null, startEpoch, endEpoch, member.getId());

        // 페이지 크기(= 구현의 limit 결과)를 관측값으로 사용
        int page1Size = page1Solo.size();
        assertThat(page1Size).isEqualTo(21);

        // 커서: 첫 페이지의 마지막 요소
        RunInfo cursor = page1Solo.get(page1Size - 1);

        // when: 둘째 페이지(커서 after)
        List<RunInfo> page2Solo = runningRepository.findRunInfosFilteredByDate(
                RunningMode.SOLO, cursor.getStartedAt(), cursor.getRunningId(),
                startEpoch, endEpoch, member.getId());

        // when: GHOST 첫 페이지
        List<RunInfo> page1Ghost = runningRepository.findRunInfosFilteredByDate(
                RunningMode.GHOST, null, null, startEpoch, endEpoch, member.getId());

        // then 1) SOLO 1페이지 값 검증 (정확히 기대 리스트의 앞부분)
        for (int i = 0; i < page1Solo.size(); i++) {
            RunInfo a = page1Solo.get(i);
            Running e = expectedSolo.get(i);
            assertThat(a.getRunningId()).isEqualTo(e.getId());
            assertThat(a.getName()).isEqualTo(e.getRunningName());
            assertThat(a.getStartedAt()).isEqualTo(e.getStartedAt());
            assertThat(a.getGhostRunningId()).isNull();
        }

        // then 2) SOLO 2페이지: 바로 이어지는 인덱스부터(중복/누락 없음)
        for (int i = 0; i < page2Solo.size(); i++) {
            RunInfo a = page2Solo.get(i);
            Running e = expectedSolo.get(page1Size + i);
            assertThat(a.getRunningId()).isEqualTo(e.getId());
            assertThat(a.getName()).isEqualTo(e.getRunningName());
            assertThat(a.getStartedAt()).isEqualTo(e.getStartedAt());
            assertThat(a.getGhostRunningId()).isNull();
        }

        // then 3) 페이지 간 중복 없음
        var ids1 = page1Solo.stream().map(RunInfo::getRunningId).toList();
        var ids2 = page2Solo.stream().map(RunInfo::getRunningId).toList();
        assertThat(Collections.disjoint(ids1, ids2)).isTrue();

        // then 4) 커서 경계(ASC: 엄격히 이후) 확인
        if (!page2Solo.isEmpty()) {
            RunInfo firstOfPage2 = page2Solo.get(0);
            boolean strictlyAfter =
                    (firstOfPage2.getStartedAt() > cursor.getStartedAt()) ||
                            (Objects.equals(firstOfPage2.getStartedAt(), cursor.getStartedAt()) &&
                                    firstOfPage2.getRunningId() > cursor.getRunningId());
            assertThat(strictlyAfter).isTrue();
        }

        // then 5) 각 결과 자체가 startedAt ASC, id ASC로 정렬되어 있는지
        assertSortedByStartedAtThenIdAsc(page1Solo);
        assertSortedByStartedAtThenIdAsc(page2Solo);
        assertSortedByStartedAtThenIdAsc(page1Ghost);

        // then 6) GHOST 1페이지 값 검증
        for (int i = 0; i < page1Ghost.size(); i++) {
            RunInfo a = page1Ghost.get(i);
            Running e = expectedGhost.get(i);
            assertThat(a.getRunningId()).isEqualTo(e.getId());
            assertThat(a.getName()).isEqualTo(e.getRunningName());
            assertThat(a.getStartedAt()).isEqualTo(e.getStartedAt());
            assertThat(a.getGhostRunningId()).isEqualTo(e.getGhostRunningId());
        }
    }

    private static void assertSortedByStartedAtThenIdAsc(List<RunInfo> list) {
        for (int i = 1; i < list.size(); i++) {
            RunInfo prev = list.get(i - 1);
            RunInfo curr = list.get(i);
            boolean ok = (curr.getStartedAt() > prev.getStartedAt()) ||
                    (Objects.equals(curr.getStartedAt(), prev.getStartedAt())
                            && curr.getRunningId() > prev.getRunningId());
            assertThat(ok)
                    .as("must be sorted by startedAt ASC, then id ASC at index " + i)
                    .isTrue();
        }
    }

    @DisplayName("기본/기간별 보기 방식으로 러닝 기록을 조회할 때 시작 시간이 같다면 ID를 기반으로 정렬된다.")
    @Test
    void sortByIdWhenStartTimesAreEqual() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse(member);
        courseRepository.save(course);

        // startedAt: 1000, 2000, 2000, 2000, 3000 (동일 startedAt(2000) 3건)
        Running r1 = createRunning("A", course, member, 1000L, RunningMode.SOLO);
        Running r2 = createRunning("B", course, member, 2000L, RunningMode.SOLO);
        Running r3 = createRunning("C", course, member, 2000L, RunningMode.SOLO);
        Running r4 = createRunning("D", course, member, 2000L, RunningMode.SOLO);
        Running r5 = createRunning("E", course, member, 3000L, RunningMode.SOLO);

        runningRepository.saveAll(List.of(r1, r2, r3, r4, r5));

        long startEpoch = 0L;
        long endEpoch   = 10_000L;

        // when: 커서 없음(첫 페이지)
        List<RunInfo> result = runningRepository.findRunInfosFilteredByDate(
                RunningMode.SOLO, null, null, startEpoch, endEpoch, member.getId()
        );

        // then: 전체가 startedAt ASC, id ASC
        assertSortedByStartedAtThenIdAsc(result);

        // 동률(2000) 집합만 골라서 id가 오름차순인지 확인
        List<RunInfo> at2000 = result.stream()
                .filter(r -> Objects.equals(r.getStartedAt(), 2000L))
                .toList();

        assertThat(at2000).hasSize(3);
        for (int i = 1; i < at2000.size(); i++) {
            Long prev = at2000.get(i - 1).getRunningId();
            Long curr = at2000.get(i).getRunningId();
            assertThat(curr).isGreaterThan(prev); // ID 기반 정렬 보장
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

        runningRepository.deleteAllByIdIn(List.of(r2.getId()));

        long startEpoch = 0L;
        long endEpoch   = 10_000L;

        // when
        List<RunInfo> result = runningRepository.findRunInfosFilteredByDate(
                RunningMode.SOLO, null, null, startEpoch, endEpoch, member.getId()
        );

        // then
        List<Long> ids = result.stream().map(RunInfo::getRunningId).toList();
        assertThat(ids).contains(r1.getId(), r3.getId());
        assertThat(ids).doesNotContain(r2.getId());
        assertSortedByStartedAtThenIdAsc(result);
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
                RunningMode.SOLO, null, null, startEpoch, endEpoch, member.getId()
        );

        // then: startedAt == 2000 인 것들만 전부 포함(동률 여러 건도 포함)
        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(r -> Objects.equals(r.getStartedAt(), 2000L));
        // 동률 집합 내에서도 id ASC
        assertSortedByStartedAtThenIdAsc(result);
        // 결과 아이디 집합이 정확히 r2, r3 로만 구성되었는지(순서는 ASC)
        List<Long> gotIds = result.stream().map(RunInfo::getRunningId).toList();
        assertThat(gotIds).containsExactlyElementsOf(
                List.of(Math.min(r2.getId(), r3.getId()), Math.max(r2.getId(), r3.getId()))
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
                RunningMode.SOLO, null, null, startEpoch, endEpoch, member.getId());
        for (int i = 0; i < 4; i++) {
            RunInfo cursor = runInfos.get(runInfos.size() - 1);
            List<RunInfo> nextRunInfos = runningRepository.findRunInfosFilteredByDate(
                    RunningMode.SOLO, cursor.getStartedAt(), cursor.getRunningId(),
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

    private RunningRecord createRunningRecord() {
        return RunningRecord.of(5.2, 30.0, 40.0, -20.0,
                6.1, 3423.2, 302.2, 120L, 56, 100, 120);
    }

    @DisplayName("코스명, 러닝ID(ASC) 커서로 기간 내 SOLO 러닝을 페이지네이션한다 — 정렬·연속성·중복없음")
    @Test
    void findRunInfosFilteredByCoursesByCursorIds_basicPaging() {
        // given
        long START_MS = 0L;
        long END_MS   = 1_000_000L;

        Member member = createMember("이복둥");
        memberRepository.save(member);

        List<String> names = List.of("공덕역 코스", "반포 코스", "이대역 코스", "태화강 코스", "한강 코스");
        List<Course> courses = names.stream()
                .map(n -> { Course c = createPublicCourse(member, n); c.setIsPublic(true); return c; })
                .toList();
        courseRepository.saveAll(courses);

        // 재현성 있는 데이터: startedAt은 [0, END_MS) 범위, 코스는 랜덤 배정
        Random rnd = new Random(42);
        List<Running> all = new ArrayList<>();

        // SOLO 100건
        for (int i = 0; i < 100; i++) {
            long ts = Math.abs(rnd.nextLong() % END_MS);
            all.add(createRunning("러닝" + i, courses.get(rnd.nextInt(courses.size())),
                    member, ts, RunningMode.SOLO));
        }
        // GHOST 100건(필터 검증용)
        for (int i = 100; i < 200; i++) {
            long ts = Math.abs(rnd.nextLong() % END_MS);
            all.add(createRunning("러닝" + i, courses.get(rnd.nextInt(courses.size())),
                    member, ts, RunningMode.GHOST));
        }
        runningRepository.saveAll(all);

        // 기대 목록(필터: SOLO) — 코스명 ASC, id ASC
        Comparator<Running> courseAscIdAsc = Comparator
                .comparing((Running r) -> r.getCourse().getName())
                .thenComparing(Running::getId);

        List<Running> expectedSolo = all.stream()
                .filter(r -> r.getRunningMode() == RunningMode.SOLO)
                .sorted(courseAscIdAsc)
                .toList();

        // when: 커서 없이 1페이지
        List<RunInfo> page = runningRepository.findRunInfosFilteredByCourses(
                RunningMode.SOLO, null, null, START_MS, END_MS, member.getId());

        // 페이지 크기는 관측값 사용(구현의 LIMIT에 의존하지 않도록)
        int pageSize = page.size();
        assertThat(pageSize).isGreaterThan(0);

        // 커서로 누적 수집(여러 페이지)
        List<RunInfo> collected = new ArrayList<>(page);
        while (true) {
            RunInfo cursor = collected.get(collected.size() - 1);
            List<RunInfo> next = runningRepository.findRunInfosFilteredByCourses(
                    RunningMode.SOLO, cursor.getCourseInfo().getName(), cursor.getRunningId(),
                    START_MS, END_MS, member.getId());
            if (next.isEmpty()) break;

            // 커서 경계: strict after (ASC)
            RunInfo first = next.get(0);
            boolean strictlyAfter =
                    first.getCourseInfo().getName().compareTo(cursor.getCourseInfo().getName()) > 0
                            || (Objects.equals(first.getCourseInfo().getName(), cursor.getCourseInfo().getName())
                            && first.getRunningId() > cursor.getRunningId());
            assertThat(strictlyAfter).isTrue();

            // 페이지 간 중복 없음
            Set<Long> prevIds = collected.stream().map(RunInfo::getRunningId).collect(Collectors.toSet());
            Set<Long> nextIds = next.stream().map(RunInfo::getRunningId).collect(Collectors.toSet());
            assertThat(Collections.disjoint(prevIds, nextIds)).isTrue();

            collected.addAll(next);

            // 방어: 과도 루프 방지
            if (collected.size() > expectedSolo.size()) {
                throw new AssertionError("Collected more than expected; check cursor logic");
            }
        }

        // then: 전체 SOLO 수와 동일, 정렬/내용 일치
        assertThat(collected).hasSize(expectedSolo.size());
        assertSortedByCourseThenIdAsc(collected);

        for (int i = 0; i < collected.size(); i++) {
            RunInfo a = collected.get(i);
            Running e = expectedSolo.get(i);
            assertThat(a.getCourseInfo().getName()).isEqualTo(e.getCourse().getName());
            assertThat(a.getRunningId()).isEqualTo(e.getId());
            assertThat(a.getName()).isEqualTo(e.getRunningName());
            assertThat(a.getStartedAt()).isEqualTo(e.getStartedAt());
            assertThat(a.getGhostRunningId()).isNull(); // SOLO
        }
    }

    @DisplayName("동일 코스명 다건이 있는 경우에도 ID ASC로 안정 정렬되고 커서 경계가 정확하다")
    @Test
    void sameCourseNameMany_rows_areStableAndSeekIsStrict() {
        // given
        long START_MS = 0L;
        long END_MS   = 1_000_000L;

        Member member = createMember("동률테스트");
        memberRepository.save(member);

        Course c = createPublicCourse(member, "같은 이름 코스");
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
                RunningMode.SOLO, null, null, START_MS, END_MS, member.getId());
        assertThat(p1).isNotEmpty();

        // 커서로 다음 페이지 조회
        RunInfo cursor = p1.get(p1.size() - 1);
        List<RunInfo> p2 = runningRepository.findRunInfosFilteredByCourses(
                RunningMode.SOLO, cursor.getCourseInfo().getName(), cursor.getRunningId(),
                START_MS, END_MS, member.getId());

        // then
        assertSortedByCourseThenIdAsc(p1);
        assertSortedByCourseThenIdAsc(p2);

        if (!p2.isEmpty()) {
            RunInfo first = p2.get(0);
            boolean strictlyAfter =
                    first.getCourseInfo().getName().compareTo(cursor.getCourseInfo().getName()) > 0
                            || (Objects.equals(first.getCourseInfo().getName(), cursor.getCourseInfo().getName())
                            && first.getRunningId() > cursor.getRunningId());
            assertThat(strictlyAfter).isTrue();
        }

        // 중복 없음
        Set<Long> ids1 = p1.stream().map(RunInfo::getRunningId).collect(Collectors.toSet());
        Set<Long> ids2 = p2.stream().map(RunInfo::getRunningId).collect(Collectors.toSet());
        assertThat(Collections.disjoint(ids1, ids2)).isTrue();
    }

    @DisplayName("기간 필터: start==end(포함)일 때 해당 시각의 레코드만 반환한다 (between inclusive)")
    @Test
    void range_inclusive_when_start_equals_end() {
        // given
        Member member = createMember("기간검증");
        memberRepository.save(member);

        Course c1 = createPublicCourse(member, "A");
        Course c2 = createPublicCourse(member, "B");
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
                RunningMode.SOLO, null, null, target, target, member.getId());

        // then: target 시각만 포함, 정렬은 (코스명, id)
        assertThat(result).isNotEmpty();
        assertThat(result).allMatch(r -> Objects.equals(r.getStartedAt(), target));
        assertSortedByCourseThenIdAsc(result);

        List<Long> got = result.stream().map(RunInfo::getRunningId).toList();
        List<Long> expect = List.of(Math.min(in1.getId(), in2.getId()), Math.max(in1.getId(), in2.getId()));
        assertThat(got).containsExactlyElementsOf(expect);
    }

    @DisplayName("기간 밖이면 빈 결과를 반환한다")
    @Test
    void outOfRange_returnsEmpty() {
        // given
        Member member = createMember("기간빈");
        memberRepository.save(member);

        Course c = createPublicCourse(member, "X");
        c.setIsPublic(true);
        courseRepository.save(c);

        runningRepository.save(createRunning("R", c, member, 1000L, RunningMode.SOLO));

        // when
        List<RunInfo> r = runningRepository.findRunInfosFilteredByCourses(
                RunningMode.SOLO, null, null, 2000L, 3000L, member.getId());

        // then
        assertThat(r).isEmpty();
    }

    @DisplayName("다른 멤버/다른 모드 데이터는 포함되지 않는다")
    @Test
    void filters_by_member_and_mode() {
        // given
        long START_MS = 0L;
        long END_MS   = 1_000_000L;

        Member me = createMember("me");
        Member other = createMember("other");
        memberRepository.saveAll(List.of(me, other));

        Course c1 = createPublicCourse(me, "A");  c1.setIsPublic(true);
        Course c2 = createPublicCourse(other, "A"); c2.setIsPublic(true);
        courseRepository.saveAll(List.of(c1, c2));

        Running s1 = createRunning("ME_SOLO", c1, me, 1000L, RunningMode.SOLO);
        Running g1 = createRunning("ME_GHOST", c1, me, 2000L, RunningMode.GHOST);
        Running s2 = createRunning("OTHER_SOLO", c2, other, 1500L, RunningMode.SOLO);
        runningRepository.saveAll(List.of(s1, g1, s2));

        // when
        List<RunInfo> result = runningRepository.findRunInfosFilteredByCourses(
                RunningMode.SOLO, null, null, START_MS, END_MS, me.getId());

        // then
        assertThat(result).extracting(RunInfo::getRunningId).containsExactly(s1.getId());
    }

    private void assertSortedByCourseThenIdAsc(List<RunInfo> list) {
        for (int i = 1; i < list.size(); i++) {
            RunInfo prev = list.get(i - 1);
            RunInfo curr = list.get(i);
            int nameCmp = curr.getCourseInfo().getName().compareTo(prev.getCourseInfo().getName());
            boolean ok = nameCmp > 0 || (nameCmp == 0 && curr.getRunningId() > prev.getRunningId());
            assertThat(ok)
                    .as("List must be sorted by course.name ASC, then id ASC at index " + i)
                    .isTrue();
        }
    }
  
}
