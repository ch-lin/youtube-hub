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
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlaylistTest {

    private Playlist playlist;
    private String playlistId;
    private String title;
    private OffsetDateTime processedAt;
    private String lastPageToken;
    private Channel channel;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        playlistId = "PL_x5XG1OV2P6uZZ5FSM9Ttw";
        title = "Google for Developers Playlist";
        processedAt = OffsetDateTime.now();
        lastPageToken = "CAUQAA";
        channel = new Channel(); // A simple new Channel object is sufficient for these tests
        playlist = Playlist.builder()
                .playlistId(playlistId)
                .title(title)
                .processedAt(processedAt)
                .lastPageToken(lastPageToken)
                .channel(channel)
                .items(new HashSet<>())
                .build();
    }

    @Test
    void testGettersAndSetters() {
        Playlist p = new Playlist(playlistId);
        Set<Item> items = new HashSet<>();

        p.setTitle(title);
        p.setProcessedAt(processedAt);
        p.setLastPageToken(lastPageToken);
        p.setChannel(channel);
        p.setItems(items);

        assertNull(p.getId(), "ID should be null before persisting to the database");
        assertEquals(playlistId, p.getPlaylistId());
        assertEquals(title, p.getTitle());
        assertEquals(processedAt, p.getProcessedAt());
        assertEquals(lastPageToken, p.getLastPageToken());
        assertEquals(channel, p.getChannel());
        assertEquals(items, p.getItems());
    }

    @Test
    void testNoArgsConstructor() {
        Playlist p = new Playlist();
        assertNotNull(p);
        assertNull(p.getId());
        assertNull(p.getPlaylistId());
        assertNull(p.getTitle());
        assertNull(p.getProcessedAt());
        assertNull(p.getLastPageToken());
        assertNull(p.getChannel());
        assertNotNull(p.getItems());
        assertTrue(p.getItems().isEmpty());
    }

    @Test
    void testBuilder() {
        Long id = 1L;
        Set<Item> items = new HashSet<>();
        Playlist p = Playlist.builder()
                .id(id)
                .playlistId(playlistId)
                .title(title)
                .processedAt(processedAt)
                .lastPageToken(lastPageToken)
                .channel(channel)
                .items(items)
                .build();

        assertNotNull(p);
        assertEquals(id, p.getId());
        assertEquals(playlistId, p.getPlaylistId());
        assertEquals(title, p.getTitle());
        assertEquals(processedAt, p.getProcessedAt());
        assertEquals(lastPageToken, p.getLastPageToken());
        assertEquals(channel, p.getChannel());
        assertEquals(items, p.getItems());
    }

    @Test
    void testEqualsAndHashCode() {
        Playlist playlist2 = new Playlist(playlistId);
        playlist2.setTitle(title);

        Playlist playlist3 = new Playlist("differentId");
        playlist3.setTitle(title);

        Playlist playlist4 = new Playlist(playlistId);
        playlist4.setTitle("differentTitle");

        // Test for equality
        assertEquals(playlist, playlist2);
        assertEquals(playlist.hashCode(), playlist2.hashCode());

        // Test for equality (Same ID, different non-key fields)
        assertEquals(playlist, playlist4);
        assertEquals(playlist.hashCode(), playlist4.hashCode());

        // Test for inequality
        assertNotEquals(playlist, playlist3);
        assertNotEquals(playlist.hashCode(), playlist3.hashCode());

        // Test against null and other types
        assertNotEquals(playlist, null);
        assertNotEquals(playlist, new Object());
    }

    @Test
    void testAddItem() {
        Item item = new Item("video123");

        // The set is initialized as empty in setUp, not null.
        assertNotNull(playlist.getItems());
        assertTrue(playlist.getItems().isEmpty());

        // Pre-set the playlist on the item to stabilize its hashCode before adding it
        // to the set.
        item.setPlaylist(playlist);

        playlist.addItem(item);

        assertNotNull(playlist.getItems());
        assertTrue(playlist.getItems().contains(item));
        assertEquals(playlist, item.getPlaylist(), "Bidirectional relationship should be set on the item.");
    }

    @Test
    void testRemoveItem() {
        Item item = new Item("video123");

        // Pre-set the playlist on the item to stabilize its hashCode.
        item.setPlaylist(playlist);

        // First add the item
        playlist.addItem(item);
        assertTrue(playlist.getItems().contains(item));
        assertEquals(playlist, item.getPlaylist());

        // Now remove it
        playlist.removeItem(item);

        assertFalse(playlist.getItems().contains(item));
        assertNull(item.getPlaylist(), "Bidirectional relationship should be unset on the item.");
    }

    @Test
    void testRemoveItemFromNullSet() {
        Playlist p = new Playlist();
        p.setItems(null); // Force items to be null to cover the missing branch
        Item item = new Item();
        // Should not throw an exception
        assertDoesNotThrow(() -> p.removeItem(item));
    }

    @Test
    void testAddItemToEmptySet() {
        Playlist p = new Playlist(); // items is empty here
        assertNotNull(p.getItems(), "Items collection should not be null initially.");
        assertTrue(p.getItems().isEmpty(), "Items collection should be empty initially.");

        Item item = new Item("video123");
        // Pre-set the playlist to stabilize hashCode.
        item.setPlaylist(p);

        p.addItem(item);

        assertNotNull(p.getItems(), "Items collection should be initialized after adding an item.");
        assertTrue(p.getItems().contains(item), "Item should be in the collection after being added.");
        assertEquals(p, item.getPlaylist(), "Bidirectional relationship should be set.");
    }

    @Test
    void testAddItemWhenItemsSetIsNull() {
        Playlist p = new Playlist();
        p.setItems(null); // Force items to be null to cover the missing branch

        Item item = new Item("video123");
        item.setPlaylist(p);
        p.addItem(item);

        assertNotNull(p.getItems(), "Items collection should be initialized after adding an item.");
        assertTrue(p.getItems().contains(item), "Item should be in the collection after being added.");
    }
}
