package soma.ghostrunner.domain.running.domain.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;
import soma.ghostrunner.global.common.error.ErrorCode;
import soma.ghostrunner.global.common.error.exception.ParsingException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class TelemetryTypeConverter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String convertFromObjectsToString(List<TelemetryDto> telemetries) {
        try {
            return telemetries.stream()
                    .map(TelemetryTypeConverter::convertToJson)
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.error("시계열 객체 -> JSON 변환을 실패했습니다.", e);
            throw new ParsingException(ErrorCode.SERVICE_UNAVAILABLE, "저장소에 업로드를 위해 객체에서 JSON으로 변환하는 중 오류가 발생했습니다.");
        }
    }

    private static String convertToJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            log.error("객체 -> JSON 변환 실패: {}", object, e);
            throw new ParsingException(ErrorCode.SERVICE_UNAVAILABLE, "S3에 업로드를 위해 객체에서 JSON으로 변환하는 중 오류가 발생했습니다.");
        }
    }

    public static List<TelemetryDto> convertFromStringToDtos(List<String> stringTelemetries) {
        try {
            List<TelemetryDto> result = new ArrayList<>();
            for (String stringTelemetry : stringTelemetries) {
                result.add(objectMapper.readValue(stringTelemetry, TelemetryDto.class));
            }
            return result;
        } catch (Exception e) {
            log.error("시계열 JSON -> 객체 변환을 실패했습니다.", e);
            throw new ParsingException(ErrorCode.SERVICE_UNAVAILABLE, "저장소에서 다운 받은 JSON을 객체로 변환하는 중 오류가 발생했습니다.");
        }
    }

}
