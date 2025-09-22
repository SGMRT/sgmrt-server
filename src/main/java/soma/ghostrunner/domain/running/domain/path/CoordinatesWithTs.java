package soma.ghostrunner.domain.running.domain.path;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public class CoordinatesWithTs implements Comparable<CoordinatesWithTs> {

    private long t;
    private double y;
    private double x;

    public static List<CoordinatesWithTs> toCoordinatesWithTsList(List<Telemetry> telemetries) {
        return telemetries.stream()
                .map(telemetry ->
                        new CoordinatesWithTs(telemetry.getT(), telemetry.getY(), telemetry.getX()))
                .collect(Collectors.toList());
    }

    public Coordinates toCoordinates() {
        return new Coordinates(y, x);
    }

    @Override
    public int compareTo(CoordinatesWithTs c) {
        return (int) (this.getT() - c.getT());
    }

}
