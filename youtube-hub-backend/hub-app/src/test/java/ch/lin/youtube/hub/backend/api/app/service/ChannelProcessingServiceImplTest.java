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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

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

@ExtendWith(MockitoExtension.class)
class ChannelProcessingServiceImplTest {

    @Mock
    private ChannelRepository channelRepository;
    @Mock
    private PlaylistRepository playlistRepository;
    @Mock
    private YoutubeApiUsageService youtubeApiUsageService;
    @Mock
    private PageProcessingService pageProcessingService;
    @Mock
    private HttpClient httpClient;

    private ChannelProcessingServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        service = new ChannelProcessingServiceImpl(channelRepository, playlistRepository, youtubeApiUsageService, pageProcessingService);
    }

    @Test
    @SuppressWarnings("null")
    void prepareChannelAndPlaylist_ShouldReturnPlaylist_WhenExists() throws Exception {
        Channel channel = new Channel();
        channel.setChannelId("UC123");
        channel.setTitle("Title");

        String responseBody = """
            {
              "items": [
                {
                  "contentDetails": { "relatedPlaylists": { "uploads": "UU123" } },
                  "snippet": { "title": "Title" }
                }
              ]
            }
            """;

        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);
        when(httpClient.get(anyString(), anyMap(), any())).thenReturn(new HttpClient.Response(200, responseBody));

        Playlist existingPlaylist = new Playlist();
        existingPlaylist.setPlaylistId("UU123");
        when(playlistRepository.findByPlaylistId("UU123")).thenReturn(Optional.of(existingPlaylist));

        Playlist result = service.prepareChannelAndPlaylist(channel, httpClient, "key", 0, 100, 10);

        assertThat(result).isEqualTo(existingPlaylist);
        verify(channelRepository, never()).save(any());
        verify(playlistRepository, never()).save(any());
        verify(youtubeApiUsageService).recordUsage(1L);
    }

    @Test
    @SuppressWarnings("null")
    void prepareChannelAndPlaylist_ShouldCreatePlaylist_WhenNotExists() throws Exception {
        Channel channel = new Channel();
        channel.setChannelId("UC123");
        channel.setTitle("Title");

        String responseBody = """
            {
              "items": [
                {
                  "contentDetails": { "relatedPlaylists": { "uploads": "UU123" } },
                  "snippet": { "title": "Title" }
                }
              ]
            }
            """;

        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);
        when(httpClient.get(anyString(), anyMap(), any())).thenReturn(new HttpClient.Response(200, responseBody));
        when(playlistRepository.findByPlaylistId("UU123")).thenReturn(Optional.empty());
        when(playlistRepository.save(any(Playlist.class))).thenAnswer(i -> i.getArgument(0));

        Playlist result = service.prepareChannelAndPlaylist(channel, httpClient, "key", 0, 100, 10);

        assertThat(result.getPlaylistId()).isEqualTo("UU123");
        assertThat(result.getTitle()).contains("Title");
        verify(playlistRepository).save(any(Playlist.class));
    }

    @Test
    void prepareChannelAndPlaylist_ShouldUpdateChannelTitle_WhenChanged() throws Exception {
        Channel channel = new Channel();
        channel.setChannelId("UC123");
        channel.setTitle("Old Title");

        String responseBody = """
            {
              "items": [
                {
                  "contentDetails": { "relatedPlaylists": { "uploads": "UU123" } },
                  "snippet": { "title": "New Title" }
                }
              ]
            }
            """;

        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);
        when(httpClient.get(anyString(), anyMap(), any())).thenReturn(new HttpClient.Response(200, responseBody));
        when(playlistRepository.findByPlaylistId("UU123")).thenReturn(Optional.of(new Playlist()));

        service.prepareChannelAndPlaylist(channel, httpClient, "key", 0, 100, 10);

        assertThat(channel.getTitle()).isEqualTo("New Title");
        verify(channelRepository).save(channel);
    }

    @Test
    void prepareChannelAndPlaylist_ShouldNotUpdateChannelTitle_WhenApiTitleIsBlank() throws Exception {
        Channel channel = new Channel();
        channel.setChannelId("UC123");
        channel.setTitle("Original Title");

        // API returns blank title (whitespace only)
        String responseBody = """
            {
              "items": [
                {
                  "contentDetails": { "relatedPlaylists": { "uploads": "UU123" } },
                  "snippet": { "title": "   " }
                }
              ]
            }
            """;

        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);
        when(httpClient.get(anyString(), anyMap(), any())).thenReturn(new HttpClient.Response(200, responseBody));
        when(playlistRepository.findByPlaylistId("UU123")).thenReturn(Optional.of(new Playlist()));

        service.prepareChannelAndPlaylist(channel, httpClient, "key", 0, 100, 10);

        assertThat(channel.getTitle()).isEqualTo("Original Title");
        verify(channelRepository, never()).save(channel);
    }

    @Test
    void prepareChannelAndPlaylist_ShouldNotUpdateChannelTitle_WhenTitleUnchanged() throws Exception {
        Channel channel = new Channel();
        channel.setChannelId("UC123");
        channel.setTitle("Same Title");

        String responseBody = """
            {
              "items": [
                {
                  "contentDetails": { "relatedPlaylists": { "uploads": "UU123" } },
                  "snippet": { "title": "Same Title" }
                }
              ]
            }
            """;

        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);
        when(httpClient.get(anyString(), anyMap(), any())).thenReturn(new HttpClient.Response(200, responseBody));
        when(playlistRepository.findByPlaylistId("UU123")).thenReturn(Optional.of(new Playlist()));

        service.prepareChannelAndPlaylist(channel, httpClient, "key", 0, 100, 10);

        verify(channelRepository, never()).save(channel);
    }

    @Test
    void prepareChannelAndPlaylist_ShouldThrow_WhenQuotaExceeded() {
        Channel channel = new Channel();
        channel.setChannelId("UC123");
        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(false);

        assertThatThrownBy(() -> service.prepareChannelAndPlaylist(channel, httpClient, "key", 0, 100, 10))
                .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    void prepareChannelAndPlaylist_ShouldThrowAuthException_WhenApiKeyInvalid() throws Exception {
        Channel channel = new Channel();
        channel.setChannelId("UC123");
        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);
        when(httpClient.get(anyString(), anyMap(), any()))
                .thenThrow(new HttpException("GET", 400, "API key not valid"));

        assertThatThrownBy(() -> service.prepareChannelAndPlaylist(channel, httpClient, "key", 0, 100, 10))
                .isInstanceOf(YoutubeApiAuthException.class);
    }

    @Test
    void prepareChannelAndPlaylist_ShouldThrowRequestException_WhenIoException() throws Exception {
        Channel channel = new Channel();
        channel.setChannelId("UC123");
        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);
        when(httpClient.get(anyString(), anyMap(), any())).thenThrow(new IOException("Network error"));

        assertThatThrownBy(() -> service.prepareChannelAndPlaylist(channel, httpClient, "key", 0, 100, 10))
                .isInstanceOf(YoutubeApiRequestException.class);
    }

    @Test
    void prepareChannelAndPlaylist_ShouldThrowRequestException_WhenResponseHasNoItems() throws Exception {
        Channel channel = new Channel();
        channel.setChannelId("UC123");

        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);
        String responseBody = "{\"items\": []}";
        when(httpClient.get(anyString(), anyMap(), any())).thenReturn(new HttpClient.Response(200, responseBody));

        assertThatThrownBy(() -> service.prepareChannelAndPlaylist(channel, httpClient, "key", 0, 100, 10))
                .isInstanceOf(YoutubeApiRequestException.class)
                .hasMessageContaining("Could not parse 'uploads' or 'title'");
    }

    @Test
    void prepareChannelAndPlaylist_ShouldThrowRequestException_WhenResponseMissingUploadsId() throws Exception {
        Channel channel = new Channel();
        channel.setChannelId("UC123");

        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);
        // uploads is missing
        String responseBody = """
            {
              "items": [
                {
                  "contentDetails": { "relatedPlaylists": {} },
                  "snippet": { "title": "Title" }
                }
              ]
            }
            """;
        when(httpClient.get(anyString(), anyMap(), any())).thenReturn(new HttpClient.Response(200, responseBody));

        assertThatThrownBy(() -> service.prepareChannelAndPlaylist(channel, httpClient, "key", 0, 100, 10))
                .isInstanceOf(YoutubeApiRequestException.class)
                .hasMessageContaining("Could not parse 'uploads' or 'title'");
    }

    @Test
    void prepareChannelAndPlaylist_ShouldThrowRequestException_WhenResponseMissingTitle() throws Exception {
        Channel channel = new Channel();
        channel.setChannelId("UC123");

        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);
        // title is missing
        String responseBody = """
            {
              "items": [
                {
                  "contentDetails": { "relatedPlaylists": { "uploads": "UU123" } },
                  "snippet": {}
                }
              ]
            }
            """;
        when(httpClient.get(anyString(), anyMap(), any())).thenReturn(new HttpClient.Response(200, responseBody));

        assertThatThrownBy(() -> service.prepareChannelAndPlaylist(channel, httpClient, "key", 0, 100, 10))
                .isInstanceOf(YoutubeApiRequestException.class)
                .hasMessageContaining("Could not parse 'uploads' or 'title'");
    }

    @Test
    void prepareChannelAndPlaylist_ShouldThrowRequestException_WhenHttpExceptionNotAuth() throws Exception {
        Channel channel = new Channel();
        channel.setChannelId("UC123");

        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);
        when(httpClient.get(anyString(), anyMap(), any()))
                .thenThrow(new HttpException("GET", 400, "Bad Request")); // Not "API key not valid"

        assertThatThrownBy(() -> service.prepareChannelAndPlaylist(channel, httpClient, "key", 0, 100, 10))
                .isInstanceOf(YoutubeApiRequestException.class)
                .hasMessageContaining("Failed to fetch channel details");
    }

    @Test
    void prepareChannelAndPlaylist_ShouldThrowRequestException_WhenHttpException400WithNullMessage() throws Exception {
        Channel channel = new Channel();
        channel.setChannelId("UC123");

        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);
        HttpException mockException = org.mockito.Mockito.mock(HttpException.class);
        when(mockException.getStatusCode()).thenReturn(400);
        when(mockException.getMessage()).thenReturn(null);

        when(httpClient.get(anyString(), anyMap(), any()))
                .thenThrow(mockException);

        assertThatThrownBy(() -> service.prepareChannelAndPlaylist(channel, httpClient, "key", 0, 100, 10))
                .isInstanceOf(YoutubeApiRequestException.class)
                .hasMessageContaining("Failed to fetch channel details");
    }

    @Test
    void prepareChannelAndPlaylist_ShouldThrowRequestException_WhenHttpException500() throws Exception {
        Channel channel = new Channel();
        channel.setChannelId("UC123");

        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);
        when(httpClient.get(anyString(), anyMap(), any()))
                .thenThrow(new HttpException("GET", 500, "Internal Server Error"));

        assertThatThrownBy(() -> service.prepareChannelAndPlaylist(channel, httpClient, "key", 0, 100, 10))
                .isInstanceOf(YoutubeApiRequestException.class)
                .hasMessageContaining("Failed to fetch channel details");
    }

    @Test
    void prepareChannelAndPlaylist_ShouldThrowRequestException_WhenURISyntaxException() throws Exception {
        Channel channel = new Channel();
        channel.setChannelId("UC123");

        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);
        when(httpClient.get(anyString(), anyMap(), any()))
                .thenThrow(new URISyntaxException("input", "reason"));

        assertThatThrownBy(() -> service.prepareChannelAndPlaylist(channel, httpClient, "key", 0, 100, 10))
                .isInstanceOf(YoutubeApiRequestException.class)
                .hasMessageContaining("Failed to fetch channel details");
    }

    @Test
    void processPlaylistItems_ShouldProcessSinglePage() {
        Playlist playlist = new Playlist();
        playlist.setPlaylistId("UU123");

        OffsetDateTime now = OffsetDateTime.now();
        PageProcessingResult pageResult = new PageProcessingResult(null, new PlaylistProcessingResult(), true, now);
        pageResult.getStats().setNewItemsCount(5);

        when(pageProcessingService.processSinglePage(eq("UU123"), any(), eq(httpClient), eq("key"), any(), anyLong(), anyLong(), anyLong()))
                .thenReturn(pageResult);

        PlaylistProcessingResult result = service.processPlaylistItems(playlist, httpClient, "key", null, false, 0, 100, 10);

        assertThat(result.getNewItemsCount()).isEqualTo(5);
        verify(pageProcessingService).updatePlaylistProcessedAt("UU123", now);
    }

    @Test
    void processPlaylistItems_ShouldLoopMultiplePages() {
        Playlist playlist = new Playlist();
        playlist.setPlaylistId("UU123");

        PageProcessingResult page1 = new PageProcessingResult("token2", new PlaylistProcessingResult(), false, null);
        PageProcessingResult page2 = new PageProcessingResult(null, new PlaylistProcessingResult(), true, null);

        when(pageProcessingService.processSinglePage(eq("UU123"), eq(null), any(), any(), any(), anyLong(), anyLong(), anyLong()))
                .thenReturn(page1);
        when(pageProcessingService.processSinglePage(eq("UU123"), eq("token2"), any(), any(), any(), anyLong(), anyLong(), anyLong()))
                .thenReturn(page2);

        service.processPlaylistItems(playlist, httpClient, "key", null, false, 0, 100, 10);

        verify(pageProcessingService, times(2)).processSinglePage(eq("UU123"), any(), any(), any(), any(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void processPlaylistItems_ShouldRespectPublishedAfter() {
        Playlist playlist = new Playlist();
        playlist.setPlaylistId("UU123");
        OffsetDateTime publishedAfter = OffsetDateTime.now().minusDays(1);

        PageProcessingResult pageResult = new PageProcessingResult(null, new PlaylistProcessingResult(), true, null);
        when(pageProcessingService.processSinglePage(any(), any(), any(), any(), eq(publishedAfter), anyLong(), anyLong(), anyLong()))
                .thenReturn(pageResult);

        service.processPlaylistItems(playlist, httpClient, "key", publishedAfter, true, 0, 100, 10);

        verify(pageProcessingService).processSinglePage(any(), any(), any(), any(), eq(publishedAfter), anyLong(), anyLong(), anyLong());
    }

    @Test
    void processPlaylistItems_ShouldUseRequestPublishedAfter_WhenLastProcessedAtIsNull() {
        Playlist playlist = new Playlist();
        playlist.setPlaylistId("UU123");
        playlist.setProcessedAt(null); // effectivePublishedAfter starts as null

        OffsetDateTime requestPublishedAfter = OffsetDateTime.now().minusDays(1);

        PageProcessingResult pageResult = new PageProcessingResult(null, new PlaylistProcessingResult(), true, null);
        when(pageProcessingService.processSinglePage(any(), any(), any(), any(), eq(requestPublishedAfter), anyLong(), anyLong(), anyLong()))
                .thenReturn(pageResult);

        // forcePublishedAfter = false
        service.processPlaylistItems(playlist, httpClient, "key", requestPublishedAfter, false, 0, 100, 10);

        verify(pageProcessingService).processSinglePage(any(), any(), any(), any(), eq(requestPublishedAfter), anyLong(), anyLong(), anyLong());
    }

    @Test
    void processPlaylistItems_ShouldUseRequestPublishedAfter_WhenNewerThanLastProcessedAt() {
        Playlist playlist = new Playlist();
        playlist.setPlaylistId("UU123");
        OffsetDateTime lastProcessedAt = OffsetDateTime.now().minusDays(10);
        playlist.setProcessedAt(lastProcessedAt);

        OffsetDateTime requestPublishedAfter = OffsetDateTime.now().minusDays(5); // Newer than lastProcessedAt

        PageProcessingResult pageResult = new PageProcessingResult(null, new PlaylistProcessingResult(), true, null);
        when(pageProcessingService.processSinglePage(any(), any(), any(), any(), eq(requestPublishedAfter), anyLong(), anyLong(), anyLong()))
                .thenReturn(pageResult);

        service.processPlaylistItems(playlist, httpClient, "key", requestPublishedAfter, false, 0, 100, 10);

        verify(pageProcessingService).processSinglePage(any(), any(), any(), any(), eq(requestPublishedAfter), anyLong(), anyLong(), anyLong());
    }

    @Test
    void processPlaylistItems_ShouldUseLastProcessedAt_WhenRequestPublishedAfterIsOlder() {
        Playlist playlist = new Playlist();
        playlist.setPlaylistId("UU123");
        OffsetDateTime lastProcessedAt = OffsetDateTime.now().minusDays(5);
        playlist.setProcessedAt(lastProcessedAt);

        OffsetDateTime requestPublishedAfter = OffsetDateTime.now().minusDays(10); // Older than lastProcessedAt

        PageProcessingResult pageResult = new PageProcessingResult(null, new PlaylistProcessingResult(), true, null);
        // Should use lastProcessedAt because requestPublishedAfter is older and force is false
        when(pageProcessingService.processSinglePage(any(), any(), any(), any(), eq(lastProcessedAt), anyLong(), anyLong(), anyLong()))
                .thenReturn(pageResult);

        service.processPlaylistItems(playlist, httpClient, "key", requestPublishedAfter, false, 0, 100, 10);

        verify(pageProcessingService).processSinglePage(any(), any(), any(), any(), eq(lastProcessedAt), anyLong(), anyLong(), anyLong());
    }

    @Test
    void processPlaylistItems_ShouldStopOnQuotaExceeded() {
        Playlist playlist = new Playlist();
        playlist.setPlaylistId("UU123");

        when(pageProcessingService.processSinglePage(any(), any(), any(), any(), any(), anyLong(), anyLong(), anyLong()))
                .thenThrow(new QuotaExceededException("Quota limit reached"));

        assertThatThrownBy(() -> service.processPlaylistItems(playlist, httpClient, "key", null, false, 0, 100, 10))
                .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    void processPlaylistItems_ShouldClearToken_WhenNoNewItemsButFinished() {
        Playlist playlist = new Playlist();
        playlist.setPlaylistId("UU123");
        playlist.setLastPageToken("oldToken");
        OffsetDateTime oldProcessedAt = OffsetDateTime.now().minusDays(5);
        playlist.setProcessedAt(oldProcessedAt);

        PageProcessingResult pageResult = new PageProcessingResult(null, new PlaylistProcessingResult(), true, null);
        // No new items
        when(pageProcessingService.processSinglePage(any(), any(), any(), any(), any(), anyLong(), anyLong(), anyLong()))
                .thenReturn(pageResult);

        service.processPlaylistItems(playlist, httpClient, "key", null, false, 0, 100, 10);

        // Should update with old processedAt to clear token
        verify(pageProcessingService).updatePlaylistProcessedAt("UU123", oldProcessedAt);
    }

    @Test
    void prepareChannelAndPlaylist_ShouldHandleInterruptedException() {
        Channel channel = new Channel();
        channel.setChannelId("UC123");

        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);

        // Interrupt the current thread before calling the service to trigger InterruptedException in Thread.sleep()
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> service.prepareChannelAndPlaylist(channel, httpClient, "key", 100, 100, 10))
                .isInstanceOf(YoutubeApiRequestException.class);

        // Verify the interrupt status was restored (and clear it for subsequent tests)
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void processPlaylistItems_ShouldPreserveNewestVideo_AcrossPages() {
        Playlist playlist = new Playlist();
        playlist.setPlaylistId("UU123");

        OffsetDateTime newest = OffsetDateTime.now();
        PlaylistProcessingResult stats1 = new PlaylistProcessingResult();
        stats1.setNewItemsCount(1);

        // First page finds a video (newestVideoPublishedAt becomes non-null)
        PageProcessingResult page1 = new PageProcessingResult("token2", stats1, false, newest);
        // Second page (newestVideoPublishedAt is already set, so if branch is skipped)
        PageProcessingResult page2 = new PageProcessingResult(null, new PlaylistProcessingResult(), true, null);

        when(pageProcessingService.processSinglePage(eq("UU123"), eq(null), any(), any(), any(), anyLong(), anyLong(), anyLong()))
                .thenReturn(page1);
        when(pageProcessingService.processSinglePage(eq("UU123"), eq("token2"), any(), any(), any(), anyLong(), anyLong(), anyLong()))
                .thenReturn(page2);

        service.processPlaylistItems(playlist, httpClient, "key", null, false, 0, 100, 10);

        verify(pageProcessingService).updatePlaylistProcessedAt("UU123", newest);
    }

    @Test
    void processPlaylistItems_ShouldWrapGenericException() {
        Playlist playlist = new Playlist();
        playlist.setPlaylistId("UU123");

        when(pageProcessingService.processSinglePage(any(), any(), any(), any(), any(), anyLong(), anyLong(), anyLong()))
                .thenThrow(new RuntimeException("Unexpected error"));

        assertThatThrownBy(() -> service.processPlaylistItems(playlist, httpClient, "key", null, false, 0, 100, 10))
                .isInstanceOf(YoutubeApiRequestException.class)
                .hasMessageContaining("Failed to process playlist UU123");
    }

    @Test
    void processPlaylistItems_ShouldStop_WhenStopFetchingIsTrue_EvenIfTokenExists() {
        Playlist playlist = new Playlist();
        playlist.setPlaylistId("UU123");

        // stopFetching = true, nextPageToken != null
        PageProcessingResult pageResult = new PageProcessingResult("nextToken", new PlaylistProcessingResult(), true, null);

        when(pageProcessingService.processSinglePage(any(), any(), any(), any(), any(), anyLong(), anyLong(), anyLong()))
                .thenReturn(pageResult);

        service.processPlaylistItems(playlist, httpClient, "key", null, false, 0, 100, 10);

        verify(pageProcessingService, times(1)).processSinglePage(any(), any(), any(), any(), any(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void processPlaylistItems_ShouldStop_WhenNoNextPageToken_AndStopFetchingIsFalse() {
        Playlist playlist = new Playlist();
        playlist.setPlaylistId("UU123");

        // stopFetching = false, nextPageToken = null
        PageProcessingResult pageResult = new PageProcessingResult(null, new PlaylistProcessingResult(), false, null);

        when(pageProcessingService.processSinglePage(any(), any(), any(), any(), any(), anyLong(), anyLong(), anyLong()))
                .thenReturn(pageResult);

        service.processPlaylistItems(playlist, httpClient, "key", null, false, 0, 100, 10);

        verify(pageProcessingService, times(1)).processSinglePage(any(), any(), any(), any(), any(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void processPlaylistItems_ShouldFallbackToOldDate_WhenNewItemsHaveNoDate() {
        Playlist playlist = new Playlist();
        playlist.setPlaylistId("UU123");
        playlist.setLastPageToken("oldToken");
        OffsetDateTime oldDate = OffsetDateTime.now().minusDays(10);
        playlist.setProcessedAt(oldDate);

        PlaylistProcessingResult stats = new PlaylistProcessingResult();
        stats.setNewItemsCount(1);
        // newestVideoPublishedAt is null
        PageProcessingResult pageResult = new PageProcessingResult(null, stats, true, null);

        when(pageProcessingService.processSinglePage(any(), any(), any(), any(), any(), anyLong(), anyLong(), anyLong()))
                .thenReturn(pageResult);

        service.processPlaylistItems(playlist, httpClient, "key", null, false, 0, 100, 10);

        // Should call update with OLD date because newestVideoPublishedAt was null
        verify(pageProcessingService).updatePlaylistProcessedAt("UU123", oldDate);
    }

    @Test
    void prepareChannelAndPlaylist_ShouldWait_WhenDelayIsPositive() throws Exception {
        Channel channel = new Channel();
        channel.setChannelId("UC123");
        channel.setTitle("Title");

        String responseBody = "{\"items\": [{\"contentDetails\": {\"relatedPlaylists\": {\"uploads\": \"UU123\"}}, \"snippet\": {\"title\": \"Title\"}}]}";
        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);
        when(httpClient.get(anyString(), anyMap(), any())).thenReturn(new HttpClient.Response(200, responseBody));
        when(playlistRepository.findByPlaylistId("UU123")).thenReturn(Optional.of(new Playlist()));

        // Execute with 10ms delay to cover Thread.sleep branch
        service.prepareChannelAndPlaylist(channel, httpClient, "key", 10, 100, 10);

        verify(httpClient).get(anyString(), anyMap(), any());
    }
}
