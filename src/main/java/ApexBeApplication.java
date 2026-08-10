import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

// Base packages must be listed explicitly: this class lives in the default (unnamed)
// package, so the implicit component/entity/repository scan base package would be ""
// which scans the entire classpath (including framework JARs) and causes bean conflicts.
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"domain", "config", "global", "security", "feat.apex_BE.beauty"})
@EntityScan(basePackages = "domain")
@EnableJpaRepositories(basePackages = "domain")
public class ApexBeApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApexBeApplication.class, args);
	}

}
