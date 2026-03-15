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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a historical record of statistics (view count, like count, etc.)
 * for a specific {@link Item} at a point in time.
 */
@Table(name = "item_statistic_history", indexes = {
    @Index(name = "idx_item_stat_item_id", columnList = "item_id"),
    @Index(name = "idx_item_stat_recorded_at", columnList = "recorded_at")
})
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ItemStatisticHistory extends BaseEntity {

    /**
     * The view count of the video at the time of recording.
     */
    @Column(name = "view_count")
    private Long viewCount = 0L;

    /**
     * The like count of the video at the time of recording.
     */
    @Column(name = "like_count")
    private Long likeCount = 0L;

    /**
     * The comment count of the video at the time of recording.
     */
    @Column(name = "comment_count")
    private Long commentCount = 0L;

    /**
     * The date and time when this statistic record was captured.
     */
    @NotNull
    @Column(name = "recorded_at", columnDefinition = "TIMESTAMP", nullable = false)
    private OffsetDateTime recordedAt;

    /**
     * The {@link Item} associated with these statistics.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", referencedColumnName = BaseEntity.ID_COLUMN, nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_item_stat_item"))
    private Item item;
}
