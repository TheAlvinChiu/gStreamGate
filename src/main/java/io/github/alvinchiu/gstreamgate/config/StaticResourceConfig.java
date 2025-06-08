package io.github.alvinchiu.gstreamgate.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 靜態資源處理 - JS/CSS files
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/static/")
                .setCachePeriod(3600);
        
        // 直接的靜態檔案
        registry.addResourceHandler("/favicon.ico", "/logo192.png", "/logo512.png", "/manifest.json", "/robots.txt", "/asset-manifest.json")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(3600);
        
        // 根目錄和 index.html
        registry.addResourceHandler("/", "/index.html")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(0);
        
        // SPA 處理 - 除了API路徑外，其他都返回 index.html
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .setCachePeriod(0)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        // 如果是API路徑，不處理
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator/") || resourcePath.startsWith("h2-console/")) {
                            return null;
                        }
                        
                        Resource requestedResource = location.createRelative(resourcePath);
                        if (requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        } else {
                            // 對於不存在的資源，返回 index.html (SPA fallback)
                            return new ClassPathResource("/static/index.html");
                        }
                    }
                });
    }
}
