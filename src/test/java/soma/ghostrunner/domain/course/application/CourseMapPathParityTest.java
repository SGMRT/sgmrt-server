package soma.ghostrunner.domain.course.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.DatabaseCleanserExtension;
import soma.ghostrunner.domain.course.dao.CourseReadModelRepository;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Coordinate;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseDataUrls;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.course.dto.CourseSearchFilterDto;
import soma.ghostrunner.domain.course.dto.RunnerProfile;
import soma.ghostrunner.domain.course.dto.response.CourseMapResponse;
import soma.ghostrunner.domain.course.enums.CourseSortType;
import soma.ghostrunner.domain.course.enums.CourseSource;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.RunningMode;
import soma.ghostrunner.domain.running.domain.RunningRecord;
import soma.ghostrunner.domain.running.infra.persistence.RunningRepository;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 조회 전환 파리티 검증 — 구경로(수동 캐시)와 신경로(리드모델+Spring Cache)가 같은 데이터에서
 * 같은 응답을 내는지 확인한다. 리드모델은 Writer 생성 경로(syncPublicity)로 채운다.
 *
 * 허용된 차이 (설계 확정): 신경로의 checkpointsUrl/createdAt 은 null.
 */
@ExtendWith(DatabaseCleanserExtension.class)
class CourseMapPathParityTest extends IntegrationTestSupport {

    @Autowired CourseFacade courseFacade;
    @Autowired CourseRepository courseRepository;
    @Autowired CourseReadModelRepository readModelRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired RunningRepository runningRepository;
    @Autowired CourseReadModelWriter readModelWriter;

    private static final double LAT = 37.5480;
    private static final double LNG = 127.0731;

    private Member courseOwner;

    @DisplayName("구경로와 신경로가 같은 데이터에서 동일한 지도 응답을 낸다 (checkpointsUrl/createdAt null 차이만 허용)")
    @Test
    void oldAndNewMapPaths_returnSameResponses() {
        // given : 공개 코스 2개 + 러너들의 공개 러닝 (리드모델은 아직 없음 — 백필 대상 상태)
        Member viewer = saveMember("조회자");
        Member rival = saveMember("경쟁자");
        Course courseA = savePublicCourse("한강 코스", LAT, LNG);
        Course courseB = savePublicCourse("올림픽 코스", LAT + 0.001, LNG + 0.001);
        saveRunning(viewer, courseA, 1800L);
        saveRunning(rival, courseA, 1500L);
        saveRunning(rival, courseB, 2000L);

        // 백필 — 리드모델 없는 공개 코스를 생성 + 전체 집계 (운영 백필은 ddl/migration-backfill.sql로 수행됨)
        readModelWriter.syncPublicity(courseA.getId(), true);
        readModelWriter.syncPublicity(courseB.getId(), true);
        assertThat(readModelRepository.findByCourseId(courseA.getId())).isPresent();
        assertThat(readModelRepository.findByCourseId(courseB.getId())).isPresent();

        // when : 캐시를 타지 않는 조건(테스트 간 간섭 방지 위해 sort 기본이지만 결과 검증엔 무관)
        CourseSearchFilterDto noFilters = CourseSearchFilterDto.of();
        List<CourseMapResponse> oldPath = courseFacade.findCoursesByPositionCached(
                LAT, LNG, 2000, CourseSortType.DISTANCE, noFilters, viewer.getUuid());
        List<CourseMapResponse> newPath = courseFacade.findCoursesByPosition(
                LAT, LNG, 2000, CourseSortType.DISTANCE, noFilters, null, viewer.getUuid());

        // then : 코스 2개 ≤ 응답 상한이라 랜덤 선별과 무관하게 두 경로 모두 전량 포함 — courseId 기준 비교
        Map<Long, CourseMapResponse> oldById = byId(oldPath);
        Map<Long, CourseMapResponse> newById = byId(newPath);
        assertThat(newById.keySet()).isEqualTo(oldById.keySet());

        for (Long courseId : oldById.keySet()) {
            CourseMapResponse expected = oldById.get(courseId);
            CourseMapResponse actual = newById.get(courseId);

            assertThat(actual.name()).isEqualTo(expected.name());
            assertThat(actual.ownerUuid()).isEqualTo(expected.ownerUuid());
            assertThat(actual.source()).isEqualTo(expected.source());
            assertThat(actual.startLat()).isEqualTo(expected.startLat());
            assertThat(actual.startLng()).isEqualTo(expected.startLng());
            assertThat(actual.routeUrl()).isEqualTo(expected.routeUrl());
            assertThat(actual.thumbnailUrl()).isEqualTo(expected.thumbnailUrl());
            assertThat(actual.distance()).isEqualTo(expected.distance());
            assertThat(actual.elevationAverage()).isEqualTo(expected.elevationAverage());
            assertThat(actual.elevationGain()).isEqualTo(expected.elevationGain());
            assertThat(actual.elevationLoss()).isEqualTo(expected.elevationLoss());
            assertThat(actual.runnersCount()).isEqualTo(expected.runnersCount());

            // TOP 러너: uuid·순서 동일. recordTimeSeconds 는 구경로가 항상 null 로 내리던 필드라
            // 신경로에서 채워지는 것은 허용된(개선) 차이다.
            assertThat(runnerUuids(actual.runners())).isEqualTo(runnerUuids(expected.runners()));
            assertThat(actual.runners()).allSatisfy(r -> assertThat(r.recordTimeSeconds()).isNotNull());

            // 내 고스트: 존재 여부·러닝 동일
            if (expected.myGhostInfo() == null) {
                assertThat(actual.myGhostInfo()).isNull();
            } else {
                assertThat(actual.myGhostInfo()).isNotNull();
                assertThat(actual.myGhostInfo().runningId()).isEqualTo(expected.myGhostInfo().runningId());
            }

            // 허용된 차이 — 신경로는 역정규화하지 않은 필드를 null 로 내린다
            assertThat(actual.checkpointsUrl()).isNull();
            assertThat(actual.createdAt()).isNull();
        }
    }

