package soma.ghostrunner.domain.running.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import soma.ghostrunner.domain.running.application.dto.CoordinateDto;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class PathSimplifierTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @DisplayName("포인트가 2개 이하면 원본 반환을 반환한다.")
    @Test
    void returnOriginalWhenSizeLe2() {
        List<CoordinateDto> pts1 = List.of(new CoordinateDto(37.0, 127.0));
        List<CoordinateDto> pts2 = List.of(new CoordinateDto(37.0, 127.0), new CoordinateDto(37.0001, 127.0001));

        assertThat(PathSimplifier.simplify(pts1)).isSameAs(pts1);
        assertThat(PathSimplifier.simplify(pts2)).isSameAs(pts2);
    }

    @DisplayName("data7.jsonl을 List<CoordinateDto>로 변환하고 RDP 알고리즘을 적용한다.")
    @Test
    void simplifyFromData7Jsonl() throws Exception {
        // given
        List<CoordinateDto> original = readCoordinatesFromJsonl("data7.jsonl");

        // when
        List<CoordinateDto> simplified = PathSimplifier.simplify(original);

        // then
        // 포인트 수가 줄었는지(줄지 않을 수도 있으니 안전하게 체크)
        assertThat(simplified.size()).isLessThanOrEqualTo(original.size());

        // 첫/끝점 보존
        assertThat(simplified.get(0)).isEqualTo(original.get(0));
        assertThat(simplified.get(simplified.size() - 1)).isEqualTo(original.get(original.size() - 1));

        System.out.println("원본 개수: " + original.size());
        System.out.println("단순화 후 개수: " + simplified.size());

        // 결과 저장
        writeJsonlToTestResources(simplified, "simplified_result_data.jsonl");
    }

    private List<CoordinateDto> readCoordinatesFromJsonl(String classpathFilename) throws Exception {
        ClassPathResource resource = new ClassPathResource(classpathFilename);
        List<CoordinateDto> list = new ArrayList<>();

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
                JsonNode latNode = node.get("lat");
                JsonNode lngNode = node.get("lng");

                if (latNode == null || lngNode == null) {
                    throw new IllegalArgumentException("JSONL 파싱 실패: line " + lineNo + "에 lat/lng가 없습니다. 내용=" + line);
                }

                double lat = latNode.asDouble();
                double lng = lngNode.asDouble();
                list.add(new CoordinateDto(lat, lng));
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
