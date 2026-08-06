package soma.ghostrunner.domain.course.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import soma.ghostrunner.domain.course.domain.Region;

import java.util.Optional;

public interface RegionRepository extends JpaRepository<Region, Long> {

    Optional<Region> findByName(String name);
}
