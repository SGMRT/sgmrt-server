package soma.ghostrunner.domain.notice.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import soma.ghostrunner.domain.notice.api.dto.response.NoticeDetailedResponse;
import soma.ghostrunner.domain.notice.domain.Notice;
import soma.ghostrunner.domain.notice.domain.enums.NoticeType;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class NoticeMapperTest {

    private final NoticeMapper noticeMapper = Mappers.getMapper(NoticeMapper.class);

    @DisplayName("Notice 엔티티를 NoticeDetailedResponse DTO로 변환한다.")
    @Test
    void toDetailedResponse_success() {
        // given
        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2025, 1, 31, 23, 59);
        Notice notice = Notice.of("테스트 제목", "테스트 내용", NoticeType.GENERAL_V2, "http://example.com/image.png", 5, start, end);
        setField(notice, "id", 1L);

        // when
        NoticeDetailedResponse response = noticeMapper.toDetailedResponse(notice);

        // then
        assertThat(response.id()).isEqualTo(notice.getId());
        assertThat(response.title()).isEqualTo(notice.getTitle());
        assertThat(response.content()).isEqualTo(notice.getContent());
        assertThat(response.type()).isEqualTo(notice.getType());
        assertThat(response.imageUrl()).isEqualTo(notice.getImageUrl());
        assertThat(response.priority()).isEqualTo(notice.getPriority());
        assertThat(response.startAt()).isEqualTo(notice.getStartAt());
        assertThat(response.endAt()).isEqualTo(notice.getEndAt());
    }

    @DisplayName("Notice 엔티티의 일부 필드가 null이어도 DTO로 정상 변환된다.")
    @Test
    void toDetailedResponse_withNullFields_edge() {
        // given
        // content, imageUrl, endAt이 null인 경우
        Notice notice = Notice.of("제목만 있는 공지", null, NoticeType.EVENT_V2, null, 1, LocalDateTime.now(), null);
        setField(notice, "id", 2L);

        // when
        NoticeDetailedResponse response = noticeMapper.toDetailedResponse(notice);

        // then
        assertThat(response.id()).isEqualTo(notice.getId());
        assertThat(response.title()).isEqualTo(notice.getTitle());
        assertThat(response.type()).isEqualTo(notice.getType());
        assertThat(response.priority()).isEqualTo(notice.getPriority());
        assertThat(response.startAt()).isEqualTo(notice.getStartAt());
        assertThat(response.content()).isNull();
        assertThat(response.imageUrl()).isNull();
        assertThat(response.endAt()).isNull();
    }

    @DisplayName("Notice 엔티티를 NoticeActivatedEvent로 변환한다.")
    @Test
    void toNoticeActivatedEvent_success() {
        // given
        LocalDateTime start = LocalDateTime.of(2025, 10, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2025, 10, 31, 23, 59);
        Notice notice1 = Notice.of("일반 공지", "공지 내용", NoticeType.GENERAL_V2, null, 10, start, end);
        setField(notice1, "id", 3L);
        Notice notice2 = Notice.of("이벤트 공지", "이벤트 내용", NoticeType.EVENT_V2, null, 10, start, end);
        setField(notice2, "id", 4L);

        // when
        var event = noticeMapper.toNoticeActivatedEvent(List.of(notice1, notice2));

        // then
        assertThat(event.activatedNotices()).hasSize(2);
        assertThat(event.activatedNotices()).extracting("noticeId")
                .containsExactlyInAnyOrder(notice1.getId(), notice2.getId());
        assertThat(event.activatedNotices()).extracting("title")
                .containsExactlyInAnyOrder(notice1.getTitle(), notice2.getTitle());
        assertThat(event.activatedNotices()).extracting("content")
                .containsExactlyInAnyOrder(notice1.getContent(), notice2.getContent());
        assertThat(event.activatedNotices()).extracting("noticeType")
                .containsExactlyInAnyOrder(notice1.getType(), notice2.getType());
        assertThat(event.activatedNotices()).extracting("startAt")
                .containsExactlyInAnyOrder(notice1.getStartAt(), notice2.getStartAt());
        assertThat(event.activatedNotices()).extracting("endAt")
                .containsExactlyInAnyOrder(notice1.getEndAt(), notice2.getEndAt());
    }

    // 리플렉션으로 ID 설정
    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}