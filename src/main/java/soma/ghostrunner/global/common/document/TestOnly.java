package soma.ghostrunner.global.common.document;

import java.lang.annotation.*;

@Documented
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.SOURCE)
public @interface TestOnly {

    String value() default "";

}
