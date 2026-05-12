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
package ch.lin.youtube.hub.backend.api.app.config;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Configuration class for initializing the AWS S3Client.
 * <p>
 * This configuration is only loaded when 'youtube.hub.storage.type' is set to
 * 's3'. It is designed to support both native AWS S3 and S3-compatible object
 * storage like SeaweedFS by allowing endpoint overrides and forcing path-style
 * access.
 */
@Configuration
@ConditionalOnProperty(name = "youtube.hub.storage.type", havingValue = "s3")
public class S3Config {

    /**
     * The custom endpoint URI for the S3-compatible service (e.g.,
     * http://s3:9000).
     */
    @Value("${youtube.hub.storage.s3.endpoint}")
    private String endpoint;

    /**
     * The access key (or username) for authentication with the S3 service.
     */
    @Value("${youtube.hub.storage.s3.access-key}")
    private String accessKey;

    /**
     * The secret key (or password) for authentication with the S3 service.
     */
    @Value("${youtube.hub.storage.s3.secret-key}")
    private String secretKey;

    /**
     * The AWS region to configure the client with. Defaults to 'us-east-1' if
     * not specified, which is typically required for client initialization even
     * in local setups like SeaweedFS.
     */
    @Value("${youtube.hub.storage.s3.region:us-east-1}")
    private String region;

    /**
     * Creates and configures the {@link S3Client} bean.
     * <p>
     * The client is configured with static credentials, a specific region, an
     * overridden endpoint, and path-style access enabled (which is critical for
     * compatibility with SeaweedFS).
     *
     * @return a fully configured {@link S3Client} instance.
     */
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
}
