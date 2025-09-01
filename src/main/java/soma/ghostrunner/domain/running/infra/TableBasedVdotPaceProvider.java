package soma.ghostrunner.domain.running.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import soma.ghostrunner.domain.running.domain.RunningType;
import soma.ghostrunner.domain.running.infra.dto.VdotPaceDto;
import soma.ghostrunner.domain.running.domain.VdotPaceProvider;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class TableBasedVdotPaceProvider implements VdotPaceProvider {

    private final ObjectMapper objectMapper;
    private Map<Integer, List<VdotPaceDto>> cache;

    @PostConstruct
    public void init() {
        try {
            ClassPathResource resource = new ClassPathResource("static/vdot-pace-table.json");
            InputStream inputStream = resource.getInputStream();

            List<VdotPaceRecord> records = objectMapper.readValue(inputStream, new TypeReference<>() {});

            this.cache = records.stream()
                    .collect(Collectors.groupingBy(
                            VdotPaceRecord::getVdot,
                            Collectors.mapping(this::toVdotPace, Collectors.toList())
                    ));

            this.cache = Collections.unmodifiableMap(this.cache);
        } catch (Exception ex) {
            throw new IllegalStateException("VDOT 페이스 테이블 로딩에 실패했습니다.", ex);
        }
    }

    @Override
    public Double getPaceByVdotAndRunningType(int vdot, RunningType runningType) {
        List<VdotPaceDto> vdotPaceDtos = cache.getOrDefault(vdot, Collections.emptyList());

        return vdotPaceDtos.stream()
                .filter(pace -> pace.type().equals(runningType))
                .map(VdotPaceDto::pacePerKm)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "VDOT " + vdot + " 또는 러닝 타입 " + runningType + "에 대한 페이스 정보를 찾을 수 없습니다."
                ));
    }

    @Getter
    private static class VdotPaceRecord {
        private int vdot;
        private String type;
        private String pacePerKm;
    }

    private VdotPaceDto toVdotPace(VdotPaceRecord record) {
        String paceStr = record.getPacePerKm();
        if (paceStr.contains("-")) {
            double middlePace = calculateMiddlePace(paceStr);
            return new VdotPaceDto(RunningType.valueOf(record.getType()), secondsToDouble(middlePace));
        } else {
            return new VdotPaceDto(RunningType.valueOf(record.getType()), stringFormatToDouble(paceStr));
        }
    }

    private double calculateMiddlePace(String paceStr) {
        String[] range = paceStr.split("-");
        BigDecimal startSeconds = BigDecimal.valueOf(paceToSeconds(range[0]));
        BigDecimal endSeconds = BigDecimal.valueOf(paceToSeconds(range[1]));
        BigDecimal totalSeconds = startSeconds.add(endSeconds);
        return totalSeconds.divide(BigDecimal.valueOf(2.0), RoundingMode.CEILING).doubleValue();
    }

    private double paceToSeconds(String pace) {
        String[] parts = pace.trim().split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid pace format: " + pace);
        }
        double minutes = Double.parseDouble(parts[0]);
        double seconds = Double.parseDouble(parts[1]);
        return minutes * 60 + seconds;
    }

    private Double secondsToDouble(double totalSeconds) {
        int minutes = (int) (totalSeconds / 60);
        int seconds = (int) (totalSeconds % 60);
        return Double.parseDouble(String.format("%d.%02d", minutes, seconds));
    }

    private double stringFormatToDouble(String pace) {
        String convertedPace = pace.replace(":", ".");
        return Double.parseDouble(convertedPace);
    }

}
