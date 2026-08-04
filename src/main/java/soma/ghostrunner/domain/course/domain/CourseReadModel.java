package soma.ghostrunner.domain.course.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import soma.ghostrunner.domain.course.enums.CourseSource;
import soma.ghostrunner.global.common.BaseTimeEntity;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 메인 화면(지도) 조회 전용 리드모델.
 *
 * 책임은 세 가지뿐이다 — 컬럼 매핑, 코스 역정규화 필드 관리, TOP4 변경 감지.
 * 순위 규칙(정렬·중복 제거·탈락 판정)은 불변 VO {@link TopRunners}가 전담한다.
 *
 * 불변식:
 * <ul>
 *   <li>TOP4는 {@link RankSlot} 슬롯 4개({@code top1}~{@code top4})로 고정 저장한다. (랭킹 도메인이 아닌 뷰 데이터)</li>
 *   <li>슬롯은 기록 오름차순으로 앞에서부터 채우며, 채워지지 않은 순위는 {@code null}(= 빈 슬롯)이다.</li>
 *   <li>따라서 빈 슬롯 뒤에 채워진 슬롯이 오는 구멍(hole)은 존재하지 않는다.</li>
 * </ul>
 */
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

    @Column(name = "owner_uuid", length = 36)
    private String ownerUuid;

    @Column(name = "route_url", nullable = false, columnDefinition = "TEXT")
    private String routeUrl;

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Embedded
    private CourseProfile courseProfile;

    // ========== 조회용 위경도 ==========

    @Column(name = "start_lat", nullable = false)
    private Double startLat;

    @Column(name = "start_lng", nullable = false)
    private Double startLng;

    // ========== TOP4 러너 (멤버 + 기록) — 기록 오름차순, 빈 슬롯은 null ==========

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "memberId",    column = @Column(name = "top1_member_id")),
        @AttributeOverride(name = "timeSeconds", column = @Column(name = "top1_time_seconds"))
    })
    private RankSlot top1;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "memberId",    column = @Column(name = "top2_member_id")),
        @AttributeOverride(name = "timeSeconds", column = @Column(name = "top2_time_seconds"))
    })
    private RankSlot top2;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "memberId",    column = @Column(name = "top3_member_id")),
        @AttributeOverride(name = "timeSeconds", column = @Column(name = "top3_time_seconds"))
    })
    private RankSlot top3;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "memberId",    column = @Column(name = "top4_member_id")),
        @AttributeOverride(name = "timeSeconds", column = @Column(name = "top4_time_seconds"))
    })
    private RankSlot top4;

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

    /**
     * 리드모델의 유일한 생성 경로는 {@link #create(Course)} 다. (코스 없이 리드모델만 존재할 수 없다)
     */
    @Builder(access = AccessLevel.PRIVATE)
    private CourseReadModel(
        Long courseId,
        String name,
        String ownerUuid,
        String routeUrl,
        String thumbnailUrl,
        CourseProfile courseProfile,
        Double startLat,
        Double startLng,
        Boolean isPublic,
        CourseSource source
    ) {
        this.courseId = courseId;
        this.name = name;
        this.ownerUuid = ownerUuid;
        this.routeUrl = routeUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.courseProfile = courseProfile;
        this.startLat = startLat;
        this.startLng = startLng;
        this.isPublic = isPublic != null ? isPublic : false;
        this.source = source != null ? source : CourseSource.USER;
        this.runnersCount = 0L;
    }

    /**
     * 코스의 조회용 정보를 역정규화해 리드모델을 만든다. TOP4 슬롯과 러너 수는 비어 있는 상태로 시작한다.
     */
    public static CourseReadModel create(Course course) {
        return CourseReadModel.builder()
            .courseId(course.getId())
            .name(course.getName())
            .ownerUuid(course.getMember() != null ? course.getMember().getUuid() : null)
            .routeUrl(course.getCourseDataUrls().getRouteUrl())
            .thumbnailUrl(course.getCourseDataUrls().getThumbnailUrl())
            .courseProfile(course.getCourseProfile())
            .startLat(course.getStartCoordinate().getLatitude())
            .startLng(course.getStartCoordinate().getLongitude())
            .isPublic(course.getIsPublic())
            .source(course.getSource())
            .build();
    }

    // ========== 비즈니스 로직: TOP4 ==========

    /**
     * 러닝 종료에 따른 증분 갱신. TOP4가 실제로 바뀐 경우에만 true 를 반환하고 필드를 변경한다.
     * (변화가 없으면 필드를 건드리지 않으므로 더티체킹이 UPDATE를 생략한다)
     */
    public boolean applyRun(Long memberId, int timeSeconds) {
        TopRunners current = topRunners();
        TopRunners updated = current.with(memberId, timeSeconds);
        if (updated.equals(current)) {
            return false;
        }
        applyTopRunners(updated);
        return true;
    }

    /**
     * 재계산 결과를 일괄 반영한다. (러닝 삭제 / 공개 전환 / 백필)
     */
    public void replaceTopRunners(TopRunners recalculated) {
        applyTopRunners(recalculated);
    }

    /**
     * 채워진 슬롯만 모아 VO로 만든다. (빈 슬롯 = null)
     */
    private TopRunners topRunners() {
        List<RankSlot> filledSlots = Stream.of(top1, top2, top3, top4)
            .filter(Objects::nonNull)
            .toList();
        return new TopRunners(filledSlots);
    }

    private void applyTopRunners(TopRunners topRunners) {
        List<RankSlot> rankedSlots = topRunners.slots();
        this.top1 = slotAtRank(rankedSlots, 1);
        this.top2 = slotAtRank(rankedSlots, 2);
        this.top3 = slotAtRank(rankedSlots, 3);
        this.top4 = slotAtRank(rankedSlots, 4);
    }

    /**
     * 해당 순위(1부터 시작)의 슬롯. 재계산 결과가 그 순위까지 채우지 못하면 빈 슬롯(null)으로 비운다.
     */
    private RankSlot slotAtRank(List<RankSlot> rankedSlots, int rank) {
        int index = rank - 1;
        return index < rankedSlots.size() ? rankedSlots.get(index) : null;
    }

    // ========== 비즈니스 로직: 기타 ==========

    public void updateRunnersCount(long count) {
        if (count >= 0) {
            this.runnersCount = count;
        }
    }

    public void rename(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
    }

    public void makePublic() {
        this.isPublic = true;
    }

    public void makePrivate() {
        this.isPublic = false;
    }
}
