/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.storage;

import inetsoft.sree.internal.cluster.*;
import inetsoft.test.*;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.util.BlobIndexedStorage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.Serializable;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78532: a reload of a key-value store whose distributed map differs from the stored content
 * (the size guard in {@link LoadKeyValueTask#run()}) writes only the changed entries over the map
 * and removes the keys that are no longer stored, instead of clearing the map first. The map must
 * still end up holding exactly the stored content.
 *
 * <p>{@code MockCluster.removeAll(Set)} fires no events, so the removal of stale keys is checked
 * by the map content only.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LoadKeyValueTaskReloadTest {
   /**
    * Reloading a map that has a changed value, a key that is no longer stored and a missing key
    * leaves it equal to the stored content, and fires no event for an unchanged entry.
    */
   @Test
   void reloadOverDivergentMapMatchesStoredContent() {
      String id = "reload78532Values";
      DistributedMap<String, String> map = cluster.getReplicatedMap(MAP_PREFIX + id);
      map.put("a", "1");
      map.put("same", "s");
      map.put("stale", "x");
      map.put("extra", "y");
      engine.put(id, "a", "2");
      engine.put(id, "same", "s");
      engine.put(id, "b", "3");
      EventCounter events = new EventCounter();
      cluster.addReplicatedMapListener(MAP_PREFIX + id, events);

      try {
         new LoadKeyValueTask<String>(id).run();
      }
      finally {
         cluster.removeReplicatedMapListener(MAP_PREFIX + id, events);
      }

      assertEquals(Map.of("a", "2", "same", "s", "b", "3"), new HashMap<>(map));
      assertEquals(List.of(), events.get("same"), "events fired for an unchanged entry");
      assertEquals(List.of("updated"), events.get("a"), "events fired for a changed entry");
      assertEquals(List.of("added"), events.get("b"), "events fired for a missing entry");
   }

   /**
    * Opening a blob store whose map holds an outdated blob and a blob that is no longer stored
    * gives the map the stored blob and removes the other, and does not rewrite an unchanged blob
    * whose metadata does not implement {@code equals()}.
    */
   @Test
   void blobReloadOverDivergentMapMatchesStoredContent() throws Exception {
      String id = "reload78532Blobs";
      Instant old = Instant.parse("2026-01-01T00:00:00Z");
      Instant now = Instant.parse("2026-10-14T00:00:00Z");
      Blob<BlobIndexedStorage.Metadata> stored = blob("k", "d2", 20L, now);
      DistributedMap<String, Blob<BlobIndexedStorage.Metadata>> map =
         cluster.getReplicatedMap(MAP_PREFIX + id);
      map.put("k", blob("k", "d1", 10L, old));
      map.put("same", blob("same", "d3", 30L, old));
      map.put("gone", blob("gone", "d4", 40L, old));
      engine.put(id, "k", stored);
      engine.put(id, "same", blob("same", "d3", 30L, old));
      EventCounter events = new EventCounter();
      cluster.addReplicatedMapListener(MAP_PREFIX + id, events);

      try(KeyValueStorageManager manager = new KeyValueStorageManager(engine, cluster)) {
         BlobStorage<BlobIndexedStorage.Metadata> storage =
            BlobStorage.createBlobStorage(id, true, blobCache, manager, cluster);
         storage.close();
      }
      finally {
         cluster.removeReplicatedMapListener(MAP_PREFIX + id, events);
      }

      assertEquals(Set.of("k", "same"), new HashSet<>(map.keySet()));
      Blob<BlobIndexedStorage.Metadata> reloaded = map.get("k");
      assertEquals(now, reloaded.getLastModified());
      assertEquals("d2", reloaded.getDigest());
      assertEquals(20L, reloaded.getLength());
      assertEquals(List.of(), events.get("same"), "events fired for an unchanged blob");
      assertEquals(List.of("updated"), events.get("k"), "events fired for a changed blob");
   }

   private static Blob<BlobIndexedStorage.Metadata> blob(String path, String digest, long length,
                                                         Instant lastModified)
   {
      // a new AssetFolder for each blob, so two otherwise identical blobs are not equal
      BlobIndexedStorage.Metadata metadata = new BlobIndexedStorage.Metadata();
      metadata.setClassName(AssetFolder.class.getName());
      metadata.setIdentifier(path);
      metadata.setFolder(new AssetFolder());
      return new Blob<>(path, digest, length, lastModified, metadata);
   }

   private static final class EventCounter implements MapChangeListener<String, Object> {
      List<String> get(String key) {
         synchronized(events) {
            return new ArrayList<>(events.getOrDefault(key, List.of()));
         }
      }

      @Override
      public void entryAdded(EntryEvent<String, Object> event) {
         add(event.getKey(), "added");
      }

      @Override
      public void entryUpdated(EntryEvent<String, Object> event) {
         add(event.getKey(), "updated");
      }

      @Override
      public void entryRemoved(EntryEvent<String, Object> event) {
         add(event.getKey(), "removed");
      }

      private void add(String key, String type) {
         synchronized(events) {
            events.computeIfAbsent(key, k -> new ArrayList<>()).add(type);
         }
      }

      private final Map<String, List<String>> events = new HashMap<>();
   }

   @Autowired Cluster cluster;
   @Autowired KeyValueEngine engine;
   @Autowired BlobCache blobCache;

   private static final String MAP_PREFIX = "inetsoft.storage.kv.";
}
