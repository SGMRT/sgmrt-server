package soma.ghostrunner.domain.running.domain.path;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.locationtech.proj4j.*;

import java.util.*;

@UtilityClass
public class PathSimplifier {

    private final CRSFactory crsFactory = new CRSFactory();
    private final CoordinateTransformFactory ctFactory = new CoordinateTransformFactory();
    private final CoordinateReferenceSystem WGS84 = crsFactory.createFromName("epsg:4326");

    private static final double RDP_EPSILON_METER = 8.0;
    private static final double VW_EPSILON_AREA = 4.0;

    // 체크포인트 추출 : UTM 변환 -> RDP 알고리즘
    public List<Coordinates> extractEdgePoints(List<CoordinatesWithTs> points) {
        if (points.size() <= 2) {
            return toCoordinates(points);
        }

        List<CoordinatesWithTs> utmPoints = toUtm(points);
        List<CoordinateWithTsForRdp> rdpUtmPoints = rdp(CoordinateWithTsForRdp.toList(utmPoints));

        List<CoordinatesWithTs> out = new ArrayList<>();
        for (CoordinateWithTsForRdp utmPoint : rdpUtmPoints) {
            out.add(points.get(utmPoint.idx));
        }

        out.sort(Comparator.naturalOrder());
        return toCoordinates(out);
    }

    private List<Coordinates> toCoordinates(List<CoordinatesWithTs> points) {
        return points.stream()
                .map(CoordinatesWithTs::toCoordinates)
                .toList();
    }

    @Getter
    @AllArgsConstructor
    private class CoordinateWithTsForRdp {

        long t;
        double y;
        double x;
        int idx;

        static List<CoordinateWithTsForRdp> toList(List<CoordinatesWithTs> points) {
            List<CoordinateWithTsForRdp> out = new ArrayList<>();
            for (int i = 0; i < points.size(); i++) {
                CoordinatesWithTs point = points.get(i);
                out.add(new CoordinateWithTsForRdp(point.getT(), point.getY(), point.getX(), i));
            }
            return out;
        }

    }

    private List<CoordinatesWithTs> toUtm(List<CoordinatesWithTs> points) {
        List<CoordinatesWithTs> out = new ArrayList<>();
        int zone = (int) Math.floor(points.get(0).getX() / 6.0) + 31;

        CoordinateReferenceSystem utmCrs = createUtmCrs(zone);

        CoordinateTransform transform = ctFactory.createTransform(WGS84, utmCrs);

        for (int i = 0; i < points.size(); i++) {
            CoordinatesWithTs point = points.get(i);
            ProjCoordinate sourceCoordinates = new ProjCoordinate(point.getX(), point.getY());
            ProjCoordinate targetCoordinates = new ProjCoordinate();
            transform.transform(sourceCoordinates, targetCoordinates);
            out.add(new CoordinatesWithTs(point.getT(), targetCoordinates.y, targetCoordinates.x));
        }
        return out;
    }

    private List<CoordinateWithTsForRdp> rdp(List<CoordinateWithTsForRdp> points) {
        List<CoordinateWithTsForRdp> out = new ArrayList<>();
        if (points.size() < 3) {
            return points;
        }

        CoordinateWithTsForRdp start = points.get(0);
        CoordinateWithTsForRdp end = points.get(points.size() - 1);

        double maxDistance = 0.0;
        int index = 0;

        for (int i = 1; i < points.size() - 1; i++) {       // 양 끝점과 가장 먼 수직선분의 꼭지점 추출
            double dist = calculateVerticalDistance(points.get(i), start, end);
            if (dist > maxDistance) {
                maxDistance = dist;
                index = i;
            }
        }

        if (maxDistance > RDP_EPSILON_METER) {          // 수직선분 길이가 임계치 보다 크다면, 그대로 남기고 다시 RDP
            List<CoordinateWithTsForRdp> leftResults = rdp(points.subList(0, index + 1));
            List<CoordinateWithTsForRdp> rightResults = rdp(points.subList(index, points.size()));

            out.addAll(leftResults.subList(0, leftResults.size() - 1));
            out.addAll(rightResults);
            return out;

        } else {
            return List.of(start, end);
        }
    }

    private static CoordinateReferenceSystem createUtmCrs(int zone) {
        String proj4Params = String.format(
                "+proj=utm +zone=%d +datum=WGS84 +units=m +no_defs", zone
        );
        return crsFactory.createFromParameters("UTM Zone " + zone, proj4Params);
    }

    private double calculateVerticalDistance(CoordinateWithTsForRdp target, CoordinateWithTsForRdp start, CoordinateWithTsForRdp end) {
        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();

        double numerator = Math.abs(dy * target.getX() - dx * target.getY() + end.getX() * start.getY() - end.getY() * start.getX());
        double denominator = Math.sqrt(dx * dx + dy * dy);

        return numerator / denominator;
    }

    // 체크포인트의 각도 계산
    public List<Checkpoint> calculateAngles(List<Coordinates> checkpoints) {
        if (checkpoints == null || checkpoints.size() < 2) {
            return new ArrayList<>();
        }

        List<Checkpoint> result = new ArrayList<>();

        // 첫 체크포인트의 angle은 0으로 설정 (전방으로 향함)
        result.add(new Checkpoint(checkpoints.get(0).y(), checkpoints.get(0).x(), 0));

        // 중간 체크포인트 angle 계산
        for (int i = 1; i < checkpoints.size() - 1; i++) {
            Coordinates prev = checkpoints.get(i - 1);
            Coordinates current = checkpoints.get(i);
            Coordinates next = checkpoints.get(i + 1);

            // 이전 벡터와 현재 벡터의 방위각을 각각 계산하여 차이를 구함
            double bearing1 = calculateBearing(prev, current);
            double bearing2 = calculateBearing(current, next);

            double relativeAngle = (bearing2 - bearing1 + 360) % 360;

            result.add(new Checkpoint(current.y(), current.x(), (int) relativeAngle));
        }

        // 마지막 체크포인트 angle은 null로 설정
        int lastIndex = checkpoints.size() - 1;
        result.add(new Checkpoint(checkpoints.get(lastIndex).y(), checkpoints.get(lastIndex).x(), null));

        return result;
    }

