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

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import software.amazon.awssdk.services.s3.S3Client;

class S3ConfigTest {

    private S3Config s3Config;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        s3Config = new S3Config();
        // Inject necessary fields that are usually populated by Spring's @Value
        ReflectionTestUtils.setField(s3Config, "endpoint", "http://localhost:9000");
        ReflectionTestUtils.setField(Objects.requireNonNull(s3Config), "accessKey", "test-access-key");
        ReflectionTestUtils.setField(Objects.requireNonNull(s3Config), "secretKey", "test-secret-key");
        ReflectionTestUtils.setField(Objects.requireNonNull(s3Config), "region", "us-east-1");
    }

    @Test
    void s3Client_ShouldCreateS3ClientSuccessfully() {
        S3Client s3Client = s3Config.s3Client();

        assertThat(s3Client).isNotNull();
    }
}
