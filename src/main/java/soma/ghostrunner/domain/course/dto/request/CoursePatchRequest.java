package soma.ghostrunner.domain.course.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CoursePatchRequest {
    private String name;
    private Boolean isPublic;
    private Set<UpdatedAttr> updatedAttrs = Set.of();

    public enum UpdatedAttr {
        NAME,
        IS_PUBLIC
    }
}
