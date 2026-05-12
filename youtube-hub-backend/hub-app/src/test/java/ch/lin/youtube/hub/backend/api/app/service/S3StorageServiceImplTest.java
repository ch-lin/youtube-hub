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
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceImplTest {

    @org.mockito.Mock
    private S3Client s3Client;

    private S3StorageServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        service = new S3StorageServiceImpl(s3Client);
        ReflectionTestUtils.setField(service, "bucketName", "test-bucket");
    }

    @Test
    void store_ShouldPutObjectToS3WithCorrectMetadata() throws Exception {
        String objectKey = "video-thumbnail.jpg";
        byte[] data = "dummy stream data".getBytes();
        InputStream inputStream = new ByteArrayInputStream(data);

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        service.store(objectKey, inputStream, data.length, "image/jpeg");

        org.mockito.ArgumentCaptor<PutObjectRequest> requestCaptor = org.mockito.ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

        PutObjectRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.bucket()).isEqualTo("test-bucket");
        assertThat(capturedRequest.key()).isEqualTo(objectKey);
        assertThat(capturedRequest.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void exists_ShouldReturnTrue_WhenHeadObjectSucceeds() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        boolean result = service.exists("existing.jpg");

        assertThat(result).isTrue();
    }

    @Test
    void exists_ShouldReturnFalse_WhenNoSuchKeyExceptionIsThrown() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("Not Found").build());

        boolean result = service.exists("missing.jpg");

        assertThat(result).isFalse();
    }

    @Test
    void exists_ShouldReturnFalse_WhenAwsServiceExceptionIsThrown() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(software.amazon.awssdk.awscore.exception.AwsServiceException.builder().message("Access Denied").build());

        // Should catch the exception, log it, and return false
        boolean result = service.exists("forbidden.jpg");

        assertThat(result).isFalse();
    }

    @Test
    void exists_ShouldReturnFalse_WhenSdkClientExceptionIsThrown() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(software.amazon.awssdk.core.exception.SdkClientException.builder().message("Network Timeout").build());

        // Should catch the exception, log it, and return false
        boolean result = service.exists("timeout.jpg");

        assertThat(result).isFalse();
    }

    @Test
    void getFileAccessUrl_ShouldReturnRelativePath_WhenPublicUrlIsNull() {
        String objectKey = "test.jpg";
        ReflectionTestUtils.setField(Objects.requireNonNull(service), "publicUrl", null);

        String result = service.getFileAccessUrl(objectKey);

        assertThat(result).isEqualTo("/test-bucket/test.jpg");
    }

    @Test
    void getFileAccessUrl_ShouldReturnRelativePath_WhenPublicUrlIsBlank() {
        String objectKey = "test.jpg";
        ReflectionTestUtils.setField(Objects.requireNonNull(service), "publicUrl", "   ");

        String result = service.getFileAccessUrl(objectKey);

        assertThat(result).isEqualTo("/test-bucket/test.jpg");
    }

    @Test
    void getFileAccessUrl_ShouldReturnFullUrl_WhenPublicUrlIsSetAndDoesNotEndWithSlash() {
        String objectKey = "test.jpg";
        ReflectionTestUtils.setField(Objects.requireNonNull(service), "publicUrl", "http://172.16.10.20:9000");

        String result = service.getFileAccessUrl(objectKey);

        assertThat(result).isEqualTo("http://172.16.10.20:9000/test-bucket/test.jpg");
    }

    @Test
    void getFileAccessUrl_ShouldReturnFullUrl_WhenPublicUrlEndsWithSlash() {
        String objectKey = "test.jpg";
        ReflectionTestUtils.setField(Objects.requireNonNull(service), "publicUrl", "http://172.16.10.20:9000/");

        String result = service.getFileAccessUrl(objectKey);

        assertThat(result).isEqualTo("http://172.16.10.20:9000/test-bucket/test.jpg");
    }
}
