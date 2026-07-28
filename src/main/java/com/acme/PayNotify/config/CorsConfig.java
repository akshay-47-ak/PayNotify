/*
 * File: CorsConfig.java
 * Created: 2026-04-23
 * Author: Akshay Athavale
 * Use: Configures PayNotify application infrastructure.
 */
package com.acme.PayNotify.config;

import com.acme.PayNotify.security.JwtAuthenticationInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class CorsConfig {

    @Autowired
    private JwtAuthenticationInterceptor jwtAuthenticationInterceptor;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("*") // for dev only
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }

            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(jwtAuthenticationInterceptor)
                        .addPathPatterns("/api/**");
            }
        };
    }


}
