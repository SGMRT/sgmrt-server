package soma.ghostrunner.domain.running.domain;

import soma.ghostrunner.domain.running.application.dto.CoordinateDtoWithTs;
import soma.ghostrunner.domain.running.application.dto.CheckpointDto;
import soma.ghostrunner.domain.running.application.dto.CoordinateDto;
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PathSimplifier {

    private static final double EPSILON_METER = 10.0;
    private static final double TARGET_DISTANCE_M = 3.0;

    public static List<CoordinateDto> extractEdgePoints(List<CoordinateDtoWithTs> points) {
        // RDP
        if (points.size() <= 2) {
            return toCoordinateDtos(points);
        }
        List<CoordinateDtoWithTs> out = new ArrayList<>();
        rdp(points, 0, points.size() - 1, out);

        // ts 기준 정렬
        out.sort(Comparator.naturalOrder());
        return toCoordinateDtos(out);
    }

    private static List<CoordinateDto> toCoordinateDtos(List<CoordinateDtoWithTs> points) {
        return points.stream()
                .map(CoordinateDtoWithTs::toCoordinateDto)
                .toList();
    }

    private static void rdp(List<CoordinateDtoWithTs> pts, int start, int end, List<CoordinateDtoWithTs> out) {
        if (start == 0) {
            // 처음 진입에서 시작점 넣기
            out.add(pts.get(start));
        }

        double dmax = -1.0;
        int index = -1;

        CoordinateDtoWithTs a = pts.get(start);
        CoordinateDtoWithTs b = pts.get(end);

        for (int i = start + 1; i < end; i++) {
            double d = perpendicularDistanceMeters(pts.get(i), a, b);
            if (d > dmax) {
                index = i;
                dmax = d;
            }
        }

        if (dmax > EPSILON_METER && index != -1) {
            // 좌/우 구간 재귀
            rdp(pts, start, index, out);
            rdp(pts, index, end, out);
        } else {
            // 종점만 추가
            out.add(pts.get(end));
        }
    }

    // 점 - 선분 거리 계산
    private static double perpendicularDistanceMeters(CoordinateDtoWithTs p, CoordinateDtoWithTs a, CoordinateDtoWithTs b) {
        // 같은 점(선분 길이 0) 처리
        if (a.getLat() == b.getLat() && a.getLng() == b.getLng()) {
            return haversineMeters(p, a);
        }

        // 기준 위도(지역)로 간단한 평면 근사
        double refLat = Math.toRadians((a.getLat() + b.getLat()) * 0.5);

        // 위도/경도를 (x: 동서, y: 남북) 미터 좌표로 변환
        Vec2 A = toLocalMeters(a, refLat);
        Vec2 B = toLocalMeters(b, refLat);
        Vec2 P = toLocalMeters(p, refLat);

        // 선분 AB에 대한 P의 수선 발을 이용한 거리
        Vec2 AB = B.minus(A);
        double ab2 = AB.dot(AB);
        double t = (P.minus(A)).dot(AB) / ab2;
        if (t < 0) t = 0;
        else if (t > 1) t = 1;
        Vec2 proj = A.plus(AB.scale(t));
        return P.minus(proj).length();
    }

    private static Vec2 toLocalMeters(CoordinateDtoWithTs c, double refLatRad) {
        double metersPerDegLat = 111_132.0;
        double metersPerDegLon = 111_320.0 * Math.cos(refLatRad);

        double x = c.getLng() * metersPerDegLon;
        double y = c.getLat() * metersPerDegLat;
        return new Vec2(x, y);
    }

    // 3초 평균 -> 3m 추출로 해상도 축소
    public static List<CoordinateDto> simplifyToRenderingTelemetries(List<CoordinateDtoWithTs> points) {
        if (points == null || points.isEmpty()) return List.of();

        List<CoordinateDtoWithTs> averaged = averageByThreeSeconds(points);
        List<CoordinateDtoWithTs> filtered = filterByDistanceMeters(averaged, TARGET_DISTANCE_M);

        return toCoordinateDtos(filtered);
    }

    // 3초 평균
    private static List<CoordinateDtoWithTs> averageByThreeSeconds(List<CoordinateDtoWithTs> points) {
        List<CoordinateDtoWithTs> result = new ArrayList<>();
        for (int i = 2; i < points.size(); i += 3) {
            double avgLat = (points.get(i - 2).getLat() + points.get(i - 1).getLat() + points.get(i).getLat()) / 3.0;
            double avgLng = (points.get(i - 2).getLng() + points.get(i - 1).getLng() + points.get(i).getLng()) / 3.0;
            long ts = points.get(i).getTs(); // 마지막 값 기준
            result.add(new CoordinateDtoWithTs(ts, avgLat, avgLng));
        }
        return result;
    }

    // 3m 이상 채택
    private static List<CoordinateDtoWithTs> filterByDistanceMeters(List<CoordinateDtoWithTs> points, double targetDistance) {
        if (points == null || points.isEmpty()) return List.of();

        List<CoordinateDtoWithTs> filtered = new ArrayList<>();
        CoordinateDtoWithTs last = points.get(0);
        filtered.add(last);

        for (int i = 1; i < points.size(); i++) {
            CoordinateDtoWithTs p = points.get(i);
            double d = haversineMeters(last, p); 
            if (d >= targetDistance) {
                filtered.add(p);
                last = p;
            }
        }
        return filtered;
    }

    // 하버사인 : 두 점 사이 거리 계산
    private static double haversineMeters(CoordinateDtoWithTs p1, CoordinateDtoWithTs p2) {
        double R = 6371_000.0; // meters
        double lat1 = Math.toRadians(p1.getLat());
        double lat2 = Math.toRadians(p2.getLat());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(p2.getLng() - p1.getLng());
        double a = Math.sin(dLat/2)*Math.sin(dLat/2)
                + Math.cos(lat1)*Math.cos(lat2)*Math.sin(dLon/2)*Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }

    /** checkpoint 각 점마다, 다음 점으로 이동할 때의 각도 차이인 angle 필드를 추가하여 반환 */
    public static List<CheckpointDto> calculateAngles(List<CoordinateDto> checkpoints) {
        if (checkpoints == null || checkpoints.size() < 2) {
            return new ArrayList<>();
        }

        List<CheckpointDto> result = new ArrayList<>();

        // 첫 체크포인트의 angle은 0으로 설정 (전방으로 향함)
        result.add(new CheckpointDto(checkpoints.get(0).lat(), checkpoints.get(0).lng(), 0));

        // 중간 체크포인트 angle 계산
        for (int i = 1; i < checkpoints.size() - 1; i++) {
            CoordinateDto prev = checkpoints.get(i - 1);
            CoordinateDto current = checkpoints.get(i);
            CoordinateDto next = checkpoints.get(i + 1);

            // 이전 벡터와 현재 벡터의 방위각을 각각 계산하여 차이를 구함
            double bearing1 = calculateBearing(prev, current);
            double bearing2 = calculateBearing(current, next);

            double relativeAngle = (bearing2 - bearing1 + 360) % 360;

            result.add(new CheckpointDto(current.lat(), current.lng(), (int) relativeAngle));
        }

        // 마지막 체크포인트 angle은 null로 설정
        int lastIndex = checkpoints.size() - 1;
        result.add(new CheckpointDto(checkpoints.get(lastIndex).lat(), checkpoints.get(lastIndex).lng(), null));

        return result;
    }

    /** 두 점을 이은 벡터의 방위각을 계산 */
    private static double calculateBearing(CoordinateDto start, CoordinateDto end) {
        double startLat = Math.toRadians(start.lat());
        double startLng = Math.toRadians(start.lng());
        double endLat = Math.toRadians(end.lat());
        double endLng = Math.toRadians(end.lng());

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
