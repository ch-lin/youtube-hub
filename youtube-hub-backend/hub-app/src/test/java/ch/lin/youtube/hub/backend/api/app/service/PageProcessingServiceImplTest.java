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
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import ch.lin.platform.http.HttpClient;
import ch.lin.platform.http.exception.HttpException;
import ch.lin.youtube.hub.backend.api.app.repository.ItemRepository;
import ch.lin.youtube.hub.backend.api.app.repository.PlaylistRepository;
import ch.lin.youtube.hub.backend.api.app.service.model.PageProcessingResult;
import ch.lin.youtube.hub.backend.api.common.exception.QuotaExceededException;
import ch.lin.youtube.hub.backend.api.common.exception.YoutubeApiAuthException;
import ch.lin.youtube.hub.backend.api.common.exception.YoutubeApiRequestException;
import ch.lin.youtube.hub.backend.api.domain.model.Item;
import ch.lin.youtube.hub.backend.api.domain.model.LiveBroadcastContent;
import ch.lin.youtube.hub.backend.api.domain.model.Playlist;

@ExtendWith(MockitoExtension.class)
class PageProcessingServiceImplTest {

    @Mock
    private PlaylistRepository playlistRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private YoutubeApiUsageService youtubeApiUsageService;
    @Mock
    private VideoFetchService videoFetchService;
    @Mock
    private HttpClient httpClient;

