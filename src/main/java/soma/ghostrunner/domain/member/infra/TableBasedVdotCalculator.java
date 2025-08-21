package soma.ghostrunner.domain.member.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import soma.ghostrunner.domain.member.domain.VdotCalculator;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TableBasedVdotCalculator implements VdotCalculator {

    private final ObjectMapper objectMapper;
    private List<PaceVdot> paceVdotTable;

    @PostConstruct
    public void init() throws IOException {
        ClassPathResource resource = new ClassPathResource("static/pace-vdot-table.json");
        try (InputStream inputStream = resource.getInputStream()) {
            List<PaceVdotDto> paceData = objectMapper.readValue(inputStream, new TypeReference<>() {});

            this.paceVdotTable = paceData.stream()
                    .map(dto -> {
                        String[] parts = dto.pace().split(":");
                        int seconds = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
                        return new PaceVdot(seconds, dto.vdot());
                    })
                    .sorted(Comparator.comparingInt(PaceVdot::paceInSeconds))
                    .toList();
        }
    }

    public int calculateFromPace(double pace) {

        // 분, 초
        int minute = (int) pace;
        int seconds = (int) (pace * 100 % 100);
        int paceToSec = 60 * minute + seconds;

        // 범위 밖 처리
        if (paceToSec <= paceVdotTable.get(0).paceInSeconds()) {
            return paceVdotTable.get(0).vdot;
        }
        if (paceToSec >= paceVdotTable.get(paceVdotTable.size()-1).paceInSeconds()) {
            return paceVdotTable.get(paceVdotTable.size()-1).vdot;
        }

        // 이분 탐색
        int left = 0, right = paceVdotTable.size() - 1;
        while (left + 1 < right) {
            int mid = (left + right) >>> 1;
            if (paceVdotTable.get(mid).paceInSeconds() == paceToSec) {
                return paceVdotTable.get(mid).vdot;
            } else if (paceVdotTable.get(mid).paceInSeconds() < paceToSec) {
                left = mid;
            } else {
                right = mid;
            }
        }

        return paceVdotTable.get(right).vdot;
    }

    private record PaceVdot(int paceInSeconds, int vdot) {

    }

    private record PaceVdotDto(String pace, int vdot) {

    }

}
