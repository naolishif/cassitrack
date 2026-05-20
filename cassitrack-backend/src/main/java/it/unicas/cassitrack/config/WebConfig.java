package it.unicas.cassitrack.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Automatically forwards http://localhost:8080/ straight to your login screen
        registry.addViewController("/").setViewName("forward:/cassitrack-login.html");
    }
}
