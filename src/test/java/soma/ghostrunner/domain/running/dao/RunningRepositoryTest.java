package soma.ghostrunner.domain.running.dao;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.course.domain.StartPoint;
import soma.ghostrunner.domain.member.Member;
import soma.ghostrunner.domain.member.MemberRepository;
import soma.ghostrunner.domain.running.application.dto.response.GhostRunDetailInfo;
import soma.ghostrunner.domain.running.application.dto.response.MemberAndRunRecordInfo;
import soma.ghostrunner.domain.running.application.dto.response.RunInfo;
import soma.ghostrunner.domain.running.application.dto.response.SoloRunDetailInfo;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;

import java.util.*;
import java.util.stream.IntStream;

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

        Course course = createCourse();
        courseRepository.save(course);

        Running running1 = createRunning(member, course);
        Running running2 = createRunning(member, course);
        Running running3 = createRunning(member, course);
        runningRepository.saveAll(List.of(running1, running2, running3));

        // when
        List<Long> runningIds = runningRepository.findIdsByCourseId(course.getId());

        // then
        Assertions.assertThat(runningIds)
                .hasSize(3)
                .containsExactly(running1.getId(), running2.getId(), running3.getId());
    }

    private Running createRunning(Member testMember, Course testCourse) {
        RunningRecord testRunningRecord = RunningRecord.of(5.2, 40, -20, 6.1, 4.9, 6.9, 3423L, 302, 120, 56);
        return Running.of("테스트 러닝 제목", RunningMode.SOLO, null, testRunningRecord, 1750729987181L,
                true, false, "URL", testMember, testCourse);
    }

    private Member createMember(String name) {
        return Member.of(name, "프로필 URL");
    }

    private Course createCourse() {
        CourseProfile testCourseProfile = createCourseProfile();
        StartPoint testStartPoint = createStartPoint();
        return Course.of(testCourseProfile, testStartPoint, createCoordinatesTelemetries());
    }

    private String createCoordinatesTelemetries() {
        return "[{'lat':37.123, 'lng':32.123}, {'lat':37.123, 'lng':32.123}, {'lat':37.123, 'lng':32.123}]";
    }

    private StartPoint createStartPoint() {
        return StartPoint.of(37.545354, 34.7878);
    }

    private CourseProfile createCourseProfile() {
        return CourseProfile.of(5.2, 40, -10);
    }

    @DisplayName("러닝 ID와 멤버 ID로 러닝을 조회한다.")
    @Test
    void findByIdAndMemberId() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse();
        courseRepository.save(course);

        Running running = createRunning(member, course);
        runningRepository.save(running);

        // when
        Running savedRunning = runningRepository.findByIdAndMemberId(running.getId(), member.getId()).get();

        // then
        Assertions.assertThat(savedRunning.getId()).isEqualTo(running.getId());
    }

    @DisplayName("러닝 ID에 해당하지 않은 멤버 ID로 조회하는 경우 Optional.Empty를 반환한다.")
    @Test
    void findByIdAndFakeMemberId() {
        // given
        Member member = createMember("이복둥", UUID.randomUUID().toString());
        Member fakeMember = createMember("페이크 이복둥", UUID.randomUUID().toString());
        memberRepository.saveAll(List.of(member, fakeMember));

        Course course = createCourse();
        courseRepository.save(course);

        Running running = createRunning(member, course);
        runningRepository.save(running);

        // when
        Optional<Running> savedRunning = runningRepository.findByIdAndMemberId(running.getId(), fakeMember.getId());

        // then
        Assertions.assertThat(savedRunning).isEmpty();
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

        Course course = createCourse();
        courseRepository.save(course);

        Running running = createRunning(member, course);
        runningRepository.save(running);

        // when
        String url = runningRepository.findById(running.getId()).get().getTelemetryUrl();

        // then
        Assertions.assertThat(url)
                .isEqualTo(running.getTelemetryUrl());
    }

    @DisplayName("기존 코스를 기반으로 혼자 뛴 러닝에 대한 상세 정보를 조회한다.")
    @Test
    void findSoloRunInfoById() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse("테스트 코스");
        courseRepository.save(course);

        Running running1 = createSoloRunning(member, course);
        Running running2 = createSoloRunning(member, course);
        runningRepository.saveAll(List.of(running1, running2));

        // when
        SoloRunDetailInfo soloRunDetailInfo = runningRepository.findSoloRunInfoById(running1.getId()).get();

        // then
        Assertions.assertThat(soloRunDetailInfo.getStartedAt()).isEqualTo(running1.getStartedAt());
        Assertions.assertThat(soloRunDetailInfo.getRunningName()).isEqualTo(running1.getRunningName());
        Assertions.assertThat(soloRunDetailInfo.getCourseInfo().getId()).isEqualTo(running1.getCourse().getId());
        Assertions.assertThat(soloRunDetailInfo.getCourseInfo().getName()).isEqualTo(running1.getCourse().getName());
        Assertions.assertThat(soloRunDetailInfo.getCourseInfo().getRunnersCount()).isEqualTo(2);
        Assertions.assertThat(soloRunDetailInfo.getTelemetryUrl()).isEqualTo(running1.getTelemetryUrl());
        Assertions.assertThat(soloRunDetailInfo.getRecordInfo().getDistance()).isEqualTo(running1.getRunningRecord().getDistance());
        Assertions.assertThat(soloRunDetailInfo.getRecordInfo().getDuration()).isEqualTo(running1.getRunningRecord().getDuration());
    }

    private Running createSoloRunning(Member testMember, Course testCourse) {
        RunningRecord testRunningRecord = RunningRecord.of(5.2, 40, -20, 6.1, 4.9, 6.9, 3423L, 302, 120, 56);
        return Running.of("테스트 러닝 제목", RunningMode.SOLO, null, testRunningRecord, 1750729987181L,
                true, false, "URL", testMember, testCourse);
    }

    private Course createCourse(String name) {
        CourseProfile testCourseProfile = createCourseProfile();
        StartPoint testStartPoint = createStartPoint();
        Course course = Course.of(testCourseProfile, testStartPoint, createCoordinatesTelemetries());
        course.setName(name);
        return course;
    }

    @DisplayName("기존 코스를 기반으로 혼자 뛴 러닝에 대한 상세 정보를 조회할 때 기록이 없다면 Empty가 조회된다.")
    @Test
    void findSoloRunInfoByInvalidRunningId() {
        // then
        Assertions.assertThat(runningRepository.findSoloRunInfoById(Long.MAX_VALUE)).isEmpty();
    }

    @DisplayName("기존 코스를 기반으로 고스트와 달린 러닝에 대한 코스와 나의 러닝 상세 정보를 조회한다. 고스트의 정보는 바로 조회되지 않는다.")
    @Test
    void findGhostRunInfoById() {
        // given
        Member ghostMember = createMember("고스트 이복둥", UUID.randomUUID().toString());
        Member member = createMember("이복둥", UUID.randomUUID().toString());
        memberRepository.saveAll(List.of(member, ghostMember));

        Course course = createCourse("테스트 코스");
        courseRepository.save(course);

        Running ghostRunning = createRunning(ghostMember, course);
        runningRepository.save(ghostRunning);
        Running running = createGhostRunning(member, course, ghostRunning.getGhostRunningId());
        runningRepository.save(running);

        // when
        GhostRunDetailInfo ghostRunDetailInfo = runningRepository.findGhostRunInfoById(running.getId()).get();

        // then
        Assertions.assertThat(ghostRunDetailInfo.getStartedAt()).isEqualTo(running.getStartedAt());
        Assertions.assertThat(ghostRunDetailInfo.getRunningName()).isEqualTo(running.getRunningName());

        Assertions.assertThat(ghostRunDetailInfo.getMyRunInfo().getNickname()).isEqualTo("이복둥");
        Assertions.assertThat(ghostRunDetailInfo.getMyRunInfo().getProfileUrl()).isEqualTo("프로필 URL");

        Assertions.assertThat(ghostRunDetailInfo.getMyRunInfo().getRecordInfo().getDistance()).isEqualTo(running.getRunningRecord().getDistance());
        Assertions.assertThat(ghostRunDetailInfo.getMyRunInfo().getRecordInfo().getDuration()).isEqualTo(running.getRunningRecord().getDuration());
        Assertions.assertThat(ghostRunDetailInfo.getTelemetryUrl()).isEqualTo(running.getTelemetryUrl());

        Assertions.assertThat(ghostRunDetailInfo.getCourseInfo().getName()).isEqualTo("테스트 코스");

        Assertions.assertThat(ghostRunDetailInfo.getGhostRunInfo()).isNull();
    }

    private Running createGhostRunning(Member testMember, Course testCourse, Long ghostRunningId) {
        RunningRecord testRunningRecord = RunningRecord.of(5.2, 40, -20, 6.1, 4.9, 6.9, 3423L, 302, 120, 56);
        return Running.of("테스트 러닝 제목", RunningMode.GHOST, ghostRunningId, testRunningRecord, 1750729987181L,
                true, false, "URL", testMember, testCourse);
    }

    @DisplayName("나의 닉네임, 프로필 URL, 러닝 상세정보를 조회한다.")
    @Test
    void findMemberAndRunRecordInfoById() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse("테스트 코스");
        courseRepository.save(course);

        Running running = createSoloRunning(member, course);
        runningRepository.save(running);

        // when
        MemberAndRunRecordInfo memberAndRunRecordInfo = runningRepository.findMemberAndRunRecordInfoById(running.getId()).get();

        // then
        Assertions.assertThat(memberAndRunRecordInfo.getNickname()).isEqualTo("이복둥");
        Assertions.assertThat(memberAndRunRecordInfo.getProfileUrl()).isEqualTo("프로필 URL");
        Assertions.assertThat(memberAndRunRecordInfo.getRecordInfo().getCadence()).isEqualTo(running.getRunningRecord().getCadence());
        Assertions.assertThat(memberAndRunRecordInfo.getRecordInfo().getDuration()).isEqualTo(running.getRunningRecord().getDuration());
        Assertions.assertThat(memberAndRunRecordInfo.getRecordInfo().getAveragePace()).isEqualTo(running.getRunningRecord().getAveragePace());
    }

    @DisplayName("러닝 시계열 url을 조회한다.")
    @Test
    void testGetRunningTelemetriesUrl() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse("테스트 코스");
        courseRepository.save(course);

        Running running = createSoloRunning(member, course);
        runningRepository.save(running);

        // when
        String url = runningRepository.findTelemetryUrlById(running.getId()).get();

        // then
        Assertions.assertThat(url).isEqualTo(running.getTelemetryUrl());
    }

    @DisplayName("코스에 대해 첫 번째 러닝 기록을 조회한다.")
    @Test
    void findFirstRunningByCourseId() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse("테스트 코스");
        courseRepository.save(course);

        Running running1 = createSoloRunning(member, course);
        Running running2 = createSoloRunning(member, course);
        Running running3 = createSoloRunning(member, course);
        runningRepository.saveAll(List.of(running1, running2, running3));

        // when
        Running firstRunning = runningRepository.findFirstRunningByCourseId(course.getId()).get();

        // then
        Assertions.assertThat(firstRunning.getId()).isEqualTo(running1.getId());
    }

    @DisplayName("러닝ID 리스트에 있는 러닝 기록을 조회한다.")
    @Test
    void findByIds() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse("테스트 코스");
        courseRepository.save(course);

        Running running1 = createRunning(member, course, "러닝 제목1");
        Running running2 = createRunning(member, course, "러닝 제목2");
        Running running3 = createRunning(member, course, "러닝 제목3");
        Running running4 = createRunning(member, course, "러닝 제목4");
        runningRepository.saveAll(List.of(running1, running2, running3, running4));

        // when
        List<Running> runnings = runningRepository.findByIds(List.of(running1.getId(), running2.getId(), running3.getId(), Long.MAX_VALUE));

        // then
        Assertions.assertThat(runnings)
                .hasSize(3)
                .extracting("id", "runningName")
                .containsExactlyInAnyOrder(
                        tuple(running1.getId(), "러닝 제목1"),
                        tuple(running2.getId(), "러닝 제목2"),
                        tuple(running3.getId(), "러닝 제목3")
                );
    }

    private Running createRunning(Member testMember, Course testCourse, String runningName) {
        RunningRecord testRunningRecord = RunningRecord.of(5.2, 40, -20, 6.1, 4.9, 6.9, 3423L, 302, 120, 56);
        return Running.of(runningName, RunningMode.SOLO, null, testRunningRecord, 1750729987181L,
                true, false, "URL", testMember, testCourse);
    }

    @DisplayName("러닝 기록을 삭제한다.")
    @Test
    void deleteRunnings() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse("테스트 코스");
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
        Assertions.assertThat(runnings)
                .hasSize(0);

        List<Running> deletedRunnings = runningRepository.findByIdsNoMatterDeleted(List.of(running1.getId(),
                running2.getId(), running3.getId(), running4.getId()));
        Assertions.assertThat(deletedRunnings)
                .hasSize(4)
                .extracting("id", "runningName")
                .containsExactlyInAnyOrder(
                        tuple(running1.getId(), "러닝 제목1"),
                        tuple(running2.getId(), "러닝 제목2"),
                        tuple(running3.getId(), "러닝 제목3"),
                        tuple(running4.getId(), "러닝 제목4")
                );
    }

    @DisplayName("시간, 러닝 ID를 기준으로 커서 페이징 방식을 활용해 조회한다.")
    @Test
    void findRunInfosByCursorIds() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course1 = createCourse();
        Course course2 = createCourse();
        Course course3 = createCourse();
        List<Course> courses = List.of(course1, course2, course3);
        courseRepository.saveAll(courses);

        Random random = new Random();
        List<Running> runnings = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            runnings.add(createRunning("러닝" + i, courses.get(random.nextInt(0, 3)), member, random.nextLong()));
        }
        runningRepository.saveAll(runnings);

        List<Running> sortedRunnings = runnings.stream()
                .sorted(Comparator.comparing(Running::getStartedAt).reversed())
                .toList();

        // when
        List<RunInfo> firstRunInfos = runningRepository.findRunInfosByCursorIds(RunningMode.SOLO, null, null, member.getId());
        RunInfo lastOfFirstRunInfo = firstRunInfos.get(firstRunInfos.size() - 1);
        List<RunInfo> secondRunInfos = runningRepository.findRunInfosByCursorIds(RunningMode.SOLO, lastOfFirstRunInfo.getStartedAt(),
                lastOfFirstRunInfo.getRunningId(), member.getId());

        // then
        IntStream.range(0, firstRunInfos.size()).forEach(idx -> {
            Assertions.assertThat(firstRunInfos.get(idx).getRunningId()).isEqualTo(sortedRunnings.get(idx).getId());
            Assertions.assertThat(firstRunInfos.get(idx).getName()).isEqualTo(sortedRunnings.get(idx).getRunningName());
            Assertions.assertThat(firstRunInfos.get(idx).getStartedAt()).isEqualTo(sortedRunnings.get(idx).getStartedAt());
        });
        IntStream.range(0, secondRunInfos.size()).forEach(idx -> {
            Assertions.assertThat(secondRunInfos.get(idx).getRunningId()).isEqualTo(sortedRunnings.get(20 + idx).getId());
            Assertions.assertThat(secondRunInfos.get(idx).getName()).isEqualTo(sortedRunnings.get(20 + idx).getRunningName());
            Assertions.assertThat(secondRunInfos.get(idx).getStartedAt()).isEqualTo(sortedRunnings.get(20 + idx).getStartedAt());
        });
    }

    private Running createRunning(String runningName, Course course, Member member, Long startedAt) {
        return Running.of(runningName, RunningMode.SOLO, null, createRunningRecord(), startedAt,
                true, false, "시계열 URL", member, course);
    }

    private RunningRecord createRunningRecord() {
        return RunningRecord.of(5.2, 40, -20, 6.1, 3423.2, 302.2, 120L, 56, 100, 120);
    }

    @DisplayName("코스명, 러닝 ID를 기준으로 커서 페이징 방식을 활용해 조회한다.")
    @Test
    void findRunInfosFilteredByCoursesByCursorIds() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        List<String> randomCourseNames = List.of("한강 코스", "반포 코스", "태화강 코스", "공덕역 코스", "이대역 코스");
        List<Course> courses = new ArrayList<>();
        randomCourseNames.forEach(name -> {
            Course newCourse = createCourse(name);
            newCourse.setIsPublic(true);
            courses.add(newCourse);
        });
        courseRepository.saveAll(courses);

        Random random = new Random();
        List<Running> runnings = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            runnings.add(createRunning("러닝" + i, courses.get(random.nextInt(0, 5)),
                    member, random.nextLong(), RunningMode.SOLO));
        }
        for (int i = 100; i < 200; i++) {
            runnings.add(createRunning("러닝" + i, courses.get(random.nextInt(0, 5)),
                    member, random.nextLong(), RunningMode.GHOST));
        }
        runningRepository.saveAll(runnings);

        List<Running> sortedSoloRunnings = runnings.stream()
                .filter(running -> running.getRunningMode().equals(RunningMode.SOLO))
                .sorted(Comparator.comparing((Running r) -> r.getCourse().getName())
                        .thenComparing(Running::getId, Comparator.reverseOrder()))
                .toList();

        // when
        List<RunInfo> runInfos = new ArrayList<>();
        runInfos.addAll(runningRepository.findRunInfosFilteredByCoursesByCursorIds(RunningMode.SOLO, null, null, member.getId()));
        for (int i = 0; i < 4; i++) {
            RunInfo lastRunInfo = runInfos.get(runInfos.size() - 1);
            runInfos.addAll(runningRepository.findRunInfosFilteredByCoursesByCursorIds(RunningMode.SOLO,
                    lastRunInfo.getCourseInfo().getName(), lastRunInfo.getRunningId(), member.getId()));
        }

        // then
        Assertions.assertThat(runInfos).hasSize(100);
        IntStream.range(0, runInfos.size()).forEach(idx -> {
            Assertions.assertThat(runInfos.get(idx).getCourseInfo().getName()).isEqualTo(sortedSoloRunnings.get(idx).getCourse().getName());
            Assertions.assertThat(runInfos.get(idx).getRunningId()).isEqualTo(sortedSoloRunnings.get(idx).getId());
            Assertions.assertThat(runInfos.get(idx).getName()).isEqualTo(sortedSoloRunnings.get(idx).getRunningName());
            Assertions.assertThat(runInfos.get(idx).getStartedAt()).isEqualTo(sortedSoloRunnings.get(idx).getStartedAt());
        });
    }

    private Running createRunning(String runningName, Course course, Member member, Long startedAt, RunningMode runningMode) {
        return Running.of(runningName, runningMode, null, createRunningRecord(), startedAt,
                true, false, "시계열 URL", member, course);
    }

    @DisplayName("갤러리 보기 방식을 위해 시간, 러닝 ID를 기준으로 커서 페이징 방식을 활용해 조회한다.")
    @Test
    void findRunInfosForGalleryViewByCursorIds() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course1 = createCourse();
        Course course2 = createCourse();
        Course course3 = createCourse();
        List<Course> courses = List.of(course1, course2, course3);
        courseRepository.saveAll(courses);

        Random random = new Random();
        List<Running> runnings = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            runnings.add(createRunning("러닝" + i, courses.get(random.nextInt(0, 3)), member, random.nextLong()));
        }
        runningRepository.saveAll(runnings);

        List<Running> sortedRunnings = runnings.stream()
                .sorted(Comparator.comparing(Running::getStartedAt).reversed())
                .toList();

        // when
        List<RunInfo> firstRunInfos = runningRepository.findRunInfosForGalleryViewByCursorIds(
                RunningMode.SOLO, null, null, member.getId());
        RunInfo lastOfFirstRunInfo = firstRunInfos.get(firstRunInfos.size() - 1);
        List<RunInfo> secondRunInfos = runningRepository.findRunInfosForGalleryViewByCursorIds(
                RunningMode.SOLO, lastOfFirstRunInfo.getStartedAt(), lastOfFirstRunInfo.getRunningId(), member.getId());

        // then
        IntStream.range(0, firstRunInfos.size()).forEach(idx -> {
            Assertions.assertThat(firstRunInfos.get(idx).getRunningId()).isEqualTo(sortedRunnings.get(idx).getId());
            Assertions.assertThat(firstRunInfos.get(idx).getName()).isEqualTo(sortedRunnings.get(idx).getRunningName());
            Assertions.assertThat(firstRunInfos.get(idx).getStartedAt()).isEqualTo(sortedRunnings.get(idx).getStartedAt());
        });
        IntStream.range(0, secondRunInfos.size()).forEach(idx -> {
            Assertions.assertThat(secondRunInfos.get(idx).getRunningId()).isEqualTo(sortedRunnings.get(8 + idx).getId());
            Assertions.assertThat(secondRunInfos.get(idx).getName()).isEqualTo(sortedRunnings.get(8 + idx).getRunningName());
            Assertions.assertThat(secondRunInfos.get(idx).getStartedAt()).isEqualTo(sortedRunnings.get(8 + idx).getStartedAt());
        });
    }
  
}
