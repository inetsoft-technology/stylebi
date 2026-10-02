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

import inetsoft.storage.*;
import inetsoft.test.*;
import inetsoft.util.DefaultDebouncer;
import inetsoft.util.FileSystemService;
import inetsoft.util.log.LogManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77142: a change-triggered reload ({@code init(true)}) published {@code null} properties
 * while it ran, so a lock-free reader that had just passed its null check failed with a
 * {@code NullPointerException} ({@code getProperty}, {@code initLogging}, {@code save}). And the
 * debounced change task of an engine reloaded whatever engine {@code getInstance()} resolved,
 * even after its own engine was shut down, i.e. the engine of the next test class.
 *
 * <p>Axis: lock-free readers x writers x change-triggered reload x shutdown. The interleavings
 * are forced with storage hooks and latches that run inside the reload, while it holds the
 * properties lock, instead of relying on timing.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PropertiesEngineReloadRaceTest {
   @BeforeEach
   void swapStorage(TestInfo info) throws Exception {
      prefix = "test77142." + info.getTestMethod().orElseThrow().getName().toLowerCase() + ".";
      engine = PropertiesEngine.getInstance();
      engine.clear();
      originalStorage = getStorage(engine);
      storage = new HookStorage();
      setStorage(engine, storage);
   }

   @AfterEach
   void restoreStorage() throws Exception {
      for(PropertiesEngine other : others) {
         other.shutdown();
      }

      for(HookStorage other : otherStorages) {
         other.close();
      }

      engine.clear();
      setStorage(engine, originalStorage);
      engine.init();
   }

   @Test
   void readerDuringReloadSeesThePreviousProperties() throws Exception {
      String name = prefix + "key";
      storage.remotePut(name, "old", false);
      initEngine();
      storage.remotePut(name, "new", false);

      AtomicReference<Properties> published = new AtomicReference<>();
      AtomicReference<String> read = new AtomicReference<>();
      AtomicReference<String> readEarlyLoaded = new AtomicReference<>();
      AtomicReference<Properties> readAll = new AtomicReference<>();
      AtomicReference<Throwable> failure = new AtomicReference<>();
      AtomicBoolean readDuringReload = new AtomicBoolean();
      Thread reader = new Thread(() -> {
         try {
            read.set(engine.getProperty(name));
            readEarlyLoaded.set(engine.getProperty(name, true));
            readAll.set(engine.getProperties());
         }
         catch(Throwable e) {
            failure.set(e);
         }
      });

      // runs inside the reload, which holds the properties lock
      storage.streamHook = () -> {
         published.set(engine.getInternalProperties());
         reader.start();
         join(reader, 5000L);
         readDuringReload.set(!reader.isAlive());
      };
      engine.init(true);
      reader.join(10000L);

      assertNotNull(published.get(), "the reload published null properties");
      assertNull(failure.get());
      assertTrue(readDuringReload.get(), "a reader had to wait for the reload");
      assertEquals("old", read.get());
      assertEquals("old", readEarlyLoaded.get(),
                   "the early-loaded properties were empty during the reload");
      assertNotNull(readAll.get());
      assertEquals("new", engine.getProperty(name));
      assertEquals("new", engine.getProperty(name, true));
   }

   @Test
   void earlyLoadedReaderDuringEarlyLoadedBuildSeesThePreviousProperties() throws Exception {
      // the reload builds new early-loaded properties (defaults, system properties, environment)
      // before it loads the storage into them. A reader of the early-loaded properties that runs
      // while they are built must still resolve the installed instance, with its stored values.
      String name = prefix + "key";
      storage.remotePut(name, "old", false);
      initEngine();
      storage.remotePut(name, "new", false);
      EarlyLoadedProperties installed = EarlyLoadedProperties.getInstance();
      assertEquals("old", engine.getProperty(name, true));

      Thread reloadThread = Thread.currentThread();
      AtomicBoolean armed = new AtomicBoolean();
      AtomicInteger builds = new AtomicInteger();
      AtomicReference<EarlyLoadedProperties> seen = new AtomicReference<>();
      AtomicReference<String> read = new AtomicReference<>();
      AtomicReference<Throwable> failure = new AtomicReference<>();
      Properties systemProperties = System.getProperties();
      // EarlyLoadedProperties.build() falls back to the system properties for this key, so the
      // hook runs inside the build of the reload's early-loaded properties
      Properties hooked = new Properties() {
         @Override
         public String getProperty(String key) {
            if(Thread.currentThread() == reloadThread &&
               "StyleReport.locale.resource".equals(key) && armed.compareAndSet(true, false))
            {
               builds.incrementAndGet();
               Thread reader = new Thread(() -> {
                  try {
                     seen.set(EarlyLoadedProperties.getInstance());
                     read.set(engine.getProperty(name, true));
                  }
                  catch(Throwable e) {
                     failure.set(e);
                  }
               });
               reader.start();
               PropertiesEngineReloadRaceTest.join(reader, 5000L);
            }

            return super.getProperty(key);
         }
      };
      hooked.putAll(systemProperties);
      System.setProperties(hooked);

      try {
         armed.set(true);
         engine.init(true);
      }
      finally {
         armed.set(false);
         System.setProperties(systemProperties);
      }

      assertEquals(1, builds.get(), "the reload did not build new early-loaded properties");
      assertNull(failure.get());
      assertSame(installed, seen.get(),
                 "the early-loaded properties were not published while the reload built them");
      assertEquals("old", read.get(),
                   "a reader saw early-loaded properties without the stored values");
      assertNotSame(installed, EarlyLoadedProperties.getInstance());
      assertEquals("new", engine.getProperty(name, true));
   }

   @Test
   void initLoggingUsesThePropertiesItBuilt() throws Exception {
      // the first init() of an engine publishes the properties, releases the lock and only then
      // initializes logging. A reload that starts at that moment must not make it read null.
      CountDownLatch inInitialize = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      AtomicBoolean first = new AtomicBoolean(true);
      LogManager logManager = mock(LogManager.class);
      doAnswer(inv -> {
         if(first.compareAndSet(true, false)) {
            inInitialize.countDown();
            release.await(10L, TimeUnit.SECONDS);
         }

         return null;
      }).when(logManager).initialize(any(), any(), anyBoolean(), anyLong(), anyInt(), anyBoolean());

      HookStorage otherStorage = newStorage();
      PropertiesEngine other = newEngine(otherStorage, event -> { }, provider(logManager));
      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread refresh = new Thread(() -> {
         try {
            other.init();
         }
         catch(Throwable e) {
            failure.set(e);
         }
      });

      refresh.start();
      assertTrue(inInitialize.await(10L, TimeUnit.SECONDS));

      // runs inside the reload, which holds the properties lock
      otherStorage.streamHook = () -> {
         release.countDown();
         join(refresh, 5000L);
      };
      other.init(true);
      refresh.join(10000L);

      assertFalse(refresh.isAlive());
      assertNull(failure.get(), () -> "init() failed during a concurrent reload: " + failure.get());
   }

   @Test
   void saveDuringReloadSnapshotsTheReloadedProperties() throws Exception {
      String name = prefix + "key";
      initEngine();
      engine.setProperty(name, "edit");

      Object changedProps = field(PropertiesEngine.class, "changedProps").get(engine);
      ReentrantLock lock = (ReentrantLock) field(PropertiesEngine.class, "propertiesLock").get(engine);
      CountDownLatch inReload = new CountDownLatch(1);
      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread saver = new Thread(() -> {
         try {
            engine.save();
         }
         catch(Throwable e) {
            failure.set(e);
         }
      });
      Thread reloader = new Thread(() -> engine.init(true));

      // runs inside the reload, which holds the properties lock
      storage.streamHook = () -> {
         inReload.countDown();
         join(saver, 5000L);
      };

      // hold the saver just before it takes its snapshot, and start a reload meanwhile
      synchronized(changedProps) {
         saver.start();
         waitFor(() -> saver.getState() == Thread.State.BLOCKED);
         reloader.start();
         // the reload either runs, or waits for the saver to finish its snapshot
         waitFor(() -> inReload.getCount() == 0 || lock.hasQueuedThread(reloader));
      }

      saver.join(10000L);
      reloader.join(10000L);

      assertNull(failure.get(), () -> "save() failed during a concurrent reload: " + failure.get());
      assertEquals("edit", storage.get(name), "the save lost the pending change");
      assertEquals("edit", engine.getProperty(name));
   }

   @Test
   void writeDuringReloadIsKept() throws Exception {
      // a writer that arrives while the reload re-applies the pending changes must change the
      // reloaded properties, not the replaced ones (it was lost with the previous properties
      // kept published and writers not waiting for the reload)
      String pending = prefix + "pending";
      String name = prefix + "late";
      storage.remotePut(pending, "stored", false);
      initEngine();
      engine.setProperty(pending, "edit");

      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread writer = new Thread(() -> {
         try {
            engine.setProperty(name, "value");
         }
         catch(Throwable e) {
            failure.set(e);
         }
      });

      // the reload reads the stored value of the pending property, after its pending snapshot
      storage.runDuringNextGet(pending, () -> {
         writer.start();
         join(writer, 500L);
      });
      engine.init(true);
      writer.join(10000L);

      assertNull(failure.get());
      assertEquals("value", engine.getProperty(name), "a write during the reload was lost");
      assertEquals("edit", engine.getProperty(pending));
      engine.save();
      assertEquals("value", storage.get(name), "a write during the reload was not saved");
      assertEquals("edit", storage.get(pending));
   }

   @Test
   void writeDuringStorageLoadIsKept() throws Exception {
      String name = prefix + "key";
      initEngine();
      AtomicReference<Throwable> failure = new AtomicReference<>();
      Thread writer = new Thread(() -> {
         try {
            engine.setProperty(name, "value");
         }
         catch(Throwable e) {
            failure.set(e);
         }
      });

      storage.streamHook = () -> {
         writer.start();
         join(writer, 500L);
      };
      engine.init(true);
      writer.join(10000L);

      assertNull(failure.get());
      assertEquals("value", engine.getProperty(name));
      engine.save();
      assertEquals("value", storage.get(name));
   }

   @Test
   void changeTaskReloadsTheEngineThatReceivedTheChange() throws Exception {
      String marker = prefix + "marker";
      initEngine();
      // only in memory, so a reload of this engine drops it
      engine.getProperties().put(marker, "memory");

      CountDownLatch reloaded = new CountDownLatch(1);
      HookStorage otherStorage = newStorage();
      PropertiesEngine other = newEngine(otherStorage, event -> {
         if(event instanceof ApplicationPropertiesChangedEvent) {
            reloaded.countDown();
         }
      }, context.getBeanProvider(LogManager.class));
      other.init();
      otherStorage.setAsyncLocalEvents(true);

      other.setProperty(prefix + "other", "value");
      other.save();

      assertTrue(reloaded.await(10L, TimeUnit.SECONDS), "the change task did not run");
      assertEquals("memory", engine.getProperties().get(marker),
                   "the change task of another engine reloaded this engine");
      assertEquals("value", other.getProperty(prefix + "other"));
   }

   @Test
   void shutDownEngineNeverReloads() throws Exception {
      String marker = prefix + "marker";
      initEngine();
      engine.getProperties().put(marker, "memory");

      AtomicInteger reloads = new AtomicInteger();
      HookStorage otherStorage = newStorage();
      PropertiesEngine other = newEngine(otherStorage, event -> {
         if(event instanceof ApplicationPropertiesChangedEvent) {
            reloads.incrementAndGet();
         }
      }, context.getBeanProvider(LogManager.class));
      other.init();
      otherStorage.setAsyncLocalEvents(true);

      // registered after the engine's listener, so the change task is scheduled when it fires
      CountDownLatch changed = new CountDownLatch(1);
      otherStorage.addListener(new KeyValueStorage.Listener<>() {
         @Override
         public void entryAdded(KeyValueStorage.Event<String> event) {
            changed.countDown();
         }

         @Override
         public void entryUpdated(KeyValueStorage.Event<String> event) {
            changed.countDown();
         }

         @Override
         public void entryRemoved(KeyValueStorage.Event<String> event) {
            changed.countDown();
         }
      });

      other.setProperty(prefix + "other", "value");
      other.save();
      assertTrue(changed.await(10L, TimeUnit.SECONDS));

      // e.g. its Spring context is closed (@PreDestroy) while the change task is pending
      other.shutdown();
      ScheduledExecutorService executor = (ScheduledExecutorService)
         field(DefaultDebouncer.class, "executor").get(field(PropertiesEngine.class, "debouncer").get(other));
      // whatever was still scheduled has run, or was discarded, once the executor terminated
      assertTrue(executor.awaitTermination(10L, TimeUnit.SECONDS));

      assertEquals(0, reloads.get(), "a shut-down engine ran its change task");
      assertEquals("memory", engine.getProperties().get(marker),
                   "the change task of a shut-down engine reloaded the current engine");
      assertFalse(otherStorage.hasListener(changeListener(other)),
                  "a shut-down engine still listens to the storage");
   }

   /**
    * Loads the fake storage and saves whatever was left pending by the test harness, so each
    * test starts with no pending properties.
    */
   private void initEngine() throws Exception {
      engine.init();
      engine.save();
   }

   private HookStorage newStorage() {
      HookStorage result = new HookStorage();
      otherStorages.add(result);
      return result;
   }

   private PropertiesEngine newEngine(KeyValueStorage<String> storage,
                                      ApplicationEventPublisher publisher,
                                      ObjectProvider<LogManager> logManagerProvider)
      throws Exception
   {
      PropertiesEngine result = new PropertiesEngine(
         context.getBean(KeyValueStorageManager.class), context.getBean(FileSystemService.class),
         publisher, logManagerProvider);
      setStorage(result, storage);
      others.add(result);
      return result;
   }

   private static ObjectProvider<LogManager> provider(LogManager logManager) {
      return new ObjectProvider<>() {
         @Override
         public LogManager getObject(Object... args) {
            return logManager;
         }

         @Override
         public LogManager getIfAvailable() {
            return logManager;
         }

         @Override
         public LogManager getIfUnique() {
            return logManager;
         }

         @Override
         public LogManager getObject() {
            return logManager;
         }
      };
   }

   private static void join(Thread thread, long millis) {
      try {
         thread.join(millis);
      }
      catch(InterruptedException e) {
         throw new RuntimeException(e);
      }
   }

   private static void waitFor(BooleanSupplier condition) throws InterruptedException {
      long end = System.currentTimeMillis() + 10000L;

      while(!condition.getAsBoolean()) {
         if(System.currentTimeMillis() > end) {
            fail("Timed out waiting for the interleaving");
         }

         Thread.sleep(5L);
      }
   }

   @SuppressWarnings("unchecked")
   private static KeyValueStorage.Listener<String> changeListener(PropertiesEngine engine)
      throws Exception
   {
      return (KeyValueStorage.Listener<String>)
         field(PropertiesEngine.class, "changeListener").get(engine);
   }

   @SuppressWarnings("unchecked")
   private static KeyValueStorage<String> getStorage(PropertiesEngine engine) throws Exception {
      return (KeyValueStorage<String>) field(PropertiesEngine.class, "kvStorage").get(engine);
   }

   private static void setStorage(PropertiesEngine engine, KeyValueStorage<String> value)
      throws Exception
   {
      field(PropertiesEngine.class, "kvStorage").set(engine, value);
   }

   private static Field field(Class<?> type, String name) throws Exception {
      Field field = type.getDeclaredField(name);
      field.setAccessible(true);
      return field;
   }

   /**
    * Runs an action once, the next time the storage contents are streamed, i.e. inside
    * {@code init()} while it holds the properties lock.
    */
   private static final class HookStorage extends InMemoryKeyValueStorage<String> {
      @Override
      public Stream<KeyValuePair<String>> stream() {
         Runnable action = streamHook;
         streamHook = null;

         if(action != null) {
            action.run();
         }

         return super.stream();
      }

      private volatile Runnable streamHook;
   }

   @Autowired
   private ConfigurableApplicationContext context;
   private final List<PropertiesEngine> others = new ArrayList<>();
   private final List<HookStorage> otherStorages = new ArrayList<>();
   private PropertiesEngine engine;
   private KeyValueStorage<String> originalStorage;
   private HookStorage storage;
   private String prefix;
}
