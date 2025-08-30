package soma.ghostrunner.domain.notice.domain;

import jakarta.persistence.*;
import lombok.Getter;
import soma.ghostrunner.global.common.BaseTimeEntity;

import java.time.LocalDateTime;

@Entity
@Getter
public class Notice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String content;

    private String imageUrl;

    @Column(nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private Integer priority;

    private LocalDateTime startAt;

    private LocalDateTime endAt;


/**
 *
 * 공지사항 (Notice)
 *
 * 필드: id, 제목, 본문, 이미지 url, int priority, start_at, end_at, created_at, updated_at
 * */

}
