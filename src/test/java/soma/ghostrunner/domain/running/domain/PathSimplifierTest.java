package soma.ghostrunner.domain.running.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import soma.ghostrunner.domain.running.application.dto.CoordinateDtoWithTs;
import soma.ghostrunner.domain.running.application.dto.CoordinateDto;
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
        List<CoordinateDtoWithTs> pts1 = List.of(new CoordinateDtoWithTs(10, 37.0, 127.0));
        List<CoordinateDtoWithTs> pts2 = List.of(
                new CoordinateDtoWithTs(20, 37.0, 127.0),
                new CoordinateDtoWithTs(30, 37.0001, 127.0001)
        );

        // when
        List<CoordinateDto> coordinateDtos1 = PathSimplifier.extractEdgePoints(pts1);
        List<CoordinateDto> coordinateDtos2 = PathSimplifier.extractEdgePoints(pts2);

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
    void extractEdgePointsFromData7Jsonl() throws Exception {
        // given
        List<CoordinateDtoWithTs> original = readCoordinatesFromJsonl("data7.jsonl");

        // when
        List<CoordinateDto> simplified = PathSimplifier.extractEdgePoints(original);

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

    @Test
    @DisplayName("빈 입력이면 빈 리스트를 반환한다")
    void resample_emptyInput_returnsEmpty() {
        List<CoordinateDtoWithTs> in = List.of();
        List<CoordinateDto> out = PathSimplifier.simplifyToRenderingTelemetries(in);
        assertThat(out).isEmpty();
    }

    @Test
    @DisplayName("3개씩 묶어 평균 후 반환한다 (불완전 꼬리(2개 이하는) 버림)")
    void resample_averageByTriplets_dropsTail() {
        // (lat, lng) = (0,0), (3,3), (6,6) → 평균 (2,2)
        List<CoordinateDtoWithTs> in = new ArrayList<>();
        in.add(pt(0.0, 0.0, 1000));
        in.add(pt(3.0, 3.0, 2000));
        in.add(pt(6.0, 6.0, 3000));
        // 꼬리(두 점) — 평균 대상 아님 → 버려짐
        in.add(pt(9.0, 9.0, 4000));
        in.add(pt(12.0, 12.0, 5000));

        List<CoordinateDto> out = PathSimplifier.simplifyToRenderingTelemetries(in);

        assertEquals(1, out.size(), "3개 묶음 1개만 평균되어야 함");
        assertLatLngAlmostEquals(3.0, 3.0, out.get(0), 1e-9);
    }

    @Test
    @DisplayName("3m 미만 이동은 필터링되어 유지되지 않는다")
    void resample_filtersBelow3m() {
        // 위도 1도 ≈ 111,132 m → 0.00002도 ≈ 2.22 m (3m 미만)
        // 3개 평균 후 나온 점들 간 거리가 3m 미만이면 제외되어 결과는 1개만 남아야 한다.
        List<CoordinateDtoWithTs> in = new ArrayList<>();
        // 첫 묶음 평균 ~ (37.000010, 127.000010)
        in.add(pt(37.000000, 127.000000, 1000));
        in.add(pt(37.000015, 127.000015, 2000));
        in.add(pt(37.000015, 127.000015, 3000));
        // 둘째 묶음 평균 ~ (37.000030, 127.000030) → 첫 평균과의 차 ~ 0.00002도*? ≈ 2m대
        in.add(pt(37.000030, 127.000030, 4000));
        in.add(pt(37.000030, 127.000030, 5000));
        in.add(pt(37.000030, 127.000030, 6000));

        List<CoordinateDto> out = PathSimplifier.simplifyToRenderingTelemetries(in);

        // 3m 미만이라면 두 번째 평균점은 필터됨 → 1개만 남음
        assertEquals(1, out.size(), "3m 미만 이동은 필터링되어야 함");
    }

    @Test
    @DisplayName("3m 이상 이동은 보존된다")
    void resample_keepsPointsWhenAtLeast3m() {
        // 위도 차이 0.000030도 ≈ 3.33 m → 3m 이상
        List<CoordinateDtoWithTs> in = new ArrayList<>();
        // 첫 평균 ~ (37.000000, 127.000000)
        in.add(pt(37.000000, 127.000000, 1000));
        in.add(pt(37.000000, 127.000000, 2000));
        in.add(pt(37.000000, 127.000000, 3000));
        // 둘째 평균 ~ (37.000030, 127.000000) → 위도만 +0.000030
        in.add(pt(37.000030, 127.000000, 4000));
        in.add(pt(37.000030, 127.000000, 5000));
        in.add(pt(37.000030, 127.000000, 6000));

        List<CoordinateDto> out = PathSimplifier.simplifyToRenderingTelemetries(in);

        assertEquals(2, out.size(), "3m 이상 이동한 평균점은 남아야 함");
        assertLatLngAlmostEquals(37.000000, 127.000000, out.get(0), 1e-9);
        assertLatLngAlmostEquals(37.000030, 127.000000, out.get(1), 1e-9);
    }

    // ---- helpers ----
    private static CoordinateDtoWithTs pt(double lat, double lng, long ts) {
        return new CoordinateDtoWithTs(ts, lat, lng);
    }

    private static void assertLatLngAlmostEquals(double expLat, double expLng, CoordinateDto actual, double eps) {
        assertEquals(expLat, actual.lat(), eps, "lat mismatch");
        assertEquals(expLng, actual.lng(), eps, "lng mismatch");
    }

}
