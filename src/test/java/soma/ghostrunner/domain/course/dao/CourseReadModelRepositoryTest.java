package soma.ghostrunner.domain.course.dao;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.domain.*;
import soma.ghostrunner.domain.course.dto.query.CourseMapDto;
import soma.ghostrunner.domain.course.enums.CourseSource;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * CourseReadModelRepository 통합 테스트
 * 
 * 주요 테스트:
 * - findCoursesForMap(): 지도 조회 쿼리
 */
@DisplayName("CourseReadModelRepository 통합 테스트")
class CourseReadModelRepositoryTest extends IntegrationTestSupport {
    
    @Autowired
    private CourseReadModelRepository readModelRepository;
    
    @Autowired
    private MemberRepository memberRepository;
    
    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("지도 조회: 공개 코스 + TOP4 러너 정보를 한 번에 조회")
    void findCoursesForMap() {
        // Given: 멤버 3명 생성
        Member member1 = createAndSaveMember("러너1");
        Member member2 = createAndSaveMember("러너2");
        Member member3 = createAndSaveMember("러너3");
        
        // Given: 코스 2개 생성 (1개는 범위 내, 1개는 범위 밖)
        Course course1 = createAndSaveCourse("한강 코스", member1, 37.5, 127.0);
        Course course2 = createAndSaveCourse("올림픽 공원", member2, 37.52, 127.1);
        Course course3 = createAndSaveCourse("제주도", member3, 33.5, 126.5); // 범위 밖
        
        // Given: 리드모델 생성 및 TOP4 설정
        CourseReadModel readModel1 = CourseReadModel.create(course1);
        readModel1.makePublic();
        readModel1.applyRun(member1.getId(), 1800); // 30분
        readModel1.applyRun(member2.getId(), 1500); // 25분
        readModel1.updateRunnersCount(2L);
        readModelRepository.save(readModel1);
        
        CourseReadModel readModel2 = CourseReadModel.create(course2);
        readModel2.makePublic();
        readModel2.applyRun(member3.getId(), 2100); // 35분
        readModel2.updateRunnersCount(1L);
        readModelRepository.save(readModel2);
        
        CourseReadModel readModel3 = CourseReadModel.create(course3);
        readModel3.makePublic();
        readModelRepository.save(readModel3);
        
        // When: 서울 지역 코스 조회
        List<CourseMapDto> results = readModelRepository.findCoursesForMap(
            37.0,   // minLat
            38.0,   // maxLat
            126.5,  // minLng
            128.0,  // maxLng
            100     // limit
        );
        
        // Then: 2개 코스 조회됨 (제주도 제외)
        assertThat(results).hasSize(2);
        
        // Then: 첫 번째 코스 검증
        CourseMapDto dto1 = results.stream()
            .filter(dto -> dto.courseId().equals(course1.getId()))
            .findFirst()
            .orElseThrow();
        
        assertThat(dto1.name()).isEqualTo("한강 코스");
        assertThat(dto1.ownerUuid()).isEqualTo(member1.getUuid());
        assertThat(dto1.runnersCount()).isEqualTo(2L);
        
        // TOP1: member2 (25분)
        assertThat(dto1.top1Uuid()).isEqualTo(member2.getUuid());
        assertThat(dto1.top1TimeSeconds()).isEqualTo(1500);
        assertThat(dto1.top1ProfileUrl()).isNotNull();
        
        // TOP2: member1 (30분)
        assertThat(dto1.top2Uuid()).isEqualTo(member1.getUuid());
        assertThat(dto1.top2TimeSeconds()).isEqualTo(1800);
        
        // TOP3, TOP4: null
        assertThat(dto1.top3Uuid()).isNull();
        assertThat(dto1.top4Uuid()).isNull();
        
        // Then: 두 번째 코스 검증
        CourseMapDto dto2 = results.stream()
            .filter(dto -> dto.courseId().equals(course2.getId()))
            .findFirst()
            .orElseThrow();
        
        assertThat(dto2.name()).isEqualTo("올림픽 공원");
        assertThat(dto2.top1Uuid()).isEqualTo(member3.getUuid());
        assertThat(dto2.top1TimeSeconds()).isEqualTo(2100);
    }
    
    @Test
    @DisplayName("비공개 코스는 조회되지 않음")
    void findCoursesForMap_excludesPrivateCourses() {
        // Given: 공개 코스 1개, 비공개 코스 1개
        Member member = createAndSaveMember("러너");
        Course publicCourse = createAndSaveCourse("공개", member, 37.5, 127.0);
        Course privateCourse = createAndSaveCourse("비공개", member, 37.51, 127.01);
        
        CourseReadModel publicReadModel = CourseReadModel.create(publicCourse);
        publicReadModel.makePublic();
        readModelRepository.save(publicReadModel);
        
        CourseReadModel privateReadModel = CourseReadModel.create(privateCourse);
        privateReadModel.makePrivate(); // 비공개
        readModelRepository.save(privateReadModel);
        
        // When
        List<CourseMapDto> results = readModelRepository.findCoursesForMap(
            37.0, 38.0, 126.5, 128.0, 100
        );
        
        // Then: 공개 코스만 조회
        assertThat(results).hasSize(1);
        assertThat(results.get(0).courseId()).isEqualTo(publicCourse.getId());
    }
    
