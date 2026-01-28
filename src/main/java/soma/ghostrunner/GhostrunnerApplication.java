package soma.ghostrunner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@SpringBootApplication
public class GhostrunnerApplication {

	public static void main(String[] args) {
		SpringApplication.run(GhostrunnerApplication.class, args);
	}

}
