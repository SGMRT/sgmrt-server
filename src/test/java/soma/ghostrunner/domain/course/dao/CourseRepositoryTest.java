package soma.ghostrunner.domain.course.dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.dto.CourseSearchFilterDto;
import soma.ghostrunner.domain.course.dto.response.CourseDetailedResponse;
import soma.ghostrunner.domain.course.enums.CourseSortType;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.running.infra.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CourseRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private RunningRepository runningRepository;

    private Member member1;
    private Member member2;

    @BeforeEach
    void setUp() {
        member1 = memberRepository.save(Member.of("user1", "profile1.url"));
        member2 = memberRepository.save(Member.of("user2", "profile2.url"));
    }

    @DisplayName("특정 멤버가 생성한 공개 코스 목록을 생성일 내림차순으로 페이징하여 조회한다")
    @Test
    void findPublicCoursesFetchJoinMembersByMemberUuidOrderByCreatedAtDesc() {
        // given
        Course course1 = createCourse(member1, "course1", true); // 최신
        Course course2 = createCourse(member2, "course2", true); // 다른 멤버
        Course course3 = createCourse(member1, "course3", false); // 비공개
        Course course4 = createCourse(member1, "course4", true); // 오래됨
        courseRepository.saveAll(List.of(course1, course2, course3, course4));

        Pageable pageable = PageRequest.of(0, 5);

        // when
        Page<Course> resultPage = courseRepository.findPublicCoursesFetchJoinMembersByMemberUuidOrderByCreatedAtDesc(member1.getUuid(), pageable);

        // then
        assertThat(resultPage.getContent()).hasSize(2);
        assertThat(resultPage.getContent()).extracting(Course::getName)
                .containsExactly("course4", "course1"); // 최신순 정렬 확인
        assertThat(resultPage.getContent()).allMatch(course -> course.getMember().getUuid().equals(member1.getUuid())); // 멤버 확인
        assertThat(resultPage.getContent()).allMatch(Course::getIsPublic); // 공개 여부 확인
    }


    @DisplayName("ID로 코스 상세 정보를 조회하면 공개된 러닝 기록 데이터를 집계한다")
    @Test
    void findCourseDetailedById_WithAggregation() {
        // given
        Course course = createCourse(member1, "Detailed Course", true);
        courseRepository.save(course);

        // 공개 러닝 2개, 비공개 러닝 1개 저장
        runningRepository.save(createRunningForCourse(course, 3600L, 6.0, true)); // Public
        runningRepository.save(createRunningForCourse(course, 1800L, 4.0, true)); // Public
        runningRepository.save(createRunningForCourse(course, 1000L, 3.0, false)); // Private (집계 제외 대상)

        // when
        CourseDetailedResponse response = courseRepository.findCourseDetailedById(course.getId()).orElseThrow();

        // then
        assertThat(response.id()).isEqualTo(course.getId());
        assertThat(response.name()).isEqualTo("Detailed Course");

        // 집계 데이터 확인
        // (3600 + 1800) / 2 = 2700
        assertThat(response.averageCompletionTime()).isEqualTo(2700);
        // (6.0 + 4.0) / 2 = 5.0
        assertThat(response.averageFinisherPace()).isEqualTo(5);
        // public 러닝 중 최소 페이스
        assertThat(response.lowestFinisherPace()).isEqualTo(4);
    }

    @DisplayName("거리 순으로 코스를 정렬하여 조회한다")
    @Test
    void findCoursesWithFilters_SortByDistance() {
        // given
        double centerLat = 37.5;
        double centerLng = 127.0;

        Course farCourse = createCourse(member1, "먼 코스", true, 38.0, 128.0);
        Course nearCourse = createCourse(member1, "근처 코스", true, 37.501, 127.001);
        Course midCourse = createCourse(member1, "중간 코스", true, 37.51, 127.01);
        courseRepository.saveAll(List.of(farCourse, nearCourse, midCourse));

        // when
        List<Course> results = courseRepository.findCoursesWithFilters(centerLat, centerLng, 37d, 38d, 127d, 129d,
                CourseSearchFilterDto.of(), CourseSortType.DISTANCE);

        // then
        assertThat(results).extracting(Course::getName)
                .containsExactly("근처 코스", "중간 코스", "먼 코스");
    }

    @DisplayName("인기순(러닝 기록 수)으로 코스를 정렬하여 조회한다")
    @Test
    void findCoursesWithFilters_SortByPopularity() {
        // given
        Course unpopular = createCourse(member1, "안 유명한 코스", true); // 러닝 0개
        Course popular = createCourse(member1, "유명한 코스", true); // 러닝 2개
        Course normal = createCourse(member1, "적당한 코스", true); // 러닝 1개
        courseRepository.saveAll(List.of(unpopular, popular, normal));
        runningRepository.save(createRunningForCourse(popular, 1000L, 5.0, true));
        runningRepository.save(createRunningForCourse(popular, 1000L, 5.0, true));
        runningRepository.save(createRunningForCourse(normal, 1000L, 5.0, true));

        // when
        List<Course> results = courseRepository.findCoursesWithFilters(0.0, 0.0, 37d, 39d, 127d, 129d,
                CourseSearchFilterDto.of(), CourseSortType.POPULARITY);

        // then
        assertThat(results).extracting(Course::getName)
                .containsExactly("유명한 코스", "적당한 코스", "안 유명한 코스");
    }

    @DisplayName("다양한 필터 조건을 조합하여 코스를 정확히 조회한다")
    @Test
    void findCoursesWithFilters_WithCombinedFilters() {
        // given
        Course course1 = createCourse(member1, "course1", true, 37.5, 127.0, 5000, 100); // 조건 부합
        Course course2 = createCourse(member2, "course2", true, 37.5, 127.0, 6000, 150); // 소유자 다름
        Course course3 = createCourse(member1, "course3", true, 37.5, 127.0, 2000, 100); // 거리 미달
        Course course4 = createCourse(member1, "course4", true, 37.5, 127.0, 5000, 50);  // 고도 미달
        courseRepository.saveAll(List.of(course1, course2, course3, course4));

        CourseSearchFilterDto filters = CourseSearchFilterDto.of(
                3000, // minDistanceM
                7000, // maxDistanceM
                80,   // minElevationM
                200,  // maxElevationM
                member1.getUuid() // ownerUuid
        );

        // when
        List<Course> results = courseRepository.findCoursesWithFilters(37.5, 127.0, 37.0, 38.0, 126.0, 128.0,
                filters, CourseSortType.DISTANCE);

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("course1");
    }


    // 헬퍼 메소드

    private Course createCourse(Member member, String name, boolean isPublic) {
        return createCourse(member, name, isPublic, 37.0, 129.0, 1000, 100);
    }

    private Course createCourse(Member member, String name, boolean isPublic, double lat, double lng) {
        return createCourse(member, name, isPublic, lat, lng, 1000, 100);
    }

    private Course createCourse(Member member, String name, boolean isPublic, double lat, double lng, double distance, double elevation) {
        Course course = Course.of(member, distance / 1000.0, 0.0, elevation, 0.0, lat, lng, "route.url", "checkpoint.url", "thumb.url");
        course.setName(name);
        course.setIsPublic(isPublic);
        return course;
    }

    private Running createRunningForCourse(Course course, long duration, double avgPace, boolean isPublic) {
        RunningRecord record = RunningRecord.of(180.0, 10.0, 10.0, 10.0,
                avgPace, avgPace, avgPace, duration,
                180, 180, 180);
        return Running.of("run", RunningMode.SOLO, null, record, System.currentTimeMillis(), isPublic, false,
                "raw.url", "interpolated.url", "screenshot.url", course.getMember(), course);
    }
}
