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
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import org.mockito.Mockito;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import ch.lin.youtube.hub.backend.api.app.repository.ItemRepository;
import ch.lin.youtube.hub.backend.api.domain.model.HubConfig;
import ch.lin.youtube.hub.backend.api.domain.model.Item;
import ch.lin.youtube.hub.backend.api.domain.model.ThumbnailStatus;

@ExtendWith(MockitoExtension.class)
class ThumbnailServiceImplTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<InputStream> httpResponse;

    @Mock
    private ConfigsService configsService;

    @Mock
    private StorageService storageService;

    private ThumbnailServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        service = new ThumbnailServiceImpl(itemRepository, configsService, storageService);

        lenient().when(configsService.getResolvedConfig(null)).thenReturn(new HubConfig("default"));

        // Inject the mocked HttpClient to prevent real network calls
        ReflectionTestUtils.setField(Objects.requireNonNull(service), "httpClient", httpClient);
    }

    @Test
    @SuppressWarnings("null")
    void downloadThumbnail_ShouldSkip_WhenThumbnailUrlIsNull() {
        Item item = new Item("v1");
        item.setThumbnailUrl(null);

        service.downloadThumbnail(item);

        verify(itemRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("null")
    void downloadThumbnail_ShouldSkip_WhenThumbnailUrlIsBlank() {
        Item item = new Item("v1");
        item.setThumbnailUrl("   ");

        service.downloadThumbnail(item);

        verify(itemRepository, never()).save(any());
    }

    @Test
    void downloadThumbnail_ShouldSkipAndMarkDownloaded_WhenObjectAlreadyExists() throws Exception {
        Item item = new Item("v1");
        item.setThumbnailUrl("http://example.com/thumb.jpg");

        when(storageService.exists("v1.jpg")).thenReturn(true);

        service.downloadThumbnail(item);

        // Verify HTTP client was never used
        verify(httpClient, never()).send(any(), any());
        verify(itemRepository).save(item);
        assertThat(item.getThumbnailStatus()).isEqualTo(ThumbnailStatus.DOWNLOADED);
        assertThat(item.getStoredThumbnailPath()).isEqualTo("v1.jpg");
    }

    @Test
    @SuppressWarnings({"unchecked"})
    void downloadThumbnail_ShouldStreamAndMarkDownloaded_WhenContentLengthProvided() throws Exception {
        Item item = new Item("v1");
        item.setThumbnailUrl("http://example.com/thumb.jpg");

        when(storageService.exists("v1.jpg")).thenReturn(false);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);

        HttpHeaders headers = mock(HttpHeaders.class);
        when(httpResponse.headers()).thenReturn(headers);
        when(headers.firstValueAsLong("Content-Length")).thenReturn(OptionalLong.of(1024L));
        when(headers.firstValue("Content-Type")).thenReturn(Optional.of("image/jpeg"));

        InputStream dummyStream = new ByteArrayInputStream(new byte[1024]);
        when(httpResponse.body()).thenReturn(dummyStream);

        service.downloadThumbnail(item);

        verify(storageService).store(eq("v1.jpg"), eq(dummyStream), eq(1024L), eq("image/jpeg"));
        verify(itemRepository).save(item);
        assertThat(item.getThumbnailStatus()).isEqualTo(ThumbnailStatus.DOWNLOADED);
        assertThat(item.getStoredThumbnailPath()).isEqualTo("v1.jpg");
    }

    @Test
    @SuppressWarnings({"unchecked"})
    void downloadThumbnail_ShouldBufferAndMarkDownloaded_WhenContentLengthMissing() throws Exception {
        Item item = new Item("v1");
        item.setThumbnailUrl("http://example.com/thumb.jpg");

        when(storageService.exists("v1.jpg")).thenReturn(false);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);

        HttpHeaders headers = mock(HttpHeaders.class);
        when(httpResponse.headers()).thenReturn(headers);
        when(headers.firstValueAsLong("Content-Length")).thenReturn(OptionalLong.empty()); // Missing
        when(headers.firstValue("Content-Type")).thenReturn(Optional.of("image/png"));

        byte[] dummyData = "dummy_image_data".getBytes();
        InputStream dummyStream = new ByteArrayInputStream(dummyData);
        when(httpResponse.body()).thenReturn(dummyStream);

        service.downloadThumbnail(item);

        verify(storageService).store(eq("v1.jpg"), any(ByteArrayInputStream.class), eq((long) dummyData.length), eq("image/png"));
        verify(itemRepository).save(item);
        assertThat(item.getThumbnailStatus()).isEqualTo(ThumbnailStatus.DOWNLOADED);
        assertThat(item.getStoredThumbnailPath()).isEqualTo("v1.jpg");
    }

    @Test
    @SuppressWarnings({"unchecked"})
    void downloadThumbnail_ShouldMarkUnavailable_WhenResponse404() throws Exception {
        Item item = new Item("v1");
        item.setThumbnailUrl("http://example.com/thumb.jpg");

        when(storageService.exists("v1.jpg")).thenReturn(false);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(404);
        when(httpResponse.body()).thenReturn(new ByteArrayInputStream(new byte[0]));

        service.downloadThumbnail(item);

        verify(itemRepository).save(item);
        assertThat(item.getThumbnailStatus()).isEqualTo(ThumbnailStatus.UNAVAILABLE);
        assertThat(item.getStoredThumbnailPath()).isNull();
    }

    @Test
    @SuppressWarnings({"unchecked"})
    void downloadThumbnail_ShouldMarkUnavailable_WhenResponse403() throws Exception {
        Item item = new Item("v1");
        item.setThumbnailUrl("http://example.com/thumb.jpg");

        when(storageService.exists("v1.jpg")).thenReturn(false);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(403);
        when(httpResponse.body()).thenReturn(new ByteArrayInputStream(new byte[0]));

        service.downloadThumbnail(item);

        verify(itemRepository).save(item);
        assertThat(item.getThumbnailStatus()).isEqualTo(ThumbnailStatus.UNAVAILABLE);
        assertThat(item.getStoredThumbnailPath()).isNull();
    }

    @Test
    @SuppressWarnings({"unchecked"})
    void downloadThumbnail_ShouldMarkTempFailed_WhenResponse500() throws Exception {
        Item item = new Item("v1");
        item.setThumbnailUrl("http://example.com/thumb.jpg");

        when(storageService.exists("v1.jpg")).thenReturn(false);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(500);
        when(httpResponse.body()).thenReturn(new ByteArrayInputStream(new byte[0]));

        service.downloadThumbnail(item);

        verify(itemRepository).save(item);
        assertThat(item.getThumbnailStatus()).isEqualTo(ThumbnailStatus.TEMP_FAILED);
        assertThat(item.getStoredThumbnailPath()).isNull();
    }

    @Test
    @SuppressWarnings({"unchecked"})
    void downloadThumbnail_ShouldMarkUnavailable_WhenMaxRetriesReached() throws Exception {
        Item item = new Item("v1");
        item.setThumbnailUrl("http://example.com/thumb.jpg");
        // Set the current retry count to 2; adding this failure will reach the MAX_THUMBNAIL_RETRIES = 3 limit
        item.setThumbnailRetryCount(2);

        when(storageService.exists("v1.jpg")).thenReturn(false);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(500); // Simulate a temporary error
        when(httpResponse.body()).thenReturn(new ByteArrayInputStream(new byte[0]));

        service.downloadThumbnail(item);

        verify(itemRepository).save(item);
        assertThat(item.getThumbnailStatus()).isEqualTo(ThumbnailStatus.UNAVAILABLE);
        assertThat(item.getThumbnailRetryCount()).isEqualTo(3);
    }

    @Test
    @SuppressWarnings({"unchecked"})
    void downloadThumbnail_ShouldHandleIOException_FromHttpClient() throws Exception {
        Item item = new Item("v1");
        item.setThumbnailUrl("http://example.com/thumb.jpg");

        when(storageService.exists("v1.jpg")).thenReturn(false);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("Network Error"));

        service.downloadThumbnail(item);

        verify(itemRepository).save(item);
        assertThat(item.getThumbnailStatus()).isEqualTo(ThumbnailStatus.TEMP_FAILED);
    }

    @Test
    @SuppressWarnings({"unchecked"})
    void downloadThumbnail_ShouldHandleInterruptedException_AndRestoreFlag() throws Exception {
        Item item = new Item("v1");
        item.setThumbnailUrl("http://example.com/thumb.jpg");

        when(storageService.exists("v1.jpg")).thenReturn(false);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException("Thread interrupted"));

        service.downloadThumbnail(item);

        assertThat(Thread.interrupted()).isTrue(); // Verifies the interrupt flag is set and clears it
        verify(itemRepository).save(item);
        assertThat(item.getThumbnailStatus()).isEqualTo(ThumbnailStatus.TEMP_FAILED);
    }

    @Test
    @SuppressWarnings({"unchecked"})
    void downloadThumbnail_ShouldMarkTempFailed_WhenStorageServiceThrowsException() throws Exception {
        Item item = new Item("v1");
        item.setThumbnailUrl("http://example.com/thumb.jpg");

        when(storageService.exists("v1.jpg")).thenReturn(false);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        when(httpResponse.statusCode()).thenReturn(200);

        HttpHeaders headers = mock(HttpHeaders.class);
        when(httpResponse.headers()).thenReturn(headers);
        when(headers.firstValueAsLong("Content-Length")).thenReturn(OptionalLong.of(1024L));
        when(headers.firstValue("Content-Type")).thenReturn(Optional.of("image/jpeg"));
        when(httpResponse.body()).thenReturn(new ByteArrayInputStream(new byte[1024]));

        // Simulate S3/Local Storage throwing an exception during save
        Mockito.doThrow(new RuntimeException("Storage Error")).when(storageService).store(anyString(), any(), anyLong(), anyString());

        service.downloadThumbnail(item);

        verify(itemRepository).save(item);
        assertThat(item.getThumbnailStatus()).isEqualTo(ThumbnailStatus.TEMP_FAILED);
    }

    @Test
    void getThumbnailCounts_ShouldReturnCounts_WhenProjectionHasValues() {
        ItemRepository.ThumbnailCountsProjection projection = mock(ItemRepository.ThumbnailCountsProjection.class);
        when(projection.getTotalCount()).thenReturn(100L);
        when(projection.getPendingCount()).thenReturn(10L);
        when(projection.getFailedCount()).thenReturn(5L);

        when(itemRepository.getThumbnailCounts(any(), any())).thenReturn(projection);

        Map<String, Long> result = service.getThumbnailCounts();

        assertThat(result.get("totalCount")).isEqualTo(100L);
        assertThat(result.get("pendingCount")).isEqualTo(10L);
        assertThat(result.get("failedCount")).isEqualTo(5L);
    }

    @Test
    void getThumbnailCounts_ShouldReturnZeros_WhenProjectionHasNulls() {
        ItemRepository.ThumbnailCountsProjection projection = mock(ItemRepository.ThumbnailCountsProjection.class);
        when(projection.getTotalCount()).thenReturn(null);
        when(projection.getPendingCount()).thenReturn(null);
        when(projection.getFailedCount()).thenReturn(null);

        when(itemRepository.getThumbnailCounts(any(), any())).thenReturn(projection);

        Map<String, Long> result = service.getThumbnailCounts();

        assertThat(result.get("totalCount")).isEqualTo(0L);
        assertThat(result.get("pendingCount")).isEqualTo(0L);
        assertThat(result.get("failedCount")).isEqualTo(0L);
    }

    @Test
    @SuppressWarnings({"unchecked"})
    void downloadThumbnail_ShouldMarkTempFailed_WhenResponse199() throws Exception {
        Item item = new Item("v1");
        item.setThumbnailUrl("http://example.com/thumb.jpg");

        when(storageService.exists("v1.jpg")).thenReturn(false);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);
        // Test short-circuit evaluation of the left side of &&: statusCode < 200 (A = False)
        when(httpResponse.statusCode()).thenReturn(199);
        when(httpResponse.body()).thenReturn(new ByteArrayInputStream(new byte[0]));

        service.downloadThumbnail(item);

        verify(itemRepository).save(item);
        assertThat(item.getThumbnailStatus()).isEqualTo(ThumbnailStatus.TEMP_FAILED);
        assertThat(item.getStoredThumbnailPath()).isNull();
    }
}