    private PageProcessingServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        service = new PageProcessingServiceImpl(playlistRepository, itemRepository, youtubeApiUsageService, videoFetchService);
    }

    @Test
    void processSinglePage_ShouldThrow_WhenPlaylistNotFound() {
        when(playlistRepository.findByPlaylistId("PL123")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.processSinglePage("PL123", null, httpClient, "key", null, 0, 100, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Playlist not found");
    }

    @Test
    void processSinglePage_ShouldThrow_WhenQuotaExceeded() {
        when(playlistRepository.findByPlaylistId("PL123")).thenReturn(Optional.of(new Playlist()));
        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(false);

        assertThatThrownBy(() -> service.processSinglePage("PL123", null, httpClient, "key", null, 0, 100, 10))
                .isInstanceOf(QuotaExceededException.class);
    }

    @Test
    void processSinglePage_ShouldReturnEmpty_WhenNoItemsInResponse() throws Exception {
        Playlist playlist = new Playlist();
        when(playlistRepository.findByPlaylistId("PL123")).thenReturn(Optional.of(playlist));
        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);

        String responseBody = "{\"items\": []}";
        when(httpClient.get(any(), anyMap(), any())).thenReturn(new HttpClient.Response(200, responseBody));

        PageProcessingResult result = service.processSinglePage("PL123", null, httpClient, "key", null, 0, 100, 10);

        assertThat(result.getStats().getNewItemsCount()).isEqualTo(0);
        assertThat(result.isStopFetching()).isFalse();
        assertThat(result.getNextPageToken()).isNull();
        verify(youtubeApiUsageService).recordUsage(1L);
    }

    @Test
    @SuppressWarnings("null")
    void processSinglePage_ShouldProcessNewItems() throws Exception {
        Playlist playlist = new Playlist();
        when(playlistRepository.findByPlaylistId("PL123")).thenReturn(Optional.of(playlist));
        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);

        String publishedAt = OffsetDateTime.now().toString();
        String responseBody = """
            {
                "nextPageToken": "token123",
                "items": [
                    {
                        "snippet": {
                            "resourceId": { "videoId": "v1" },
                            "publishedAt": "%s"
                        }
                    }
                ]
            }
            """.formatted(publishedAt);
        when(httpClient.get(any(), anyMap(), any())).thenReturn(new HttpClient.Response(200, responseBody));
        when(itemRepository.findByVideoId("v1")).thenReturn(Optional.empty());

        Item newItem = new Item();
        newItem.setVideoId("v1");
        newItem.setLiveBroadcastContent(LiveBroadcastContent.NONE);
        when(videoFetchService.fetchAndCreateItemsFromVideoIds(any(), any(), anyMap(), anyLong(), anyLong(), anyLong()))
                .thenReturn(List.of(newItem));

        PageProcessingResult result = service.processSinglePage("PL123", null, httpClient, "key", null, 0, 100, 10);

        assertThat(result.getStats().getNewItemsCount()).isEqualTo(1);
        assertThat(result.getStats().getStandardVideoCount()).isEqualTo(1);
        assertThat(result.getNextPageToken()).isEqualTo("token123");
        assertThat(result.isStopFetching()).isFalse();
        verify(itemRepository).saveAll(anyList());
        verify(playlistRepository).save(playlist);
    }

    @Test
    @SuppressWarnings("null")
    void processSinglePage_ShouldUpdateExistingItems() throws Exception {
        Playlist playlist = new Playlist();
        when(playlistRepository.findByPlaylistId("PL123")).thenReturn(Optional.of(playlist));
        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);

        String publishedAt = OffsetDateTime.now().toString();
        String responseBody = """
            {
                "items": [
                    {
                        "snippet": {
                            "resourceId": { "videoId": "v1" },
                            "publishedAt": "%s"
                        }
                    }
                ]
            }
            """.formatted(publishedAt);
        when(httpClient.get(any(), anyMap(), any())).thenReturn(new HttpClient.Response(200, responseBody));

        Item existingItem = new Item();
        existingItem.setVideoId("v1");
        when(itemRepository.findByVideoId("v1")).thenReturn(Optional.of(existingItem));
        when(videoFetchService.updateExistingItems(any(), any(), anyList(), anyLong(), anyLong(), anyLong()))
                .thenReturn(1);

        PageProcessingResult result = service.processSinglePage("PL123", null, httpClient, "key", null, 0, 100, 10);

        assertThat(result.getStats().getNewItemsCount()).isEqualTo(0);
        assertThat(result.getStats().getUpdatedItemsCount()).isEqualTo(1);
        verify(itemRepository, never()).saveAll(anyList());
    }

    @Test
    void processSinglePage_ShouldStopFetching_WhenPublishedAfterReached() throws Exception {
        Playlist playlist = new Playlist();
        when(playlistRepository.findByPlaylistId("PL123")).thenReturn(Optional.of(playlist));
        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);

        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(1);
        OffsetDateTime oldDate = cutoff.minusHours(1);

        String responseBody = """
            {
                "nextPageToken": "token123",
                "items": [
                    {
                        "snippet": {
                            "resourceId": { "videoId": "v1" },
                            "publishedAt": "%s"
                        }
                    }
                ]
            }
            """.formatted(oldDate.toString());
        when(httpClient.get(any(), anyMap(), any())).thenReturn(new HttpClient.Response(200, responseBody));

        PageProcessingResult result = service.processSinglePage("PL123", null, httpClient, "key", cutoff, 0, 100, 10);

        assertThat(result.isStopFetching()).isTrue();
        assertThat(playlist.getLastPageToken()).isNull(); // Should be cleared when stopping
        verify(playlistRepository).save(playlist);
    }

    @Test
    void processSinglePage_ShouldHandleApiAuthException() throws Exception {
        when(playlistRepository.findByPlaylistId("PL123")).thenReturn(Optional.of(new Playlist()));
        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);

        when(httpClient.get(any(), anyMap(), any()))
                .thenThrow(new HttpException("GET", 400, "API key not valid"));

        assertThatThrownBy(() -> service.processSinglePage("PL123", null, httpClient, "key", null, 0, 100, 10))
                .isInstanceOf(YoutubeApiAuthException.class);
    }

    @Test
    void processSinglePage_ShouldHandleRequestException() throws Exception {
        when(playlistRepository.findByPlaylistId("PL123")).thenReturn(Optional.of(new Playlist()));
        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);

        when(httpClient.get(any(), anyMap(), any()))
                .thenThrow(new IOException("Network error"));

        assertThatThrownBy(() -> service.processSinglePage("PL123", null, httpClient, "key", null, 0, 100, 10))
                .isInstanceOf(YoutubeApiRequestException.class);
    }

    @Test
    void updatePlaylistProcessedAt_ShouldUpdateAndSave() {
        Playlist playlist = new Playlist();
        playlist.setPlaylistId("PL123");
        when(playlistRepository.findByPlaylistId("PL123")).thenReturn(Optional.of(playlist));

        OffsetDateTime now = OffsetDateTime.now();
        service.updatePlaylistProcessedAt("PL123", now);

        assertThat(playlist.getProcessedAt()).isEqualTo(now);
        assertThat(playlist.getLastPageToken()).isNull();
        verify(playlistRepository).save(playlist);
    }

    @Test
    void processSinglePage_ShouldSkipItemsWithBlankVideoId() throws Exception {
        Playlist playlist = new Playlist();
        when(playlistRepository.findByPlaylistId("PL123")).thenReturn(Optional.of(playlist));
        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);

        String responseBody = """
            {
                "items": [ { "snippet": { "resourceId": { "videoId": "" }, "publishedAt": "2023-01-01T00:00:00Z" } } ]
            }
            """;
        when(httpClient.get(any(), anyMap(), any())).thenReturn(new HttpClient.Response(200, responseBody));

        PageProcessingResult result = service.processSinglePage("PL123", null, httpClient, "key", null, 0, 100, 10);

        assertThat(result.getStats().getNewItemsCount()).isEqualTo(0);
    }

    @Test
    void processSinglePage_ShouldHandleInterruptedException() {
        when(playlistRepository.findByPlaylistId("PL123")).thenReturn(Optional.of(new Playlist()));
        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);

        // Interrupt the current thread before calling the service to trigger InterruptedException in Thread.sleep()
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> service.processSinglePage("PL123", null, httpClient, "key", null, 100, 100, 10))
                .isInstanceOf(YoutubeApiRequestException.class);

        // Verify the interrupt status was restored (and clear it for subsequent tests)
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void processSinglePage_ShouldIncludePageToken_WhenProvided() throws Exception {
        Playlist playlist = new Playlist();
        when(playlistRepository.findByPlaylistId("PL123")).thenReturn(Optional.of(playlist));
        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);

        String responseBody = "{\"items\": []}";
        when(httpClient.get(any(), anyMap(), any())).thenReturn(new HttpClient.Response(200, responseBody));

        service.processSinglePage("PL123", "token123", httpClient, "key", null, 0, 100, 10);

        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(httpClient).get(any(), paramsCaptor.capture(), any());

        assertThat(paramsCaptor.getValue()).containsEntry("pageToken", "token123");
    }

    @Test
    void processSinglePage_ShouldReturnStopFetching_WhenItemsNodeIsMissing() throws Exception {
        Playlist playlist = new Playlist();
        when(playlistRepository.findByPlaylistId("PL123")).thenReturn(Optional.of(playlist));
        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);

        String responseBody = "{}";
        when(httpClient.get(any(), anyMap(), any())).thenReturn(new HttpClient.Response(200, responseBody));

        PageProcessingResult result = service.processSinglePage("PL123", null, httpClient, "key", null, 0, 100, 10);

        assertThat(result.isStopFetching()).isTrue();
    }

    @Test
    void processSinglePage_ShouldReturnStopFetching_WhenItemsNodeIsNotArray() throws Exception {
        Playlist playlist = new Playlist();
        when(playlistRepository.findByPlaylistId("PL123")).thenReturn(Optional.of(playlist));
        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);

        String responseBody = "{\"items\": \"not-an-array\"}";
        when(httpClient.get(any(), anyMap(), any())).thenReturn(new HttpClient.Response(200, responseBody));

        PageProcessingResult result = service.processSinglePage("PL123", null, httpClient, "key", null, 0, 100, 10);

        assertThat(result.isStopFetching()).isTrue();
    }

    @Test
    void processSinglePage_ShouldHandleMultipleItems_AndCountTypes() throws Exception {
        Playlist playlist = new Playlist();
        when(playlistRepository.findByPlaylistId("PL123")).thenReturn(Optional.of(playlist));
        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);

        String publishedAt1 = OffsetDateTime.now().toString();
        String publishedAt2 = OffsetDateTime.now().minusHours(1).toString();
        String publishedAt3 = OffsetDateTime.now().minusHours(2).toString();

        String responseBody = """
            {
                "nextPageToken": "token123",
                "items": [
                    { "snippet": { "resourceId": { "videoId": "v1" }, "publishedAt": "%s" } },
                    { "snippet": { "resourceId": { "videoId": "v2" }, "publishedAt": "%s" } },
                    { "snippet": { "resourceId": { "videoId": "v3" }, "publishedAt": "%s" } }
                ]
            }
            """.formatted(publishedAt1, publishedAt2, publishedAt3);

        when(httpClient.get(any(), anyMap(), any())).thenReturn(new HttpClient.Response(200, responseBody));
        when(itemRepository.findByVideoId(anyString())).thenReturn(Optional.empty());

        Item item1 = new Item();
        item1.setVideoId("v1");
        item1.setLiveBroadcastContent(LiveBroadcastContent.NONE);
        Item item2 = new Item();
        item2.setVideoId("v2");
        item2.setLiveBroadcastContent(LiveBroadcastContent.UPCOMING);
        Item item3 = new Item();
        item3.setVideoId("v3");
        item3.setLiveBroadcastContent(LiveBroadcastContent.LIVE);

        when(videoFetchService.fetchAndCreateItemsFromVideoIds(any(), any(), anyMap(), anyLong(), anyLong(), anyLong()))
                .thenReturn(List.of(item1, item2, item3));

        PageProcessingResult result = service.processSinglePage("PL123", null, httpClient, "key", null, 0, 100, 10);

        assertThat(result.getStats().getNewItemsCount()).isEqualTo(3);
        assertThat(result.getStats().getStandardVideoCount()).isEqualTo(1);
        assertThat(result.getStats().getUpcomingVideoCount()).isEqualTo(1);
        assertThat(result.getStats().getLiveVideoCount()).isEqualTo(1);
        assertThat(result.getNewestVideoPublishedAt()).isEqualTo(OffsetDateTime.parse(publishedAt1));
    }

    @Test
    void processSinglePage_ShouldNotStopFetching_WhenVideoIsNewerThanPublishedAfter() throws Exception {
        Playlist playlist = new Playlist();
        when(playlistRepository.findByPlaylistId("PL123")).thenReturn(Optional.of(playlist));
        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);

        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(1);
        OffsetDateTime newerDate = OffsetDateTime.now();

        String responseBody = "{\"items\": [{ \"snippet\": { \"resourceId\": { \"videoId\": \"v1\" }, \"publishedAt\": \"" + newerDate + "\" } }]}";
        when(httpClient.get(any(), anyMap(), any())).thenReturn(new HttpClient.Response(200, responseBody));
        when(itemRepository.findByVideoId("v1")).thenReturn(Optional.empty());
        when(videoFetchService.fetchAndCreateItemsFromVideoIds(any(), any(), anyMap(), anyLong(), anyLong(), anyLong())).thenReturn(Collections.emptyList());

        PageProcessingResult result = service.processSinglePage("PL123", null, httpClient, "key", cutoff, 0, 100, 10);

        assertThat(result.isStopFetching()).isFalse();
    }

    @Test
    void processSinglePage_ShouldThrowRequestException_WhenHttpExceptionNotAuth() throws Exception {
        when(playlistRepository.findByPlaylistId("PL123")).thenReturn(Optional.of(new Playlist()));
        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);

        when(httpClient.get(any(), anyMap(), any()))
                .thenThrow(new HttpException("GET", 500, "Server Error"));

        assertThatThrownBy(() -> service.processSinglePage("PL123", null, httpClient, "key", null, 0, 100, 10))
                .isInstanceOf(YoutubeApiRequestException.class)
                .hasMessageContaining("Failed to process page");
    }

    @Test
    void processSinglePage_ShouldThrowRequestException_WhenHttpException400ButNotAuth() throws Exception {
        when(playlistRepository.findByPlaylistId("PL123")).thenReturn(Optional.of(new Playlist()));
        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);

        when(httpClient.get(any(), anyMap(), any()))
                .thenThrow(new HttpException("GET", 400, "Bad Request"));

        assertThatThrownBy(() -> service.processSinglePage("PL123", null, httpClient, "key", null, 0, 100, 10))
                .isInstanceOf(YoutubeApiRequestException.class)
                .hasMessageContaining("Failed to process page");
    }

    @Test
    void processSinglePage_ShouldWait_WhenDelayIsPositive() throws Exception {
        Playlist playlist = new Playlist();
        when(playlistRepository.findByPlaylistId("PL123")).thenReturn(Optional.of(playlist));
        when(youtubeApiUsageService.hasSufficientQuota(anyLong(), anyLong())).thenReturn(true);

        String responseBody = "{\"items\": []}";
        when(httpClient.get(any(), anyMap(), any())).thenReturn(new HttpClient.Response(200, responseBody));

        // Execute with 10ms delay to cover Thread.sleep branch
        service.processSinglePage("PL123", null, httpClient, "key", null, 10, 100, 10);

        verify(httpClient).get(any(), anyMap(), any());
    }
}
