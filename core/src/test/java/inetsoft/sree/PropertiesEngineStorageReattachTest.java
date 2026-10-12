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

import inetsoft.storage.InMemoryKeyValueStorage;
import inetsoft.storage.KeyValueStorage;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.util.FileSystemService;
import inetsoft.util.log.LogManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.beans.PropertyChangeEvent;
import java.lang.reflect.Field;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77871: the reload that {@code PropertiesEngine} schedules when it re-attaches a closed
 * property storage. Each test owns an engine that holds a closable in-memory storage and a manager
 * that hands out a replacement, so the reloads and listener events can be counted.
 * {@link PropertiesEngineStorageEvictionTest} drives the same path through a real eviction.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PropertiesEngineStorageReattachTest {
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
    * Bug #77871: a change event schedules an ordinary reload, and before it runs the held
    * instance is evicted and another node changes a key. The reload's own init(true) re-attaches
    * and already loads the missed value, so the follow-up reload the re-attach schedules sees no
    * difference. The listeners of the missed key must still be notified, once, and the key that
    * came as an event must not be notified again.
    */
   @Test
   void reattachInsideScheduledReloadNotifiesListeners() throws Exception {
      ClosableStorage replacement = new ClosableStorage();
      PropertiesEngine owner = createOwner(replacement);
      java.util.List<PropertyChangeEvent> events = new CopyOnWriteArrayList<>();
      owner.addPropertyChangeListener("test77871.changed", events::add);
      owner.addPropertyChangeListener("test77871.event", events::add);

      try {
         // an event on the held instance schedules an ordinary reload 500 ms later
         replacement.remotePut("test77871.event", "e", false);
         ownerStorage.remotePut("test77871.event", "e", true);

         // within that delay the instance is evicted and another node changes a key, which
         // fires no event here; nothing re-attaches until the scheduled reload runs
         ownerStorage.closed = true;
         replacement.remotePut("test77871.changed", "new", false);

         waitFor(() -> "new".equals(owner.getProperty("test77871.changed")));
         waitFor(() -> events.stream().anyMatch(e -> "test77871.changed".equals(e.getPropertyName())
            && "old".equals(e.getOldValue()) && "new".equals(e.getNewValue())));

         // let the reload the re-attach scheduled run too, then check nothing fired twice
         Thread.sleep(1200L);
         assertEquals(2, events.size(), events.toString());
         assertEquals(1, events.stream()
            .filter(e -> "test77871.event".equals(e.getPropertyName())).count(), events.toString());
      }
      finally {
         owner.shutdown();
         EarlyLoadedProperties.restore(earlyLoaded);
      }
   }

   /**
    * Bug #77871: a change event schedules an ordinary reload, and before it runs the held
    * instance is evicted, another node changes a key, and another caller re-attaches. The reload
    * the re-attach schedules merges into the pending one, so a single reload runs. It must still
    * notify the listeners of the missed key, once, and must not notify the key that came as an
    * event again.
    */
   @Test
   void reattachWhileReloadPendingMergesIntoOneReloadAndNotifiesListeners() throws Exception {
      ClosableStorage replacement = new ClosableStorage();
      PropertiesEngine owner = createOwner(replacement);
      java.util.List<PropertyChangeEvent> events = new CopyOnWriteArrayList<>();
      owner.addPropertyChangeListener("test77871.changed", events::add);
      owner.addPropertyChangeListener("test77871.event", events::add);

      try {
         // an event on the held instance schedules an ordinary reload 500 ms later
         replacement.remotePut("test77871.event", "e", false);
         ownerStorage.remotePut("test77871.event", "e", true);

         // within that delay the instance is evicted, another node changes a key without an
         // event here, and a storage read re-attaches before the scheduled reload runs
         ownerStorage.closed = true;
         replacement.remotePut("test77871.changed", "new", false);
         assertEquals("new", owner.getPropertyFromStorage("test77871.changed"));
         assertSame(replacement, getKvStorageField(owner));

         waitFor(() -> "new".equals(owner.getProperty("test77871.changed")));
         waitFor(() -> events.stream().anyMatch(e -> "test77871.changed".equals(e.getPropertyName())
            && "old".equals(e.getOldValue()) && "new".equals(e.getNewValue())));

         // the two tasks were merged into one reload, and nothing fired twice
         Thread.sleep(800L);
         assertEquals(1, reloads.get(), "the pending reload and the re-attach reload did not merge");
         assertEquals(2, events.size(), events.toString());
         assertEquals(1, events.stream()
            .filter(e -> "test77871.event".equals(e.getPropertyName())).count(), events.toString());
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


   private static void waitFor(BooleanSupplier condition) throws InterruptedException {
      long end = System.currentTimeMillis() + 10000L;

      while(!condition.getAsBoolean()) {
         if(System.currentTimeMillis() > end) {
            fail("Timed out waiting for the condition");
         }

         Thread.sleep(50L);
      }
   }

   @Autowired
   private ConfigurableApplicationContext context;
   private final AtomicInteger reloads = new AtomicInteger();
   private ClosableStorage ownerStorage;
   private EarlyLoadedProperties earlyLoaded;
}
