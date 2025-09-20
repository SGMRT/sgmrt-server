package soma.ghostrunner.domain.running.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import soma.ghostrunner.domain.running.domain.path.CoordinatesWithTs;
import soma.ghostrunner.domain.running.domain.path.Coordinates;
import soma.ghostrunner.domain.running.domain.path.PathSimplifier;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PathSimplifierTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @DisplayName("포인트가 2개 이하면 원본 반환을 반환한다.")
    @Test
    void returnOriginalWhenSizeLe2() {
        // given
        List<CoordinatesWithTs> pts1 = List.of(new CoordinatesWithTs(10, 37.0, 127.0));
        List<CoordinatesWithTs> pts2 = List.of(
                new CoordinatesWithTs(20, 37.0, 127.0),
                new CoordinatesWithTs(30, 37.0001, 127.0001)
        );

        // when
        List<Coordinates> coordinateDtos1 = PathSimplifier.extractEdgePoints(pts1);
        List<Coordinates> coordinateDtos2 = PathSimplifier.extractEdgePoints(pts2);

        // then
        assertThat(coordinateDtos1.get(0).y()).isEqualTo(pts1.get(0).getY());
        assertThat(coordinateDtos1.get(0).x()).isEqualTo(pts1.get(0).getX());

        assertThat(coordinateDtos2.get(0).y()).isEqualTo(pts2.get(0).getY());
        assertThat(coordinateDtos2.get(0).x()).isEqualTo(pts2.get(0).getX());
        assertThat(coordinateDtos2.get(1).y()).isEqualTo(pts2.get(1).getY());
        assertThat(coordinateDtos2.get(1).x()).isEqualTo(pts2.get(1).getX());
    }

    @DisplayName("data7.jsonl을 List<CoordinateDto>로 변환하고 RDP 알고리즘을 적용한다. 적용 후 해상도 줄인 데이터는 뛴 순서대로 정렬된다.")
    @Test
    void extractEdgePointsFromData7Jsonl() throws Exception {
        // given
        List<CoordinatesWithTs> original = readCoordinatesFromJsonl("data7.jsonl");

        // when
        List<Coordinates> simplified = PathSimplifier.extractEdgePoints(original);

        // then
        assertThat(simplified.size()).isLessThanOrEqualTo(original.size());

        // 첫/끝점 보존
        assertThat(simplified.get(0)).isEqualTo(original.get(0).toCoordinates());
        assertThat(simplified.get(simplified.size() - 1)).isEqualTo(original.get(original.size() - 1).toCoordinates());

        // 순서 검증
        List<Integer> simplifiedOrders = new ArrayList<>();
        for (int i = 0; i < simplified.size(); i++) {
            for (int j = 0; j < original.size(); j++) {
                if (simplified.get(i).y() == original.get(j).getY() && simplified.get(i).x() == original.get(j).getX()) {
                    simplifiedOrders.add(j);
                }
            }
        }
        for (int i = 0; i < simplifiedOrders.size() - 1; i++) {
            assertThat(simplifiedOrders.get(i)).isLessThanOrEqualTo(simplifiedOrders.get(i + 1));
        }

        // 결과 저장
        System.out.println("원본 개수: " + original.size());
        System.out.println("단순화 후 개수: " + simplified.size());
        writeJsonlToTestResources(simplified, "simplified_rdp_data.jsonl");
    }

    private List<CoordinatesWithTs> readCoordinatesFromJsonl(String classpathFilename) throws Exception {
        ClassPathResource resource = new ClassPathResource(classpathFilename);
        List<CoordinatesWithTs> list = new ArrayList<>();

        try (var is = resource.getInputStream();
             var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                line = line.trim();
                if (line.isEmpty()) continue;

                JsonNode node = MAPPER.readTree(line);

                // 기본 필드명: y, x
                JsonNode tsNode = node.get("t");
                JsonNode latNode = node.get("y");
                JsonNode lngNode = node.get("x");

                if (tsNode == null || latNode == null || lngNode == null) {
                    throw new IllegalArgumentException("JSONL 파싱 실패: line " + lineNo + "에 y/lng가 없습니다. 내용=" + line);
                }

                long ts = tsNode.asLong();
                double lat = latNode.asDouble();
                double lng = lngNode.asDouble();
                list.add(new CoordinatesWithTs(ts, lat, lng));
            }
        }
        return List.copyOf(list);
    }

    private void writeJsonlToTestResources(List<Coordinates> coords, String filename) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        // 실제 소스 디렉토리 경로를 명시
        File outputFile = new File("src/test/resources/" + filename);

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            for (Coordinates c : coords) {
                String json = mapper.writeValueAsString(c);
                fos.write(json.getBytes(StandardCharsets.UTF_8));
                fos.write('\n');
            }
        }
        System.out.println("Saved to: " + outputFile.getAbsolutePath());
    }

    @Test
    @DisplayName("빈 입력이면 빈 리스트를 반환한다")
    void resample_emptyInput_returnsEmpty() {
        List<CoordinatesWithTs> in = List.of();
        List<Coordinates> out = PathSimplifier.simplifyToRenderingTelemetries(in);
        assertThat(out).isEmpty();
    }

    @DisplayName("data7.jsonl을 List<CoordinateDto>로 변환하고 RDP 알고리즘을 적용한다. 적용 후 해상도 줄인 데이터는 뛴 순서대로 정렬된다.")
    @Test
    void simplifyToRenderingTelemetriesFromData7Jsonl() throws Exception {
        // given
        List<CoordinatesWithTs> original = readCoordinatesFromJsonl("data7.jsonl");

        // when
        List<Coordinates> simplified = PathSimplifier.simplifyToRenderingTelemetries(original);

        // then
        assertThat(simplified.size()).isLessThanOrEqualTo(original.size());
        System.out.println("원본 개수: " + original.size());
        System.out.println("단순화 후 개수: " + simplified.size());
        writeJsonlToTestResources(simplified, "simplified_vw_data.jsonl");
    }

}