    @Test
    @DisplayName("탈퇴한 멤버는 TOP4에서 제외")
    void findCoursesForMap_excludesDeletedMembers() {
        // Given: 멤버 2명 (1명은 탈퇴)
        Member activeMember = createAndSaveMember("활동중");
        Member deletedMember = createAndSaveMember("탈퇴");
        
        // 먼저 리드모델에 추가한 후 탈퇴 처리
        Course course = createAndSaveCourse("코스", activeMember, 37.5, 127.0);
        CourseReadModel readModel = CourseReadModel.create(course);
        readModel.makePublic();
        readModel.applyRun(deletedMember.getId(), 1500); // 탈퇴 예정 멤버가 1위
        readModel.applyRun(activeMember.getId(), 1800);  // 활동 멤버가 2위
        readModelRepository.save(readModel);
        
        // 이제 멤버 탈퇴 (소프트 삭제)
        memberRepository.delete(deletedMember);
        memberRepository.flush();
        
        // When
        List<CourseMapDto> results = readModelRepository.findCoursesForMap(
            37.0, 38.0, 126.5, 128.0, 100
        );
        
        // Then: 탈퇴 멤버는 조회 안 됨
        CourseMapDto dto = results.get(0);
        assertThat(dto.top1Uuid()).isNull(); // 탈퇴 멤버 제외
        assertThat(dto.top2Uuid()).isEqualTo(activeMember.getUuid()); // 활동 멤버만
    }
    
    /**
     * 설계 문서: docs/refactoring/course-read-model/04-detailed-design.md §1-2(필드 구성), §1-3(RankSlot), §1-7(테스트 계획)
     *
     * 검증 대상은 "매핑이 DB 왕복에서 깨지지 않는다" 한 가지다.
     * - @Embedded CourseProfile 4컬럼 + thumbnailUrl 역정규화 필드
     * - @Embedded RankSlot × 4 (@AttributeOverride 로 topN_member_id / topN_time_seconds)
     * - 빈 슬롯 = null 하이드레이션 (Hibernate 가 모든 필드 null 인 embeddable 을 null 로 읽는 동작)
     * - ownerUuid nullable (OFFICIAL / 더미 코스는 소유자 없음)
     */
    @Nested
    @DisplayName("영속성 매핑")
    class PersistenceMapping {

        @Test
        @DisplayName("전체 왕복: create(Course)로 만든 리드모델의 역정규화 필드가 저장/조회 후 그대로 복원된다")
        void roundTrip_restoresAllDenormalizedFields() {
            // given : 프로필 4개 값을 서로 다르게 둬서 컬럼이 뒤바뀌면 드러나게 한다
            Member owner = createAndSaveMember("주인");
            CourseProfile profile = CourseProfile.of(7.7, 11.1, 222.2, 33.3);
            Course course = courseRepository.save(Course.of(
                owner,
                "왕복 검증 코스",
                profile,
                Coordinate.of(37.1234, 127.5678),
                CourseSource.USER,
                true,
                CourseDataUrls.of(
                    "https://example.com/route-roundtrip.json",
                    "https://example.com/checkpoints-roundtrip.json",
                    "https://example.com/thumbnail-roundtrip.jpg"
                )
            ));

            CourseReadModel readModel = CourseReadModel.create(course);
            readModelRepository.save(readModel);

            // when : 영속성 컨텍스트를 비우고 DB에서 다시 읽는다
            flushAndClear();
            CourseReadModel found = readModelRepository.findByCourseId(course.getId()).orElseThrow();

            // then : @Embedded CourseProfile 4필드
            assertThat(found.getCourseProfile().getDistance()).isEqualTo(7.7);
            assertThat(found.getCourseProfile().getElevationAverage()).isEqualTo(11.1);
            assertThat(found.getCourseProfile().getElevationGain()).isEqualTo(222.2);
            assertThat(found.getCourseProfile().getElevationLoss()).isEqualTo(33.3);

            // then : 나머지 역정규화 필드
            assertThat(found.getThumbnailUrl()).isEqualTo("https://example.com/thumbnail-roundtrip.jpg");
            assertThat(found.getName()).isEqualTo("왕복 검증 코스");
            assertThat(found.getOwnerUuid()).isEqualTo(owner.getUuid());
            assertThat(found.getRouteUrl()).isEqualTo("https://example.com/route-roundtrip.json");
            assertThat(found.getStartLat()).isEqualTo(37.1234);
            assertThat(found.getStartLng()).isEqualTo(127.5678);
            assertThat(found.getSource()).isEqualTo(CourseSource.USER);
        }

