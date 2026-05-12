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
package ch.lin.youtube.hub.backend.api.app.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ch.lin.youtube.hub.backend.api.domain.model.Item;
import ch.lin.youtube.hub.backend.api.domain.model.Tag;
import ch.lin.youtube.hub.backend.api.domain.model.ThumbnailStatus;

/**
 * Spring Data JPA repository for {@link Item} entities.
 * <p>
 * This interface provides the mechanism for CRUD operations on items, and it
 * includes support for pagination and sorting through {@link JpaRepository} and
 * complex, dynamic queries via {@link JpaSpecificationExecutor}.
 */
@Repository
public interface ItemRepository extends JpaRepository<Item, Long>, JpaSpecificationExecutor<Item> {

    /**
     * Deletes all records from the 'item' table.
     * <p>
     * This custom bulk delete operation is intended for cleanup or reset
     * purposes. It is more efficient than standard JPA deletion because it
     * bypasses the persistence context and issues a direct SQL `DELETE`
     * statement.
     */
    @Transactional
    @Modifying // Indicates a data-changing query
    @Query("DELETE FROM Item i")
    void cleanTable();

    /**
     * Resets the sequence for the 'item' table's primary key.
     * <p>
     * <b>Note:</b> This is a native SQL query for resetting the
     * {@code AUTO_INCREMENT} value on the primary key column, commonly used in
     * databases like MySQL. It should be used with caution, typically after
     * cleaning the table, to reset ID generation.
     */
    @Transactional
    @Modifying
    @Query(value = "ALTER TABLE item AUTO_INCREMENT = 1", nativeQuery = true)
    void resetSequence();

    /**
     * Finds an item by its unique YouTube video ID.
     *
     * @param videoId The non-null, unique video ID assigned by YouTube.
     * @return An {@link Optional} containing the found item, or an empty
     * Optional if no item matches the ID.
     */
    Optional<Item> findByVideoId(String videoId);

    /**
     * Finds all {@link Item} entities with a video ID present in the given
     * list.
     *
     * @param videoIds A list of YouTube video IDs.
     * @return A list of matching {@link Item} entities.
     */
    List<Item> findAllByVideoIdIn(List<String> videoIds);

    /**
     * Finds an {@link Item} by its video ID and eagerly fetches its associated
     * entities.
     * <p>
     * This method uses {@code LEFT JOIN FETCH} to load the item's
     * {@code playlist}, the playlist's {@code channel}, and the item's
     * {@code tag} in a single query. This helps prevent N+1 select problems
     * when accessing these associations later.
     *
     * @param videoId The non-null, unique video ID assigned by YouTube.
     * @return An {@link Optional} containing the found item with its
     * associations, or an empty Optional if no item matches the ID.
     */
    @Query("SELECT i FROM Item i "
            + "LEFT JOIN FETCH i.playlist p "
            + "LEFT JOIN FETCH p.channel "
            + "LEFT JOIN FETCH i.tag "
            + "WHERE i.videoId = :videoId")
    Optional<Item> findByVideoIdWithAssociations(@Param("videoId") String videoId);

    /**
     * Finds items that have a specific tag and at least one download with a
     * specific file size, excluding an item with a given videoId. This is used
     * to detect potential duplicate files across different items.
     *
     * @param tag The tag to search for.
     * @param videoId The videoId of the item to exclude from the search.
     * @param fileSize The file size of a download to match.
     * @return A list of items matching the criteria.
     */
    @Query("SELECT i FROM Item i JOIN i.downloadInfos di WHERE i.tag = :tag AND i.videoId <> :videoId AND di.fileSize = :fileSize")
    List<Item> findByTagAndVideoIdNotAndDownloadInfosFileSize(@Param("tag") Tag tag, @Param("videoId") String videoId,
            @Param("fileSize") long fileSize);

    /**
     * Finds items published after a specific date and time.
     *
     * @param publishedAfter The cutoff date and time.
     * @return A list of items published after the given time.
     */
    List<Item> findAllByVideoPublishedAtAfter(OffsetDateTime publishedAfter);

    /**
     * Finds items published after a specific date and time, filtered by a list
     * of channel IDs.
     *
     * @param publishedAfter The cutoff date and time.
     * @param channelIds A list of YouTube channel IDs.
     * @return A list of matching items.
     */
    List<Item> findAllByVideoPublishedAtAfterAndPlaylistChannelChannelIdIn(OffsetDateTime publishedAfter, List<String> channelIds);

    /**
     * Finds pending thumbnails and sorts them by attempted time with nulls
     * first.
     *
     * @param statuses A list of {@link ThumbnailStatus} to filter by.
     * @param pageable The pagination information.
     * @return A page of matching {@link Item} entities.
     */
    @Query("SELECT i FROM Item i WHERE i.thumbnailStatus IN :statuses ORDER BY i.thumbnailAttemptedAt ASC NULLS FIRST")
    Page<Item> findPendingThumbnailsWithNullsFirst(@Param("statuses") List<ThumbnailStatus> statuses, Pageable pageable);

    /**
     * Projection interface for mapping aggregated thumbnail statistics.
     */
    interface ThumbnailCountsProjection {

        Long getTotalCount();

        Long getPendingCount();

        Long getFailedCount();
    }

    /**
     * Calculates total, pending, and failed thumbnail counts in a single query
     * using conditional aggregation to minimize database round-trips.
     *
     * @param pendingStatuses The statuses considered as pending.
     * @param failedStatus The status considered as permanently failed.
     * @return A projection containing the aggregated counts.
     */
    @Query("SELECT COUNT(i) as totalCount, "
            + "COALESCE(SUM(CASE WHEN i.thumbnailStatus IN :pendingStatuses THEN 1L ELSE 0L END), 0L) as pendingCount, "
            + "COALESCE(SUM(CASE WHEN i.thumbnailStatus = :failedStatus THEN 1L ELSE 0L END), 0L) as failedCount "
            + "FROM Item i")
    ThumbnailCountsProjection getThumbnailCounts(@Param("pendingStatuses") List<ThumbnailStatus> pendingStatuses,
            @Param("failedStatus") ThumbnailStatus failedStatus);

    /**
     * Resets all UNAVAILABLE thumbnails back to PENDING.
     *
     * @param pending The target pending status.
     * @param unavailable The current unavailable status.
     * @return The number of items updated.
     */
    @Modifying
    @Query("UPDATE Item i SET i.thumbnailStatus = :pending, i.thumbnailRetryCount = 0, i.thumbnailAttemptedAt = null WHERE i.thumbnailStatus = :unavailable")
    int resetAllUnavailableThumbnails(@Param("pending") ThumbnailStatus pending, @Param("unavailable") ThumbnailStatus unavailable);

    /**
     * Resets specific UNAVAILABLE thumbnails back to PENDING.
     *
     * @param videoIds The list of video IDs to reset.
     * @param pending The target pending status.
     * @param unavailable The current unavailable status.
     * @return The number of items updated.
     */
    @Modifying
    @Query("UPDATE Item i SET i.thumbnailStatus = :pending, i.thumbnailRetryCount = 0, i.thumbnailAttemptedAt = null WHERE i.thumbnailStatus = :unavailable AND i.videoId IN :videoIds")
    int resetUnavailableThumbnailsByVideoIds(@Param("videoIds") List<String> videoIds, @Param("pending") ThumbnailStatus pending, @Param("unavailable") ThumbnailStatus unavailable);
}
