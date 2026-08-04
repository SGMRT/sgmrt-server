package soma.ghostrunner.domain.course.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import soma.ghostrunner.global.common.BaseTimeEntity;

/**
 * 코스 지도 캐시키의 단위가 되는 행정구역(동네).
 *
 * 설계 문서: docs/refactoring/course-read-model/cache/05-cache-key-design.md §3, §6-1
 *
 * 불변식:
 * <ul>
 *   <li>{@code name}은 "시 구 동" 전체 경로이며 유니크({@code uk_region_name}) — 동명 지역 충돌 방지</li>
 *   <li>대표좌표({@code centerLat}, {@code centerLng})는 <b>최초 등록 좌표로 고정</b>이며 수정 메서드가 없다.
 *       캐시 값이 대표좌표 기준 고정 반경 조회 결과이므로, 좌표가 바뀌면 같은 키에 다른 값이 적재되어 결정성이 깨진다.</li>
 * </ul>
 */
@Entity
@Table(
    name = "region",
    uniqueConstraints = @UniqueConstraint(name = "uk_region_name", columnNames = "name"),
    indexes = @Index(name = "idx_region_center", columnList = "center_lat, center_lng")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Region extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** "서울특별시 강남구 역삼동" 형태의 전체 경로 */
    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "center_lat", nullable = false)
    private Double centerLat;

    @Column(name = "center_lng", nullable = false)
    private Double centerLng;

    private Region(String name, Double centerLat, Double centerLng) {
        this.name = name;
        this.centerLat = centerLat;
        this.centerLng = centerLng;
    }

    public static Region of(String name, Double centerLat, Double centerLng) {
        return new Region(name, centerLat, centerLng);
    }
}
