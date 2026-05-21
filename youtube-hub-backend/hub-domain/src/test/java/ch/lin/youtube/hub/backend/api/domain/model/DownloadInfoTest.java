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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

class DownloadInfoTest {

    @Test
    void testConstructorWithItem() {
        Item item = new Item("video123");
        DownloadInfo downloadInfo = new DownloadInfo(item);

        assertNotNull(downloadInfo);
        assertNull(downloadInfo.getId(), "ID should be null before persisting to the database");
        assertEquals(item, downloadInfo.getItem());
        assertEquals(0L, downloadInfo.getFileSize(), "Default fileSize should be 0");
    }

    @Test
    void testGettersAndSetters() {
        Item item = new Item("video123");
        DownloadInfo downloadInfo = new DownloadInfo(item);

        String taskId = "task-uuid-1234";
        long fileSize = 2048L;
        String filePath = "/downloads/video123.mp4";

        downloadInfo.setDownloadTaskId(taskId);
        downloadInfo.setFileSize(fileSize);
        downloadInfo.setFilePath(filePath);

        assertEquals(taskId, downloadInfo.getDownloadTaskId());
        assertEquals(fileSize, downloadInfo.getFileSize());
        assertEquals(filePath, downloadInfo.getFilePath());
    }

    @Test
    void testBuilder() {
        Item item = new Item("video123");
        Long id = 1L;
        DownloadInfo downloadInfo = DownloadInfo.builder()
                .id(id)
                .downloadTaskId("task-uuid")
                .fileSize(1024L)
                .filePath("/path/to/video.mp4")
                .item(item)
                .build();

        assertEquals(id, downloadInfo.getId());
        assertEquals("task-uuid", downloadInfo.getDownloadTaskId());
        assertEquals(1024L, downloadInfo.getFileSize());
        assertEquals("/path/to/video.mp4", downloadInfo.getFilePath());
        assertEquals(item, downloadInfo.getItem());
    }

    @Test
    void testEqualsAndHashCode() {
        DownloadInfo info1 = DownloadInfo.builder().downloadTaskId("task1").fileSize(100L).filePath("path1").build();
        DownloadInfo info2 = DownloadInfo.builder().downloadTaskId("task1").fileSize(100L).filePath("path1").build();
        DownloadInfo info3 = DownloadInfo.builder().downloadTaskId("task2").fileSize(200L).filePath("path2").build();

        assertEquals(info1, info2);
        assertEquals(info1.hashCode(), info2.hashCode());
        assertNotEquals(info1, info3);
        assertNotEquals(info1.hashCode(), info3.hashCode());
    }
}
