/*
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
 */
package ch.lin.youtube.hub.backend.api.app.service;

import java.io.InputStream;
import java.io.Writer;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import ch.lin.youtube.hub.backend.api.app.service.model.ItemUpdateResult;
import ch.lin.youtube.hub.backend.api.domain.model.Item;
import ch.lin.youtube.hub.backend.api.domain.model.ProcessingStatus;

/**
 * Defines the service layer contract for managing YouTube video items
 * ({@link Item} entities).
 * <p>
 * This interface outlines the core business logic for retrieving items with
 * complex filtering, updating item information after a download, and performing
 * cleanup operations.
 */
public interface ItemService {

    /**
     * Performs cleanup operations on item-related tables.
     * <p>
     * This is a destructive operation that typically involves deleting all
     * records from the 'item' and related tables and resetting their primary
     * key sequences. It is intended for system reset or testing purposes.
     */
    void cleanup();

    /**
     * Retrieves a list of {@link Item} entities based on a dynamic set of
     * filter criteria.
     *
     * @param notDownloaded If true, returns only items that have not been
     * downloaded.
     * @param filterNoFileSize If true, excludes items where the file size is
     * zero or null.
     * @param liveBroadcastContent Filters by the live broadcast content status
     * (e.g., 'live', 'upcoming', 'none').
     * @param pastOnly If true, returns only items with a published date in the
     * past.
     * @param filterNoTag If true, excludes items that do not have an associated
     * tag.
     * @param filterDeleted If true, returns only items that are marked as
     * deleted.
     * @param channelIds A list of channel IDs to filter by. If provided, only
     * items from these channels are returned.
     * @param pageable The pagination information.
     * @return A page of {@link Item} entities matching the filter criteria.
     */
    Page<Item> getItems(Boolean notDownloaded, Boolean filterNoFileSize, String liveBroadcastContent,
            Boolean pastOnly, Boolean filterNoTag, Boolean filterDeleted, List<String> channelIds, Pageable pageable);

    /**
     * Updates an item's information after a download attempt.
     * <p>
     * This method is typically called by a background process to record the
     * outcome of a download task. It also checks for potential duplicate files.
     *
     * @param videoId The unique YouTube video ID of the item to update.
     * @param downloadTaskId The ID of the download task, for tracking purposes.
     * @param fileSize The size of the downloaded file in bytes.
     * @param filePath The local path where the file was saved.
     * @param status The final {@link ProcessingStatus} of the download task.
     * @return An {@link ItemUpdateResult} containing the updated item and a
     * list of any warnings (e.g., potential duplicates).
     */
    ItemUpdateResult updateItemFileInfo(String videoId, String downloadTaskId, Long fileSize, String filePath, ProcessingStatus status);

    /**
     * Retrieves the processing statuses for a given list of video IDs.
     *
     * @param videoIds The list of YouTube video IDs.
     * @return A map where the key is the video ID and the value is its
     * processing status.
     */
    Map<String, ProcessingStatus> getItemStatuses(List<String> videoIds);

    /**
     * Exports specific item fields (videoId, status, width, height) to a CSV
     * writer.
     *
     * @param writer The writer to stream the CSV data to.
     */
    void exportItemsToCsv(Writer writer);

    /**
     * Imports and updates item fields (status, width, height) from a CSV input
     * stream.
     *
     * @param inputStream The input stream containing the CSV data.
     * @return A list of video IDs that were present in the CSV but not found in
     * the database.
     */
    List<String> importItemsFromCsv(InputStream inputStream);
}
