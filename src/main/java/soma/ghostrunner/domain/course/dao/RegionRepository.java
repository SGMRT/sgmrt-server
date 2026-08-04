package soma.ghostrunner.domain.course.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import soma.ghostrunner.domain.course.domain.Region;

import java.util.List;
import java.util.Optional;

public interface RegionRepository extends JpaRepository<Region, Long> {

    Optional<Region> findByName(String name);

    /** 대표좌표가 박스 안에 있는 지역 — 코스 좌표 → 영향받는 캐시 엔트리 역산용 (이빅트) */
    List<Region> findByCenterLatBetweenAndCenterLngBetween(
            Double minLat, Double maxLat, Double minLng, Double maxLng);
}
