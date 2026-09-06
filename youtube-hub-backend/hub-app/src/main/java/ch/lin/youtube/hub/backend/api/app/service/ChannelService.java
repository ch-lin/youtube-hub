/*
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
 */
package ch.lin.youtube.hub.backend.api.app.service;

import java.util.List;

import ch.lin.youtube.hub.backend.api.app.service.model.AddChannelsResult;
import ch.lin.youtube.hub.backend.api.domain.model.Channel;

/**
 * Defines the service layer contract for managing YouTube channels.
 * <p>
 * This interface outlines the core business logic for operations such as
 * retrieving, adding, deleting, and fetching details for channels.
 */
public interface ChannelService {

    /**
     * Retrieves all channels stored in the local database.
     *
     * @return A list of all {@link Channel} entities.
     */
    List<Channel> getAllChannels();

    /**
     * Adds new channels by parsing a list of YouTube channel URLs.
     * <p>
     * For each URL, it extracts the channel ID, fetches channel details from
     * the YouTube API, and persists the new channel. It handles existing
     * channels gracefully and reports any URLs that could not be processed.
     *
     * @param apiKey The YouTube Data API key required for fetching channel
     * information.
     * @param configName The name of the configuration to use for resolving the
     * API key.
     * @param urls A list of YouTube channel URLs to add.
     * @return An {@link AddChannelsResult} object containing lists of
     * successfully added channels and failed URLs.
     */
    AddChannelsResult addChannelsByUrl(String apiKey, String configName, List<String> urls);

    /**
     * Fetches raw channel details as a JSON string from the YouTube Data API.
     *
     * @param channelId The unique ID of the YouTube channel.
     * @param apiKey The YouTube Data API key.
     * @param configName The name of the configuration to use for resolving the
     * API key.
     * @return A JSON string representing the channel details from the API
     * response.
     */
    String getChannelDetailsFromApi(String channelId, String apiKey, String configName);

    /**
     * Deletes a channel from the local database based on its YouTube channel
     * ID.
     *
     * @param channelId The unique ID of the channel to be deleted.
     */
    void deleteChannel(String channelId);

}
