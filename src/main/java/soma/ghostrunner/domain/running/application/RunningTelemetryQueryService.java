package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import soma.ghostrunner.clients.aws.upload.S3TelemetryClient;
import soma.ghostrunner.domain.running.application.dto.CoordinateDto;
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;
import soma.ghostrunner.domain.running.application.support.CoordinateConverter;
import soma.ghostrunner.domain.running.application.support.TelemetryTypeConverter;
import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.error.exception.ExternalIOException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RunningTelemetryQueryService {

    private final S3TelemetryClient s3TelemetryClient;

    public List<TelemetryDto> findTotalTelemetries(Long runningId, String telemetryUrl) {
        List<String> stringTelemetries = downloadTelemetries(runningId, telemetryUrl);
        return TelemetryTypeConverter.convertFromStringToDtos(stringTelemetries);
    }

    public List<CoordinateDto> findCoordinateTelemetries(Long runningId, String telemetryUrl) {
        List<String> stringTelemetries = downloadTelemetries(runningId, telemetryUrl);
        return CoordinateConverter.convertToCoordinateList(stringTelemetries);
    }

    private List<String> downloadTelemetries(Long runningId, String telemetryUrl) {
        try {
            return null;
        } catch (Exception e) {
            log.error("runningId {}의 요청에 대해 S3에서 다운로드를 실패했습니다.", runningId, e);
            throw new ExternalIOException(ErrorCode.SERVICE_UNAVAILABLE, "S3에서 데이터를 조회하는 과정에서 오류가 발생했습니다.");
        }
    }

}
