package com.cqu.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Slf4j
@Component
public class WebMvcConfiguration implements WebMvcConfigurer {

    @Autowired
    private HttpAuthInterceptor httpAuthInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 发布包静态页（:4173）直连 API（:8080）
        registry.addMapping("/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("开始注册自定义拦截器...");
        registry.addInterceptor(httpAuthInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/users/register")
                .excludePathPatterns("/users/login");
    }
}
