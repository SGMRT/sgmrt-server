package soma.ghostrunner.domain.running.domain.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import soma.ghostrunner.domain.running.application.dto.CoordinateDto;
import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.error.exception.ParsingException;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class CoordinateConverter {

    private static ObjectMapper objectMapper = new ObjectMapper();

    private static final int LATITUDE_PART_INDEX = 1;
    private static final int LONGITUDE_PART_INDEX = 2;
    private static final int VALUE_INDEX = 1;
    private static final String MAIN_DELIMITER = ",";
    private static final String VALUE_DELIMITER = ":";

    public static String convertToString(List<CoordinateDto> coordinates) {
        try {
            return objectMapper.writeValueAsString(coordinates);
        } catch (Exception e) {
            throw new ParsingException(ErrorCode.SERVICE_UNAVAILABLE, "시계열에서 위경도 데이터 추출을 실패했습니다.");
        }
    }

    public static List<CoordinateDto> convertToCoordinateList(String jsonListString) {
        try {
            return objectMapper.readValue(jsonListString, objectMapper.getTypeFactory().constructCollectionType(List.class, CoordinateDto.class));
        } catch (Exception e) {
            throw new ParsingException(ErrorCode.SERVICE_UNAVAILABLE, "JSON 문자열을 코스 좌표 목록으로 변환하는 데 실패했습니다.");
        }
    }

    public static List<CoordinateDto> convertToCoordinateList(List<String> coordinateStringList) {
        if (coordinateStringList == null) {
            return Collections.emptyList();
        }
        return coordinateStringList.stream()
                .map(CoordinateConverter::parseStringToCoordinateDto)
                .collect(Collectors.toList());
    }

    private static CoordinateDto parseStringToCoordinateDto(String coordinateString) {
        String[] parts = coordinateString.split(MAIN_DELIMITER);

        String latitudePart = parts[LATITUDE_PART_INDEX];
        String longitudePart = parts[LONGITUDE_PART_INDEX];

        double latitude = Double.parseDouble(latitudePart.split(VALUE_DELIMITER)[VALUE_INDEX]);
        double longitude = Double.parseDouble(longitudePart.split(VALUE_DELIMITER)[VALUE_INDEX]);

        return new CoordinateDto(latitude, longitude);
    }

}
