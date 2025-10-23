package soma.ghostrunner.domain.course.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CoursePatchRequest {

    @NotEmpty
    private String name;

    @NotEmpty
    private Boolean isPublic;

    private Set<UpdatedAttr> updatedAttrs = Set.of();
    public enum UpdatedAttr {
        NAME,
        IS_PUBLIC
    }

}
