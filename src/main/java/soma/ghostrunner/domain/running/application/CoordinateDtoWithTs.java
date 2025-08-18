package soma.ghostrunner.domain.running.application;

import lombok.AllArgsConstructor;
import lombok.Getter;
import soma.ghostrunner.domain.running.application.dto.CoordinateDto;
import soma.ghostrunner.domain.running.application.dto.TelemetryDto;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public class CoordinateDtoWithTs implements Comparable<CoordinateDtoWithTs> {

    private long ts;
    private double lat;
    private double lng;

    public static List<CoordinateDtoWithTs> toCoordinateDtoWithTsList(List<TelemetryDto> telemetryDtos) {
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
