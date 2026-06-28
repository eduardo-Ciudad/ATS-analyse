package eduar.atsanalyzer.infra.config;


import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ATSReady API")
                        .description("API de análise de currículos baseada em métricas ATS (Applicant Tracking System)")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Eduardo Ciudad")
                                .url("https://github.com/eduardo-Ciudad")));
    }
}
