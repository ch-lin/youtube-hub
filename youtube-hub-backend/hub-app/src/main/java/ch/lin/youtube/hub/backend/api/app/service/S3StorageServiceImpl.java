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

import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * AWS S3 (and SeaweedFS compatible) implementation of {@link StorageService}.
 * <p>
 * This service is activated when 'youtube.hub.storage.type' is set to 's3'. It
 * streams files directly to Object Storage without writing to the local disk.
 */
@Service
@ConditionalOnProperty(name = "youtube.hub.storage.type", havingValue = "s3")
public class S3StorageServiceImpl implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(S3StorageServiceImpl.class);

    private final S3Client s3Client;

    /**
     * The name of the S3/SeaweedFS bucket where files will be stored. Defaults
     * to "youtube-thumbnails" if not configured.
     */
    @Value("${youtube.hub.storage.s3.bucket-name:youtube-thumbnails}")
    private String bucketName;

    /**
     * The public base URL for accessing files directly from S3/SeaweedFS.
     * Should include the protocol and host (e.g., "http://172.16.10.20:9000").
     */
    @Value("${youtube.hub.storage.s3.public-url:}")
    private String publicUrl;

    /**
     * Constructs the S3 storage service.
     *
     * @param s3Client the pre-configured AWS S3 SDK v2 client
     */
    public S3StorageServiceImpl(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation streams the data directly from the given InputStream
     * to the Object Storage. By explicitly providing the exact
     * {@code contentLength}, it allows the AWS SDK to perform a direct stream
     * transfer. This is a critical optimization that prevents the SDK from
     * buffering the entire file into JVM memory, ensuring a very low RAM
     * footprint.
     */
    @Override
    public void store(String objectKey, InputStream inputStream, long contentLength, String contentType) throws Exception {
        logger.debug("Streaming file to S3 bucket '{}' with key: {}", bucketName, objectKey);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(contentType)
                .build();

        // Key optimization: Use RequestBody.fromInputStream with the exact contentLength.
        // This ensures the AWS SDK streams the upload directly and prevents it from
        // caching/buffering the entire InputStream content into memory.
        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, contentLength));

        logger.info("Successfully uploaded object '{}' to S3 bucket '{}'.", objectKey, bucketName);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This method uses a lightweight {@code HeadObject} request to check for
     * file existence. It only retrieves the object's metadata without
     * downloading the actual file content, making it highly efficient and
     * bandwidth-friendly.
     */
    @Override
    public boolean exists(String objectKey) {
        try {
            // A HeadObject request is a lightweight operation that only fetches metadata.
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            // S3 explicitly throws NoSuchKeyException when the requested object does not exist.
            return false;
        } catch (AwsServiceException | SdkClientException e) {
            // Catch any other exceptions (e.g., network issues, permission denied)
            // and return false so the application can attempt to process/download the file again.
            logger.error("Error checking existence of object '{}' in bucket '{}'", objectKey, bucketName, e);
            return false;
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * For S3 storage, constructs the direct public URL bypassing the
     * application backend. E.g.,
     * http://s3-ip:9000/youtube-thumbnails/video123.jpg
     */
    @Override
    public String getFileAccessUrl(String objectKey) {
        if (publicUrl == null || publicUrl.isBlank()) {
            return "/" + bucketName + "/" + objectKey;
        }
        String baseUrl = publicUrl.endsWith("/") ? publicUrl : publicUrl + "/";
        return baseUrl + bucketName + "/" + objectKey;
    }
}
