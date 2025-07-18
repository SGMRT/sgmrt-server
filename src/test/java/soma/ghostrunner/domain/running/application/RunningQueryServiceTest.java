package soma.ghostrunner.domain.running.application;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.clients.aws.TelemetryClient;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.course.domain.StartPoint;
import soma.ghostrunner.domain.member.Member;
import soma.ghostrunner.domain.member.MemberRepository;
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;
import soma.ghostrunner.domain.running.application.dto.response.RunInfo;
import soma.ghostrunner.domain.running.dao.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.*;

class RunningQueryServiceTest extends IntegrationTestSupport {

    @Autowired
    private RunningQueryService runningQueryService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    RunningRepository runningRepository;

    @MockitoBean
    TelemetryClient telemetryClient;

    @DisplayName("러닝의 전체 시계열을 조회한다.")
    @Test
    void findRunningTelemetries() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse();
        courseRepository.save(course);

        Running running = createRunning("러닝", course, member, "러닝의 URL");
        runningRepository.save(running);

        List<String> downloadedStringTelemetries = List.of(
                "{\"timeStamp\":0,\"lat\":37.2,\"lng\":37.5,\"dist\":110.0,\"pace\":6.0,\"alt\":100,\"cadence\":120,\"bpm\":110,\"isRunning\":true}",
                "{\"timeStamp\":1,\"lat\":37.3,\"lng\":37.6,\"dist\":110.1,\"pace\":6.1,\"alt\":101,\"cadence\":121,\"bpm\":111,\"isRunning\":true}",
                "{\"timeStamp\":2,\"lat\":37.4,\"lng\":37.7,\"dist\":110.2,\"pace\":6.2,\"alt\":102,\"cadence\":122,\"bpm\":112,\"isRunning\":true}",
                "{\"timeStamp\":3,\"lat\":37.5,\"lng\":37.8,\"dist\":110.3,\"pace\":6.3,\"alt\":103,\"cadence\":123,\"bpm\":113,\"isRunning\":false}"
        );

        given(telemetryClient.downloadTelemetryFromUrl("러닝의 URL")).willReturn(downloadedStringTelemetries);

        // when
        List<TelemetryDto> telemetries = runningQueryService.findRunningTelemetries(running.getId(), member.getUuid());

