package soma.ghostrunner.domain.running.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import soma.ghostrunner.clients.aws.TelemetryClient;
import soma.ghostrunner.domain.course.application.CourseService;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.running.application.dto.CourseCoordinateDto;
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;
import soma.ghostrunner.domain.running.application.dto.response.RunDetailInfo;
import soma.ghostrunner.domain.running.dao.RunningRepository;
import soma.ghostrunner.domain.running.domain.Running;
import soma.ghostrunner.domain.running.domain.support.CoordinateConverter;
import soma.ghostrunner.domain.running.domain.support.TelemetryTypeConverter;
import soma.ghostrunner.domain.running.exception.RunningNotFoundException;
import soma.ghostrunner.global.common.error.ErrorCode;
import soma.ghostrunner.global.common.error.exception.ExternalIOException;
import soma.ghostrunner.global.common.error.exception.ParsingException;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RunningTelemetryQueryService {

    private final CourseService courseService;
    private final RunningRepository runningRepository;

    private final TelemetryClient telemetryClient;

    @Transactional(readOnly = true)
    public List<TelemetryDto> findTotalTelemetries(Long runningId) {
        String telemetryUrl = findTelemetryUrlByRunningId(runningId);
        return downloadTelemetries(runningId, telemetryUrl);
    }

    private String findTelemetryUrlByRunningId(Long runningId) {
        return runningRepository.findTelemetryUrlById(runningId)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, runningId));
    }

    public List<TelemetryDto> downloadTelemetries(Long runningId, String telemetryUrl) {
        try {
            List<String> stringTelemetries = telemetryClient.downloadTelemetryFromUrl(telemetryUrl);
            return TelemetryTypeConverter.convertFromStringToDtos(stringTelemetries);
        } catch (Exception e) {
            log.error("runningId {}의 요청에 대해 S3에서 다운로드를 실패했습니다.", runningId, e);
            throw new ExternalIOException(ErrorCode.SERVICE_UNAVAILABLE, "S3에서 데이터를 조회하는 과정에서 오류가 발생했습니다.");
        }
    }

    @Transactional(readOnly = true)
    public List<CourseCoordinateDto> findCoordinateTelemetries(Long courseId) {
        Course course = findCourseByCourseId(courseId);
        Running running = findFirstRunning(course.getId());

        List<String> stringTelemetries = telemetryClient.downloadTelemetryFromUrl(running.getTelemetryUrl());
        return CoordinateConverter.convertToCoordinateList(stringTelemetries);
    }

    private Course findCourseByCourseId(Long courseId) {
        return courseService.findCourseById(courseId);
    }

    private Running findFirstRunning(Long courseId) {
        return runningRepository.findFirstRunningByCourseId(courseId)
                .orElseThrow(() -> new RunningNotFoundException(ErrorCode.ENTITY_NOT_FOUND, "코스 ID : " + courseId + "를 갖는 러닝이 없습니다."));
    }

}
