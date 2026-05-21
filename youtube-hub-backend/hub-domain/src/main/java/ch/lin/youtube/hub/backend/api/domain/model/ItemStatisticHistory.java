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

import org.hibernate.annotations.ColumnDefault;

import ch.lin.platform.domain.model.BaseEntity;
import static ch.lin.youtube.hub.backend.api.domain.model.ItemStatisticHistory.ITEM_ID_COLUMN;
import static ch.lin.youtube.hub.backend.api.domain.model.ItemStatisticHistory.ITEM_ID_INDEX;
import static ch.lin.youtube.hub.backend.api.domain.model.ItemStatisticHistory.RECORDED_AT_COLUMN;
import static ch.lin.youtube.hub.backend.api.domain.model.ItemStatisticHistory.RECORDED_AT_INDEX;
import static ch.lin.youtube.hub.backend.api.domain.model.ItemStatisticHistory.TABLE_NAME;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Represents a historical record of statistics (view count, like count, etc.)
 * for a specific {@link Item} at a point in time.
 */
@Entity
@Table(name = TABLE_NAME, indexes = {
    @Index(name = ITEM_ID_INDEX, columnList = ITEM_ID_COLUMN),
    @Index(name = RECORDED_AT_INDEX, columnList = RECORDED_AT_COLUMN)
})
@Getter
@EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ItemStatisticHistory extends BaseEntity {

    /**
     * The name of the item statistic history table in the database.
     */
    public static final String TABLE_NAME = "item_statistic_history";

    /**
     * The name of the item ID column in the database.
     */
    public static final String ITEM_ID_COLUMN = "item_id";

    /**
     * The name of the index for the item ID column.
     */
    public static final String ITEM_ID_INDEX = "idx_item_stat_item_id";

    /**
     * The name of the recorded at column in the database.
     */
    public static final String RECORDED_AT_COLUMN = "recorded_at";

    /**
     * The name of the index for the recorded at column.
     */
    public static final String RECORDED_AT_INDEX = "idx_item_stat_recorded_at";

    /**
     * The name of the view count column in the database.
     */
    public static final String VIEW_COUNT_COLUMN = "view_count";

    /**
     * The name of the like count column in the database.
     */
    public static final String LIKE_COUNT_COLUMN = "like_count";

    /**
     * The name of the comment count column in the database.
     */
    public static final String COMMENT_COUNT_COLUMN = "comment_count";

    /**
     * The name of the foreign key constraint for the item relationship.
     */
    public static final String FK_ITEM_STAT_ITEM = "fk_item_stat_item";

    /**
     * The view count of the video at the time of recording.
     */
    @ColumnDefault("0")
    @Column(name = ItemStatisticHistory.VIEW_COUNT_COLUMN, nullable = false)
    @Setter
    @lombok.Builder.Default
    private long viewCount = 0L;

    /**
     * The like count of the video at the time of recording.
     */
    @ColumnDefault("0")
    @Column(name = ItemStatisticHistory.LIKE_COUNT_COLUMN, nullable = false)
    @Setter
    @lombok.Builder.Default
    private long likeCount = 0L;

    /**
     * The comment count of the video at the time of recording.
     */
    @ColumnDefault("0")
    @Column(name = ItemStatisticHistory.COMMENT_COUNT_COLUMN, nullable = false)
    @Setter
    @lombok.Builder.Default
    private long commentCount = 0L;

    /**
     * The date and time when this statistic record was captured.
     */
    @NotNull
    @Column(name = ItemStatisticHistory.RECORDED_AT_COLUMN, columnDefinition = "TIMESTAMP", nullable = false)
    @Setter
    private OffsetDateTime recordedAt;

    /**
     * The {@link Item} associated with these statistics.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = ItemStatisticHistory.ITEM_ID_COLUMN, referencedColumnName = BaseEntity.ID_COLUMN, nullable = false, updatable = false, foreignKey = @ForeignKey(name = ItemStatisticHistory.FK_ITEM_STAT_ITEM))
    private Item item;

    /**
     * Creates a new ItemStatisticHistory linked to a specific Item.
     *
     * @param item The associated Item entity.
     */
    public ItemStatisticHistory(Item item) {
        this();
        this.item = item;
        this.recordedAt = OffsetDateTime.now();
    }
}
