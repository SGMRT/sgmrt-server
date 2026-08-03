package soma.ghostrunner.domain.course.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * CourseReadModel 단위 테스트
 * 
 * 주요 테스트 대상:
 * - insertIfBetter(): 증분 갱신 로직
 *   - 빈 TOP4에 삽입
 *   - 기존 TOP4에 더 빠른 기록 삽입
 *   - 기존 TOP4에 더 느린 기록 삽입 (실패)
 *   - 기존 러너가 기록 갱신
 *   - 순위 교체 (2위가 1위보다 빠른 기록 달성)
 */
@DisplayName("CourseReadModel 단위 테스트")
class CourseReadModelTest {
    
    @Nested
    @DisplayName("insertIfBetter() - 신규 러너 삽입")
    class InsertNewRunner {
        
        @Test
        @DisplayName("빈 TOP4에 첫 기록 삽입 - TOP1으로")
        void insertFirst() {
            // Given
            CourseReadModel readModel = createReadModel();
            
            // When
            boolean result = readModel.insertIfBetter(1L, 1800); // 30분
            
            // Then
            assertThat(result).isTrue();
            assertThat(readModel.getTop1MemberId()).isEqualTo(1L);
            assertThat(readModel.getTop1TimeSeconds()).isEqualTo(1800);
            assertThat(readModel.getTop2MemberId()).isNull();
        }
        
        @Test
        @DisplayName("TOP1보다 빠른 기록 - 기존 1위를 밀어내고 TOP1으로")
        void insertFasterThanTop1() {
            // Given
            CourseReadModel readModel = createReadModel();
            readModel.insertIfBetter(1L, 1800); // 30분 (1위)
            
            // When
            boolean result = readModel.insertIfBetter(2L, 1500); // 25분 (더 빠름!)
            
            // Then
            assertThat(result).isTrue();
            assertThat(readModel.getTop1MemberId()).isEqualTo(2L);
            assertThat(readModel.getTop1TimeSeconds()).isEqualTo(1500);
            assertThat(readModel.getTop2MemberId()).isEqualTo(1L); // 기존 1위가 2위로
            assertThat(readModel.getTop2TimeSeconds()).isEqualTo(1800);
        }
        
        @Test
        @DisplayName("TOP2 자리에 삽입")
        void insertAsTop2() {
            // Given
            CourseReadModel readModel = createReadModel();
            readModel.insertIfBetter(1L, 1500); // 25분 (1위)
            
            // When
            boolean result = readModel.insertIfBetter(2L, 1800); // 30분 (2위)
            
            // Then
            assertThat(result).isTrue();
            assertThat(readModel.getTop1MemberId()).isEqualTo(1L);
            assertThat(readModel.getTop2MemberId()).isEqualTo(2L);
            assertThat(readModel.getTop2TimeSeconds()).isEqualTo(1800);
        }
        
        @Test
        @DisplayName("TOP3 자리에 삽입")
        void insertAsTop3() {
            // Given
            CourseReadModel readModel = createReadModel();
            readModel.insertIfBetter(1L, 1500); // 25분
            readModel.insertIfBetter(2L, 1800); // 30분
            
            // When
            boolean result = readModel.insertIfBetter(3L, 2100); // 35분
            
            // Then
            assertThat(result).isTrue();
            assertThat(readModel.getTop3MemberId()).isEqualTo(3L);
            assertThat(readModel.getTop3TimeSeconds()).isEqualTo(2100);
        }
        
        @Test
        @DisplayName("TOP4 자리에 삽입")
        void insertAsTop4() {
            // Given
            CourseReadModel readModel = createReadModel();
            readModel.insertIfBetter(1L, 1500); // 25분
            readModel.insertIfBetter(2L, 1800); // 30분
            readModel.insertIfBetter(3L, 2100); // 35분
            
            // When
            boolean result = readModel.insertIfBetter(4L, 2400); // 40분
            
            // Then
            assertThat(result).isTrue();
            assertThat(readModel.getTop4MemberId()).isEqualTo(4L);
            assertThat(readModel.getTop4TimeSeconds()).isEqualTo(2400);
        }
        
        @Test
        @DisplayName("TOP4보다 느린 기록 - 삽입 실패")
        void insertSlowerThanTop4_fails() {
            // Given
            CourseReadModel readModel = createReadModel();
            readModel.insertIfBetter(1L, 1500); // 25분
            readModel.insertIfBetter(2L, 1800); // 30분
            readModel.insertIfBetter(3L, 2100); // 35분
            readModel.insertIfBetter(4L, 2400); // 40분
            
            // When
            boolean result = readModel.insertIfBetter(5L, 2700); // 45분 (TOP4보다 느림)
            
            // Then
            assertThat(result).isFalse();
            assertThat(readModel.getTop4MemberId()).isEqualTo(4L); // 변경 없음
            assertThat(readModel.getTop4TimeSeconds()).isEqualTo(2400);
        }
        
