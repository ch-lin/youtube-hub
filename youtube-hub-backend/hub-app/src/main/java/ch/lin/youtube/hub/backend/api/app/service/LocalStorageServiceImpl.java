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
package ch.lin.youtube.hub.backend.api.app.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * Local file system implementation of {@link StorageService}.
 * <p>
 * This service acts as the fallback or default strategy for storing files. It
 * is activated when 'youtube.hub.storage.type' is set to 'local' or is missing.
 */
@Service
@ConditionalOnProperty(name = "youtube.hub.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageServiceImpl implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(LocalStorageServiceImpl.class);

    /**
     * The local directory path where files will be stored. Configurable via
     * application properties. Defaults to a temporary directory if not
     * provided.
     */
    @Value("${youtube.hub.thumbnail.storage-path:/tmp/youtube-thumbnails/}")
    private String storagePath;

    /**
     * The URL path pattern used to serve thumbnail images.
     */
    @Value("${youtube.hub.thumbnail.path-pattern:/thumbnails/**}")
    private String thumbnailPathPattern;

    /**
     * Ensures the directory for storing files exists during Spring Bean
     * initialization. This prevents runtime exceptions when the first file is
     * being stored.
     */
    @PostConstruct
    public void init() {
        try {
            Path path = Paths.get(storagePath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                logger.info("Created local storage directory at: {}", path.toAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("Failed to create local storage directory.", e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Uses standard Java NIO to copy the input stream directly to the local
     * file system. Existing files with the same name will be overwritten.
     */
    @Override
    public void store(String objectKey, InputStream inputStream, long contentLength, String contentType) throws IOException {
        Path targetFilePath = Paths.get(storagePath, objectKey);
        logger.debug("Storing file locally at: {}", targetFilePath);
        Files.copy(inputStream, targetFilePath, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean exists(String objectKey) {
        Path targetFilePath = Paths.get(storagePath, objectKey);
        return Files.exists(targetFilePath);
    }

    /**
     * {@inheritDoc}
     * <p>
     * For local storage, returns the relative API path to access the file via
     * the application's internal proxy/controller.
     */
    @Override
    public String getFileAccessUrl(String objectKey) {
        String basePath = thumbnailPathPattern.replace("**", "");
        if (!basePath.startsWith("/")) {
            basePath = "/" + basePath;
        }
        if (!basePath.endsWith("/")) {
            basePath += "/";
        }
        return basePath + objectKey;
    }
}
