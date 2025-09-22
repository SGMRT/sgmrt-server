package soma.ghostrunner.domain.notice.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.notice.domain.NoticeDismissal;

import java.util.Optional;

@Repository
public interface NoticeDismissalRepository extends JpaRepository<NoticeDismissal, Long> {

    @Query("SELECT nd FROM NoticeDismissal nd JOIN nd.member m " +
            "WHERE nd.notice.id = :noticeId AND m.uuid = :memberUuid")
    Optional<NoticeDismissal> findByNoticeIdAndMemberUuid(Long noticeId, String memberUuid);

}
