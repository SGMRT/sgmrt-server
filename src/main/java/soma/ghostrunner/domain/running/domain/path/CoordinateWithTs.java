package soma.ghostrunner.domain.running.domain.path;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public class CoordinateWithTs implements Comparable<CoordinateWithTs> {

    private long ts;
    private double lat;
    private double lng;

    public static List<CoordinateWithTs> toCoordinatesWithTsList(List<Telemetry> telemetries) {
        return telemetries.stream()
                .map(telemetry ->
                        new CoordinateWithTs(telemetry.getTimeStamp(), telemetry.getLat(), telemetry.getLng()))
                .collect(Collectors.toList());
    }

    public Coordinates toCoordinates() {
        return new Coordinates(lat, lng);
    }

    @Override
    public int compareTo(CoordinateWithTs c) {
        return (int) (this.getTs() - c.getTs());
    }

}
