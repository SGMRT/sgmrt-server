package soma.ghostrunner.domain.running.domain.path;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import org.locationtech.proj4j.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@UtilityClass
public class PathSimplifier {

    private final CRSFactory crsFactory = new CRSFactory();
    private final CoordinateTransformFactory ctFactory = new CoordinateTransformFactory();
    private final CoordinateReferenceSystem WGS84 = crsFactory.createFromName("epsg:4326");

    private static final double RDP_EPSILON_METER = 8.0;
    private static final double TARGET_DISTANCE_M = 3.0;

    // 체크포인트 추출
    public List<Coordinates> extractEdgePoints(List<CoordinateWithTs> points) {
        if (points.size() <= 2) {
            return toCoordinateDtos(points);
        }

        List<CoordinateWithTsAndIdx> utmPoints = toUtm(points);
        List<CoordinateWithTsAndIdx> rdpUtmPoints = rdp(utmPoints);

        List<CoordinateWithTs> out = new ArrayList<>();
        for (CoordinateWithTsAndIdx utmPoint : rdpUtmPoints) {
            out.add(points.get(utmPoint.idx));
        }

        out.sort(Comparator.naturalOrder());
        return toCoordinateDtos(out);
    }

    private List<Coordinates> toCoordinateDtos(List<CoordinateWithTs> points) {
        return points.stream()
                .map(CoordinateWithTs::toCoordinates)
                .toList();
    }

    @Getter
    @AllArgsConstructor
    private class CoordinateWithTsAndIdx {
        private long t;
        private double y;
        private double x;
        private int idx;
    }

    private List<CoordinateWithTsAndIdx> toUtm(List<CoordinateWithTs> points) {
        List<CoordinateWithTsAndIdx> out = new ArrayList<>();
        int zone = (int) Math.floor(points.get(0).getX() / 6.0) + 31;

        CoordinateReferenceSystem utmCrs = createUtmCrs(zone);

        CoordinateTransform transform = ctFactory.createTransform(WGS84, utmCrs);

        for (int i = 0; i < points.size(); i++) {
            CoordinateWithTs point = points.get(i);
            ProjCoordinate sourceCoordinates = new ProjCoordinate(point.getX(), point.getY());
            ProjCoordinate targetCoordinates = new ProjCoordinate();
            transform.transform(sourceCoordinates, targetCoordinates);
            out.add(new CoordinateWithTsAndIdx(point.getT(), targetCoordinates.y, targetCoordinates.x, i));
        }
        return out;
    }

    private List<CoordinateWithTsAndIdx> rdp(List<CoordinateWithTsAndIdx> points) {
        List<CoordinateWithTsAndIdx> out = new ArrayList<>();
        if (points.size() < 3) {
            return points;
        }

        CoordinateWithTsAndIdx start = points.get(0);
        CoordinateWithTsAndIdx end = points.get(points.size() - 1);

        double maxDistance = 0.0;
        int index = 0;

        for (int i = 1; i < points.size() - 1; i++) {
            double dist = calculateVerticalDistance(points.get(i), start, end);
            if (dist > maxDistance) {
                maxDistance = dist;
                index = i;
            }
        }

        if (maxDistance > RDP_EPSILON_METER) {
            List<CoordinateWithTsAndIdx> leftResults = rdp(points.subList(0, index + 1));
            List<CoordinateWithTsAndIdx> rightResults = rdp(points.subList(index, points.size()));

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

    private double calculateVerticalDistance(CoordinateWithTsAndIdx target, CoordinateWithTsAndIdx start, CoordinateWithTsAndIdx end) {
        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();

        double numerator = Math.abs(dy * target.getX() - dx * target.getY() + end.getX() * start.getY() - end.getY() * start.getX());
        double denominator = Math.sqrt(dx * dx + dy * dy);

        return numerator / denominator;
    }

    // 평균 보간법
    public List<Coordinates> simplifyToRenderingTelemetries(List<CoordinateWithTs> points) {
        if (points == null || points.isEmpty()) return List.of();

        List<CoordinateWithTs> averaged = averageByThreeSeconds(points);
        List<CoordinateWithTs> filtered = filterByDistanceMeters(averaged, TARGET_DISTANCE_M);

        return toCoordinateDtos(filtered);
    }

    // 3초 평균
    private List<CoordinateWithTs> averageByThreeSeconds(List<CoordinateWithTs> points) {
        List<CoordinateWithTs> result = new ArrayList<>();
        for (int i = 2; i < points.size(); i += 3) {
            double avgLat = (points.get(i - 2).getY() + points.get(i - 1).getY() + points.get(i).getY()) / 3.0;
            double avgLng = (points.get(i - 2).getX() + points.get(i - 1).getX() + points.get(i).getX()) / 3.0;
            long ts = points.get(i).getT(); // 마지막 값 기준
            result.add(new CoordinateWithTs(ts, avgLat, avgLng));
        }
        return result;
    }

    // 3m 이상 채택
    private List<CoordinateWithTs> filterByDistanceMeters(List<CoordinateWithTs> points, double targetDistance) {
        if (points == null || points.isEmpty()) return List.of();

        List<CoordinateWithTs> filtered = new ArrayList<>();
        CoordinateWithTs last = points.get(0);
        filtered.add(last);

        for (int i = 1; i < points.size(); i++) {
            CoordinateWithTs p = points.get(i);
            double d = haversineMeters(last, p); 
            if (d >= targetDistance) {
                filtered.add(p);
                last = p;
            }
        }
        return filtered;
    }

    // 하버사인 : 두 점 사이 거리 계산
    private static double haversineMeters(CoordinateWithTs p1, CoordinateWithTs p2) {
        double R = 6371_000.0; // meters
        double lat1 = Math.toRadians(p1.getY());
        double lat2 = Math.toRadians(p2.getY());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(p2.getX() - p1.getX());
        double a = Math.sin(dLat/2)*Math.sin(dLat/2)
                + Math.cos(lat1)*Math.cos(lat2)*Math.sin(dLon/2)*Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }

    /** checkpoint 각 점마다, 다음 점으로 이동할 때의 각도 차이인 angle 필드를 추가하여 반환 */
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

    private record Vec2(double x, double y) {
        Vec2 minus(Vec2 o) { return new Vec2(x - o.x, y - o.y); }
        Vec2 plus(Vec2 o)  { return new Vec2(x + o.x, y + o.y); }
        Vec2 scale(double s){ return new Vec2(x * s, y * s); }
        double dot(Vec2 o) { return x * o.x + y * o.y; }
        double length()    { return Math.hypot(x, y); }
    }

}