    /** 두 점을 이은 벡터의 방위각을 계산 */
    private double calculateBearing(Coordinates start, Coordinates end) {
        double startLat = Math.toRadians(start.y());
        double startLng = Math.toRadians(start.x());
        double endLat = Math.toRadians(end.y());
        double endLng = Math.toRadians(end.x());

        double dLng = endLng - startLng;

        double y = Math.sin(dLng) * Math.cos(endLat);
        double x = Math.cos(startLat) * Math.sin(endLat) - Math.sin(startLat) * Math.cos(endLat) * Math.cos(dLng);

        // atan2의 결과(= 라디안)를 각도로 변환
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    // 렌더링용 가공 : 최소힙 기반 VW 알고리즘 사용
    public List<Coordinates> simplifyToRenderingTelemetries(List<CoordinatesWithTs> points) {
        if (points.size() < 3) {
            return toCoordinates(points);
        }

        List<CoordinatesWithTs> utmPoints = toUtm(points);
        List<CoordinatesWithTs> coordinatesWithTsList = visValingamWhyatt(points, utmPoints);
        return toCoordinates(coordinatesWithTsList);
    }

    private List<CoordinatesWithTs> visValingamWhyatt(List<CoordinatesWithTs> points, List<CoordinatesWithTs> utmPoints) {
        PriorityQueue<VWHeapNode> heap = new PriorityQueue<>();
        for (int i = 1; i < utmPoints.size()-1; i++) {
            double area = calculateTriangleArea(utmPoints.get(i-1), utmPoints.get(i), utmPoints.get(i+1));
            heap.add(new VWHeapNode(area, i - 1, i, i + 1));
        }

        List<VWLNode> vwNodes = VWLNode.toList(points.size());     // 연결 정보

        while (!heap.isEmpty()) {
            VWHeapNode point = heap.poll();     // 가장 넓이가 적은 삼각형
            double area = point.getArea();
            int start = point.startCoordinatesIdx;
            int mid = point.midCoordinatesIdx;
            int end = point.endCoordinatesIdx;

            if (!vwNodes.get(mid).isActive || vwNodes.get(mid).prevIdx != start || vwNodes.get(mid).nextIdx != end) {       // 비활성화 점, 변경된 점 스킵
                continue;
            }

            if (area < VW_EPSILON_AREA) {
                vwNodes.get(mid).setActive(false);      // 가운데 인덱스 비활성화
                vwNodes.get(start).setNextIdx(vwNodes.get(mid).nextIdx);        // 주변 노드 정보 갱신
                vwNodes.get(end).setPrevIdx(vwNodes.get(mid).prevIdx);

                if (vwNodes.get(start).prevIdx != null) {       // 왼쪽 삼각형
                    double leftTriangleArea = calculateTriangleArea(
                            utmPoints.get(vwNodes.get(start).prevIdx),
                            utmPoints.get(start),
                            utmPoints.get(end)
                    );
                    heap.add(new VWHeapNode(leftTriangleArea, vwNodes.get(start).prevIdx, start, end));
                }

                if (vwNodes.get(end).nextIdx != null) {       // 오른쪽 삼각형
                    double rightTriangleArea = calculateTriangleArea(
                            utmPoints.get(start),
                            utmPoints.get(end),
                            utmPoints.get(vwNodes.get(end).nextIdx)
                    );
                    heap.add(new VWHeapNode(rightTriangleArea, start, end, vwNodes.get(end).nextIdx));
                }
            } else {
                break;
            }
        }

        List<CoordinatesWithTs> coordinatesWithTsList = new ArrayList<>();
        for (int i = 0; i < vwNodes.size(); i++) {
            if (vwNodes.get(i).isActive) {
                coordinatesWithTsList.add(utmPoints.get(i));
            }
        }
        return coordinatesWithTsList;
    }

    private Double calculateTriangleArea(CoordinatesWithTs p1, CoordinatesWithTs p2, CoordinatesWithTs p3) {
        return 0.5 * Math.abs(
                p1.getY() * (p2.getX() - p3.getX()) +
                        p2.getY() * (p3.getX() - p1.getX()) +
                        p3.getY() * (p1.getX() - p2.getX())
        );
    }

    @Getter @Setter
    @AllArgsConstructor
    private class VWHeapNode implements Comparable<VWHeapNode> {

        double area;
        int startCoordinatesIdx;
        int midCoordinatesIdx;
        int endCoordinatesIdx;

        @Override
        public int compareTo(@NotNull VWHeapNode other) {
            return Double.compare(this.area, other.area);
        }

    }

    @Getter @Setter
    @AllArgsConstructor
    private class VWLNode {

        boolean isActive;
        Integer prevIdx;
        Integer nextIdx;

        static List<VWLNode> toList(int size) {
            List<VWLNode> result = new ArrayList<>();
            result.add(new VWLNode(true, null, 1));
            for (int i = 1; i < size - 1; i++) {
                result.add(new VWLNode(true, i - 1, i + 1));
            }
            result.add(new VWLNode(true, size - 2, null));
            return result;
        }

    }

}
