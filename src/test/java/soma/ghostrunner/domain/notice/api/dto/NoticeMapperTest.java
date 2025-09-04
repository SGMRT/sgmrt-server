package soma.ghostrunner.domain.notice.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import soma.ghostrunner.domain.notice.api.dto.response.NoticeDetailedResponse;
import soma.ghostrunner.domain.notice.domain.Notice;

import java.time.LocalDateTime;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class NoticeMapperTest {

    private final NoticeMapper noticeMapper = Mappers.getMapper(NoticeMapper.class);

    @DisplayName("Notice 엔티티를 NoticeDetailedResponse DTO로 변환한다.")
    @Test
    void toDetailedResponse_success() {
        // given
        LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2025, 1, 31, 23, 59);
        Notice notice = Notice.of("테스트 제목", "테스트 내용", "http://example.com/image.png", 5, start, end);
        // ID는 보통 DB에서 생성되므로, 테스트에서는 직접 설정해줍니다.
        setField(notice, "id", 1L);

        // when
        NoticeDetailedResponse response = noticeMapper.toDetailedResponse(notice);

        // then
        assertThat(response.id()).isEqualTo(notice.getId());
        assertThat(response.title()).isEqualTo(notice.getTitle());
        assertThat(response.content()).isEqualTo(notice.getContent());
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
        Notice notice = Notice.of("제목만 있는 공지", null, null, 1, LocalDateTime.now(), null);
        setField(notice, "id", 2L);


        // when
        NoticeDetailedResponse response = noticeMapper.toDetailedResponse(notice);

        // then
        assertThat(response.id()).isEqualTo(notice.getId());
        assertThat(response.title()).isEqualTo(notice.getTitle());
        assertThat(response.content()).isNull();
        assertThat(response.imageUrl()).isNull();
    }

    // 테스트를 위해 리플렉션을 사용하여 ID 필드 설정
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