package soma.ghostrunner.domain.running.application;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.course.domain.StartPoint;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.dao.MemberRepository;
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;
import soma.ghostrunner.domain.running.application.dto.response.GhostRunDetailInfo;
import soma.ghostrunner.domain.running.application.dto.response.RunInfo;
import soma.ghostrunner.domain.running.application.dto.response.SoloRunDetailInfo;
import soma.ghostrunner.domain.running.dao.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;
import soma.ghostrunner.domain.running.exception.InvalidRunningException;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;

import java.util.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.*;

// TODO : 단위 테스트로 전환
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
    RunningTelemetryQueryService runningTelemetryQueryService;

    @DisplayName("혼자 뛴 러닝에 대한 상세 정보를 조회한다.")
    @Test
    void findSoloRunInfoById() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse(member, "테스트 코스");
        courseRepository.save(course);

        Running running1 = createSoloRunning(member, course);
        Running running2 = createSoloRunning(member, course);
        Running running3 = createSoloRunning(member, course);
        runningRepository.saveAll(List.of(running1, running2, running3));

        List<TelemetryDto> mockTelemetryDtos = createTelemetryDtos();
        given(runningTelemetryQueryService.findTotalTelemetries(running1.getId(), running1.getTelemetrySavedUrl()))
                .willReturn(mockTelemetryDtos);

        // when
        SoloRunDetailInfo soloRunDetailInfo = runningQueryService.findSoloRunInfo(running1.getId(), member.getUuid());

        // then
        Assertions.assertThat(soloRunDetailInfo.getStartedAt()).isEqualTo(running1.getStartedAt());
        Assertions.assertThat(soloRunDetailInfo.getRunningName()).isEqualTo(running1.getRunningName());
        Assertions.assertThat(soloRunDetailInfo.getTelemetryUrl()).isEqualTo(running1.getTelemetrySavedUrl());
        Assertions.assertThat(soloRunDetailInfo.getRecordInfo().getDistance()).isEqualTo(running1.getRunningRecord().getDistance());
        Assertions.assertThat(soloRunDetailInfo.getRecordInfo().getDuration()).isEqualTo(running1.getRunningRecord().getDuration());

        Assertions.assertThat(soloRunDetailInfo.getCourseInfo().getId()).isEqualTo(running1.getCourse().getId());
        Assertions.assertThat(soloRunDetailInfo.getCourseInfo().getName()).isEqualTo(running1.getCourse().getName());
        Assertions.assertThat(soloRunDetailInfo.getCourseInfo().getRunnersCount()).isEqualTo(3);

        Assertions.assertThat(soloRunDetailInfo.getTelemetries())
                .hasSize(4)
                .extracting("timeStamp", "lat", "lng", "dist", "pace", "alt", "cadence", "bpm", "isRunning")
                .containsExactly(
                        tuple(0L, 37.2, 37.5, 110.0, 6.0, 100, 120, 110, true),
                        tuple(1L, 37.3, 37.6, 110.1, 6.1, 101, 121, 111, true),
                        tuple(2L, 37.4, 37.7, 110.2, 6.2, 102, 122, 112, true),
                        tuple(3L, 37.5, 37.8, 110.3, 6.3, 103, 123, 113, false)
                );
    }

    private Member createMember(String name) {
        return Member.of(name, "프로필 URL");
    }

    private Course createCourse(Member testMember, String courseName) {
        Course course = Course.of(
                testMember, createCourseProfile(), createStartPoint(),
                "[{'lat':37.123, 'lng':32.123}, {'lat':37.123, 'lng':32.123}, {'lat':37.123, 'lng':32.123}]");
        course.setName(courseName);
        return course;
    }

    private Running createSoloRunning(Member testMember, Course testCourse) {
        RunningRecord testRunningRecord = createRunningRecord();
        return Running.of("테스트 러닝 제목", RunningMode.SOLO, null, testRunningRecord,
                1750729987181L, true, false, "URL", testMember, testCourse);
    }

    private RunningRecord createRunningRecord() {
        return RunningRecord.of(5.2, 40, -20, 6.1, 3423.2,
                302.2, 120L, 56, 100, 120);
    }

    private List<TelemetryDto> createTelemetryDtos() {
        return List.of(
                new TelemetryDto(0L, 37.2, 37.5, 110.0, 6.0, 100, 120, 110, true),
                new TelemetryDto(1L, 37.3, 37.6, 110.1, 6.1, 101, 121, 111, true),
                new TelemetryDto(2L, 37.4, 37.7, 110.2, 6.2, 102, 122, 112, true),
                new TelemetryDto(3L, 37.5, 37.8, 110.3, 6.3, 103, 123, 113, false)
        );
    }

    @DisplayName("혼자 뛴 러닝을 조회할 때 자신의 러닝 정보가 아니라면 NOT_FOUND 예외를 응답한다.")
    @Test
    void findSoloRunInfoByNoneOwnerId() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse(member, "테스트 코스");
        courseRepository.save(course);

        Running running = createSoloRunning(member, course);
        runningRepository.save(running);

        // when // then
        Assertions.assertThatThrownBy(
                        () -> runningQueryService.findSoloRunInfo(running.getId(), UUID.randomUUID().toString()))
                .isInstanceOf(RunningNotFoundException.class)
                .hasMessage("id " + running.getId() +" is not found");
    }

    @DisplayName("고스트와 뛴 러닝에 대한 상세 정보를 조회한다.")
    @Test
    void findGhostRunInfo() {
        // given
        Member member = createMember("이복둥");
        Member followingMember = createMember("고스트 이복둥");
        memberRepository.saveAll(List.of(member, followingMember));

        Course course = createCourse(member, "테스트 코스");
        courseRepository.save(course);

        RunningRecord runningRecord = RunningRecord.of(4.0, 40, -20, 6.1,
                6.1, 8.1, 120L, 50, 100, 120);
        Running running = Running.of("러닝 제목", RunningMode.SOLO, null, runningRecord,
                1750729987181L, true, false, "URL", member, course);
        runningRepository.save(running);

        RunningRecord ghostRunningRecord = RunningRecord.of(5.0, 50, -10, 7.1,
                7.1, 9.1, 130L, 60, 110, 130);
        Running followingRunning = Running.of("고스트 러닝 제목", RunningMode.GHOST, running.getId(), ghostRunningRecord,
                1750729987181L, true, false, "URL", followingMember, course);
        Running followingRunning2 = Running.of("고스트 러닝 제목2", RunningMode.GHOST, running.getId(), ghostRunningRecord,
                1750729987181L, true, false, "URL", followingMember, course);
        runningRepository.saveAll(List.of(followingRunning, followingRunning2));

        List<TelemetryDto> mockTelemetryDtos = createTelemetryDtos();
        given(runningTelemetryQueryService.findTotalTelemetries(followingRunning.getId(), followingRunning.getTelemetrySavedUrl()))
                .willReturn(mockTelemetryDtos);

        // when
        GhostRunDetailInfo ghostRunDetailInfo = runningQueryService.findGhostRunInfo(
                followingRunning.getId(), running.getId(), followingMember.getUuid());

        // then
        Assertions.assertThat(ghostRunDetailInfo.getStartedAt()).isEqualTo(followingRunning.getStartedAt());
        Assertions.assertThat(ghostRunDetailInfo.getRunningName()).isEqualTo(followingRunning.getRunningName());
        Assertions.assertThat(ghostRunDetailInfo.getTelemetryUrl()).isEqualTo(followingRunning.getTelemetrySavedUrl());

        Assertions.assertThat(ghostRunDetailInfo.getCourseInfo().getId()).isEqualTo(course.getId());
        Assertions.assertThat(ghostRunDetailInfo.getCourseInfo().getName()).isEqualTo(course.getName());
        Assertions.assertThat(ghostRunDetailInfo.getCourseInfo().getRunnersCount()).isEqualTo(3);

        Assertions.assertThat(ghostRunDetailInfo.getMyRunInfo().getNickname())
                .isEqualTo(followingMember.getNickname());
        Assertions.assertThat(ghostRunDetailInfo.getMyRunInfo().getProfileUrl())
                .isEqualTo(followingMember.getProfilePictureUrl());
        Assertions.assertThat(ghostRunDetailInfo.getMyRunInfo().getRecordInfo().getDistance())
                .isEqualTo(followingRunning.getRunningRecord().getDistance());
        Assertions.assertThat(ghostRunDetailInfo.getMyRunInfo().getRecordInfo().getDuration())
                .isEqualTo(followingRunning.getRunningRecord().getDuration());

        Assertions.assertThat(ghostRunDetailInfo.getGhostRunId()).isEqualTo(running.getId());

        Assertions.assertThat(ghostRunDetailInfo.getGhostRunInfo().getNickname())
                .isEqualTo(member.getNickname());
        Assertions.assertThat(ghostRunDetailInfo.getGhostRunInfo().getProfileUrl())
                .isEqualTo(member.getProfilePictureUrl());
        Assertions.assertThat(ghostRunDetailInfo.getGhostRunInfo().getRecordInfo().getCadence())
                .isEqualTo(running.getRunningRecord().getCadence());
        Assertions.assertThat(ghostRunDetailInfo.getGhostRunInfo().getRecordInfo().getDuration())
                .isEqualTo(running.getRunningRecord().getDuration());

        Assertions.assertThat(ghostRunDetailInfo.getComparisonInfo().getDistance()).isEqualTo(-0.8);
        Assertions.assertThat(ghostRunDetailInfo.getComparisonInfo().getDuration()).isEqualTo(10L);
        Assertions.assertThat(ghostRunDetailInfo.getComparisonInfo().getCadence()).isEqualTo(10);
        Assertions.assertThat(ghostRunDetailInfo.getComparisonInfo().getPace()).isEqualTo(1.0);

        Assertions.assertThat(ghostRunDetailInfo.getTelemetries())
                .hasSize(4)
                .extracting("timeStamp", "lat", "lng", "dist", "pace", "alt", "cadence", "bpm", "isRunning")
                .containsExactly(
                        tuple(0L, 37.2, 37.5, 110.0, 6.0, 100, 120, 110, true),
                        tuple(1L, 37.3, 37.6, 110.1, 6.1, 101, 121, 111, true),
                        tuple(2L, 37.4, 37.7, 110.2, 6.2, 102, 122, 112, true),
                        tuple(3L, 37.5, 37.8, 110.3, 6.3, 103, 123, 113, false)
                );
    }

    @DisplayName("고스트와 뛴 러닝에 대한 상세 정보를 조회할 때 입력한 고스트 러닝 ID가 실제 고스트 러닝 ID와 일치하지 않을 때 예외가 발생한다.")
    @Test
    void findGhostRunInfoWithInvalidGhostRunningId() {
        // given
        Member member = createMember("이복둥");
        Member followingMember = createMember("고스트 이복둥");
        memberRepository.saveAll(List.of(member, followingMember));

        Course course = createCourse(member, "테스트 코스");
        courseRepository.save(course);

        RunningRecord runningRecord = RunningRecord.of(4.0, 40, -20, 6.1,
                6.1, 8.1, 120L, 50, 100, 120);
        Running running = Running.of("러닝 제목", RunningMode.SOLO, null, runningRecord,
                1750729987181L, true, false, "URL", member, course);
        runningRepository.save(running);

        RunningRecord ghostRunningRecord = RunningRecord.of(5.0, 50, -10, 7.1,
                7.1, 9.1, 130L, 60, 110, 130);
        Running followingRunning = Running.of("고스트 러닝 제목", RunningMode.GHOST, running.getId(), ghostRunningRecord,
                1750729987181L, true, false, "URL", followingMember, course);
        runningRepository.save(followingRunning);

        List<TelemetryDto> mockTelemetryDtos = createTelemetryDtos();
        given(runningTelemetryQueryService.findTotalTelemetries(followingRunning.getId(), followingRunning.getTelemetrySavedUrl()))
                .willReturn(mockTelemetryDtos);

        // when // then
        Assertions.assertThatThrownBy(() ->
                        runningQueryService.findGhostRunInfo(
                                followingRunning.getId(), running.getId() + 1L, followingMember.getUuid()))
                .isInstanceOf(InvalidRunningException.class)
                .hasMessage("고스트의 러닝 ID가 Null이거나 실제로 뛴 고스트러닝 ID가 아닌 경우");
    }

     @DisplayName("고스트와 뛴 러닝을 조회할 때 자신의 러닝 정보가 아니라면 NOT_FOUND 예외를 응답한다.")
     @Test
     void findGhostRunInfoByNoneOwnerId() {
         // given
         Member member = createMember("이복둥");
         Member followingMember = createMember("고스트 이복둥");
         memberRepository.saveAll(List.of(member, followingMember));

         Course course = createCourse(member, "테스트 코스");
         courseRepository.save(course);

         RunningRecord runningRecord = RunningRecord.of(4.0, 40, -20, 6.1,
                 6.1, 8.1, 120L, 50, 100, 120);
         Running running = Running.of("러닝 제목", RunningMode.SOLO, null, runningRecord,
                 1750729987181L, true, false, "URL", member, course);
         runningRepository.save(running);

         RunningRecord ghostRunningRecord = RunningRecord.of(5.0, 50, -10, 7.1,
                 7.1, 9.1, 130L, 60, 110, 130);
         Running followingRunning = Running.of("고스트 러닝 제목", RunningMode.GHOST, running.getId(), ghostRunningRecord,
                 1750729987181L, true, false, "URL", followingMember, course);
         runningRepository.save(followingRunning);

         List<TelemetryDto> mockTelemetryDtos = createTelemetryDtos();
         given(runningTelemetryQueryService.findTotalTelemetries(followingRunning.getId(), followingRunning.getTelemetrySavedUrl()))
                 .willReturn(mockTelemetryDtos);

         // when // then
         Assertions.assertThatThrownBy(() ->
                         runningQueryService.findGhostRunInfo(
                                 followingRunning.getId(), running.getId(), UUID.randomUUID().toString()))
                 .isInstanceOf(RunningNotFoundException.class)
                 .hasMessage("id " + followingRunning.getId() + " is not found");
     }

    @DisplayName("러닝의 전체 시계열을 조회한다.")
    @Test
    void findRunningTelemetries() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse(member);
        courseRepository.save(course);

        Running running = createRunning("러닝", course, member, "러닝의 URL");
        runningRepository.save(running);

        List<TelemetryDto> mockTelemetryDtos = createTelemetryDtos();
        given(runningTelemetryQueryService.findTotalTelemetries(running.getId(), running.getTelemetrySavedUrl()))
                .willReturn(mockTelemetryDtos);

        // when
        List<TelemetryDto> telemetries = runningQueryService.findRunningTelemetries(running.getId(), member.getUuid());

        // then
        Assertions.assertThat(telemetries)
                .hasSize(4)
                .extracting("timeStamp", "lat", "lng", "dist", "pace", "alt", "cadence", "bpm", "isRunning")
                .containsExactly(
                        tuple(0L, 37.2, 37.5, 110.0, 6.0, 100, 120, 110, true),
                        tuple(1L, 37.3, 37.6, 110.1, 6.1, 101, 121, 111, true),
                        tuple(2L, 37.4, 37.7, 110.2, 6.2, 102, 122, 112, true),
                        tuple(3L, 37.5, 37.8, 110.3, 6.3, 103, 123, 113, false)
                );
    }

    private Running createRunning(String runningName, Course course, Member member, String telemetryUrl) {
        return Running.of(runningName, RunningMode.SOLO, null, createRunningRecord(), 1750729987181L,
                true, false, telemetryUrl, member, course);
    }

    private Course createCourse(Member testMember) {
        return Course.of(
                testMember, createCourseProfile(), createStartPoint(),
                "[{'lat':37.123, 'lng':32.123}, {'lat':37.123, 'lng':32.123}, {'lat':37.123, 'lng':32.123}]");
    }

    private StartPoint createStartPoint() {
        return StartPoint.of(37.545354, 34.7878);
    }

    private CourseProfile createCourseProfile() {
        return CourseProfile.of(5.2, 40, -20);
    }

    @DisplayName("존재하지 않는 러닝의 전체 시계열을 조회하면 NOT_FOUND를 응답한다.")
    @Test
    void findRunningTelemetriesWithNoneRunning() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse(member);
        courseRepository.save(course);

        Running running = createRunning("러닝", course, member, "러닝의 URL");
        runningRepository.save(running);

        // when // then
        Assertions.assertThatThrownBy(
                        () -> runningQueryService.findRunningTelemetries(running.getId(), UUID.randomUUID().toString()))
                .isInstanceOf(RunningNotFoundException.class)
                .hasMessage("id " + running.getId() +" is not found");
    }

    @DisplayName("러닝 ID로 러닝을 조회한다.")
    @Test
    void findRunningByRunningId() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course = createCourse(member);
        courseRepository.save(course);

        Running running = createRunning("러닝", course, member, "러닝의 URL");
        runningRepository.save(running);

        // when
        Running savedRunning = runningQueryService.findRunningByRunningId(running.getId());

        // then
        Assertions.assertThat(savedRunning.getRunningName()).isEqualTo(running.getRunningName());
        Assertions.assertThat(savedRunning.getGhostRunningId()).isEqualTo(running.getGhostRunningId());
        Assertions.assertThat(savedRunning.getTelemetrySavedUrl()).isEqualTo(running.getTelemetrySavedUrl());
    }

    @DisplayName("존재하지 않는 러닝 ID로 러닝을 조회하면 NOT_FOUND 예외가 발생한다.")
    @Test
    void findRunningByNoneRunningId() {
        // when // then
        Assertions.assertThatThrownBy(() -> runningQueryService.findRunningByRunningId(1L))
                .isInstanceOf(RunningNotFoundException.class)
                .hasMessage("id " + 1L +" is not found");
    }

    @DisplayName("코스 ID를 기반으로 코스에 대한 첫 번째 러닝 데이터를 조회한다.")
    @Test
    void findFirstRunning() {
        // given
        Member member1 = createMember("이복둥1");
        Member member2 = createMember("이복둥2");
        memberRepository.saveAll(List.of(member1, member2));

        Course course = createCourse(member1);
        courseRepository.save(course);

        Running running1 = createRunning("러닝1", course, member1, "러닝1의 URL");
        Running running2 = createRunning("러닝2", course, member1, "러닝2의 URL");
        Running running3 = createRunning("러닝3", course, member2, "러닝3의 URL");
        runningRepository.saveAll(List.of(running1, running2, running3));

        // when
        Running firstRunning = runningQueryService.findFirstRunning(course.getId());

        // then
        Assertions.assertThat(firstRunning.getRunningName()).isEqualTo(running1.getRunningName());
    }

    @DisplayName("코스 ID를 기반으로 코스에 대한 첫 번째 러닝 데이터를 조회할 때 존재하지 않는다면 NOT_FOUND를 응답한다.")
    @Test
    void findFirstRunningOnNoneCourse() {
        // given
        Member member1 = createMember("이복둥1");
        Member member2 = createMember("이복둥2");
        memberRepository.saveAll(List.of(member1, member2));

        Course course = createCourse(member1);
        courseRepository.save(course);

        // when // then
        Assertions.assertThatThrownBy(() -> runningQueryService.findFirstRunning(course.getId()))
                .isInstanceOf(RunningNotFoundException.class)
                .hasMessage("코스 ID : " + course.getId() + "에 대한 러닝 데이터가 없습니다.");
    }

    @DisplayName("기본/기간별 보기 방식으로 러닝 기록을 조회할 때 러닝 시작순으로 정렬된다.")
    @Test
    void findRunningsSortedByStartedAt() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course1 = createCourse(member);
        Course course2 = createCourse(member);
        Course course3 = createCourse(member);
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
        List<Running> sortedGhostRunnings = runnings.stream()
                .filter(running -> running.getRunningMode().equals(RunningMode.GHOST))
                .sorted(Comparator.comparing(Running::getStartedAt).reversed())
                .toList();

        // when
        List<RunInfo> soloRunInfos = new ArrayList<>();
        soloRunInfos.addAll(runningQueryService.findRunnings("SOLO", null, null, member.getUuid()));
        for (int i = 0; i < 4; i++) {
            RunInfo lastRunInfo = soloRunInfos.get(soloRunInfos.size() - 1);
            soloRunInfos.addAll(runningQueryService.findRunnings("SOLO", lastRunInfo.getStartedAt(),
                    lastRunInfo.getRunningId(), member.getUuid()));
        }
        List<RunInfo> ghostRunInfos = new ArrayList<>();
        ghostRunInfos.addAll(runningQueryService.findRunnings("GHOST", null, null, member.getUuid()));
        for (int i = 0; i < 4; i++) {
            RunInfo lastRunInfo = ghostRunInfos.get(ghostRunInfos.size() - 1);
            ghostRunInfos.addAll(runningQueryService.findRunnings("GHOST", lastRunInfo.getStartedAt(),
                    lastRunInfo.getRunningId(), member.getUuid()));
        }

        // then
        Assertions.assertThat(soloRunInfos).hasSize(100);
        IntStream.range(0, soloRunInfos.size()).forEach(idx -> {
            Assertions.assertThat(soloRunInfos.get(idx).getRunningId()).isEqualTo(sortedSoloRunnings.get(idx).getId());
            Assertions.assertThat(soloRunInfos.get(idx).getName()).isEqualTo(sortedSoloRunnings.get(idx).getRunningName());
            Assertions.assertThat(soloRunInfos.get(idx).getStartedAt()).isEqualTo(sortedSoloRunnings.get(idx).getStartedAt());
            Assertions.assertThat(soloRunInfos.get(idx).getGhostRunningId()).isNull();
        });

        Assertions.assertThat(ghostRunInfos).hasSize(100);
        IntStream.range(0, ghostRunInfos.size()).forEach(idx -> {
            Assertions.assertThat(ghostRunInfos.get(idx).getRunningId()).isEqualTo(sortedGhostRunnings.get(idx).getId());
            Assertions.assertThat(ghostRunInfos.get(idx).getName()).isEqualTo(sortedGhostRunnings.get(idx).getRunningName());
            Assertions.assertThat(ghostRunInfos.get(idx).getStartedAt()).isEqualTo(sortedGhostRunnings.get(idx).getStartedAt());
            Assertions.assertThat(ghostRunInfos.get(idx).getGhostRunningId()).isEqualTo(sortedGhostRunnings.get(idx).getGhostRunningId());
        });
    }

    private Running createRunning(String runningName, Course course, Member member, Long startedAt, RunningMode runningMode) {
        if (runningMode.equals(RunningMode.SOLO)) {
            return Running.of(runningName, runningMode, null, createRunningRecord(), startedAt,
                    true, false, "시계열 URL", member, course);
        } else {
            Random random = new Random();
            return Running.of(runningName, runningMode, random.nextLong(), createRunningRecord(), startedAt,
                    true, false, "시계열 URL", member, course);
        }
    }

    @DisplayName("기본/기간별 보기 방식으로 러닝 기록을 조회할 때 시작시간이 같다면 ID를 기반으로 정렬된다.")
    @Test
    void findRunningsSortedByIdWhenSameStartedAt() {
        // given
        Member member = createMember("이복둥");
        memberRepository.save(member);

        Course course1 = createCourse(member);
        Course course2 = createCourse(member);
        Course course3 = createCourse(member);
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

        Course privateCourse = createCourse(member);
        privateCourse.setName("비공개 코스 러닝");
        Course publicCourse = createCourse(member);
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
            Course newCourse = createCourse(member, name);
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
            Course newCourse = createCourse(member, name);
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
