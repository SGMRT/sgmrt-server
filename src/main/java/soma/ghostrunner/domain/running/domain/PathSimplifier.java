package soma.ghostrunner.domain.running.domain;

import soma.ghostrunner.domain.running.application.CoordinateDtoWithTs;
import soma.ghostrunner.domain.running.application.dto.CoordinateDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PathSimplifier {

    private static final double EPSILON_METER = 8.0;

    public static List<CoordinateDto> simplify(List<CoordinateDtoWithTs> points) {
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
        // 위도 1도 ≈ 111_132 m, 경도 1도 ≈ 111_320 * cos(lat) m (근사)
        double metersPerDegLat = 111_132.0;
        double metersPerDegLon = 111_320.0 * Math.cos(refLatRad);

        double x = c.getLng() * metersPerDegLon;
        double y = c.getLat() * metersPerDegLat;
        return new Vec2(x, y);
    }

    // 필요시 두 점 사이의 구면 거리(하버사인). 여기선 선분 길이 0일 때만 사용.
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

    private record Vec2(double x, double y) {
        Vec2 minus(Vec2 o) { return new Vec2(x - o.x, y - o.y); }
        Vec2 plus(Vec2 o)  { return new Vec2(x + o.x, y + o.y); }
        Vec2 scale(double s){ return new Vec2(x * s, y * s); }
        double dot(Vec2 o) { return x * o.x + y * o.y; }
        double length()    { return Math.hypot(x, y); }
    }

}
