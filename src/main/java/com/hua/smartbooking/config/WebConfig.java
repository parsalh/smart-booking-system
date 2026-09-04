package com.hua.smartbooking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Exposes the on-disk upload directory (mounted as a Docker volume at /app/uploads,
 * see docker-compose.yml) at the /uploads/** URL path, so uploaded room images can be
 * served back to the browser (e.g. via Room.imageUrl = "/uploads/rooms/xyz.jpg").
 *
 * @author Stavroula Parsali
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:/app/uploads/");
    }
}