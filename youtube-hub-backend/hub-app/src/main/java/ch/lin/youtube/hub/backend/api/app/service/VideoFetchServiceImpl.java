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
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import ch.lin.platform.http.HttpClient;
import ch.lin.platform.http.exception.HttpException;
import ch.lin.youtube.hub.backend.api.app.repository.ItemRepository;
import ch.lin.youtube.hub.backend.api.common.exception.QuotaExceededException;
import ch.lin.youtube.hub.backend.api.common.exception.YoutubeApiAuthException;
import ch.lin.youtube.hub.backend.api.common.exception.YoutubeApiRequestException;
import ch.lin.youtube.hub.backend.api.domain.model.Item;
import ch.lin.youtube.hub.backend.api.domain.model.ItemStatisticHistory;
import ch.lin.youtube.hub.backend.api.domain.model.LiveBroadcastContent;

/**
 * Service dedicated to interacting with the YouTube Data API for Video
 * resources.
 * <p>
 * This service handles the low-level details of constructing API requests,
 * parsing JSON responses, and mapping them to {@link Item} domain entities.
 */
@Service
public class VideoFetchServiceImpl implements VideoFetchService {

    private static final Logger logger = LoggerFactory.getLogger(VideoFetchServiceImpl.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final YoutubeApiUsageService youtubeApiUsageService;
    private final ItemRepository itemRepository;

    public VideoFetchServiceImpl(YoutubeApiUsageService youtubeApiUsageService, ItemRepository itemRepository) {
        this.youtubeApiUsageService = youtubeApiUsageService;
        this.itemRepository = itemRepository;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Item> fetchAndCreateItemsFromVideoIds(HttpClient client, String apiKey,
            Map<String, JsonNode> newVideoSnippets, long delayInMilliseconds,
            long quotaLimit, long quotaThreshold) throws InterruptedException {

        if (newVideoSnippets.isEmpty()) {
            return Collections.emptyList();
        }

        List<Item> newItems = new ArrayList<>();
        String videoIds = String.join(",", newVideoSnippets.keySet());

        Map<String, String> params = new HashMap<>();
        params.put("part", "snippet,liveStreamingDetails,statistics");
        params.put("id", videoIds);
        params.put("key", apiKey);
        params.put("maxResults", "50");

        try {
            checkQuota(quotaLimit, quotaThreshold);
            delayRequest(delayInMilliseconds);

            youtubeApiUsageService.recordUsage(1L);
            logger.debug("    -> Requested video IDs {}.", videoIds);
            // Quota cost: 1 unit for videos.list operation
            String responseBody = client.get("/youtube/v3/videos", params, null).body();
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            JsonNode videoItemsNode = root.path("items");

            if (videoItemsNode.isMissingNode() || !videoItemsNode.isArray()) {
                logger.warn("    -> No detailed video items found in API response for video IDs: {}", videoIds);
                return Collections.emptyList();
            }

            for (JsonNode videoItemNode : videoItemsNode) {
                String videoId = videoItemNode.path("id").asText();
                JsonNode originalSnippet = newVideoSnippets.get(videoId);
                if (originalSnippet == null) {
                    continue;
                }

                JsonNode videoSnippet = videoItemNode.path("snippet");

                String liveBroadcastContentStr = videoSnippet.path("liveBroadcastContent").asText("NONE").toUpperCase();

                String thumbnailUrl = getBestAvailableThumbnailUrl(videoSnippet.path("thumbnails"));

                var itemBuilder = Item.builder()
                        .videoId(videoId)
                        .title(videoSnippet.path("title").asText())
                        .description(videoSnippet.path("description").asText(null))
                        .kind(videoItemNode.path("kind").asText())
                        .videoPublishedAt(OffsetDateTime.parse(originalSnippet.path("publishedAt").asText()))
                        .liveBroadcastContent(LiveBroadcastContent.valueOf(liveBroadcastContentStr))
                        .thumbnailUrl(thumbnailUrl);

                JsonNode liveStreamingDetails = videoItemNode.path("liveStreamingDetails");
                if (!liveStreamingDetails.isMissingNode() && liveStreamingDetails.has("scheduledStartTime")) {
                    String scheduledTimeStr = liveStreamingDetails.path("scheduledStartTime").asText(null);
                    Objects.requireNonNull(scheduledTimeStr);
                    itemBuilder.scheduledStartTime(OffsetDateTime.parse(scheduledTimeStr));
                }

                Item newItem = itemBuilder.build();

                // Add statistics history
                appendStatisticsHistory(newItem, videoItemNode);

                logger.info("    -> Creating new video: '{}' ({}) published at {} with status {}",
                        newItem.getTitle(), videoId, newItem.getVideoPublishedAt(), newItem.getLiveBroadcastContent());
                newItems.add(newItem);
            }
        } catch (IOException | URISyntaxException e) {
            if (e instanceof HttpException && ((HttpException) e).getStatusCode() == 400
                    && e.getMessage() != null && e.getMessage().contains("API key not valid")) {
                throw new YoutubeApiAuthException("The provided YouTube API key is not valid.", e);
            }
            throw new YoutubeApiRequestException("Failed to fetch video details from YouTube API for video IDs: " + videoIds, e);
        }
        return newItems;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public int updateExistingItems(HttpClient client, String apiKey, List<Item> existingItemsToUpdate,
            long delayInMilliseconds, long quotaLimit, long quotaThreshold) throws InterruptedException {

        if (existingItemsToUpdate.isEmpty()) {
            return 0;
        }

        int updatedCount = 0;
        List<String> videoIdsList = existingItemsToUpdate.stream().map(Item::getVideoId).toList();
        String videoIds = String.join(",", videoIdsList);

        Map<String, String> params = new HashMap<>();
        params.put("part", "snippet,liveStreamingDetails,statistics");
        params.put("id", videoIds);
        params.put("key", apiKey);
        params.put("maxResults", "50");

        try {
            checkQuota(quotaLimit, quotaThreshold);
            delayRequest(delayInMilliseconds);

            // Quota cost: 1 unit for videos.list operation
            youtubeApiUsageService.recordUsage(1L);
            logger.debug("    -> Checking for updates for video IDs {}.", videoIds);

            String responseBody = client.get("/youtube/v3/videos", params, null).body();
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            JsonNode videoItemsNode = root.path("items");

            if (videoItemsNode.isMissingNode() || !videoItemsNode.isArray()) {
                logger.warn("    -> No detailed video items found for update check in API response for video IDs: {}", videoIds);
                return 0;
            }

            for (JsonNode videoItemNode : videoItemsNode) {
                String videoId = videoItemNode.path("id").asText();
                Optional<Item> itemOptional = existingItemsToUpdate.stream()
                        .filter(item -> item.getVideoId().equals(videoId)).findFirst();

                if (itemOptional.isEmpty()) {
                    continue;
                }

                Item existingItem = itemOptional.get();
                boolean updated = false;

                JsonNode videoSnippet = videoItemNode.path("snippet");

                String newTitle = videoSnippet.path("title").asText();
                if (!existingItem.getTitle().equals(newTitle)) {
                    logger.info("    -> Updating title for video {}: '{}' -> '{}'", videoId, existingItem.getTitle(), newTitle);
                    existingItem.setTitle(newTitle);
                    updated = true;
                }

                String newDescription = videoSnippet.path("description").asText(null);
                if (!Objects.equals(existingItem.getDescription(), newDescription)) {
                    logger.info("    -> Updating description for video {}", videoId);
                    existingItem.setDescription(newDescription);
                    updated = true;
                }

                String publishedAtStr = videoSnippet.path("publishedAt").asText(null);
                if (publishedAtStr != null) {
                    OffsetDateTime newPublishedAt = OffsetDateTime.parse(publishedAtStr);
                    if (!newPublishedAt.equals(existingItem.getVideoPublishedAt())) {
                        logger.info("    -> Updating publishedAt for video {}: {} -> {}", videoId, existingItem.getVideoPublishedAt(), newPublishedAt);
                        existingItem.setVideoPublishedAt(newPublishedAt);
                        updated = true;
                    }
                }

                String liveBroadcastContentStr = videoSnippet.path("liveBroadcastContent").asText("NONE").toUpperCase();
                LiveBroadcastContent newLiveStatus = LiveBroadcastContent.valueOf(liveBroadcastContentStr);
                if (existingItem.getLiveBroadcastContent() != newLiveStatus) {
                    logger.info("    -> Updating live status for video {}: {} -> {}", videoId, existingItem.getLiveBroadcastContent(), newLiveStatus);
                    existingItem.setLiveBroadcastContent(newLiveStatus);
                    updated = true;
                }

                JsonNode liveStreamingDetails = videoItemNode.path("liveStreamingDetails");
                if (!liveStreamingDetails.isMissingNode() && liveStreamingDetails.has("scheduledStartTime")) {
                    String scheduledTimeStr = liveStreamingDetails.path("scheduledStartTime").asText(null);
                    if (scheduledTimeStr != null) {
                        OffsetDateTime newScheduledTime = OffsetDateTime.parse(scheduledTimeStr);
                        if (!newScheduledTime.equals(existingItem.getScheduledStartTime())) {
                            logger.info("    -> Updating scheduled time for video {}: {} -> {}", videoId, existingItem.getScheduledStartTime(), newScheduledTime);
                            existingItem.setScheduledStartTime(newScheduledTime);
                            updated = true;
                        }
                    }
                }

                // Always add a new statistics history record
                if (appendStatisticsHistory(existingItem, videoItemNode)) {
                    updated = true;
                }

                if (updated) {
                    itemRepository.save(existingItem);
                    updatedCount++;
                }
            }
        } catch (IOException | URISyntaxException e) {
            if (e instanceof HttpException && ((HttpException) e).getStatusCode() == 400
                    && e.getMessage() != null && e.getMessage().contains("API key not valid")) {
                throw new YoutubeApiAuthException("The provided YouTube API key is not valid.", e);
            }
            throw new YoutubeApiRequestException("Failed to fetch video details for update check from YouTube API for video IDs: " + videoIds, e);
        }
        return updatedCount;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public int syncStatisticsForItems(HttpClient client, String apiKey, List<Item> itemsToUpdate,
            long delayInMilliseconds, long quotaLimit, long quotaThreshold) throws InterruptedException {

        if (itemsToUpdate.isEmpty()) {
            return 0;
        }

        int updatedCount = 0;
        List<String> videoIdsList = itemsToUpdate.stream().map(Item::getVideoId).toList();
        String videoIds = String.join(",", videoIdsList);

        Map<String, String> params = new HashMap<>();
        // Only fetch statistics to save quota and bandwidth
        params.put("part", "statistics");
        params.put("id", videoIds);
        params.put("key", apiKey);
        params.put("maxResults", "50");

        try {
            checkQuota(quotaLimit, quotaThreshold);
            delayRequest(delayInMilliseconds);

            // Quota cost: 1 unit for videos.list operation
            youtubeApiUsageService.recordUsage(1L);
            logger.debug("    -> Syncing statistics only for video IDs {}.", videoIds);

            String responseBody = client.get("/youtube/v3/videos", params, null).body();
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            JsonNode videoItemsNode = root.path("items");

            if (videoItemsNode.isMissingNode() || !videoItemsNode.isArray()) {
                logger.warn("    -> No statistics found for video IDs: {}", videoIds);
                return 0;
            }

            // Re-fetch items within the current transaction to ensure they are managed
            // and avoid LazyInitializationException when accessing lazy collections.
            List<Item> managedItems = itemRepository.findAllByVideoIdIn(videoIdsList);

            for (JsonNode videoItemNode : videoItemsNode) {
                String videoId = videoItemNode.path("id").asText();
                Optional<Item> itemOptional = managedItems.stream()
                        .filter(item -> item.getVideoId().equals(videoId)).findFirst();

                if (itemOptional.isEmpty()) {
                    continue;
                }

                Item existingItem = itemOptional.get();
                if (appendStatisticsHistory(existingItem, videoItemNode)) {
                    itemRepository.save(Objects.requireNonNull(existingItem));
                    updatedCount++;
                }
            }
        } catch (IOException | URISyntaxException e) {
            if (e instanceof HttpException && ((HttpException) e).getStatusCode() == 400
                    && e.getMessage() != null && e.getMessage().contains("API key not valid")) {
                throw new YoutubeApiAuthException("The provided YouTube API key is not valid.", e);
            }
            throw new YoutubeApiRequestException("Failed to fetch statistics from YouTube API for video IDs: " + videoIds, e);
        }
        return updatedCount;
    }

    /**
     * Checks if the daily quota limit has been reached.
     *
     * @param limit The daily quota limit.
     * @param threshold The safety threshold.
     * @throws QuotaExceededException if the daily quota limit is reached.
     */
    private void checkQuota(long limit, long threshold) {
        if (!youtubeApiUsageService.hasSufficientQuota(limit, threshold)) {
            throw new QuotaExceededException("Daily quota limit reached.");
        }
    }

    /**
     * Pauses execution for a specified duration.
     *
     * @param milliseconds The number of milliseconds to wait.
     * @throws InterruptedException if the thread is interrupted while sleeping.
     */
    private void delayRequest(long milliseconds) throws InterruptedException {
        if (milliseconds > 0) {
            logger.debug("Waiting for {} millisecond(s) before next API request.", milliseconds);
            Thread.sleep(milliseconds);
        } else {
            logger.debug("No delay configured, proceeding with request immediately.");
        }
    }

    /**
     * Selects the best available thumbnail URL from the thumbnails JSON object,
     * starting from the highest resolution and falling back to lower ones.
     *
     * @param thumbnailsNode The 'thumbnails' JsonNode from the YouTube API
     * response.
     * @return The URL of the best available thumbnail, or null if none are
     * found.
     */
    private String getBestAvailableThumbnailUrl(JsonNode thumbnailsNode) {
        if (thumbnailsNode == null || thumbnailsNode.isMissingNode()) {
            return null;
        }
        // Order of preference from highest to lowest resolution
        final String[] resolutions = {"maxres", "standard", "high", "medium", "default"};
        for (String res : resolutions) {
            String url = thumbnailsNode.path(res).path("url").asText(null);
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    /**
     * Extracts statistics from the video item JSON node and appends them to the
     * Item's history.
     *
     * @param item The Item entity to update.
     * @param videoItemNode The API response node for the video.
     * @return true if statistics were found and appended, false otherwise.
     */
    private boolean appendStatisticsHistory(Item item, JsonNode videoItemNode) {
        JsonNode statisticsNode = videoItemNode.path("statistics");
        if (!statisticsNode.isMissingNode()) {
            ItemStatisticHistory history = ItemStatisticHistory.builder()
                    .item(item)
                    .recordedAt(OffsetDateTime.now())
                    .viewCount(statisticsNode.path("viewCount").asLong(0L))
                    .likeCount(statisticsNode.path("likeCount").asLong(0L))
                    .commentCount(statisticsNode.path("commentCount").asLong(0L))
                    .build();
            item.getStatistics().add(history);
            return true;
        }
        return false;
    }
}
