package soma.ghostrunner.domain.notice.api.dto.response;

public record NoticeDetailedResponse (
  Long id,
  String title,
  String imageUrl,
  String content
) {}
