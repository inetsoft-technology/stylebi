/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.ConfigurationContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.OutputStream;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81573, the managers close the least recently used store when more than 50 stores are in
 * use. A holder of such a store, e.g. the copy of an organization, must still list all of its
 * entries. Only a store closed by the shutdown of the manager lists nothing.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class StorageEvictionEnumerationTest {
   private static final int BLOBS = 100;
   private static final int FILLERS = 60;
   private static final int TRIALS = 20;

   static class Meta implements Serializable {
   }

   @Test
   void blobStorageListsAllPathsAfterItWasEvicted() throws Exception {
      BlobStorageManager manager = BlobStorageManager.getInstance();
      int evicted = 0;

      for(int t = 0; t < TRIALS; t++) {
         String a = "evict81573a" + t + "__mvws";
         String b = "evict81573b" + t + "__mvws";
         BlobStorage<Meta> source = manager.getStorage(a, false);
         Set<String> expected = new TreeSet<>();

         for(int i = 0; i < BLOBS; i++) {
            String path = "blob" + i;
            expected.add(path);

            try(BlobTransaction<Meta> tx = source.beginTransaction();
                OutputStream out = tx.newStream(path, new Meta()))
            {
               out.write(i);
               tx.commit();
            }
         }

         touchFillers(manager);
         // as the copy of an organization does: the source, then the target, then the listing
         BlobStorage<Meta> held = manager.getStorage(a, false);
         manager.getStorage(b, false);

         if(held.isClosed()) {
            evicted++;
         }

         assertEquals(expected, held.paths().collect(Collectors.toCollection(TreeSet::new)),
                      "trial " + t + ", closed=" + held.isClosed());
         assertEquals(BLOBS, held.stream().count(), "trial " + t);
      }

      // the situation of the bug must have been reached, or the test proves nothing
      assertTrue(evicted > 0, "no store was evicted in " + TRIALS + " trials");
   }

   @Test
   void keyValueStorageListsAllKeysAfterItWasEvicted() throws Exception {
      KeyValueStorageManager manager = KeyValueStorageManager.getInstance();

      for(int t = 0; t < TRIALS; t++) {
         String a = "evict81573kva" + t;
         String b = "evict81573kvb" + t;
         KeyValueStorage<String> source = manager.getStorage(a);
         Set<String> expected = new TreeSet<>();

         for(int i = 0; i < BLOBS; i++) {
            expected.add("key" + i);
            source.put("key" + i, "value" + i).get(1, TimeUnit.MINUTES);
         }

         for(int k = 0; k < 3; k++) {
            for(int i = 0; i < FILLERS; i++) {
               manager.getStorage("evict81573kvf" + i);
            }
         }

         KeyValueStorage<String> held = manager.getStorage(a);

         assertEquals(expected, held.keys().collect(Collectors.toCollection(TreeSet::new)),
                      "trial " + t + ", closed=" + held.isClosed());
         assertEquals(BLOBS, held.stream().count(), "trial " + t);
      }

      // an eviction is not deterministic for plain key-value stores, closing the store as the
      // eviction listener does is
      KeyValueStorage<String> closed = manager.getStorage("evict81573kvc");
      closed.put("k1", "v1").get(1, TimeUnit.MINUTES);
      closed.put("k2", "v2").get(1, TimeUnit.MINUTES);
      closed.close();
      assertTrue(closed.isClosed());
      assertEquals(Set.of("k1", "k2"), closed.keys().collect(Collectors.toSet()));
      assertEquals(2, closed.stream().count());
   }

   @Test
   void closingTheManagersDoesNotThrowAndListsNothing() throws Exception {
      ConfigurationContext context = ConfigurationContext.getContext();
      Cluster cluster = context.getSpringBean(Cluster.class);
      KeyValueStorageManager kvManager =
         new KeyValueStorageManager(context.getSpringBean(KeyValueEngine.class), cluster);
      BlobStorageManager blobManager = new BlobStorageManager(
         context.getSpringBean(BlobEngine.class), kvManager,
         context.getSpringBean(BlobCache.class), cluster);

      KeyValueStorage<String> kv = kvManager.getStorage("shutdown81573kv");
      kv.put("k", "v").get(1, TimeUnit.MINUTES);
      BlobStorage<Meta> blob = blobManager.getStorage("shutdown81573__mvws", false);

      try(BlobTransaction<Meta> tx = blob.beginTransaction();
          OutputStream out = tx.newStream("p", new Meta()))
      {
         out.write(1);
         tx.commit();
      }

      assertEquals(1, kv.keys().count());
      assertEquals(1, blob.paths().count());

      assertDoesNotThrow(blobManager::close);
      assertDoesNotThrow(kvManager::close);

      assertTrue(kv.isClosed());
      assertTrue(blob.isClosed());
      assertEquals(0, kv.keys().count());
      assertEquals(0, kv.stream().count());
      assertEquals(0, blob.paths().count());
      // reads of single entries keep working, as before
      assertEquals("v", kv.get("k"));
   }

   private void touchFillers(BlobStorageManager manager) {
      for(int k = 0; k < 3; k++) {
         for(int i = 0; i < FILLERS; i++) {
            manager.getStorage("evict81573f" + i + "__indexedStorage", true);
         }
      }
   }
}
