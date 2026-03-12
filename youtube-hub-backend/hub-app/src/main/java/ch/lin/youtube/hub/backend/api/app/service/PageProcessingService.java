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

import java.time.OffsetDateTime;

import ch.lin.platform.http.HttpClient;
import ch.lin.youtube.hub.backend.api.app.service.model.PageProcessingResult;

/**
 * Service responsible for processing a single page of YouTube playlist items
 * within a transaction.
 */
public interface PageProcessingService {

    /**
     * Processes a single page of a playlist: fetches items, saves them, updates
     * existing ones, and updates the playlist token. This method MUST be
     * executed within a transaction.
     *
     * @param playlistId The ID of the playlist to process.
     * @param pageToken The token for the page to fetch (can be null for the
     * first page).
     * @param client The HTTP client.
     * @param apiKey The YouTube API key.
     * @param effectivePublishedAfter The cutoff date for fetching videos.
     * @param delayInMilliseconds Delay before API call.
     * @param quotaLimit Quota limit.
     * @param quotaThreshold Quota threshold.
     * @return The result of processing the page.
     */
    PageProcessingResult processSinglePage(String playlistId, String pageToken, HttpClient client, String apiKey,
            OffsetDateTime effectivePublishedAfter, long delayInMilliseconds, long quotaLimit, long quotaThreshold);

    /**
     * Updates the playlist's processedAt timestamp and clears the page token.
     * This is typically called after all pages have been successfully
     * processed.
     *
     * @param playlistId The ID of the playlist.
     * @param processedAt The timestamp of the newest video found.
     */
    void updatePlaylistProcessedAt(String playlistId, OffsetDateTime processedAt);
}
