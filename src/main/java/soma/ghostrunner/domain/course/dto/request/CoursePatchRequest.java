package soma.ghostrunner.domain.course.dto.request;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CoursePatchRequest {
    private String name;
    private Boolean isPublic;
    private Set<UpdatedAttr> updatedAttrs = Set.of();

    enum UpdatedAttr {
        NAME,
        IS_PUBLIC;
    }
}
