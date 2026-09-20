package pe.edu.pucp.sisrap.sisrap_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "pe.edu.pucp.sisrap")
public class SisrapBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SisrapBackendApplication.class, args);
    }
}