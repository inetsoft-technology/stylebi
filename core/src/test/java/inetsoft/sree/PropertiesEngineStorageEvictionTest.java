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
package inetsoft.sree;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.storage.KeyValueStorage;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77177: {@link PropertiesEngineClusterSyncTest} and {@link PropertiesEngineLogLevelResetTest}
 * simulate a storage-instance swap via reflection (setting the {@code kvStorage} field directly to
 * an {@link inetsoft.storage.InMemoryKeyValueStorage} test double). That exercises the same
 * "stale/closed instance replaced" code path in {@code PropertiesEngine}, but never actually drives
 * a real {@link KeyValueStorageManager}'s Caffeine cache to evict and {@code close()} an instance
 * the way production does under many-org pressure (Bug #77177's root cause).
 *
 * <p>This test uses the real {@code KeyValueStorageManager} Spring bean (backed by
 * {@link inetsoft.sree.internal.cluster.MockCluster}, which provides working replicated maps and
 * map-change listener dispatch, unlike a plain reflection swap) and forces a genuine eviction by
 * fetching more than {@code MAX_SIZE} (50) distinct, unrelated store ids through the same manager,
 * exactly like the many-organization listing scenario in journal #317060 / test case P1-16. It then
 * confirms {@code PropertiesEngine} notices the evicted+closed instance and self-heals: a local
 * write still works, the storage handle is replaced, and a genuinely remote write (through the
 * shared cluster map, not through the held {@code KeyValueStorage} instance) is still observed.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PropertiesEngineStorageEvictionTest {
   @BeforeEach
   void setUp() {
      engine = PropertiesEngine.getInstance();
      engine.clear();
      engine.init();
      manager = KeyValueStorageManager.getInstance();
   }

   @AfterEach
   void tearDown() {
      engine.clear();
      engine.init();
   }

   @Test
   void engineSelfHealsAfterRealCaffeineEviction() throws Exception {
      String localKey = "test77177.local." + UUID.randomUUID();
      String remoteKey = "test77177.remote." + UUID.randomUUID();

      // Sanity: the engine already holds a live, real storage instance for "sreeProperties",
      // fetched through KeyValueStorageManager during initEngine()/init() above.
      KeyValueStorage<String> before = getKvStorageField();
      assertNotNull(before);
      assertFalse(before.isClosed());

      // Force a REAL eviction: push more than MAX_SIZE(50) distinct, unrelated ids through the
      // same manager instance so Caffeine's eviction policy reclaims the idle "sreeProperties"
      // entry and its removalListener calls close() on it -- this is KeyValueStorageManager's
      // actual production eviction path, not a reflection-based field swap.
      for(int i = 0; i < 60; i++) {
         manager.<Serializable>getStorage("test77177.evict." + i);
      }

      // Caffeine's removal listener runs asynchronously relative to the triggering get() (see
      // 02-refute.md's recheck of the "synchronously calls close()" wording), so poll for it.
      waitFor(before::isClosed);
      assertTrue(before.isClosed(),
                 "KeyValueStorageManager never evicted+closed the held storage instance; " +
                 "this test's premise (a real Caffeine eviction) did not occur");

      // Before the fix, PropertiesEngine.kvStorage would still point at this closed instance,
      // permanently: get()/put() would keep silently working against the still-live shared map
      // (masking the bug for point reads/writes), but the listener would be permanently dead and
      // any future *enumeration* (loadFromStorage()/LayerProperties.load()) would go silently
      // empty. Drive a local write -- the confirmed self-healing trigger -- and confirm it
      // recovers instead of silently operating on a dead instance.
      engine.setProperty(localKey, "value-after-eviction");
      engine.save();

      assertEquals("value-after-eviction", engine.getProperty(localKey),
                   "a local write after eviction was lost");

      KeyValueStorage<String> after = getKvStorageField();
      assertNotSame(before, after,
                    "the engine kept using the evicted, closed storage instance instead of " +
                    "re-fetching a live one");
      assertFalse(after.isClosed(), "the engine healed into another already-closed instance");

      // And the healed instance's listener genuinely works: a write made directly on the shared
      // cluster-level replicated map (i.e. NOT through the KeyValueStorage instance the engine
      // holds -- simulating another node's write) must still reach PropertiesEngine's change
      // listener, trigger the debounced reload, and show up in the in-memory properties. Before
      // the fix this would never happen once the original instance was closed: addListener() on
      // a closed LocalKeyValueStorage is a permanent, silent dead end (01-diagnosis.md).
      Cluster cluster = Cluster.getInstance();
      DistributedMap<String, String> map =
         cluster.getReplicatedMap("inetsoft.storage.kv.sreeProperties");
      map.put(remoteKey, "remote-value");

      waitFor(() -> "remote-value".equals(engine.getProperty(remoteKey)));
   }

   @SuppressWarnings("unchecked")
   private KeyValueStorage<String> getKvStorageField() throws Exception {
      Field field = PropertiesEngine.class.getDeclaredField("kvStorage");
      field.setAccessible(true);
      return (KeyValueStorage<String>) field.get(engine);
   }

   private static void waitFor(BooleanSupplier condition) throws InterruptedException {
      long end = System.currentTimeMillis() + 10000L;

      while(!condition.getAsBoolean()) {
         if(System.currentTimeMillis() > end) {
            fail("Timed out waiting for the condition");
         }

         Thread.sleep(50L);
      }
   }

   private PropertiesEngine engine;
   private KeyValueStorageManager manager;
}
