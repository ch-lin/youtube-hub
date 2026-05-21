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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import ch.lin.platform.http.HttpClient;
import ch.lin.platform.http.exception.HttpException;
import ch.lin.youtube.hub.backend.api.app.repository.ChannelRepository;
import ch.lin.youtube.hub.backend.api.app.repository.PlaylistRepository;
import ch.lin.youtube.hub.backend.api.app.service.model.PageProcessingResult;
import ch.lin.youtube.hub.backend.api.app.service.model.PlaylistProcessingResult;
import ch.lin.youtube.hub.backend.api.common.exception.QuotaExceededException;
import ch.lin.youtube.hub.backend.api.common.exception.YoutubeApiAuthException;
import ch.lin.youtube.hub.backend.api.common.exception.YoutubeApiRequestException;
import ch.lin.youtube.hub.backend.api.domain.model.Channel;
import ch.lin.youtube.hub.backend.api.domain.model.Playlist;

/**
 * Implementation of {@link ChannelProcessingService}.
 * <p>
 * This service handles the logic for processing individual YouTube channels.
 */
@Service
public class ChannelProcessingServiceImpl implements ChannelProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(ChannelProcessingServiceImpl.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final ChannelRepository channelRepository;
    private final PlaylistRepository playlistRepository;
    private final YoutubeApiUsageService youtubeApiUsageService;
    private final PageProcessingService pageProcessingService;

    /**
     * Constructs a new ChannelProcessingServiceImpl.
     *
     * @param channelRepository the repository for channel data
     * @param playlistRepository the repository for playlist data
     * @param youtubeApiUsageService the service for tracking API usage
     * @param pageProcessingService the service for processing single pages
     */
    public ChannelProcessingServiceImpl(ChannelRepository channelRepository, PlaylistRepository playlistRepository,
            YoutubeApiUsageService youtubeApiUsageService, PageProcessingService pageProcessingService) {
        this.channelRepository = channelRepository;
        this.playlistRepository = playlistRepository;
        this.youtubeApiUsageService = youtubeApiUsageService;
        this.pageProcessingService = pageProcessingService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Playlist prepareChannelAndPlaylist(Channel channel, HttpClient client, String apiKey,
            long delayInMilliseconds, long quotaLimit, long quotaThreshold) {

        logger.info("Processing channel: {} ({})", channel.getTitle(), channel.getChannelId());

        // 1. Fetch channel details (Uploads Playlist ID & Title)
        Map<String, String> channelDetails = fetchChannelDetailsFromApi(client, channel.getChannelId(), apiKey,
                delayInMilliseconds, quotaLimit, quotaThreshold);

        String uploadsPlaylistId = channelDetails.get("uploadsPlaylistId");
        String latestTitle = channelDetails.get("title");
        Objects.requireNonNull(latestTitle);

        // 2. Update channel info if changed
        if (!latestTitle.isBlank() && !channel.getTitle().equals(latestTitle)) {
            logger.info("  -> Channel title has changed from '{}' to '{}'. Updating.", channel.getTitle(), latestTitle);
            channel.setTitle(latestTitle);
            channelRepository.save(channel);
        }

        // 3. Get or Create Playlist
        return playlistRepository.findByPlaylistId(uploadsPlaylistId)
                .orElseGet(() -> {
                    logger.info("  -> Uploads playlist with ID {} not found in DB. Creating it.", uploadsPlaylistId);
                    Playlist newPlaylist = new Playlist(uploadsPlaylistId);
                    newPlaylist.setTitle("Uploads from " + channel.getTitle());
                    newPlaylist.setChannel(channel);
                    return playlistRepository.save(Objects.requireNonNull(newPlaylist));
                });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PlaylistProcessingResult processPlaylistItems(Playlist playlist, HttpClient client, String apiKey,
            OffsetDateTime publishedAfter, boolean forcePublishedAfter,
            long delayInMilliseconds, long quotaLimit, long quotaThreshold) {
        // Note: 'playlist' might be detached here, but we pass its ID to the transactional service.

        logger.info("  -> Processing playlist: {} ({})", playlist.getTitle(), playlist.getPlaylistId());

        // 4. Fetch items
        return processPlaylistLoop(client, playlist, apiKey,
                publishedAfter, forcePublishedAfter, delayInMilliseconds, quotaLimit, quotaThreshold);
    }

    /**
     * Fetches channel details (uploads playlist ID and title) from the YouTube
     * Data API.
     *
     * @param client The reusable HttpClient for making the request.
     * @param channelId the ID of the YouTube channel
     * @param apiKey the YouTube Data API key
     * @param delayInMilliseconds the delay in milliseconds before making the
     * API request
     * @param quotaLimit the daily quota limit
     * @param quotaThreshold the safety threshold for quota
     * @return a map containing "uploadsPlaylistId" and "title"
     * @throws QuotaExceededException if the daily quota limit is reached.
     * @throws YoutubeApiAuthException if the API key is invalid.
     * @throws YoutubeApiRequestException if the API call fails or the response
     * cannot be parsed.
     */
    private Map<String, String> fetchChannelDetailsFromApi(HttpClient client, String channelId, String apiKey,
            long delayInMilliseconds, long quotaLimit, long quotaThreshold) {
        try {
            if (!youtubeApiUsageService.hasSufficientQuota(quotaLimit, quotaThreshold)) {
                throw new QuotaExceededException("Daily quota limit reached.");
            }
            Map<String, String> params = new HashMap<>();
            params.put("part", "contentDetails,snippet");
            params.put("id", channelId);
            params.put("key", apiKey);

            delayRequest(delayInMilliseconds);
            // Quota cost: 1 unit for channels.list operation
            youtubeApiUsageService.recordUsage(1L);
            String responseBody = client.get("/youtube/v3/channels", params, null).body();

            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            JsonNode itemNode = root.path("items").path(0);

            if (!itemNode.isMissingNode()) {
                String uploadsId = itemNode.path("contentDetails").path("relatedPlaylists").path("uploads")
                        .asText(null);
                String title = itemNode.path("snippet").path("title").asText(null);

                if (uploadsId != null && title != null) {
                    Map<String, String> details = new HashMap<>();
                    details.put("uploadsPlaylistId", uploadsId);
                    details.put("title", title);
                    return details;
                }
            }
            throw new YoutubeApiRequestException(
                    "Could not parse 'uploads' or 'title' from YouTube API response for channelId " + channelId);
        } catch (IOException | URISyntaxException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (e instanceof HttpException httpException) {
                if (httpException.getStatusCode() == 404) {
                    // Channel is likely terminated or does not exist.
                    logger.warn("Channel with ID {} not found on YouTube (HTTP 404). It might be terminated.", channelId);
                    throw new YoutubeApiRequestException("Channel not found on YouTube (HTTP 404).", e);
                }
                if (httpException.getStatusCode() == 400 && httpException.getMessage() != null
                        && httpException.getMessage().contains("API key not valid")) {
                    throw new YoutubeApiAuthException("The provided YouTube API key is not valid.", e);
                }
            }
            logger.error("Failed to fetch channel details from YouTube API for channelId {}: {}", channelId,
                    e.getMessage(), e);
            throw new YoutubeApiRequestException(
                    "Failed to fetch channel details from YouTube API for channelId " + channelId, e);
        }
    }

    /**
     * Fetches new video items from a playlist since the last processing time
     * and stores them in the database. It handles API pagination and stops
     * fetching when it encounters a video that is older than the playlist's
     * last `processedAt` timestamp.
     *
     * @param client The reusable HttpClient for making the request.
     * @param playlist the playlist to process
     * @param apiKey the YouTube Data API key
     * @param requestPublishedAfter optional datetime to filter videos
     * @param forcePublishedAfter if true, forces the use of
     * {@code requestPublishedAfter}
     * @param delayInMilliseconds the delay in milliseconds before each API
     * request
     * @param quotaLimit the daily quota limit
     * @param quotaThreshold the safety threshold for quota
     * @return a {@link PlaylistProcessingResult} containing the counts of new
     * and updated items
     * @throws QuotaExceededException if the daily quota limit is reached.
     * @throws YoutubeApiAuthException if the API key is invalid.
     * @throws YoutubeApiRequestException if the API call fails.
     */
    private PlaylistProcessingResult processPlaylistLoop(HttpClient client, Playlist playlist, String apiKey,
            OffsetDateTime requestPublishedAfter,
            boolean forcePublishedAfter,
            long delayInMilliseconds,
            long quotaLimit,
            long quotaThreshold) {
        OffsetDateTime lastProcessedAt = playlist.getProcessedAt();

        // Determine the effective cutoff time for fetching videos.
        OffsetDateTime effectivePublishedAfter = lastProcessedAt;
        if (requestPublishedAfter != null) {
            if (forcePublishedAfter || effectivePublishedAfter == null
                    || requestPublishedAfter.isAfter(effectivePublishedAfter)) {
                effectivePublishedAfter = requestPublishedAfter;
                logger.info("    -> Overriding last processed time with provided publishedAfter: {}",
                        requestPublishedAfter);
            }
        }

        logger.info("    -> Last processed at: {}. Using effective cutoff time: {}",
                lastProcessedAt == null ? "Never" : lastProcessedAt.toString(),
                effectivePublishedAfter == null ? "None" : effectivePublishedAfter.toString());

        OffsetDateTime newestVideoPublishedAt = null;
        String nextPageToken = playlist.getLastPageToken();
        if (nextPageToken != null) {
            logger.info("    -> Resuming from checkpoint token: {}", nextPageToken);
        }

        boolean stopFetching = false;
        PlaylistProcessingResult result = new PlaylistProcessingResult();

        do {
            try {
                // Delegate single page processing to the new transactional service.
                // This ensures each page is a separate transaction, preventing deadlocks.
                PageProcessingResult pageResult = pageProcessingService.processSinglePage(
                        playlist.getPlaylistId(), nextPageToken, client, apiKey,
                        effectivePublishedAfter, delayInMilliseconds, quotaLimit, quotaThreshold);

                // Aggregate statistics
                PlaylistProcessingResult pageStats = pageResult.getStats();
                result.setNewItemsCount(result.getNewItemsCount() + pageStats.getNewItemsCount());
                result.setStandardVideoCount(result.getStandardVideoCount() + pageStats.getStandardVideoCount());
                result.setUpcomingVideoCount(result.getUpcomingVideoCount() + pageStats.getUpcomingVideoCount());
                result.setLiveVideoCount(result.getLiveVideoCount() + pageStats.getLiveVideoCount());
                result.setUpdatedItemsCount(result.getUpdatedItemsCount() + pageStats.getUpdatedItemsCount());

                // Capture the newest video found so far (which is usually on the first page)
                if (newestVideoPublishedAt == null) {
                    newestVideoPublishedAt = pageResult.getNewestVideoPublishedAt();
                }

                nextPageToken = pageResult.getNextPageToken();
                stopFetching = pageResult.isStopFetching();

            } catch (Exception e) {
                logger.error("Error processing page for playlist {}", playlist.getPlaylistId(), e);
                // If quota is exceeded, we must stop immediately.
                if (e instanceof QuotaExceededException quotaExceededException) {
                    throw quotaExceededException;
                }
                // For other errors, we might choose to break or continue.
                // To be safe and consistent with previous behavior, we stop processing this playlist.
                throw new YoutubeApiRequestException("Failed to process playlist " + playlist.getPlaylistId(), e);
            }
        } while (!stopFetching && nextPageToken != null);

        // Finalize: Update the playlist's processedAt timestamp if we finished successfully.
        if (result.getNewItemsCount() > 0 && newestVideoPublishedAt != null) {
            logger.info("    -> Found {} new video(s) for playlist {}. Updating playlist's processedAt time to {}.",
                    result.getNewItemsCount(), playlist.getPlaylistId(), newestVideoPublishedAt);
            pageProcessingService.updatePlaylistProcessedAt(playlist.getPlaylistId(), newestVideoPublishedAt);
        } else {
            logger.info("    -> No new videos found for playlist {}.", playlist.getPlaylistId());
            // Even if no new videos, we should clear the token if we are done.
            if (playlist.getLastPageToken() != null) {
                // We can pass the old processedAt to keep it, but clear the token.
                pageProcessingService.updatePlaylistProcessedAt(playlist.getPlaylistId(), playlist.getProcessedAt());
            }
        }

        return result;
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
}
