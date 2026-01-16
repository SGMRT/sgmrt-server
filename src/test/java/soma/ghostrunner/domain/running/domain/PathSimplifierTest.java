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

    private void writeHtmlVisualization(List<Coordinates> original, List<Coordinates> simplified, String filename) throws Exception {
        File outputFile = new File("src/test/resources/" + filename);

        // 중심점 계산 (지도 초기 위치)
        double centerLat = original.stream().mapToDouble(Coordinates::y).average().orElse(37.5);
        double centerLng = original.stream().mapToDouble(Coordinates::x).average().orElse(127.0);

        // HTML 생성
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"utf-8\">\n");
        html.append("    <title>VW Algorithm Visualization</title>\n");
        html.append("    <link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.css\"/>\n");
        html.append("    <script src=\"https://unpkg.com/leaflet@1.9.4/dist/leaflet.js\"></script>\n");
        html.append("    <style>\n");
        html.append("        body { margin: 0; padding: 0; font-family: Arial, sans-serif; }\n");
        html.append("        #map { width: 100%; height: 100vh; }\n");
        html.append("        .info-panel {\n");
        html.append("            position: absolute; top: 10px; right: 10px; z-index: 1000;\n");
        html.append("            background: white; padding: 15px; border-radius: 5px;\n");
        html.append("            box-shadow: 0 2px 6px rgba(0,0,0,0.3);\n");
        html.append("        }\n");
        html.append("        .info-panel h3 { margin: 0 0 10px 0; }\n");
        html.append("        .legend { margin: 5px 0; }\n");
        html.append("        .legend-item { display: flex; align-items: center; margin: 5px 0; }\n");
        html.append("        .legend-color { width: 30px; height: 3px; margin-right: 10px; }\n");
        html.append("        .legend-marker { width: 10px; height: 10px; border-radius: 50%; margin-right: 10px; }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"info-panel\">\n");
        html.append("        <h3>VW Algorithm Result</h3>\n");
        html.append("        <div><strong>Original:</strong> ").append(original.size()).append(" points</div>\n");
        html.append("        <div><strong>Simplified:</strong> ").append(simplified.size()).append(" points</div>\n");
        html.append("        <div><strong>Compression:</strong> ").append(String.format("%.2f%%", (1.0 - (double) simplified.size() / original.size()) * 100)).append("</div>\n");
        html.append("        <div class=\"legend\">\n");
        html.append("            <div class=\"legend-item\"><div class=\"legend-color\" style=\"background: #ff0000;\"></div>Original Path</div>\n");
        html.append("            <div class=\"legend-item\"><div class=\"legend-color\" style=\"background: #0066ff;\"></div>Simplified Path</div>\n");
        html.append("            <div class=\"legend-item\"><div class=\"legend-marker\" style=\"background: #0066ff;\"></div>Simplified Points</div>\n");
        html.append("        </div>\n");
        html.append("    </div>\n");
        html.append("    <div id=\"map\"></div>\n");
        html.append("    <script>\n");
        html.append("        var map = L.map('map').setView([").append(centerLat).append(", ").append(centerLng).append("], 15);\n");
        html.append("        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {\n");
        html.append("            attribution: '© OpenStreetMap contributors'\n");
        html.append("        }).addTo(map);\n\n");

        // 원본 경로 (빨간색 선)
        html.append("        // Original path\n");
        html.append("        var originalPath = [\n");
        for (int i = 0; i < original.size(); i++) {
            Coordinates c = original.get(i);
            html.append("            [").append(c.y()).append(", ").append(c.x()).append("]");
            if (i < original.size() - 1) html.append(",");
            html.append("\n");
        }
        html.append("        ];\n");
        html.append("        L.polyline(originalPath, {color: 'red', weight: 2, opacity: 0.5}).addTo(map);\n\n");

        // 단순화된 경로 (파란색 선)
        html.append("        // Simplified path\n");
        html.append("        var simplifiedPath = [\n");
        for (int i = 0; i < simplified.size(); i++) {
            Coordinates c = simplified.get(i);
            html.append("            [").append(c.y()).append(", ").append(c.x()).append("]");
            if (i < simplified.size() - 1) html.append(",");
            html.append("\n");
        }
        html.append("        ];\n");
        html.append("        L.polyline(simplifiedPath, {color: '#0066ff', weight: 3, opacity: 0.8}).addTo(map);\n\n");

        // 단순화된 점들 (파란색 마커)
        html.append("        // Simplified points\n");
        for (int i = 0; i < simplified.size(); i++) {
            Coordinates c = simplified.get(i);
            String label = (i == 0) ? "Start" : (i == simplified.size() - 1) ? "End" : "Point " + (i + 1);
            html.append("        L.circleMarker([").append(c.y()).append(", ").append(c.x()).append("], {\n");
            html.append("            radius: 5, color: '#0066ff', fillColor: '#0066ff',\n");
            html.append("            fillOpacity: 0.8, weight: 2\n");
            html.append("        }).bindPopup('").append(label).append("<br>Lat: ").append(String.format("%.6f", c.y()))
                    .append("<br>Lng: ").append(String.format("%.6f", c.x())).append("').addTo(map);\n");
        }

        // 지도 범위 자동 조정
        html.append("\n        // Fit bounds\n");
        html.append("        var bounds = L.latLngBounds(originalPath);\n");
        html.append("        map.fitBounds(bounds, {padding: [50, 50]});\n");

        html.append("    </script>\n");
        html.append("</body>\n");
        html.append("</html>\n");

        // 파일 쓰기
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(html.toString().getBytes(StandardCharsets.UTF_8));
        }
        System.out.println("HTML visualization saved to: " + outputFile.getAbsolutePath());
    }

    @Test
    @DisplayName("VW - 빈 입력이면 빈 리스트를 반환한다")
    void vw_emptyInput_returnsEmpty() {
        List<CoordinatesWithTs> in = List.of();
        List<Coordinates> out = PathSimplifier.simplifyToRenderingTelemetries(in);
        assertThat(out).isEmpty();
    }

    @Test
    @DisplayName("VW - 점이 1개면 원본을 그대로 반환한다")
    void vw_singlePoint_returnsOriginal() {
        // given
        List<CoordinatesWithTs> points = List.of(
                new CoordinatesWithTs(100L, 37.5, 127.0)
        );

        // when
        List<Coordinates> result = PathSimplifier.simplifyToRenderingTelemetries(points);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).y()).isEqualTo(37.5);
        assertThat(result.get(0).x()).isEqualTo(127.0);
    }

    @Test
    @DisplayName("VW - 점이 2개면 원본을 그대로 반환한다")
    void vw_twoPoints_returnsOriginal() {
        // given
        List<CoordinatesWithTs> points = List.of(
                new CoordinatesWithTs(100L, 37.5, 127.0),
                new CoordinatesWithTs(200L, 37.51, 127.01)
        );

        // when
        List<Coordinates> result = PathSimplifier.simplifyToRenderingTelemetries(points);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).y()).isEqualTo(37.5);
        assertThat(result.get(1).y()).isEqualTo(37.51);
    }

    @Test
    @DisplayName("VW - 점이 3개면 삼각형 면적에 따라 처리한다")
    void vw_threePoints_processesCorrectly() {
        // given - 거의 일직선에 가까운 3개 점 (중간 점이 제거될 수 있음)
        List<CoordinatesWithTs> points = List.of(
                new CoordinatesWithTs(100L, 37.5, 127.0),
                new CoordinatesWithTs(200L, 37.500001, 127.0001),  // 거의 일직선
                new CoordinatesWithTs(300L, 37.501, 127.002)
        );

        // when
        List<Coordinates> result = PathSimplifier.simplifyToRenderingTelemetries(points);

        // then
        assertThat(result.size()).isLessThanOrEqualTo(3);
        // 첫 점과 마지막 점은 항상 보존
        assertThat(result.get(0).y()).isEqualTo(37.5);
        assertThat(result.get(result.size() - 1).y()).isEqualTo(37.501);
    }

    @Test
    @DisplayName("VW - 모든 점이 일직선상에 있으면 중간 점들이 제거된다")
    void vw_collinearPoints_removesMiddlePoints() {
        // given - 완전히 일직선상에 있는 5개 점
        List<CoordinatesWithTs> points = List.of(
                new CoordinatesWithTs(100L, 37.5, 127.0),
                new CoordinatesWithTs(200L, 37.5001, 127.001),
                new CoordinatesWithTs(300L, 37.5002, 127.002),
                new CoordinatesWithTs(400L, 37.5003, 127.003),
                new CoordinatesWithTs(500L, 37.5004, 127.004)
        );

        // when
        List<Coordinates> result = PathSimplifier.simplifyToRenderingTelemetries(points);

        // then
        // 일직선이므로 중간 점들이 제거되고 첫/끝 점만 남을 수 있음
        assertThat(result.size()).isLessThan(points.size());
        // 첫 점과 마지막 점은 반드시 보존
        assertThat(result.get(0).y()).isEqualTo(37.5);
        assertThat(result.get(result.size() - 1).y()).isEqualTo(37.5004);
    }

    @Test
    @DisplayName("VW - 모든 삼각형의 면적이 크면 모든 점이 유지된다")
    void vw_largeTriangles_keepAllPoints() {
        // given - 큰 삼각형을 만드는 지그재그 패턴
        List<CoordinatesWithTs> points = List.of(
                new CoordinatesWithTs(100L, 37.5, 127.0),
                new CoordinatesWithTs(200L, 37.6, 127.1),   // 큰 편차
                new CoordinatesWithTs(300L, 37.5, 127.2),   // 큰 편차
                new CoordinatesWithTs(400L, 37.6, 127.3),   // 큰 편차
                new CoordinatesWithTs(500L, 37.5, 127.4)
        );

        // when
        List<Coordinates> result = PathSimplifier.simplifyToRenderingTelemetries(points);

        // then
        // 모든 삼각형의 면적이 임계치보다 크면 모든 점이 유지되어야 함
        assertThat(result.size()).isEqualTo(points.size());
    }

    @Test
    @DisplayName("VW - 첫 점과 마지막 점은 항상 보존된다")
    void vw_alwaysPreservesFirstAndLastPoints() {
        // given
        List<CoordinatesWithTs> points = List.of(
                new CoordinatesWithTs(100L, 37.5, 127.0),
                new CoordinatesWithTs(200L, 37.500001, 127.0001),
                new CoordinatesWithTs(300L, 37.500002, 127.0002),
                new CoordinatesWithTs(400L, 37.500003, 127.0003),
                new CoordinatesWithTs(500L, 37.501, 127.01)
        );

        // when
        List<Coordinates> result = PathSimplifier.simplifyToRenderingTelemetries(points);

        // then
        assertThat(result).isNotEmpty();
        assertThat(result.get(0).y()).isEqualTo(37.5);
        assertThat(result.get(0).x()).isEqualTo(127.0);
        assertThat(result.get(result.size() - 1).y()).isEqualTo(37.501);
        assertThat(result.get(result.size() - 1).x()).isEqualTo(127.01);
    }

    @Test
    @DisplayName("VW - 타임스탬프 순서가 보존된다")
    void vw_preservesTimestampOrder() {
        // given
        List<CoordinatesWithTs> points = List.of(
                new CoordinatesWithTs(100L, 37.5, 127.0),
                new CoordinatesWithTs(200L, 37.51, 127.01),
                new CoordinatesWithTs(300L, 37.52, 127.02),
                new CoordinatesWithTs(400L, 37.53, 127.03),
                new CoordinatesWithTs(500L, 37.54, 127.04)
        );

        // when
        List<Coordinates> result = PathSimplifier.simplifyToRenderingTelemetries(points);

        // then
        // 결과가 원본의 순서를 유지하는지 확인
        for (int i = 0; i < result.size() - 1; i++) {
            int idx1 = findIndexInOriginal(result.get(i), points);
            int idx2 = findIndexInOriginal(result.get(i + 1), points);
            assertThat(idx1).isLessThan(idx2);
        }
    }

    private int findIndexInOriginal(Coordinates coord, List<CoordinatesWithTs> original) {
        for (int i = 0; i < original.size(); i++) {
            if (Math.abs(original.get(i).getY() - coord.y()) < 1e-9 &&
                Math.abs(original.get(i).getX() - coord.x()) < 1e-9) {
                return i;
            }
        }
        return -1;
    }

    @DisplayName("VW - data7.jsonl을 List<CoordinateDto>로 변환하고 VW 알고리즘을 적용한다. 적용 후 해상도 줄인 데이터는 뛴 순서대로 정렬된다.")
    @Test
    void simplifyToRenderingTelemetriesFromData7Jsonl() throws Exception {
        // given
        List<CoordinatesWithTs> original = readCoordinatesFromJsonl("data7.jsonl");

        // when
        List<Coordinates> simplified = PathSimplifier.simplifyToRenderingTelemetries(original);

        // then
        assertThat(simplified.size()).isLessThanOrEqualTo(original.size());
        
        // 첫/끝점 보존 확인
        assertThat(simplified.get(0).y()).isEqualTo(original.get(0).getY());
        assertThat(simplified.get(0).x()).isEqualTo(original.get(0).getX());
        assertThat(simplified.get(simplified.size() - 1).y()).isEqualTo(original.get(original.size() - 1).getY());
        assertThat(simplified.get(simplified.size() - 1).x()).isEqualTo(original.get(original.size() - 1).getX());
        
        System.out.println("원본 개수: " + original.size());
        System.out.println("단순화 후 개수: " + simplified.size());
        System.out.println("압축률: " + String.format("%.2f%%", (1.0 - (double) simplified.size() / original.size()) * 100));
        
        // 결과 저장
        writeJsonlToTestResources(simplified, "simplified_vw_data.jsonl");
        
        // HTML 시각화 생성
        List<Coordinates> originalCoords = original.stream()
                .map(CoordinatesWithTs::toCoordinates)
                .toList();
        writeHtmlVisualization(originalCoords, simplified, "vw_visualization.html");
    }

}
