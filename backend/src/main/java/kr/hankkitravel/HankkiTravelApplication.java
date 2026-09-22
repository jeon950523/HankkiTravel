package kr.hankkitravel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class HankkiTravelApplication {
    public static void main(String[] args) {
        SpringApplication.run(HankkiTravelApplication.class, args);
    }
}