        @Test
        @DisplayName("TOP3과 TOP4 사이 기록 - TOP4 교체")
        void insertBetweenTop3AndTop4() {
            // Given
            CourseReadModel readModel = createReadModel();
            readModel.insertIfBetter(1L, 1500); // 25분
            readModel.insertIfBetter(2L, 1800); // 30분
            readModel.insertIfBetter(3L, 2100); // 35분
            readModel.insertIfBetter(4L, 2700); // 45분 (4위)
            
            // When
            boolean result = readModel.insertIfBetter(5L, 2400); // 40분 (3위와 4위 사이)
            
            // Then
            assertThat(result).isTrue();
            assertThat(readModel.getTop3MemberId()).isEqualTo(3L);
            assertThat(readModel.getTop3TimeSeconds()).isEqualTo(2100);
            assertThat(readModel.getTop4MemberId()).isEqualTo(5L); // 새 러너가 4위
            assertThat(readModel.getTop4TimeSeconds()).isEqualTo(2400);
        }
    }
    
    @Nested
    @DisplayName("insertIfBetter() - 기존 러너 기록 갱신")
    class UpdateExistingRunner {
        
        @Test
        @DisplayName("TOP1 러너가 더 빠른 기록 달성 - 기록만 업데이트")
        void top1ImprovedRecord() {
            // Given
            CourseReadModel readModel = createReadModel();
            readModel.insertIfBetter(1L, 1800); // 30분 (1위)
            readModel.insertIfBetter(2L, 1900); // 31분 40초 (2위)
            
            // When
            boolean result = readModel.insertIfBetter(1L, 1500); // 25분 (더 빠름!)
            
            // Then
            assertThat(result).isTrue();
            assertThat(readModel.getTop1MemberId()).isEqualTo(1L); // 그대로 1위
            assertThat(readModel.getTop1TimeSeconds()).isEqualTo(1500); // 기록 업데이트
        }
        
        @Test
        @DisplayName("TOP2 러너가 TOP1보다 빠른 기록 달성 - 순위 교체")
        void top2BecameTop1() {
            // Given
            CourseReadModel readModel = createReadModel();
            readModel.insertIfBetter(1L, 1800); // 30분 (1위)
            readModel.insertIfBetter(2L, 1900); // 31분 40초 (2위)
            
            // When
            boolean result = readModel.insertIfBetter(2L, 1500); // 25분 (1위보다 빠름!)
            
            // Then
            assertThat(result).isTrue();
            assertThat(readModel.getTop1MemberId()).isEqualTo(2L); // 2번이 1위로
            assertThat(readModel.getTop1TimeSeconds()).isEqualTo(1500);
            assertThat(readModel.getTop2MemberId()).isEqualTo(1L); // 1번이 2위로
            assertThat(readModel.getTop2TimeSeconds()).isEqualTo(1800);
        }
        
        @Test
        @DisplayName("TOP3 러너가 TOP1까지 치고 올라감")
        void top3BecameTop1() {
            // Given
            CourseReadModel readModel = createReadModel();
            readModel.insertIfBetter(1L, 1800); // 30분
            readModel.insertIfBetter(2L, 1900); // 31분 40초
            readModel.insertIfBetter(3L, 2000); // 33분 20초
            
            // When
            boolean result = readModel.insertIfBetter(3L, 1500); // 25분 (전체 1위!)
            
            // Then
            assertThat(result).isTrue();
            assertThat(readModel.getTop1MemberId()).isEqualTo(3L); // 3번이 1위
            assertThat(readModel.getTop1TimeSeconds()).isEqualTo(1500);
            assertThat(readModel.getTop2MemberId()).isEqualTo(1L); // 1번이 2위
            assertThat(readModel.getTop3MemberId()).isEqualTo(2L); // 2번이 3위
        }
        
        @Test
        @DisplayName("TOP4 러너가 TOP1까지 치고 올라감")
        void top4BecameTop1() {
            // Given
            CourseReadModel readModel = createReadModel();
            readModel.insertIfBetter(1L, 1800); // 30분
            readModel.insertIfBetter(2L, 1900); // 31분 40초
            readModel.insertIfBetter(3L, 2000); // 33분 20초
            readModel.insertIfBetter(4L, 2100); // 35분
            
            // When
            boolean result = readModel.insertIfBetter(4L, 1500); // 25분 (전체 1위!)
            
            // Then
            assertThat(result).isTrue();
            assertThat(readModel.getTop1MemberId()).isEqualTo(4L); // 4번이 1위
            assertThat(readModel.getTop1TimeSeconds()).isEqualTo(1500);
            assertThat(readModel.getTop2MemberId()).isEqualTo(1L); // 1번이 2위
            assertThat(readModel.getTop3MemberId()).isEqualTo(2L); // 2번이 3위
            assertThat(readModel.getTop4MemberId()).isEqualTo(3L); // 3번이 4위
        }
        
