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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LocalStorageServiceImplTest {

    @TempDir
    Path tempDir;

    private LocalStorageServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        service = new LocalStorageServiceImpl();
        // Inject the JUnit TempDir as the storage path to prevent real file system pollution
        ReflectionTestUtils.setField(Objects.requireNonNull(service), "storagePath", tempDir.toString());
    }

    @Test
    void init_ShouldCreateDirectory_WhenNotExists() {
        Path newDir = tempDir.resolve("new-thumbnails");
        ReflectionTestUtils.setField(Objects.requireNonNull(service), "storagePath", newDir.toString());

        assertThat(Files.exists(newDir)).isFalse();

        service.init();

        assertThat(Files.exists(newDir)).isTrue();
    }

    @Test
    void init_ShouldNotThrow_WhenDirectoryAlreadyExists() throws IOException {
        Path existingDir = tempDir.resolve("existing-thumbnails");
        Files.createDirectories(existingDir);
        ReflectionTestUtils.setField(Objects.requireNonNull(service), "storagePath", existingDir.toString());

        assertDoesNotThrow(() -> service.init());
        assertThat(Files.exists(existingDir)).isTrue();
    }

    @Test
    void init_ShouldLogError_WhenCreateDirectoriesThrowsIOException() {
        Path newDir = tempDir.resolve("error-thumbnails");
        ReflectionTestUtils.setField(Objects.requireNonNull(service), "storagePath", newDir.toString());

        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(any(Path.class))).thenReturn(false);
            mockedFiles.when(() -> Files.createDirectories(any(Path.class)))
                    .thenThrow(new IOException("Simulated directory creation error"));

            assertDoesNotThrow(() -> service.init());
        }
    }

    @Test
    void store_ShouldSaveFileToDisk() throws Exception {
        String objectKey = "test.jpg";
        byte[] data = "dummy_image_data".getBytes();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(data);

        service.store(objectKey, inputStream, data.length, "image/jpeg");

        Path savedFile = tempDir.resolve(objectKey);
        assertThat(Files.exists(savedFile)).isTrue();
        assertThat(Files.readAllBytes(savedFile)).isEqualTo(data);
    }

    @Test
    void exists_ShouldReturnTrue_WhenFileExists() throws IOException {
        String objectKey = "existing.jpg";
        Files.createFile(tempDir.resolve(objectKey));

        assertThat(service.exists(objectKey)).isTrue();
    }

    @Test
    void exists_ShouldReturnFalse_WhenFileDoesNotExist() {
        assertThat(service.exists("missing.jpg")).isFalse();
    }

    @Test
    void getFileAccessUrl_ShouldFormatCorrectly_WithStandardPattern() {
        ReflectionTestUtils.setField(Objects.requireNonNull(service), "thumbnailPathPattern", "/thumbnails/**");
        String url = service.getFileAccessUrl("video123.jpg");
        assertThat(url).isEqualTo("/thumbnails/video123.jpg");
    }

    @Test
    void getFileAccessUrl_ShouldFormatCorrectly_WhenMissingLeadingSlash() {
        // Simulate a misconfigured pattern: "thumbnails/**"
        ReflectionTestUtils.setField(Objects.requireNonNull(service), "thumbnailPathPattern", "thumbnails/**");
        String url = service.getFileAccessUrl("video123.jpg");
        assertThat(url).isEqualTo("/thumbnails/video123.jpg");
    }

    @Test
    void getFileAccessUrl_ShouldFormatCorrectly_WhenMissingTrailingSlashAndWildcard() {
        // Simulate a misconfigured pattern: "/thumbnails"
        ReflectionTestUtils.setField(Objects.requireNonNull(service), "thumbnailPathPattern", "/thumbnails");
        String url = service.getFileAccessUrl("video123.jpg");
        assertThat(url).isEqualTo("/thumbnails/video123.jpg");
    }
}
