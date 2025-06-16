package soma.ghostrunner.domain;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import soma.ghostrunner.global.common.error.ErrorCode;
import soma.ghostrunner.global.common.error.exception.BusinessException;

@RestController
public class CiCdTestController {
    @GetMapping("/")
    public String hello() {
        return "Hello World!";
    }

    @GetMapping("/test")
    public String test() {
        throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
    }
}
