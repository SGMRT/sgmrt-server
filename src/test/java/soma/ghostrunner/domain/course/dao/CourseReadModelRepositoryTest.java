package soma.ghostrunner.domain.course.dao;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.IntegrationTestSupport;
import soma.ghostrunner.domain.course.domain.*;
import soma.ghostrunner.domain.course.dto.query.CourseMapDto;
import soma.ghostrunner.domain.member.domain.Member;
import soma.ghostrunner.domain.member.infra.dao.MemberRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
        readModel1.insertIfBetter(member1.getId(), 1800); // 30분
        readModel1.insertIfBetter(member2.getId(), 1500); // 25분
        readModel1.updateRunnersCount(2L);
        readModelRepository.save(readModel1);
        
        CourseReadModel readModel2 = CourseReadModel.create(course2);
        readModel2.makePublic();
        readModel2.insertIfBetter(member3.getId(), 2100); // 35분
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
        readModel.insertIfBetter(deletedMember.getId(), 1500); // 탈퇴 예정 멤버가 1위
        readModel.insertIfBetter(activeMember.getId(), 1800);  // 활동 멤버가 2위
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
    
    // ========== Helper Methods ==========
    
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
