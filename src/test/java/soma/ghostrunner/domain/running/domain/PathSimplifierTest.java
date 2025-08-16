package soma.ghostrunner.domain.running.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import soma.ghostrunner.domain.running.application.CoordinateDtoWithTs;
import soma.ghostrunner.domain.running.application.dto.CoordinateDto;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class PathSimplifierTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @DisplayName("포인트가 2개 이하면 원본 반환을 반환한다.")
    @Test
    void returnOriginalWhenSizeLe2() {
        // given
        List<CoordinateDtoWithTs> pts1 = List.of(new CoordinateDtoWithTs(10, 37.0, 127.0));
        List<CoordinateDtoWithTs> pts2 = List.of(
                new CoordinateDtoWithTs(20, 37.0, 127.0),
                new CoordinateDtoWithTs(30, 37.0001, 127.0001)
        );

        // when
        List<CoordinateDto> coordinateDtos1 = PathSimplifier.simplify(pts1);
        List<CoordinateDto> coordinateDtos2 = PathSimplifier.simplify(pts2);

        // then
        assertThat(coordinateDtos1.get(0).lat()).isEqualTo(pts1.get(0).getLat());
        assertThat(coordinateDtos1.get(0).lng()).isEqualTo(pts1.get(0).getLng());

        assertThat(coordinateDtos2.get(0).lat()).isEqualTo(pts2.get(0).getLat());
        assertThat(coordinateDtos2.get(0).lng()).isEqualTo(pts2.get(0).getLng());
        assertThat(coordinateDtos2.get(1).lat()).isEqualTo(pts2.get(1).getLat());
        assertThat(coordinateDtos2.get(1).lng()).isEqualTo(pts2.get(1).getLng());
    }

    @DisplayName("data7.jsonl을 List<CoordinateDto>로 변환하고 RDP 알고리즘을 적용한다. 적용 후 해상도 줄인 데이터는 뛴 순서대로 정렬된다.")
    @Test
    void simplifyFromData7Jsonl() throws Exception {
        // given
        List<CoordinateDtoWithTs> original = readCoordinatesFromJsonl("data7.jsonl");

        // when
        List<CoordinateDto> simplified = PathSimplifier.simplify(original);

        // then
        // 포인트 수가 줄었는지(줄지 않을 수도 있으니 안전하게 체크)
        assertThat(simplified.size()).isLessThanOrEqualTo(original.size());

        // 첫/끝점 보존
        assertThat(simplified.get(0)).isEqualTo(original.get(0).toCoordinateDto());
        assertThat(simplified.get(simplified.size() - 1)).isEqualTo(original.get(original.size() - 1).toCoordinateDto());

        // 순서 검증
        List<Integer> simplifiedOrders = new ArrayList<>();
        for (int i = 0; i < simplified.size(); i++) {
            for (int j = 0; j < original.size(); j++) {
                if (simplified.get(i).lat() == original.get(j).getLat() && simplified.get(i).lng() == original.get(j).getLng()) {
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
        writeJsonlToTestResources(simplified, "simplified_result_data.jsonl");
    }

    private List<CoordinateDtoWithTs> readCoordinatesFromJsonl(String classpathFilename) throws Exception {
        ClassPathResource resource = new ClassPathResource(classpathFilename);
        List<CoordinateDtoWithTs> list = new ArrayList<>();

        try (var is = resource.getInputStream();
             var reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                line = line.trim();
                if (line.isEmpty()) continue;

                JsonNode node = MAPPER.readTree(line);

                // 기본 필드명: lat, lng
                JsonNode tsNode = node.get("timeStamp");
                JsonNode latNode = node.get("lat");
                JsonNode lngNode = node.get("lng");

                if (tsNode == null || latNode == null || lngNode == null) {
                    throw new IllegalArgumentException("JSONL 파싱 실패: line " + lineNo + "에 lat/lng가 없습니다. 내용=" + line);
                }

                long ts = tsNode.asLong();
                double lat = latNode.asDouble();
                double lng = lngNode.asDouble();
                list.add(new CoordinateDtoWithTs(ts, lat, lng));
            }
        }
        return List.copyOf(list);
    }

    private void writeJsonlToTestResources(List<CoordinateDto> coords, String filename) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        // 실제 소스 디렉토리 경로를 명시
        File outputFile = new File("src/test/resources/" + filename);

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            for (CoordinateDto c : coords) {
                String json = mapper.writeValueAsString(c);
                fos.write(json.getBytes(StandardCharsets.UTF_8));
                fos.write('\n');
            }
        }
        System.out.println("Saved to: " + outputFile.getAbsolutePath());
    }

}
