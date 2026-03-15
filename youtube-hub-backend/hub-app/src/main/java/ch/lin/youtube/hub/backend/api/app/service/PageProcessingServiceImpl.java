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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import ch.lin.platform.http.HttpClient;
import ch.lin.platform.http.exception.HttpException;
import ch.lin.youtube.hub.backend.api.app.repository.ItemRepository;
import ch.lin.youtube.hub.backend.api.app.repository.PlaylistRepository;
import ch.lin.youtube.hub.backend.api.app.service.model.PageProcessingResult;
import ch.lin.youtube.hub.backend.api.app.service.model.PlaylistProcessingResult;
import ch.lin.youtube.hub.backend.api.common.exception.QuotaExceededException;
import ch.lin.youtube.hub.backend.api.common.exception.YoutubeApiAuthException;
import ch.lin.youtube.hub.backend.api.common.exception.YoutubeApiRequestException;
import ch.lin.youtube.hub.backend.api.domain.model.Item;
import ch.lin.youtube.hub.backend.api.domain.model.Playlist;

/**
 * Implementation of {@link PageProcessingService}.
 * <p>
 * This service handles the processing of a single page of YouTube playlist
 * items. It fetches items from the YouTube API, creates or updates them in the
 * database, and manages the playlist's state (page token).
 */
@Service
public class PageProcessingServiceImpl implements PageProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(PageProcessingServiceImpl.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final PlaylistRepository playlistRepository;
    private final ItemRepository itemRepository;
    private final YoutubeApiUsageService youtubeApiUsageService;
    private final VideoFetchService videoFetchService;

    /**
     * Constructs a new PageProcessingServiceImpl.
     *
     * @param playlistRepository the repository for playlist data
     * @param itemRepository the repository for item data
     * @param youtubeApiUsageService the service for tracking API usage
     * @param videoFetchService the service for fetching video details
     */
    public PageProcessingServiceImpl(PlaylistRepository playlistRepository, ItemRepository itemRepository,
            YoutubeApiUsageService youtubeApiUsageService, VideoFetchService videoFetchService) {
        this.playlistRepository = playlistRepository;
        this.itemRepository = itemRepository;
        this.youtubeApiUsageService = youtubeApiUsageService;
        this.videoFetchService = videoFetchService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public PageProcessingResult processSinglePage(String playlistId, String pageToken, HttpClient client, String apiKey,
            OffsetDateTime effectivePublishedAfter, long delayInMilliseconds, long quotaLimit, long quotaThreshold) {

        Playlist playlist = playlistRepository.findByPlaylistId(playlistId)
                .orElseThrow(() -> new IllegalArgumentException("Playlist not found: " + playlistId));

        if (!youtubeApiUsageService.hasSufficientQuota(quotaLimit, quotaThreshold)) {
            throw new QuotaExceededException("Daily quota limit reached.");
        }

        Map<String, String> params = new HashMap<>();
        params.put("part", "snippet");
        params.put("playlistId", playlistId);
        params.put("key", apiKey);
        params.put("maxResults", "50");
        if (pageToken != null) {
            params.put("pageToken", pageToken);
        }

        try {
            delayRequest(delayInMilliseconds);
            youtubeApiUsageService.recordUsage(1L);
            String responseBody = client.get("/youtube/v3/playlistItems", params, null).body();
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);

            String nextPageToken = root.path("nextPageToken").asText(null);
            JsonNode itemsNode = root.path("items");

            if (itemsNode.isMissingNode() || !itemsNode.isArray()) {
                logger.warn("    -> No items found in API response for playlist {}", playlistId);
                return new PageProcessingResult(null, new PlaylistProcessingResult(), true, null);
            }

            Map<String, JsonNode> newVideoSnippetsOnPage = new LinkedHashMap<>();
            List<Item> existingItemsToUpdateOnPage = new ArrayList<>();
            OffsetDateTime newestVideoOnPage = null;
            boolean stopFetching = false;

            for (JsonNode itemNode : itemsNode) {
                JsonNode snippet = itemNode.path("snippet");
                String videoId = snippet.path("resourceId").path("videoId").asText("");
                if (videoId.isBlank()) {
                    continue;
                }

                OffsetDateTime videoPublishedAt = OffsetDateTime.parse(snippet.path("publishedAt").asText());

                if (newestVideoOnPage == null) {
                    newestVideoOnPage = videoPublishedAt;
                }

                if (effectivePublishedAfter != null && !videoPublishedAt.isAfter(effectivePublishedAfter)) {
                    logger.info("    -> Reached video published at {}, which is not newer than last processed time {}. Stopping.",
                            videoPublishedAt, effectivePublishedAfter);
                    stopFetching = true;
                    break;
                }

                Optional<Item> existingItemOpt = itemRepository.findByVideoId(videoId);
                if (existingItemOpt.isPresent()) {
                    existingItemsToUpdateOnPage.add(existingItemOpt.get());
                } else {
                    newVideoSnippetsOnPage.put(videoId, snippet);
                }
            }

            PlaylistProcessingResult stats = new PlaylistProcessingResult();

            if (!newVideoSnippetsOnPage.isEmpty()) {
                List<Item> createdItems = videoFetchService.fetchAndCreateItemsFromVideoIds(client, apiKey, newVideoSnippetsOnPage,
                        delayInMilliseconds, quotaLimit, quotaThreshold);

                for (Item newItem : createdItems) {
                    newItem.setPlaylist(playlist);
                    stats.setNewItemsCount(stats.getNewItemsCount() + 1);
                    switch (newItem.getLiveBroadcastContent()) {
                        case NONE ->
                            stats.setStandardVideoCount(stats.getStandardVideoCount() + 1);
                        case UPCOMING ->
                            stats.setUpcomingVideoCount(stats.getUpcomingVideoCount() + 1);
                        case LIVE ->
                            stats.setLiveVideoCount(stats.getLiveVideoCount() + 1);
                    }
                }
                itemRepository.saveAll(Objects.requireNonNull(createdItems));
            }

            if (!existingItemsToUpdateOnPage.isEmpty()) {
                int updated = videoFetchService.updateExistingItems(client, apiKey, existingItemsToUpdateOnPage,
                        delayInMilliseconds, quotaLimit, quotaThreshold);
                stats.setUpdatedItemsCount(updated);
            }

            playlist.setLastPageToken(stopFetching ? null : nextPageToken);
            playlistRepository.save(playlist);

            return new PageProcessingResult(nextPageToken, stats, stopFetching, newestVideoOnPage);

        } catch (IOException | URISyntaxException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (e instanceof HttpException && ((HttpException) e).getStatusCode() == 400
                    && e.getMessage() != null && e.getMessage().contains("API key not valid")) {
                throw new YoutubeApiAuthException("The provided YouTube API key is not valid.", e);
            }
            throw new YoutubeApiRequestException("Failed to process page for playlist " + playlistId, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void updatePlaylistProcessedAt(String playlistId, OffsetDateTime processedAt) {
        Playlist playlist = playlistRepository.findByPlaylistId(playlistId).orElseThrow();
        playlist.setProcessedAt(processedAt);
        playlist.setLastPageToken(null);
        playlistRepository.save(playlist);
    }

    /**
     * Pauses execution for a specified duration.
     *
     * @param milliseconds The number of milliseconds to wait.
     * @throws InterruptedException if the thread is interrupted while sleeping.
     */
    private void delayRequest(long milliseconds) throws InterruptedException {
        if (milliseconds > 0) {
            Thread.sleep(milliseconds);
        }
    }
}
