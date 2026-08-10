package feat.apex_BE;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ApexBeApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApexBeApplication.class, args);
	}

}
