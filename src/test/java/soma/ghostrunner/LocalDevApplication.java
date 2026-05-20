package soma.ghostrunner;

import org.springframework.boot.SpringApplication;

public class LocalDevApplication {

    public static void main(String[] args) {
        SpringApplication.from(GhostrunnerApplication::main)
                .with(LocalContainersConfig.class)
                .run(args);
    }
}
