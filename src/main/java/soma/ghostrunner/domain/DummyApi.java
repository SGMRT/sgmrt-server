package soma.ghostrunner.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import soma.ghostrunner.domain.course.dao.CourseRepository;
import soma.ghostrunner.domain.course.domain.Coordinate;
import soma.ghostrunner.domain.course.domain.Course;
import soma.ghostrunner.domain.course.domain.CourseDataUrls;
import soma.ghostrunner.domain.course.domain.CourseProfile;
import soma.ghostrunner.domain.course.enums.CourseSource;
import soma.ghostrunner.domain.running.domain.path.*;
import soma.ghostrunner.domain.running.exception.TelemetryCalculationException;
import soma.ghostrunner.global.error.ErrorCode;
import soma.ghostrunner.global.security.jwt.JwtUserDetails;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class DummyApi {

    private final CourseRepository courseRepository;
    private final RunningFileUploader runningFileUploader;
    private final ObjectMapper objectMapper;

    @PostMapping("/v1/dummy")
    public Long createDummy(
            @AuthenticationPrincipal JwtUserDetails userDetails,
            @RequestPart("req") @Valid CreateDummyRequest req,
            @RequestPart MultipartFile interpolatedTelemetry) {

        // 1. List<Telemetry> 변환
        List<Telemetry> telemetries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(interpolatedTelemetry.getInputStream()))) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                // 읽기
                Telemetry telemetry = objectMapper.readValue(line, Telemetry.class);
                telemetries.add(telemetry);
            }
        } catch (IOException exception) {
            throw new TelemetryCalculationException(ErrorCode.SERVICE_UNAVAILABLE, "시계열 좌표를 가공하는 중 에러가 발생했습니다.");
        }

        // 2. List<Telemetry> -> List<CoordinatesWithTs>
        List<CoordinateWithTs> telemetryCoordinates = new ArrayList<>();
        for (int i = 0; i < telemetries.size(); i++) {
            CoordinateWithTs coordinateWithTs = new CoordinateWithTs(i, telemetries.get(i).getY(), telemetries.get(i).getX());
            telemetryCoordinates.add(coordinateWithTs);
            telemetries.get(i).setT((long) i);
        }

        // 3. 체크포인트 추출
        List<Coordinates> edgePoints = PathSimplifier.extractEdgePoints(telemetryCoordinates);
        List<Checkpoint> checkpoints = PathSimplifier.calculateAngles(edgePoints);

        // 2. 렌더링 + 체크포인트 시계열 S3 업로드
        String routeUrl = runningFileUploader.uploadInterpolatedTelemetry(telemetries, "OFFICIAL");
        String checkpointUrl = runningFileUploader.uploadCheckpoints(checkpoints, "OFFICIAL");

        // 3. DB 저장
        CourseProfile courseProfile = CourseProfile.of(req.distance, req.elevationLoss + req.elevationGain,
                req.elevationGain, req.elevationLoss);
        Coordinate startCoordinate = Coordinate.of(telemetryCoordinates.get(0).getY(), telemetryCoordinates.get(0).getX());
        CourseDataUrls courseDataUrls = CourseDataUrls.of(routeUrl, checkpointUrl, null);
        Course course = Course.of(req.name, courseProfile, startCoordinate, CourseSource.OFFICIAL, true, courseDataUrls);
        Course savedCourse = courseRepository.save(course);
        return savedCourse.getId();
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CreateDummyRequest {

        @NotBlank
        private String name;

        @NotNull
        @Positive
        private Double distance;    // km

        @NotNull @PositiveOrZero
        private Double elevationGain;      // m, 고도

        @NotNull @NegativeOrZero
        private Double elevationLoss;

    }


}
