package soma.ghostrunner.domain.course.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import soma.ghostrunner.domain.course.enums.CourseSource;
import soma.ghostrunner.global.common.BaseTimeEntity;

import java.util.Objects;


@Entity
@Table(
    name = "course_read_model",
    indexes = {
        @Index(name = "uk_course_id", columnList = "course_id", unique = true),
        @Index(name = "idx_is_public_location", columnList = "is_public, start_lat, start_lng")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseReadModel extends BaseTimeEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // ========== 코스 참조 ==========
    
    @Column(name = "course_id", nullable = false, unique = true)
    private Long courseId;
    
    // ========== 필수 응답 필드 (코스 정보 - 역정규화) ==========
    
    @Column(nullable = false)
    private String name;
    
    @Column(name = "owner_uuid", nullable = false, length = 36)
    private String ownerUuid;
    
    @Column(name = "route_url", nullable = false, columnDefinition = "TEXT")
    private String routeUrl;
    
    // ========== 조회용 위경도 ==========
    
    @Column(name = "start_lat", nullable = false)
    private Double startLat;
    
    @Column(name = "start_lng", nullable = false)
    private Double startLng;
    
    // ========== TOP4 러너 (ID + 기록) ==========
    
    @Column(name = "top1_member_id")
    private Long top1MemberId;
    
    @Column(name = "top1_time_seconds")
    private Integer top1TimeSeconds;
    
    @Column(name = "top2_member_id")
    private Long top2MemberId;
    
    @Column(name = "top2_time_seconds")
    private Integer top2TimeSeconds;
    
    @Column(name = "top3_member_id")
    private Long top3MemberId;
    
    @Column(name = "top3_time_seconds")
    private Integer top3TimeSeconds;
    
    @Column(name = "top4_member_id")
    private Long top4MemberId;
    
    @Column(name = "top4_time_seconds")
    private Integer top4TimeSeconds;
    
    // ========== 집계 정보 ==========
    
    @Column(name = "runners_count", nullable = false)
    private Long runnersCount = 0L;
    
    // ========== 상태 ==========
    
    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = false;
    
    @Column(name = "source", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private CourseSource source;
    
    // ========== 생성 메서드 ==========
    
    @Builder
    public CourseReadModel(
        Long courseId,
        String name,
        String ownerUuid,
        String routeUrl,
        Double startLat,
        Double startLng,
        Boolean isPublic,
        CourseSource source
    ) {
        this.courseId = courseId;
        this.name = name;
        this.ownerUuid = ownerUuid;
        this.routeUrl = routeUrl;
        this.startLat = startLat;
        this.startLng = startLng;
        this.isPublic = isPublic != null ? isPublic : false;
        this.source = source != null ? source : CourseSource.USER;
        this.runnersCount = 0L;
    }
    
    public static CourseReadModel create(Course course) {
        return CourseReadModel.builder()
            .courseId(course.getId())
            .name(course.getName())
            .ownerUuid(course.getMember() != null ? course.getMember().getUuid() : null)
            .routeUrl(course.getCourseDataUrls().getRouteUrl())
            .startLat(course.getStartCoordinate().getLatitude())
            .startLng(course.getStartCoordinate().getLongitude())
            .isPublic(course.getIsPublic())
            .source(course.getSource())
            .build();
    }
    
    // ========== 비즈니스 로직: 증분 갱신 ==========
    
    /**
     * 새 러닝 기록을 TOP4에 삽입 (더 빠르면)
     * 
     * 동작:
     * 1. 이미 TOP4에 있는 러너인지 확인
     *    - 있으면: 기록 갱신 시도 (더 빠르면 업데이트 + 재정렬)
     *    - 없으면: TOP4보다 빠른지 확인 후 삽입
     * 2. TOP1 ~ TOP4 순서대로 비교
     * 3. 삽입 시 기존 순위를 한 칸씩 밀어냄
     * 
     * @param memberId 러너 ID
     * @param timeSeconds 기록 (초)
     * @return true if inserted or updated, false otherwise
     */
    public boolean insertIfBetter(Long memberId, int timeSeconds) {
        if (memberId == null || timeSeconds <= 0) {
            throw new IllegalArgumentException("memberId and timeSeconds must be valid");
        }
        
        // 1. 이미 TOP4에 있는 러너인지 확인
        if (isInTop4(memberId)) {
            return updateExistingRunner(memberId, timeSeconds);
        }
        
        // 2. TOP1보다 빠른가?
        if (top1TimeSeconds == null || timeSeconds < top1TimeSeconds) {
            shiftDown(1);
            top1MemberId = memberId;
            top1TimeSeconds = timeSeconds;
            return true;
        }
        
        // 3. TOP2보다 빠른가?
        if (top2TimeSeconds == null || timeSeconds < top2TimeSeconds) {
            shiftDown(2);
            top2MemberId = memberId;
            top2TimeSeconds = timeSeconds;
            return true;
        }
        
        // 4. TOP3보다 빠른가?
        if (top3TimeSeconds == null || timeSeconds < top3TimeSeconds) {
            shiftDown(3);
            top3MemberId = memberId;
            top3TimeSeconds = timeSeconds;
            return true;
        }
        
        // 5. TOP4보다 빠른가?
        if (top4TimeSeconds == null || timeSeconds < top4TimeSeconds) {
            top4MemberId = memberId;
            top4TimeSeconds = timeSeconds;
            return true;
        }
        
        // 6. TOP4 진입 실패
        return false;
    }
    
    /**
     * 기존 TOP4 러너가 기록을 갱신한 경우
     */
    private boolean updateExistingRunner(Long memberId, int newTime) {
        // 1위 갱신
        if (Objects.equals(top1MemberId, memberId)) {
            if (newTime < top1TimeSeconds) {
                top1TimeSeconds = newTime;
                return true;
            }
            // 기록이 느려진 경우는 무시 (전체 재계산 필요, 일단 보수적 접근)
            return false;
        }
        
        // 2위 갱신
        if (Objects.equals(top2MemberId, memberId)) {
            if (newTime < top2TimeSeconds) {
                top2TimeSeconds = newTime;
                // 1위보다 빠르면 순위 교체
                if (top1TimeSeconds != null && newTime < top1TimeSeconds) {
                    swap(1, 2);
                }
                return true;
            }
            return false;
        }
        
        // 3위 갱신
        if (Objects.equals(top3MemberId, memberId)) {
            if (newTime < top3TimeSeconds) {
                top3TimeSeconds = newTime;
                // 2위보다 빠르면 순위 교체
                if (top2TimeSeconds != null && newTime < top2TimeSeconds) {
                    swap(2, 3);
                    // 1위보다도 빠르면 추가 교체
                    if (top1TimeSeconds != null && newTime < top1TimeSeconds) {
                        swap(1, 2);
                    }
                }
                return true;
            }
            return false;
        }
        
        // 4위 갱신
        if (Objects.equals(top4MemberId, memberId)) {
            if (newTime < top4TimeSeconds) {
                top4TimeSeconds = newTime;
                // 3위보다 빠르면 순위 교체
                if (top3TimeSeconds != null && newTime < top3TimeSeconds) {
                    swap(3, 4);
                    // 2위보다도 빠르면 추가 교체
                    if (top2TimeSeconds != null && newTime < top2TimeSeconds) {
                        swap(2, 3);
                        // 1위보다도 빠르면 추가 교체
                        if (top1TimeSeconds != null && newTime < top1TimeSeconds) {
                            swap(1, 2);
                        }
                    }
                }
                return true;
            }
            return false;
        }
        
        return false;
    }
    
    /**
     * 순위를 한 칸씩 밀어내림
     * 
     * @param from 삽입 위치 (1~3)
     */
    private void shiftDown(int from) {
        if (from == 1) {
            // TOP1 삽입 → 1위를 2위로, 2위를 3위로, 3위를 4위로
            top4MemberId = top3MemberId;
            top4TimeSeconds = top3TimeSeconds;
            top3MemberId = top2MemberId;
            top3TimeSeconds = top2TimeSeconds;
            top2MemberId = top1MemberId;
            top2TimeSeconds = top1TimeSeconds;
        } else if (from == 2) {
            // TOP2 삽입 → 2위를 3위로, 3위를 4위로
            top4MemberId = top3MemberId;
            top4TimeSeconds = top3TimeSeconds;
            top3MemberId = top2MemberId;
            top3TimeSeconds = top2TimeSeconds;
        } else if (from == 3) {
            // TOP3 삽입 → 3위를 4위로
            top4MemberId = top3MemberId;
            top4TimeSeconds = top3TimeSeconds;
        }
    }
    
    /**
     * 두 순위를 교체
     * 
     * @param rank1 첫 번째 순위 (1~4)
     * @param rank2 두 번째 순위 (1~4)
     */
    private void swap(int rank1, int rank2) {
        if (rank1 == 1 && rank2 == 2) {
            Long tempId = top1MemberId;
            Integer tempTime = top1TimeSeconds;
            top1MemberId = top2MemberId;
            top1TimeSeconds = top2TimeSeconds;
            top2MemberId = tempId;
            top2TimeSeconds = tempTime;
        } else if (rank1 == 2 && rank2 == 3) {
            Long tempId = top2MemberId;
            Integer tempTime = top2TimeSeconds;
            top2MemberId = top3MemberId;
            top2TimeSeconds = top3TimeSeconds;
            top3MemberId = tempId;
            top3TimeSeconds = tempTime;
        } else if (rank1 == 3 && rank2 == 4) {
            Long tempId = top3MemberId;
            Integer tempTime = top3TimeSeconds;
            top3MemberId = top4MemberId;
            top3TimeSeconds = top4TimeSeconds;
            top4MemberId = tempId;
            top4TimeSeconds = tempTime;
        }
    }
    
    private boolean isInTop4(Long memberId) {
        return Objects.equals(top1MemberId, memberId)
            || Objects.equals(top2MemberId, memberId)
            || Objects.equals(top3MemberId, memberId)
            || Objects.equals(top4MemberId, memberId);
    }
    
    /**
     * TOP4에 특정 멤버가 포함되어 있는지 확인
     * (러닝 삭제 시 전체 재계산 필요 여부 판단용)
     */
    public boolean containsMember(Long memberId) {
        return isInTop4(memberId);
    }
    
    // ========== 비즈니스 로직: 기타 ==========
    
    public void updateRunnersCount(Long count) {
        if (count != null && count >= 0) {
            this.runnersCount = count;
        }
    }
    
    public void makePublic() {
        this.isPublic = true;
    }
    
    public void makePrivate() {
        this.isPublic = false;
    }
    
    public void updateName(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
    }
    
    public void updateRouteUrl(String routeUrl) {
        if (routeUrl != null && !routeUrl.isBlank()) {
            this.routeUrl = routeUrl;
        }
    }
    
    /**
     * TOP4 전체 교체 (전체 재계산 시 사용)
     * 러닝 삭제 등으로 증분 갱신이 불가능한 경우 사용
     */
    public void replaceTop4(
        Long member1Id, Integer time1,
        Long member2Id, Integer time2,
        Long member3Id, Integer time3,
        Long member4Id, Integer time4
    ) {
        this.top1MemberId = member1Id;
        this.top1TimeSeconds = time1;
        this.top2MemberId = member2Id;
        this.top2TimeSeconds = time2;
        this.top3MemberId = member3Id;
        this.top3TimeSeconds = time3;
        this.top4MemberId = member4Id;
        this.top4TimeSeconds = time4;
    }
}