        // then
        Assertions.assertThat(telemetries)
                .hasSize(4)
                .extracting("timeStamp", "lat", "lng", "dist", "pace", "alt", "cadence", "bpm", "isRunning")
                .containsExactly(
                        tuple(0L, 37.2, 37.5, 110.0, 6.0, 100, 120, 110, true),
                        tuple(  1L, 37.3, 37.6, 110.1, 6.1, 101, 121, 111, true),
                        tuple(2L, 37.4, 37.7, 110.2, 6.2, 102, 122, 112, true),
                        tuple(3L, 37.5, 37.8, 110.3, 6.3, 103, 123, 113, false)
                );
    }

    private Running createRunning(String runningName, Course course, Member member, String telemetryUrl) {
        return Running.of(runningName, RunningMode.SOLO, null, createRunningRecord(), 1750729987181L,
                true, false, telemetryUrl, member, course);
    }

    private Member createMember(String name) {
        return Member.of(name, "프로필 URL");
    }

    private Course createCourse() {
        return Course.of(createCourseProfile(), createStartPoint(), "[{'lat':37.123, 'lng':32.123}, {'lat':37.123, 'lng':32.123}, {'lat':37.123, 'lng':32.123}]");
    }

    private StartPoint createStartPoint() {
        return StartPoint.of(37.545354, 34.7878);
    }

    private CourseProfile createCourseProfile() {
        return CourseProfile.of(5.2, 40, -20);
    }

    private RunningRecord createRunningRecord() {
        return RunningRecord.of(5.2, 40, -20, 6.1, 3423.2, 302.2, 120L, 56, 100, 120);
    }

    @DisplayName("기본/기간별 보기 방식으로 러닝 기록을 조회할 때 러닝 시작순으로 정렬된다.")
    @Test
    void findRunningsSortedByStartedAt() {
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
            runnings.add(createRunning("러닝" + i, courses.get(random.nextInt(0, 3)),
                    member, random.nextLong(), RunningMode.SOLO));
        }
        for (int i = 100; i < 200; i++) {
            runnings.add(createRunning("러닝" + i, courses.get(random.nextInt(0, 3)),
                    member, random.nextLong(), RunningMode.GHOST));
        }
        runningRepository.saveAll(runnings);

        List<Running> sortedSoloRunnings = runnings.stream()
                .filter(running -> running.getRunningMode().equals(RunningMode.SOLO))
                .sorted(Comparator.comparing(Running::getStartedAt).reversed())
                .toList();

        // when
        List<RunInfo> runInfos = new ArrayList<>();
        runInfos.addAll(runningQueryService.findRunnings("SOLO", null, null, member.getUuid()));
        for (int i = 0; i < 4; i++) {
            RunInfo lastRunInfo = runInfos.get(runInfos.size() - 1);
            runInfos.addAll(runningQueryService.findRunnings("SOLO", lastRunInfo.getStartedAt(),
                    lastRunInfo.getRunningId(), member.getUuid()));
        }

        // then
        Assertions.assertThat(runInfos).hasSize(100);
        IntStream.range(0, runInfos.size()).forEach(idx -> {
            Assertions.assertThat(runInfos.get(idx).getRunningId()).isEqualTo(sortedSoloRunnings.get(idx).getId());
            Assertions.assertThat(runInfos.get(idx).getName()).isEqualTo(sortedSoloRunnings.get(idx).getRunningName());
            Assertions.assertThat(runInfos.get(idx).getStartedAt()).isEqualTo(sortedSoloRunnings.get(idx).getStartedAt());
        });
    }

    private Running createRunning(String runningName, Course course, Member member, Long startedAt, RunningMode runningMode) {
        return Running.of(runningName, runningMode, null, createRunningRecord(), startedAt,
                true, false, "시계열 URL", member, course);
    }

    @DisplayName("기본/기간별 보기 방식으로 러닝 기록을 조회할 때 시작시간이 같다면 ID를 기반으로 정렬된다.")
    @Test
    void findRunningsSortedByIdWhenSameStartedAt() {
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
        for (int i = 0; i < 20; i++) {
            runnings.add(createRunning("러닝" + i, courses.get(random.nextInt(0, 3)),
                    member, 20L - (long) i, RunningMode.SOLO));
        }
        runningRepository.saveAll(runnings);

        Running lastRunning = runnings.get(runnings.size() - 1);
        runningRepository.save(createRunning(lastRunning.getRunningName(), lastRunning.getCourse(),
                member, lastRunning.getStartedAt(), RunningMode.SOLO));

        // when
        List<RunInfo> firstRunInfos = runningQueryService.findRunnings("SOLO", null, null, member.getUuid());
        RunInfo lastOfFirstRunInfo = firstRunInfos.get(firstRunInfos.size() - 1);
        List<RunInfo> secondRunInfos = runningQueryService.findRunnings("SOLO", lastOfFirstRunInfo.getStartedAt(),
                lastOfFirstRunInfo.getRunningId(), member.getUuid());

        // then
        for (RunInfo firstRunInfo : firstRunInfos) {
            System.out.println(firstRunInfo.getRunningId());
        }
        System.out.println(secondRunInfos.get(0).getRunningId());
        Assertions.assertThat(secondRunInfos).hasSize(1);
        Assertions.assertThat(lastOfFirstRunInfo.getStartedAt()).isEqualTo(secondRunInfos.get(0).getStartedAt());
        Assertions.assertThat(lastOfFirstRunInfo.getRunningId()).isEqualTo(secondRunInfos.get(0).getRunningId()+1L);
    }

    @DisplayName("기본/기간별 보기 방식으로 러닝 기록을 조회할 때 비공개 코스라면 코스 정보는 Null로 조회된다.")
    @Test
    void findRunningsWithNullCourse() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course privateCourse = createCourse();
        privateCourse.setName("비공개 코스 러닝");
        Course publicCourse = createCourse();
        publicCourse.setIsPublic(true);
        publicCourse.setName("공개 코스 러닝");
        List<Course> courses = List.of(privateCourse, publicCourse);
        courseRepository.saveAll(courses);

        Running publicRunning = createRunning("공개 코스 러닝", publicCourse, member, 20L, RunningMode.SOLO);
        Running privateRunning = createRunning("비공개 코스 러닝", privateCourse, member, 20L, RunningMode.SOLO);
        runningRepository.saveAll(List.of(publicRunning, privateRunning));

        // when
        List<RunInfo> runInfos = runningQueryService.findRunnings("SOLO", null, null, member.getUuid());

        // then
        RunInfo privateRunInfo = runInfos.get(0);
        Assertions.assertThat(privateRunInfo.getRunningId()).isEqualTo(privateRunning.getId());
        Assertions.assertThat(privateRunInfo.getCourseInfo().getName()).isNull();

        RunInfo publicRunInfo = runInfos.get(1);
        Assertions.assertThat(publicRunInfo.getRunningId()).isEqualTo(publicRunning.getId());
        Assertions.assertThat(publicRunInfo.getCourseInfo().getName()).isEqualTo("공개 코스 러닝");
    }

    @DisplayName("코스별 러닝 기록을 조회한다.")
    @Test
    void findRunningsGroupedByCourse() {
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
        runInfos.addAll(runningQueryService.findRunningsFilteredByCourse("SOLO", null, null, member.getUuid()));
        for (int i = 0; i < 4; i++) {
            RunInfo lastRunInfo = runInfos.get(runInfos.size() - 1);
            runInfos.addAll(runningQueryService.findRunningsFilteredByCourse("SOLO",
                    lastRunInfo.getCourseInfo().getName(), lastRunInfo.getRunningId(), member.getUuid()));
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

    private Course createCourse(String courseName) {
        Course course = Course.of(createCourseProfile(), createStartPoint(),
                "[{'lat':37.123, 'lng':32.123}, {'lat':37.123, 'lng':32.123}, {'lat':37.123, 'lng':32.123}]");
        course.setName(courseName);
        return course;
    }

    @DisplayName("갤러리 보기 방식으로 러닝 기록을 조회한다.")
    @Test
    void findRunningsForGalleryView() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Random random = new Random();
        List<String> randomCourseNames = List.of("한강 코스", "반포 코스", "태화강 코스", "공덕역 코스", "이대역 코스");
        List<Course> courses = new ArrayList<>();
        randomCourseNames.forEach(name -> {
            Course newCourse = createCourse(name);
            courses.add(newCourse);
        });
        courseRepository.saveAll(courses);

        List<Running> runnings = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            runnings.add(createRunning("러닝" + i, courses.get(random.nextInt(0, 3)),
                    member, random.nextLong(), RunningMode.SOLO));
        }
        for (int i = 100; i < 200; i++) {
            runnings.add(createRunning("러닝" + i, courses.get(random.nextInt(0, 3)),
                    member, random.nextLong(), RunningMode.GHOST));
        }
        runningRepository.saveAll(runnings);

        List<Running> sortedSoloRunnings = runnings.stream()
                .filter(running -> running.getRunningMode().equals(RunningMode.SOLO))
                .sorted(Comparator.comparing(Running::getStartedAt).reversed())
                .toList();

        // when
        List<RunInfo> runInfos = new ArrayList<>();
        runInfos.addAll(runningQueryService.findRunningsForGalleryView("SOLO", null, null, member.getUuid()));
        for (int i = 0; i < 4; i++) {
            RunInfo lastRunInfo = runInfos.get(runInfos.size() - 1);
            runInfos.addAll(runningQueryService.findRunningsForGalleryView("SOLO", lastRunInfo.getStartedAt(),
                    lastRunInfo.getRunningId(), member.getUuid()));
        }

        // then
        Assertions.assertThat(runInfos).hasSize(40);
        IntStream.range(0, runInfos.size()).forEach(idx -> {
            Assertions.assertThat(runInfos.get(idx).getRunningId()).isEqualTo(sortedSoloRunnings.get(idx).getId());
            Assertions.assertThat(runInfos.get(idx).getName()).isEqualTo(sortedSoloRunnings.get(idx).getRunningName());
            Assertions.assertThat(runInfos.get(idx).getStartedAt()).isEqualTo(sortedSoloRunnings.get(idx).getStartedAt());
        });
    }

}
