package soma.ghostrunner.domain.notice.dao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import soma.ghostrunner.domain.notice.domain.Notice;
import soma.ghostrunner.domain.notice.domain.enums.NoticeType;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NoticeRepository extends JpaRepository<Notice, Long> {

    @Query("SELECT n FROM Notice n " +
            "WHERE (n.startAt <= :now AND n.endAt >= :now) " +
            "AND (:noticeType IS NULL OR n.type = :noticeType) " + // noticeType != null일 때만 필터링
            "AND n.id NOT IN (" +
            "   SELECT nd.notice.id FROM NoticeDismissal nd " +
            "   WHERE nd.member.uuid = :memberUuid " +
            "   AND (nd.dismissUntil IS NULL OR nd.dismissUntil >= :now) " +
            ") " +
            "ORDER BY n.priority DESC, n.createdAt DESC")
    List<Notice> findActiveNoticesForMember(LocalDateTime now, String memberUuid, NoticeType noticeType);

    @Query("SELECT n FROM Notice n WHERE (:noticeType IS NULL OR n.type = :noticeType)") // noticeType != null일 때만 필터링
    Page<Notice> findAllByType(NoticeType noticeType, Pageable pageable);

    @Query("SELECT n FROM Notice n WHERE n.startAt IS NULL AND n.endAt IS NULL ")
    List<Notice> findDeactivatedNotices();

}
