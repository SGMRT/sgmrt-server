package soma.ghostrunner.domain.running.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public class CoordinateWithTsDto implements Comparable<CoordinateWithTsDto> {

    private long ts;
    private double lat;
    private double lng;

    public static List<CoordinateWithTsDto> toCoordinateDtosWithTsList(List<TelemetryDto> telemetryDtos) {
        return telemetryDtos.stream()
                .map(telemetryDto ->
                        new CoordinateWithTsDto(telemetryDto.getTimeStamp(), telemetryDto.getLat(), telemetryDto.getLng()))
                .collect(Collectors.toList());
    }

    public CoordinateDto toCoordinateDto() {
        return new CoordinateDto(lat, lng);
    }

    @Override
    public int compareTo(CoordinateWithTsDto c) {
        return (int) (this.getTs() - c.getTs());
    }

}
