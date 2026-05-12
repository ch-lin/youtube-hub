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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.youtube.hub.backend.api.app.repository.ItemRepository;
import ch.lin.youtube.hub.backend.api.domain.model.Item;
import ch.lin.youtube.hub.backend.api.domain.model.ThumbnailStatus;

/**
 * Implementation of {@link ThumbnailService}.
 * <p>
 * This service is responsible for downloading YouTube video thumbnails via a
 * streaming approach and persisting them through the provided
 * {@link StorageService} strategy.
 */
@Service
public class ThumbnailServiceImpl implements ThumbnailService {

    private static final Logger logger = LoggerFactory.getLogger(ThumbnailServiceImpl.class);

    private final ItemRepository itemRepository;
    private final HttpClient httpClient;
    private final ConfigsService configsService;
    private final StorageService storageService;

    /**
     * Constructs a new ThumbnailServiceImpl.
     *
     * @param itemRepository the repository for item data access
     * @param configsService the service for accessing application configuration
     * @param storageService the dynamically selected storage strategy (Local vs
     * S3)
     */
    public ThumbnailServiceImpl(ItemRepository itemRepository, ConfigsService configsService, StorageService storageService) {
        this.itemRepository = itemRepository;
        this.configsService = configsService;
        this.storageService = storageService;
        // Create a reusable HttpClient and set a connection timeout to prevent hanging
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Downloads the thumbnail for the specified Item and updates the database
     * record.
     */
    @Override
    public void downloadThumbnail(Item item) {
        if (item.getThumbnailUrl() == null || item.getThumbnailUrl().isBlank()) {
            logger.warn("Item {} has no thumbnail URL to download.", item.getVideoId());
            return;
        }

        // YouTube thumbnails usually end with .jpg
        String objectKey = item.getVideoId() + ".jpg";

        try {
            // If the object already exists in the selected storage, we skip the download to save bandwidth
            if (storageService.exists(objectKey)) {
                logger.info("Thumbnail already exists for video {}. Skipping download.", item.getVideoId());
                updateThumbnailState(item, objectKey, ThumbnailStatus.DOWNLOADED, false);
                return;
            }

            logger.info("Downloading thumbnail for video {} from {}", item.getVideoId(), item.getThumbnailUrl());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(item.getThumbnailUrl()))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            // Use BodyHandlers.ofInputStream() to establish a data pipeline!
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            try (InputStream inputStream = response.body()) {
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                    String contentType = response.headers().firstValue("Content-Type").orElse("image/jpeg");

                    // Fallback: If YouTube API doesn't provide Content-Length, we must buffer it to memory
                    // since S3 requires a known object size for input streams.
                    if (contentLength > 0) {
                        storageService.store(objectKey, inputStream, contentLength, contentType);
                    } else {
                        byte[] bytes = inputStream.readAllBytes();
                        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
                            storageService.store(objectKey, bais, bytes.length, contentType);
                        }
                    }

                    logger.info("Successfully downloaded and stored thumbnail for video {}.", item.getVideoId());
                    updateThumbnailState(item, objectKey, ThumbnailStatus.DOWNLOADED, false);
                } else if (response.statusCode() == 404 || response.statusCode() == 403) {
                    // 404 or 403 usually means the video is deleted or private. Mark as failed and do not retry.
                    logger.warn("Thumbnail not found or forbidden for video {}. HTTP Status: {}. Marking as failed.", item.getVideoId(), response.statusCode());
                    updateThumbnailState(item, null, ThumbnailStatus.UNAVAILABLE, false);
                } else {
                    // 5xx or other errors might be temporary YouTube issues. Mark as temporary failure so the scheduler retries later.
                    logger.error("Temporary error downloading thumbnail for video {}. HTTP Status: {}", item.getVideoId(), response.statusCode());
                    updateThumbnailState(item, null, ThumbnailStatus.TEMP_FAILED, true);
                }
            }

        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.error("Exception occurred while downloading/storing thumbnail for video " + item.getVideoId(), e);
            updateThumbnailState(item, null, ThumbnailStatus.TEMP_FAILED, true);
        }
    }

    /**
     * Opens a brief Transaction to update the database, avoiding prolonged DB
     * Connection usage.
     *
     * @param item the {@link Item} entity to update
     * @param fileName the file name of the downloaded thumbnail
     * @param status the new {@link ThumbnailStatus} to set
     * @param isTemporaryFailure whether this update is due to a temporary
     * failure
     */
    @Transactional
    protected void updateThumbnailState(Item item, String fileName, ThumbnailStatus status, boolean isTemporaryFailure) {
        item.setStoredThumbnailPath(fileName);
        item.setThumbnailAttemptedAt(java.time.OffsetDateTime.now());

        if (isTemporaryFailure) {
            int newRetryCount = item.getThumbnailRetryCount() + 1;
            item.setThumbnailRetryCount(newRetryCount);
            int maxRetries = Optional.ofNullable(configsService.getResolvedConfig(null).getMaxThumbnailRetries()).orElse(3);
            if (newRetryCount >= maxRetries) {
                logger.warn("Thumbnail for video {} failed {} times. Marking as permanently UNAVAILABLE.", item.getVideoId(), newRetryCount);
                item.setThumbnailStatus(ThumbnailStatus.UNAVAILABLE);
            } else {
                item.setThumbnailStatus(ThumbnailStatus.TEMP_FAILED);
            }
        } else {
            item.setThumbnailStatus(status);
        }

        itemRepository.save(item);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Long> getThumbnailCounts() {
        ItemRepository.ThumbnailCountsProjection projection = itemRepository.getThumbnailCounts(
                Arrays.asList(ThumbnailStatus.PENDING, ThumbnailStatus.TEMP_FAILED),
                ThumbnailStatus.UNAVAILABLE);

        return Map.of(
                "totalCount", Optional.ofNullable(projection.getTotalCount()).orElse(0L),
                "pendingCount", Optional.ofNullable(projection.getPendingCount()).orElse(0L),
                "failedCount", Optional.ofNullable(projection.getFailedCount()).orElse(0L)
        );
    }
}