        @Test
        @DisplayName("빈 슬롯 하이드레이션: TOP4가 모두 비어 있으면 null로 읽히고, 그 상태에서 applyRun이 정상 동작한다")
        void roundTrip_hydratesEmptySlotsAsNull() {
            // given : TOP4를 한 번도 채우지 않은 리드모델
            Member member = createAndSaveMember("러너");
            Course course = createAndSaveCourse("빈 슬롯 코스", member, 37.5, 127.0);
            readModelRepository.save(CourseReadModel.create(course));

            // when
            flushAndClear();
            CourseReadModel found = readModelRepository.findByCourseId(course.getId()).orElseThrow();

            // then : 모든 필드가 null 인 embeddable 은 슬롯 자체가 null 로 하이드레이션된다
            assertThat(found.getTop1()).isNull();
            assertThat(found.getTop2()).isNull();
            assertThat(found.getTop3()).isNull();
            assertThat(found.getTop4()).isNull();

            // then : 빈 슬롯 상태에서도 NPE 없이 TOP1이 채워진다
            assertThatCode(() -> found.applyRun(member.getId(), 1500)).doesNotThrowAnyException();
            assertThat(found.getTop1()).isEqualTo(new RankSlot(member.getId(), 1500));
            assertThat(found.getTop2()).isNull();
        }

        @Test
        @DisplayName("부분 슬롯 왕복: 채워진 TOP1~2는 값과 순서가 유지되고 TOP3~4는 null로 복원된다")
        void roundTrip_restoresPartiallyFilledSlots() {
            // given : 2명만 기록한 리드모델 (빠른 기록이 TOP1)
            Member fast = createAndSaveMember("빠른러너");
            Member slow = createAndSaveMember("느린러너");
            Course course = createAndSaveCourse("부분 슬롯 코스", fast, 37.5, 127.0);

            CourseReadModel readModel = CourseReadModel.create(course);
            readModel.applyRun(slow.getId(), 1800);
            readModel.applyRun(fast.getId(), 1500);
            readModelRepository.save(readModel);

            // when
            flushAndClear();
            CourseReadModel found = readModelRepository.findByCourseId(course.getId()).orElseThrow();

            // then : 기록 오름차순 순서 그대로 복원
            assertThat(found.getTop1()).isEqualTo(new RankSlot(fast.getId(), 1500));
            assertThat(found.getTop2()).isEqualTo(new RankSlot(slow.getId(), 1800));
            assertThat(found.getTop3()).isNull();
            assertThat(found.getTop4()).isNull();
        }

        @Test
        @DisplayName("ownerUuid null: 소유자 없는 OFFICIAL 코스의 리드모델도 제약 위반 없이 저장된다")
        void save_allowsNullOwnerUuidForOfficialCourse() {
            // given : member 가 없는 OFFICIAL 코스
            Course officialCourse = courseRepository.save(Course.of(
                "공식 코스",
                CourseProfile.of(5.0, 10.0, 100.0, 50.0),
                Coordinate.of(37.5, 127.0),
                CourseSource.OFFICIAL,
                true,
                CourseDataUrls.of(
                    "https://example.com/route-official.json",
                    "https://example.com/checkpoints-official.json",
                    "https://example.com/thumbnail-official.jpg"
                )
            ));

            CourseReadModel readModel = CourseReadModel.create(officialCourse);
            assertThat(readModel.getOwnerUuid()).isNull();

            // when & then : owner_uuid NOT NULL 제약에 걸리지 않는다
            assertThatCode(() -> {
                readModelRepository.save(readModel);
                flushAndClear();
            }).doesNotThrowAnyException();

            CourseReadModel found = readModelRepository.findByCourseId(officialCourse.getId()).orElseThrow();
            assertThat(found.getOwnerUuid()).isNull();
            assertThat(found.getSource()).isEqualTo(CourseSource.OFFICIAL);
        }
    }

    // ========== Helper Methods ==========

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private Member createAndSaveMember(String nickname) {
        Member member = Member.of(nickname, "https://example.com/" + nickname + ".jpg");
        return memberRepository.save(member);
    }
    
    private Course createAndSaveCourse(String name, Member owner, double lat, double lng) {
        CourseProfile profile = CourseProfile.of(5.0, 10.0, 100.0, 50.0);
        Coordinate coordinate = Coordinate.of(lat, lng);
        CourseDataUrls urls = CourseDataUrls.of(
            "https://example.com/route.json",
            "https://example.com/checkpoints.json",
            "https://example.com/thumbnail.jpg"
        );
        
        Course course = Course.of(
            owner,
            name,
            profile,
            coordinate,
            soma.ghostrunner.domain.course.enums.CourseSource.USER,
            false,
            urls
        );
        
        return courseRepository.save(course);
    }
}
