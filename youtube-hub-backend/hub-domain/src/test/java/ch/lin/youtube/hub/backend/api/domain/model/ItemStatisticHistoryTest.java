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

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

class ItemStatisticHistoryTest {

    @Test
    void testConstructorWithItem() {
        Item item = new Item("video123");
        ItemStatisticHistory history = new ItemStatisticHistory(item);

        assertNotNull(history);
        assertNull(history.getId(), "ID should be null before persisting to the database");
        assertEquals(item, history.getItem());
        assertEquals(0L, history.getViewCount(), "Default viewCount should be 0");
        assertEquals(0L, history.getLikeCount(), "Default likeCount should be 0");
        assertEquals(0L, history.getCommentCount(), "Default commentCount should be 0");
    }

    @Test
    void testGettersAndSetters() {
        Item item = new Item("video123");
        ItemStatisticHistory history = new ItemStatisticHistory(item);
        OffsetDateTime now = OffsetDateTime.now();

        history.setViewCount(1500L);
        history.setLikeCount(200L);
        history.setCommentCount(30L);
        history.setRecordedAt(now);

        assertEquals(1500L, history.getViewCount());
        assertEquals(200L, history.getLikeCount());
        assertEquals(30L, history.getCommentCount());
        assertEquals(now, history.getRecordedAt());
    }

    @Test
    void testBuilder() {
        Item item = new Item("video123");
        Long id = 1L;
        OffsetDateTime now = OffsetDateTime.now();

        ItemStatisticHistory history = ItemStatisticHistory.builder()
                .id(id)
                .viewCount(1000L)
                .likeCount(50L)
                .commentCount(5L)
                .recordedAt(now)
                .item(item)
                .build();

        assertEquals(id, history.getId());
        assertEquals(1000L, history.getViewCount());
        assertEquals(50L, history.getLikeCount());
        assertEquals(5L, history.getCommentCount());
        assertEquals(now, history.getRecordedAt());
        assertEquals(item, history.getItem());
    }
}
