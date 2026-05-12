/*=============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2025 Che-Hung Lin
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *===========================================================================*/
package ch.lin.youtube.hub.backend.api.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration class for Spring MVC web settings.
 * <p>
 * This class maps static resource requests (e.g., /thumbnails/**) to the
 * corresponding directories on the local file system.
 */
@Configuration
@ConditionalOnProperty(name = "youtube.hub.storage.type", havingValue = "local", matchIfMissing = true)
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * The URL path pattern used to serve thumbnail images, injected from
     * properties.
     */
    @Value("${youtube.hub.thumbnail.path-pattern:/thumbnails/**}")
    private String thumbnailPathPattern;

    /**
     * The local file system path where downloaded thumbnails are stored.
     */
    @Value("${youtube.hub.thumbnail.storage-path:/tmp/youtube-thumbnails/}")
    private String thumbnailStoragePath;

    /**
     * Configures static resource handlers.
     *
     * @param registry the {@link ResourceHandlerRegistry} to which handlers are
     * added
     */
    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        String location = thumbnailStoragePath;
        if (!location.endsWith("/")) {
            location += "/";
        }

        registry.addResourceHandler(thumbnailPathPattern)
                // file: prefix tells Spring Boot to look into the file system instead of classpath
                .addResourceLocations("file:" + location)
                .setCachePeriod(3600);
    }
}