        @Test
        @DisplayName("기존 러너가 더 느린 기록 - 업데이트 안 함")
        void existingRunnerSlowerRecord_noUpdate() {
            // Given
            CourseReadModel readModel = createReadModel();
            readModel.insertIfBetter(1L, 1800); // 30분
            
            // When
            boolean result = readModel.insertIfBetter(1L, 2000); // 33분 20초 (더 느림)
            
            // Then
            assertThat(result).isFalse();
            assertThat(readModel.getTop1TimeSeconds()).isEqualTo(1800); // 변경 없음
        }
    }
    
    @Nested
    @DisplayName("insertIfBetter() - 엣지 케이스")
    class EdgeCases {
        
        @Test
        @DisplayName("null memberId - 예외 발생")
        void nullMemberId_throwsException() {
            // Given
            CourseReadModel readModel = createReadModel();
            
            // When & Then
            assertThatThrownBy(() -> readModel.insertIfBetter(null, 1800))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("memberId");
        }
        
        @Test
        @DisplayName("0 이하 시간 - 예외 발생")
        void invalidTime_throwsException() {
            // Given
            CourseReadModel readModel = createReadModel();
            
            // When & Then
            assertThatThrownBy(() -> readModel.insertIfBetter(1L, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeSeconds");
            
            assertThatThrownBy(() -> readModel.insertIfBetter(1L, -100))
                .isInstanceOf(IllegalArgumentException.class);
        }
        
        @Test
        @DisplayName("동일한 시간 기록 - TOP2에 삽입됨")
        void sameTime_insertedAsTop2() {
            // Given
            CourseReadModel readModel = createReadModel();
            readModel.insertIfBetter(1L, 1800); // 30분
            
            // When
            boolean result = readModel.insertIfBetter(2L, 1800); // 똑같이 30분
            
            // Then
            // TOP1과 같은 시간 → TOP1 조건(< 1800)은 실패
            // TOP2 조건(< null 또는 < 1800)은? → TOP2가 null이므로 성공!
            assertThat(result).isTrue();
            assertThat(readModel.getTop1MemberId()).isEqualTo(1L);
            assertThat(readModel.getTop2MemberId()).isEqualTo(2L); // TOP2에 삽입됨
            assertThat(readModel.getTop2TimeSeconds()).isEqualTo(1800);
        }
    }
    
    @Nested
    @DisplayName("기타 메서드")
    class OtherMethods {
        
        @Test
        @DisplayName("containsMember() - TOP4에 포함 여부 확인")
        void containsMember() {
            // Given
            CourseReadModel readModel = createReadModel();
            readModel.insertIfBetter(1L, 1800);
            readModel.insertIfBetter(2L, 1900);
            
            // When & Then
            assertThat(readModel.containsMember(1L)).isTrue();
            assertThat(readModel.containsMember(2L)).isTrue();
            assertThat(readModel.containsMember(3L)).isFalse();
        }
        
        @Test
        @DisplayName("updateRunnersCount() - 러너 수 업데이트")
        void updateRunnersCount() {
            // Given
            CourseReadModel readModel = createReadModel();
            
            // When
            readModel.updateRunnersCount(10L);
            
            // Then
            assertThat(readModel.getRunnersCount()).isEqualTo(10L);
        }
        
        @Test
        @DisplayName("makePublic/makePrivate() - 공개 여부 변경")
        void changePublicity() {
            // Given
            CourseReadModel readModel = createReadModel();
            
            // When & Then
            readModel.makePublic();
            assertThat(readModel.getIsPublic()).isTrue();
            
            readModel.makePrivate();
            assertThat(readModel.getIsPublic()).isFalse();
        }
        
        @Test
        @DisplayName("updateName() - 이름 변경")
        void updateName() {
            // Given
            CourseReadModel readModel = createReadModel();
            
            // When
            readModel.updateName("새로운 코스 이름");
            
            // Then
            assertThat(readModel.getName()).isEqualTo("새로운 코스 이름");
        }
        
        @Test
        @DisplayName("replaceTop4() - TOP4 전체 교체")
        void replaceTop4() {
            // Given
            CourseReadModel readModel = createReadModel();
            readModel.insertIfBetter(1L, 1800);
            
            // When
            readModel.replaceTop4(
                10L, 1000,
                11L, 1100,
                12L, 1200,
                13L, 1300
            );
            
            // Then
            assertThat(readModel.getTop1MemberId()).isEqualTo(10L);
            assertThat(readModel.getTop1TimeSeconds()).isEqualTo(1000);
            assertThat(readModel.getTop2MemberId()).isEqualTo(11L);
            assertThat(readModel.getTop3MemberId()).isEqualTo(12L);
            assertThat(readModel.getTop4MemberId()).isEqualTo(13L);
        }
    }
    
    // ========== Helper Methods ==========
    
    private CourseReadModel createReadModel() {
        return CourseReadModel.builder()
            .courseId(1L)
            .name("테스트 코스")
            .ownerUuid("test-uuid")
            .routeUrl("https://example.com/route.json")
            .startLat(37.5)
            .startLng(127.0)
            .isPublic(true)
            .build();
    }
}
