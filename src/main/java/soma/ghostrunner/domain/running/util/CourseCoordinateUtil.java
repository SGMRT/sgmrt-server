package soma.ghostrunner.domain.running.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import soma.ghostrunner.domain.running.application.dto.CourseCoordinateDto;
import soma.ghostrunner.global.common.error.ErrorCode;
import soma.ghostrunner.global.common.error.exception.ParsingException;

//@Component
// @RequiredArgsConstructor
public class CourseCoordinateUtil {
  private static ObjectMapper objectMapper = new ObjectMapper();

  public static String convertToString(List<CourseCoordinateDto> coordinates) {
    try {
      return objectMapper.writeValueAsString(coordinates);
    } catch (Exception e) {
      throw new ParsingException(ErrorCode.SERVICE_UNAVAILABLE, "시계열에서 위경도 데이터 추출을 실패했습니다.");
    }
  }

  public static List<CourseCoordinateDto> convertToCoordinateList(String jsonListString) {
    try {
      return objectMapper.readValue(jsonListString, objectMapper.getTypeFactory().constructCollectionType(List.class, CourseCoordinateDto.class));
    } catch (Exception e) {
      throw new ParsingException(ErrorCode.SERVICE_UNAVAILABLE, "JSON 문자열을 코스 좌표 목록으로 변환하는 데 실패했습니다.");
    }
  }
}
