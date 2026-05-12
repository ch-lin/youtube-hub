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

import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

@ExtendWith(MockitoExtension.class)
class WebMvcConfigTest {

    private static final String TEST_PATH_PATTERN = "/custom-thumbnails/**";

    private WebMvcConfig webMvcConfig;

    @Mock
    private ResourceHandlerRegistry resourceHandlerRegistry;

    @Mock
    private ResourceHandlerRegistration resourceHandlerRegistration;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        webMvcConfig = new WebMvcConfig();
    }

    @Test
    void addResourceHandlers_ShouldAppendSlash_WhenPathDoesNotEndWithSlash() {
        // Arrange
        ReflectionTestUtils.setField(Objects.requireNonNull(webMvcConfig), "thumbnailPathPattern", TEST_PATH_PATTERN);
        ReflectionTestUtils.setField(Objects.requireNonNull(webMvcConfig), "thumbnailStoragePath", "/data/thumbnails");

        when(resourceHandlerRegistry.addResourceHandler(TEST_PATH_PATTERN)).thenReturn(resourceHandlerRegistration);
        when(resourceHandlerRegistration.addResourceLocations(anyString())).thenReturn(resourceHandlerRegistration);
        when(resourceHandlerRegistration.setCachePeriod(anyInt())).thenReturn(resourceHandlerRegistration);

        // Act
        webMvcConfig.addResourceHandlers(Objects.requireNonNull(resourceHandlerRegistry));

        // Assert
        verify(resourceHandlerRegistry).addResourceHandler(TEST_PATH_PATTERN);
        verify(resourceHandlerRegistration).addResourceLocations("file:/data/thumbnails/");
        verify(resourceHandlerRegistration).setCachePeriod(3600);
    }

    @Test
    void addResourceHandlers_ShouldNotAppendSlash_WhenPathEndsWithSlash() {
        // Arrange
        ReflectionTestUtils.setField(Objects.requireNonNull(webMvcConfig), "thumbnailPathPattern", TEST_PATH_PATTERN);
        ReflectionTestUtils.setField(Objects.requireNonNull(webMvcConfig), "thumbnailStoragePath", "/data/thumbnails/");

        when(resourceHandlerRegistry.addResourceHandler(TEST_PATH_PATTERN)).thenReturn(resourceHandlerRegistration);
        when(resourceHandlerRegistration.addResourceLocations(anyString())).thenReturn(resourceHandlerRegistration);
        when(resourceHandlerRegistration.setCachePeriod(anyInt())).thenReturn(resourceHandlerRegistration);

        // Act
        webMvcConfig.addResourceHandlers(Objects.requireNonNull(resourceHandlerRegistry));

        // Assert
        verify(resourceHandlerRegistry).addResourceHandler(TEST_PATH_PATTERN);
        verify(resourceHandlerRegistration).addResourceLocations("file:/data/thumbnails/");
        verify(resourceHandlerRegistration).setCachePeriod(3600);
    }
}
