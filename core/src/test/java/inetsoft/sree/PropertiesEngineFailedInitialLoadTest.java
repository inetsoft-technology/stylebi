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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.benmanes.caffeine.cache.Cache;
import inetsoft.storage.InMemoryKeyValueStorage;
import inetsoft.storage.KeyValueStorage;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77177, fix round 1: @fredaan's external review pointed out that {@code getStorage()}'s
 * self-heal branch only checked {@code isClosed()}, never {@code isLoaded()} -- a freshly
 * (re-)fetched {@link inetsoft.storage.LocalKeyValueStorage} whose constructor's initial
 * {@code load()} call fails/times out is not closed, so it would have been treated as fine
 * forever, even though {@code stream()}/{@code keys()}/{@code get()} are gated only by
 * {@code isClosed()}, never by {@code isLoaded()} (the #76975/#76979 "silently wiped" shape).
 *
 * <p>Neither {@code PropertiesEngineStorageEvictionTest} (real Caffeine eviction) nor
 * {@code PropertiesEngineClusterSyncTest}/{@code PropertiesEngineLogLevelResetTest} (reflective
 * {@code kvStorage} field swap to an already-loaded {@code InMemoryKeyValueStorage}) exercises a
 * storage instance whose initial load never completed. Driving that scenario against the real
 * {@code LocalKeyValueStorage}/{@code MockCluster} stack turns out to be impractical: the default
 * {@link inetsoft.storage.LoadKeyValueTask#run()} swallows every checked exception internally
 * (only an uncaught {@code RuntimeException} escapes, and {@code LocalKeyValueStorage.load()}
 * treats that case by *rethrowing*, not by returning {@code false}), and the only paths that make
 * {@code load()} return {@code false} without throwing are a genuine 3-minute
 * {@code cluster.submit(...).get()} timeout or a thread interruption mid-wait -- neither of which
 * can be triggered deterministically and quickly in a unit test.
 *
 * <p>Instead, this test injects a hand-written {@link KeyValueStorage} double directly into
 * {@link KeyValueStorageManager}'s backing Caffeine cache (via reflection on its private
 * {@code storages} field), standing in for "a freshly fetched instance whose initial load did not
 * complete". This is a narrower boundary than the real cluster stack, but it exercises the exact
 * production code path: {@code PropertiesEngine.getStorage()} calls
 * {@code keyValueStorageManager.getStorage(STORAGE_ID)}, which returns whatever the manager's cache
 * holds for that id, exactly as it would for a real freshly-constructed
 * {@code LocalKeyValueStorage}.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PropertiesEngineFailedInitialLoadTest {
   @BeforeEach
   void setUp() throws Exception {
      engine = PropertiesEngine.getInstance();
      engine.init();
      manager = KeyValueStorageManager.getInstance();
   }

   @AfterEach
   void tearDown() throws Exception {
      // remove the injected double from the manager's cache and the engine's field, then force a
      // real re-fetch so later tests in this class (or, without @DirtiesContext, other classes)
      // see a normal, fully loaded storage instance again
      storagesCache().asMap().remove(STORAGE_ID);
      setKvStorage(null);
      engine.clear();
      engine.init();
   }

   /**
    * The core of @fredaan's finding: a freshly-fetched instance whose initial load failed must
    * not be treated as permanently fine, but a runtime caller must not see an exception either --
    * only a one-time WARN, per the fix's documented "warn, don't throw at runtime" design.
    */
   @Test
   void getStorageRetriesOnceAndWarnsButDoesNotThrowWhenLoadStaysFailed() throws Exception {
      FailingStorage storage = new FailingStorage(false);
      injectFreshlyFetchedStorage(storage);

      Logger logger = (Logger) LoggerFactory.getLogger(PropertiesEngine.class);
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);

      String value;

      try {
         // getPropertyFromStorage() goes straight through getStorage(); a runtime caller reaching
         // this because the load stayed unloaded must not see an exception
         value = assertDoesNotThrow(
            () -> engine.getPropertyFromStorage("test77177.retry." + System.nanoTime()));
      }
      finally {
         logger.detachAppender(appender);
      }

      assertNull(value, "the empty double has no properties to return");
      assertEquals(1, storage.retryCount(),
                   "getStorage() must retry the failed load exactly once for a freshly-fetched " +
                   "instance");
      assertTrue(
         appender.list.stream().anyMatch(
            e -> e.getLevel() == Level.WARN &&
               e.getFormattedMessage().contains("has not finished loading after a retry")),
         "expected a WARN log when the retry still leaves the storage unloaded: " + appender.list);
      assertSame(storage, getKvStorage(),
                 "the still-unloaded instance must still be adopted, not discarded");

      // a second call while the field still holds this same, still-unloaded-but-not-closed
      // instance must NOT retry again -- the fix scopes the retry to the fetch branch only
      engine.getPropertyFromStorage("test77177.retry2." + System.nanoTime());
      assertEquals(1, storage.retryCount(),
                   "getStorage() retried on a call after the first, but the fix's whole point is " +
                   "that a persistently-failing load must not turn every access into a repeated " +
                   "blocking retry");
   }

   /**
    * When the one retry succeeds, no warning should be logged and the instance is adopted as
    * normal -- the WARN is specifically for a retry that is still unsuccessful.
    */
   @Test
   void getStorageDoesNotWarnWhenTheRetrySucceeds() throws Exception {
      FailingStorage storage = new FailingStorage(true);
      injectFreshlyFetchedStorage(storage);

      Logger logger = (Logger) LoggerFactory.getLogger(PropertiesEngine.class);
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);

      try {
         engine.getPropertyFromStorage("test77177.recover." + System.nanoTime());
      }
      finally {
         logger.detachAppender(appender);
      }

      assertEquals(1, storage.retryCount());
      assertTrue(storage.isLoaded());
      assertTrue(
         appender.list.stream().noneMatch(e -> e.getLevel() == Level.WARN),
         "no WARN is expected once the retry succeeds: " + appender.list);
   }

   /**
    * Unlike a runtime call, cold start ({@code initEngine()}, the {@code @PostConstruct} method)
    * must still fail fast rather than start the node with an unloaded property store -- and it
    * must do so by reading the result of {@code getStorage()}'s own retry, not by retrying a
    * second time itself (the "no double retry" claim in 07-fix-r1.md).
    */
   @Test
   void initEngineStillFailsClosedOnAnUnrecoveredLoadWithoutRetryingTwice() throws Exception {
      FailingStorage storage = new FailingStorage(false);
      injectFreshlyFetchedStorage(storage);

      assertThrows(IllegalStateException.class, engine::initEngine,
                   "cold start must still fail closed when the load never completes (Bug #76975)");
      assertEquals(1, storage.retryCount(),
                   "initEngine() must not call retryLoad() a second time; getStorage() already " +
                   "retried once for this freshly-fetched instance");
   }

   /**
    * Puts a freshly-constructed double where {@code getStorage()}'s fetch branch will find it:
    * primes {@link KeyValueStorageManager}'s cache for {@code STORAGE_ID} with the double (so the
    * manager returns it instead of constructing a real {@code LocalKeyValueStorage}), and clears
    * the engine's own {@code kvStorage} field so the next {@code getStorage()} call takes the
    * fetch-and-retry branch instead of returning whatever it already held.
    */
   private void injectFreshlyFetchedStorage(KeyValueStorage<String> storage) throws Exception {
      storagesCache().put(STORAGE_ID, storage);
      setKvStorage(null);
   }

   @SuppressWarnings("unchecked")
   private Cache<String, KeyValueStorage<?>> storagesCache() throws Exception {
      Field field = KeyValueStorageManager.class.getDeclaredField("storages");
      field.setAccessible(true);
      return (Cache<String, KeyValueStorage<?>>) field.get(manager);
   }

   @SuppressWarnings("unchecked")
   private KeyValueStorage<String> getKvStorage() throws Exception {
      return (KeyValueStorage<String>) kvStorageField().get(engine);
   }

   private void setKvStorage(KeyValueStorage<String> storage) throws Exception {
      kvStorageField().set(engine, storage);
   }

   private static Field kvStorageField() throws Exception {
      Field field = PropertiesEngine.class.getDeclaredField("kvStorage");
      field.setAccessible(true);
      return field;
   }

   /**
    * A {@link KeyValueStorage} double standing in for a freshly-constructed
    * {@code LocalKeyValueStorage} whose initial load has not completed: {@code isClosed()} stays
    * {@code false} (inherited from {@link InMemoryKeyValueStorage}) but {@code isLoaded()} starts
    * {@code false}, exactly the combination @fredaan's review identified as unguarded.
    */
   private static final class FailingStorage extends InMemoryKeyValueStorage<String> {
      FailingStorage(boolean recoversOnRetry) {
         this.recoversOnRetry = recoversOnRetry;
      }

      @Override
      public boolean isLoaded() {
         return loaded;
      }

      @Override
      public boolean retryLoad() {
         retryCount.incrementAndGet();

         if(recoversOnRetry) {
            loaded = true;
         }

         return loaded;
      }

      int retryCount() {
         return retryCount.get();
      }

      private final boolean recoversOnRetry;
      private volatile boolean loaded;
      private final AtomicInteger retryCount = new AtomicInteger();
   }

   private PropertiesEngine engine;
   private KeyValueStorageManager manager;
   private static final String STORAGE_ID = "sreeProperties";
}
