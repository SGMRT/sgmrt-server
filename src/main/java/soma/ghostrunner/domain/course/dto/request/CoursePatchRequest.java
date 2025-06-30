package soma.ghostrunner.domain.course.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CoursePatchRequest {
    @NotBlank(message = "코스 이름은 비어있을 수 없습니다.")
    private String name;
}
