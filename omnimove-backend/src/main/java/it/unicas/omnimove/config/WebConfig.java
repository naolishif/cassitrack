package it.unicas.omnimove.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // This takes the base URL "/" and forwards it straight to your login page!
        registry.addViewController("/").setViewName("forward:/omnimove-login.html");
    }
}