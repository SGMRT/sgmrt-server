package soma.ghostrunner.domain.running.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public class CoordinateDtoWithTs implements Comparable<CoordinateDtoWithTs> {

    private long ts;
    private double lat;
    private double lng;

    public static List<CoordinateDtoWithTs> toCoordinateDtosWithTsList(List<TelemetryDto> telemetryDtos) {
        return telemetryDtos.stream()
                .map(telemetryDto ->
                        new CoordinateDtoWithTs(telemetryDto.getTimeStamp(), telemetryDto.getLat(), telemetryDto.getLng()))
                .collect(Collectors.toList());
    }

    public CoordinateDto toCoordinateDto() {
        return new CoordinateDto(lat, lng);
    }

    @Override
    public int compareTo(CoordinateDtoWithTs c) {
        return (int) (this.getTs() - c.getTs());
    }

}
