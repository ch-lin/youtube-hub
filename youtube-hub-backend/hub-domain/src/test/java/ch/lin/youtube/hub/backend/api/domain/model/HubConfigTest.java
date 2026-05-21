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
package ch.lin.youtube.hub.backend.api.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class HubConfigTest {

    @Test
    void testNoArgsConstructor_ShouldHaveDefaultValues() {
        HubConfig config = new HubConfig();
        assertThat(config).isNotNull();
        // Verify default values set in the entity
        assertThat(config.getEnabled()).isFalse();
        assertThat(config.getAutoStartFetchScheduler()).isFalse();
        assertThat(config.getFixedRate()).isEqualTo(86400000L); // Default 24 hours
        assertThat(config.getQuota()).isEqualTo(10000L);
        assertThat(config.getQuotaSafetyThreshold()).isEqualTo(500L);
        assertThat(config.getApiCallDelay()).isEqualTo(100L);
        assertThat(config.getActiveVideosSyncDays()).isEqualTo(30);
        assertThat(config.getMaxThumbnailRetries()).isEqualTo(3);
    }

    @Test
    void testBuilder_ShouldSetAllFields() {
        HubConfig config = HubConfig.builder()
                .name("test-config")
                .enabled(true)
                .youtubeApiKey("api-key")
                .clientId("client-id")
                .clientSecret("client-secret")
                .autoStartFetchScheduler(true)
                .schedulerType(SchedulerType.CRON)
                .fixedRate(3600000L)
                .cronExpression("0 0 * * * *")
                .cronTimeZone("UTC")
                .quota(50000L)
                .quotaSafetyThreshold(1000L)
                .apiCallDelay(200L)
                .activeVideosSyncDays(45)
                .maxThumbnailRetries(5)
                .build();

        assertThat(config.getName()).isEqualTo("test-config");
        assertThat(config.getEnabled()).isTrue();
        assertThat(config.getYoutubeApiKey()).isEqualTo("api-key");
        assertThat(config.getClientId()).isEqualTo("client-id");
        assertThat(config.getClientSecret()).isEqualTo("client-secret");
        assertThat(config.getAutoStartFetchScheduler()).isTrue();
        assertThat(config.getSchedulerType()).isEqualTo(SchedulerType.CRON);
        assertThat(config.getFixedRate()).isEqualTo(3600000L);
        assertThat(config.getCronExpression()).isEqualTo("0 0 * * * *");
        assertThat(config.getCronTimeZone()).isEqualTo("UTC");
        assertThat(config.getQuota()).isEqualTo(50000L);
        assertThat(config.getQuotaSafetyThreshold()).isEqualTo(1000L);
        assertThat(config.getApiCallDelay()).isEqualTo(200L);
        assertThat(config.getActiveVideosSyncDays()).isEqualTo(45);
        assertThat(config.getMaxThumbnailRetries()).isEqualTo(5);
    }

    @Test
    void testSettersAndGetters_ShouldWorkForNewFields() {
        HubConfig config = new HubConfig("new-name");
        config.setAutoStartFetchScheduler(true);
        config.setSchedulerType(SchedulerType.FIXED_RATE);
        config.setFixedRate(1000L);
        config.setCronExpression("0 0 12 * * ?");
        config.setCronTimeZone("Asia/Taipei");
        config.setQuota(20000L);
        config.setQuotaSafetyThreshold(200L);
        config.setApiCallDelay(150L);
        config.setActiveVideosSyncDays(15);
        config.setMaxThumbnailRetries(8);

        assertThat(config.getName()).isEqualTo("new-name");
        assertThat(config.getAutoStartFetchScheduler()).isTrue();
        assertThat(config.getSchedulerType()).isEqualTo(SchedulerType.FIXED_RATE);
        assertThat(config.getFixedRate()).isEqualTo(1000L);
        assertThat(config.getCronExpression()).isEqualTo("0 0 12 * * ?");
        assertThat(config.getCronTimeZone()).isEqualTo("Asia/Taipei");
        assertThat(config.getQuota()).isEqualTo(20000L);
        assertThat(config.getQuotaSafetyThreshold()).isEqualTo(200L);
        assertThat(config.getApiCallDelay()).isEqualTo(150L);
        assertThat(config.getActiveVideosSyncDays()).isEqualTo(15);
        assertThat(config.getMaxThumbnailRetries()).isEqualTo(8);
    }

    @Test
    void testEqualsAndHashCode_ShouldIncludeNewFields() {
        HubConfig config1 = HubConfig.builder()
                .name("cfg").enabled(true).youtubeApiKey("k").clientId("i").clientSecret("s")
                .autoStartFetchScheduler(true).schedulerType(SchedulerType.CRON).fixedRate(100L)
                .cronExpression("cron").cronTimeZone("UTC").quota(10000L).quotaSafetyThreshold(500L)
                .apiCallDelay(100L).activeVideosSyncDays(30).maxThumbnailRetries(3)
                .build();

        HubConfig config2 = HubConfig.builder()
                .name("cfg").enabled(true).youtubeApiKey("k").clientId("i").clientSecret("s")
                .autoStartFetchScheduler(true).schedulerType(SchedulerType.CRON).fixedRate(100L)
                .cronExpression("cron").cronTimeZone("UTC").quota(10000L).quotaSafetyThreshold(500L)
                .apiCallDelay(100L).activeVideosSyncDays(30).maxThumbnailRetries(3)
                .build();

        HubConfig config3 = HubConfig.builder()
                .name("cfg").enabled(true).youtubeApiKey("k").clientId("i").clientSecret("s")
                .autoStartFetchScheduler(false) // Different autoStart
                .schedulerType(SchedulerType.CRON).fixedRate(100L)
                .cronExpression("cron").cronTimeZone("UTC").quota(10000L).quotaSafetyThreshold(500L)
                .apiCallDelay(100L).activeVideosSyncDays(30).maxThumbnailRetries(3)
                .build();

        assertThat(config1).isEqualTo(config2);
        assertThat(config1.hashCode()).isEqualTo(config2.hashCode());

        assertThat(config1).isNotEqualTo(config3);
        assertThat(config1.hashCode()).isNotEqualTo(config3.hashCode());
    }
}