    // ========== helpers ==========

    private Map<Long, CourseMapResponse> byId(List<CourseMapResponse> responses) {
        return responses.stream().collect(Collectors.toMap(CourseMapResponse::id, Function.identity()));
    }

    private List<String> runnerUuids(List<RunnerProfile> runners) {
        return runners.stream().map(RunnerProfile::uuid).toList();
    }

    private Member saveMember(String nickname) {
        return memberRepository.save(Member.of(nickname, "https://example.com/" + nickname + ".jpg"));
    }

    private Course savePublicCourse(String name, double lat, double lng) {
        return courseRepository.save(Course.of(
                courseOwner(), name,
                CourseProfile.of(5.0, 10.0, 100.0, 50.0),
                Coordinate.of(lat, lng),
                CourseSource.USER, true,
                CourseDataUrls.of("https://example.com/route.json",
                        "https://example.com/checkpoints.json",
                        "https://example.com/thumb.jpg")));
    }

    /** 코스 주인은 파리티 검증의 관심사가 아니라 코스 생성에 필요한 값일 뿐이므로 첫 요청 시 한 번만 만든다. */
    private Member courseOwner() {
        if (courseOwner == null) {
            courseOwner = saveMember("코스주인");
        }
        return courseOwner;
    }

    private void saveRunning(Member member, Course course, Long durationSeconds) {
        RunningRecord record = RunningRecord.of(5.2, 30.0, 40.0, -20.0,
                6.1, 4.9, 6.9, durationSeconds, 302, 120, 56);
        runningRepository.save(Running.of("파리티 러닝", RunningMode.SOLO, null, record, 1750729987181L,
                true, false, "URL", "URL", "URL", member, course));
    }
}
