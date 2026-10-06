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
import inetsoft.storage.InMemoryKeyValueStorage;
import inetsoft.storage.KeyValueStorage;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.storage.PutKeyValueTask;
import inetsoft.test.*;
import inetsoft.util.FileSystemService;
import inetsoft.util.log.LogManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
      // actual production eviction path, not a reflection-based field swap. The removal listener
      // runs asynchronously, and a recently re-fetched entry (e.g. by another test in this class)
      // may survive one pass, so evict() polls and repeats.
      evict(before);

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

   /**
    * Bug #77871: a change another node stores while the held instance is evicted (closed, so its
    * map listener is removed) fires no event here. Re-attaching through a read path must reload
    * the properties without waiting for some later, unrelated change, and must notify the
    * per-property listeners that refresh derived settings.
    */
   @Test
   void changeStoredWhileDetachedIsAppliedAfterReattach() throws Exception {
      String key = "test77871.remote." + UUID.randomUUID();
      java.util.List<PropertyChangeEvent> events = new CopyOnWriteArrayList<>();
      PropertyChangeListener listener = events::add;
      engine.addPropertyChangeListener(key, listener);

      try {
         KeyValueStorage<String> before = getKvStorageField();
         evict(before);

         // the task another node's LocalKeyValueStorage.put() submits; nothing has re-attached
         // yet, so no listener of this engine hears it
         Cluster.getInstance()
            .submit("sreeProperties", new PutKeyValueTask<>("sreeProperties", key, "remote-value"))
            .get();
         assertNull(engine.getProperty(key));

         // a storage read re-attaches through getStorage()
         assertEquals("remote-value", engine.getPropertyFromStorage(key));
         assertNotSame(before, getKvStorageField());

         // no later change is made: the re-attach itself must bring the value in
         waitFor(() -> "remote-value".equals(engine.getProperty(key)));
         waitFor(() -> events.stream().anyMatch(e -> "remote-value".equals(e.getNewValue())));
      }
      finally {
         engine.removePropertyChangeListener(key, listener);
         engine.remove(key);
         engine.save();
      }
   }

   /**
    * Evicts the given instance through the real manager. A recently re-fetched entry may survive
    * one pass of filler ids (W-TinyLFU admission), so the same ids are fetched again until it is
    * closed.
    */
   private void evict(KeyValueStorage<String> storage) throws InterruptedException {
      for(int pass = 0; pass < 10 && !storage.isClosed(); pass++) {
         for(int i = 0; i < 60; i++) {
            manager.<Serializable>getStorage("test77871.evict." + i);
         }

         long end = System.currentTimeMillis() + 2000L;

         while(!storage.isClosed() && System.currentTimeMillis() < end) {
            Thread.sleep(50L);
         }
      }

      assertTrue(storage.isClosed(), "the held storage instance was never evicted and closed");
   }

   /**
    * Bug #77871: the reload that a re-attach schedules runs once and does not schedule another
    * one, notifies the listeners of the keys changed and removed while detached, and keeps a
    * local change that was not saved yet. The engine here owns a closable in-memory storage and
    * a manager that hands out a replacement, so the reloads it publishes can be counted.
    */
   @Test
   void reattachReloadsOnceNotifiesListenersAndKeepsLocalChange() throws Exception {
      ClosableStorage replacement = new ClosableStorage();
      PropertiesEngine owner = createOwner(replacement);
      java.util.List<PropertyChangeEvent> events = new CopyOnWriteArrayList<>();
      owner.addPropertyChangeListener("test77871.changed", events::add);
      owner.addPropertyChangeListener("test77871.removed", events::add);
      owner.addPropertyChangeListener("test77871.unchanged", events::add);

      try {
         // the held instance is evicted and closed; its contents stay in the shared map, which
         // the replacement shares, and another node changes and removes keys meanwhile
         ownerStorage.closed = true;
         replacement.remotePut("test77871.unchanged", "same", false);
         replacement.remotePut("test77871.changed", "new", false);
         assertEquals("old", owner.getProperty("test77871.changed"));
         assertEquals("same", owner.getProperty("test77871.unchanged"));
         assertEquals("gone", owner.getProperty("test77871.removed"));

         // a local change, not saved yet, re-attaches through the baseline read
         owner.setProperty("test77871.local", "local-unsaved");
         assertSame(replacement, getKvStorageField(owner));

         waitFor(() -> reloads.get() > 0);
         assertEquals("new", owner.getProperty("test77871.changed"));
         assertNull(owner.getProperty("test77871.removed"));
         assertEquals("local-unsaved", owner.getProperty("test77871.local"));

         assertTrue(events.stream().anyMatch(e -> "test77871.changed".equals(e.getPropertyName()) &&
            "old".equals(e.getOldValue()) && "new".equals(e.getNewValue())), events.toString());
         assertTrue(events.stream().anyMatch(e -> "test77871.removed".equals(e.getPropertyName()) &&
            "gone".equals(e.getOldValue()) && e.getNewValue() == null), events.toString());
         assertTrue(events.stream().noneMatch(
            e -> "test77871.unchanged".equals(e.getPropertyName())), events.toString());
         assertEquals(2, events.size(), events.toString());

         // the reload's own getStorage() finds the live replacement, so nothing is rescheduled
         Thread.sleep(1200L);
         assertEquals(1, reloads.get(), "the re-attach reload scheduled another reload");

         // the local change is still saved to the replacement afterwards
         owner.save();
         assertEquals("local-unsaved", replacement.get("test77871.local"));
      }
      finally {
         owner.shutdown();
         EarlyLoadedProperties.restore(earlyLoaded);
      }
   }

   /**
    * Bug #77871 / Bug #77201: a re-attach after the engine shut down schedules no reload. Its
    * debouncer is closed, so a schedule would also throw out of the read.
    */
   @Test
   void reattachAfterShutdownSchedulesNoReload() throws Exception {
      ClosableStorage replacement = new ClosableStorage();
      PropertiesEngine owner = createOwner(replacement);
      Properties before = owner.getInternalProperties();

      try {
         replacement.remotePut("test77871.changed", "new", false);
         owner.shutdown();
         ownerStorage.closed = true;

         assertEquals("new", owner.getPropertyFromStorage("test77871.changed"));
         assertSame(replacement, getKvStorageField(owner));

         Thread.sleep(1000L);
         assertEquals(0, reloads.get(), "a closed engine reloaded after a re-attach");
         assertSame(before, owner.getInternalProperties());
      }
      finally {
         EarlyLoadedProperties.restore(earlyLoaded);
      }
   }

   /**
    * Creates an engine that holds an in-memory storage with three stored properties, and whose
    * manager returns the given replacement once that storage is closed. The replacement starts
    * with the same contents, as an instance re-fetched from the same shared map does.
    */
   private PropertiesEngine createOwner(ClosableStorage replacement) throws Exception {
      earlyLoaded = EarlyLoadedProperties.getInstance();
      reloads.set(0);
      ApplicationEventPublisher publisher = event -> {
         if(event instanceof ApplicationPropertiesChangedEvent) {
            reloads.incrementAndGet();
         }
      };
      KeyValueStorageManager ownerManager = mock(KeyValueStorageManager.class);
      when(ownerManager.<String>getStorage(anyString())).thenReturn(replacement);
      PropertiesEngine owner = new PropertiesEngine(
         ownerManager, context.getBean(FileSystemService.class), publisher,
         context.getBeanProvider(LogManager.class));
      ownerStorage = new ClosableStorage();

      for(ClosableStorage storage : java.util.List.of(ownerStorage, replacement)) {
         storage.remotePut("test77871.changed", "old", false);
         storage.remotePut("test77871.removed", "gone", false);
         storage.remotePut("test77871.unchanged", "same", false);
      }

      replacement.remoteRemove("test77871.removed", false);
      Field field = PropertiesEngine.class.getDeclaredField("kvStorage");
      field.setAccessible(true);
      field.set(owner, ownerStorage);
      owner.init();
      return owner;
   }

   @SuppressWarnings("unchecked")
   private static KeyValueStorage<String> getKvStorageField(PropertiesEngine owner)
      throws Exception
   {
      Field field = PropertiesEngine.class.getDeclaredField("kvStorage");
      field.setAccessible(true);
      return (KeyValueStorage<String>) field.get(owner);
   }

   private static final class ClosableStorage extends InMemoryKeyValueStorage<String> {
      @Override
      public boolean isClosed() {
         return closed;
      }

      private volatile boolean closed;
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
   @Autowired
   private ConfigurableApplicationContext context;
   private final AtomicInteger reloads = new AtomicInteger();
   private ClosableStorage ownerStorage;
   private EarlyLoadedProperties earlyLoaded;
}
