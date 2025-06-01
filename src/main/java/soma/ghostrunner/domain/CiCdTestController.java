package soma.ghostrunner.domain;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CiCdTestController {
    @GetMapping("/")
    public String hello() {
        return "Hello World!";
    }

    @GetMapping("/test")
    public String test() {
        return "this is ci/cd test";
    }
}
