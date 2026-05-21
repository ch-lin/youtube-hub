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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class YoutubeApiUsageTest {

    @Test
    void testGettersAndSetters() {
        LocalDate date = LocalDate.now();
        YoutubeApiUsage usage = new YoutubeApiUsage(date);
        long requestCount = 10;
        long quotaUsed = 100;
        OffsetDateTime lastUpdated = OffsetDateTime.now();

        usage.setRequestCount(requestCount);
        usage.setQuotaUsed(quotaUsed);
        usage.setLastUpdated(lastUpdated);

        assertNull(usage.getId(), "ID should be null before persisting to the database");
        assertEquals(date, usage.getUsageDate());
        assertEquals(requestCount, usage.getRequestCount());
        assertEquals(quotaUsed, usage.getQuotaUsed());
        assertEquals(lastUpdated, usage.getLastUpdated());
    }

    @Test
    void testNoArgsConstructor() {
        YoutubeApiUsage usage = new YoutubeApiUsage();
        assertNotNull(usage);
        assertNull(usage.getId());
        assertNull(usage.getUsageDate());
        assertEquals(0, usage.getRequestCount());
        assertEquals(0, usage.getQuotaUsed());
        assertNull(usage.getLastUpdated());
    }

    @Test
    void testBuilder() {
        Long id = 1L;
        LocalDate date = LocalDate.now();
        long requestCount = 10;
        long quotaUsed = 100;
        OffsetDateTime lastUpdated = OffsetDateTime.now();

        YoutubeApiUsage usage = YoutubeApiUsage.builder()
                .id(id)
                .usageDate(date)
                .requestCount(requestCount)
                .quotaUsed(quotaUsed)
                .lastUpdated(lastUpdated)
                .build();

        assertEquals(id, usage.getId());
        assertEquals(date, usage.getUsageDate());
        assertEquals(requestCount, usage.getRequestCount());
        assertEquals(quotaUsed, usage.getQuotaUsed());
        assertEquals(lastUpdated, usage.getLastUpdated());
    }

    @Test
    void testEqualsAndHashCode() {
        LocalDate date1 = LocalDate.of(2023, 1, 1);
        LocalDate date2 = LocalDate.of(2023, 1, 2);

        YoutubeApiUsage usage1 = new YoutubeApiUsage(date1);

        YoutubeApiUsage usage2 = new YoutubeApiUsage(date1); // Same date

        YoutubeApiUsage usage3 = new YoutubeApiUsage(date2); // Different date

        // Test equality
        assertEquals(usage1, usage2);
        assertNotEquals(usage1, usage3);

        // Test hash code
        assertEquals(usage1.hashCode(), usage2.hashCode());
        assertNotEquals(usage1.hashCode(), usage3.hashCode());

        // Test against null and other types
        assertNotEquals(usage1, null);
        assertNotEquals(usage1, new Object());
    }

    @Test
    void testIncrement() {
        YoutubeApiUsage usage = new YoutubeApiUsage(LocalDate.now());
        usage.setRequestCount(5);
        usage.setQuotaUsed(50);

        OffsetDateTime before = OffsetDateTime.now();

        usage.increment(10);

        assertEquals(6, usage.getRequestCount());
        assertEquals(60, usage.getQuotaUsed());
        assertNotNull(usage.getLastUpdated());
        // Ensure lastUpdated is recent (not before the test started)
        assertTrue(!usage.getLastUpdated().isBefore(before));
    }

    @Test
    void testGetCurrentQuotaDate() {
        LocalDate quotaDate = YoutubeApiUsage.getCurrentQuotaDate();
        assertNotNull(quotaDate);
        assertEquals(LocalDate.now(ZoneId.of("America/Los_Angeles")), quotaDate);
    }
}
