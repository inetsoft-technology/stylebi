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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77177, fix round 3: review round 3 found that {@code getStorage()}'s fetch call itself
 * ({@code keyValueStorageManager.getStorage(STORAGE_ID)}, constructing a brand-new
 * {@code LocalKeyValueStorage} whose first, unconditional load attempt can throw the same kind of
 * rethrown {@code RuntimeException} round 2 already guarded {@code retryLoad()} against) was still
 * unguarded -- one line above round 2's fix.
 *
 * <p>{@code PropertiesEngineFailedInitialLoadTest} cannot exercise this: every test there primes
 * {@link KeyValueStorageManager}'s cache with a pre-built double under {@code STORAGE_ID} before
 * calling {@code getStorage()}, so Caffeine's {@code Cache.get(key, mappingFunction)} always finds
 * the key already present and never invokes the mapping function -- i.e. the construction path
 * itself (where this round's finding lives) is never reached by that file's injection technique,
 * regardless of which double is injected (confirmed by review round 3's own trace of
 * {@code KeyValueStorageManager.get()}/Caffeine's {@code get(id, loader)} contract).</p>
 *
 * <p>Driving a genuine construction failure through the real {@code LocalKeyValueStorage}/
 * {@code MockCluster}/{@code LoadKeyValueTask} stack was investigated and, like round 1's
 * equivalent investigation (documented in this file's sibling), found impractical for a
 * deterministic, fast unit test: {@code LoadKeyValueTask.run()} only lets a {@code RuntimeException}
 * escape via its {@code validate(Map)} extension point, but {@code PropertiesEngine} always calls
 * {@link KeyValueStorageManager#getStorage(String)} (the single-argument overload, which always
 * builds the <em>default</em> {@code LoadKeyValueTask} internally) for {@code STORAGE_ID} -- there is
 * no way for a test to substitute a custom, throwing {@code LoadKeyValueTask} for that specific call
 * without either forking {@code PropertiesEngine} itself or reaching deep into
 * {@code KeyValueStorage.newInstance()}'s internals, neither of which is a reasonable trade for a
 * single failure-path test.</p>
 *
 * <p>Instead, this test verifies the same behavior one architectural layer up, at the exact
 * boundary {@code PropertiesEngine.getStorage()} actually depends on: it replaces
 * {@code PropertiesEngine}'s own {@code keyValueStorageManager} field with a Mockito spy of the
 * real, Spring-managed singleton (so every other store id and every other component sharing that
 * singleton keeps working normally) and stubs only its {@code getStorage(STORAGE_ID)} call to
 * throw. This exercises the real production code inside {@code PropertiesEngine.getStorage()} that
 * this round's fix changed -- what matters to that code is only "did this call throw", not which
 * internal mechanism produced the exception, and round 2's own reasoning (`09-review-r2.md`
 * section 3.1) already establishes, by direct source reading, that {@code LocalKeyValueStorage}'s
 * construction really can throw a bare {@code RuntimeException} this way in production.</p>
 *
 * <p>Round 4 added {@link #initReloadKeepsThePreviousPropertiesWhenTheFallbackInstanceIsClosed()}:
 * review round 4 found that round 3's own fallback (returning the previous, closed instance
 * instead of throwing) reintroduced Bug #77177's original mechanism for {@code init()}'s reload
 * path specifically, since that is the one caller that enumerates the storage rather than just
 * reading/writing individual keys.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PropertiesEngineStorageFetchFailureTest {
   @BeforeEach
   void setUp() throws Exception {
      engine = PropertiesEngine.getInstance();
      engine.init();
      originalManager = getManagerField();
   }

   @AfterEach
   void tearDown() throws Exception {
      // restore the real manager and force a real re-fetch, so later tests (in this class or,
      // without @DirtiesContext, other classes) see a normal, fully loaded storage instance
      setManagerField(originalManager);
      setKvStorage(null);
      engine.clear();
      engine.init();
   }

   /**
    * The core of review round 3's finding: when the previous instance was evicted/closed and the
    * replacement fetch itself throws, a runtime caller must not see the exception -- only a
    * one-time WARN -- and the previous (stale/closed) instance must still be served rather than
    * the field being left in some broken state.
    */
   @Test
   void getStorageFallsBackToThePreviousInstanceWhenTheFetchThrows() throws Exception {
      // stands in for "the previous instance was evicted and closed", forcing getStorage()'s
      // next call to take the fetch branch
      ClosedStorage previous = new ClosedStorage();
      setKvStorage(previous);

      KeyValueStorageManager spyManager = spy(originalManager);
      doThrow(new RuntimeException("simulated genuinely broken storage engine"))
         .when(spyManager).getStorage(STORAGE_ID);
      setManagerField(spyManager);

      Logger logger = (Logger) LoggerFactory.getLogger(PropertiesEngine.class);
      ListAppender<ILoggingEvent> appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);

      String value;

      try {
         // getPropertyFromStorage() goes straight through getStorage(); a runtime caller reaching
         // this because the replacement fetch failed must not see an exception
         value = assertDoesNotThrow(
            () -> engine.getPropertyFromStorage("test77177.fetchThrows." + System.nanoTime()),
            "a RuntimeException from the replacement fetch must not escape getStorage() at " +
            "runtime when a previous instance exists to fall back on");
      }
      finally {
         logger.detachAppender(appender);
      }

      assertNull(value, "the closed previous instance has no properties to return");
      assertTrue(
         appender.list.stream().anyMatch(
            e -> e.getLevel() == Level.WARN &&
               e.getFormattedMessage().contains("Failed to fetch a replacement")),
         "expected a WARN log for the caught fetch exception: " + appender.list);
      assertSame(previous, getKvStorage(),
                 "the previous, stale instance must still be served when the replacement " +
                 "fetch fails, not discarded or replaced with null");

      // a later call must attempt the fetch again -- there is no backoff/cooldown, matching
      // KeyValueStorageManager's own systemic lack of failure caching for every other caller
      verify(spyManager, times(1)).getStorage(STORAGE_ID);
      engine.getPropertyFromStorage("test77177.fetchThrows2." + System.nanoTime());
      verify(spyManager, times(2)).getStorage(STORAGE_ID);
   }

   /**
    * Unlike a runtime self-heal, true cold start ({@code initEngine()}'s very first call) has no
    * previous instance to fall back on, so the fetch failure must still propagate -- but as the
    * raw exception the manager threw, not wrapped in {@code getStorage()}'s own logic, matching
    * exactly what would already have happened before {@code getStorage()} existed.
    */
   @Test
   void initEngineStillFailsWhenTheFetchThrowsAndThereIsNoPreviousInstance() throws Exception {
      setKvStorage(null);

      KeyValueStorageManager spyManager = spy(originalManager);
      RuntimeException failure =
         new RuntimeException("simulated genuinely broken storage engine");
      doThrow(failure).when(spyManager).getStorage(STORAGE_ID);
      setManagerField(spyManager);

      RuntimeException thrown = assertThrows(RuntimeException.class, engine::initEngine,
         "cold start must still fail when there is no previous instance to fall back on " +
         "(Bug #76975's fail-fast intent)");
      assertSame(failure, thrown,
                 "the raw fetch exception should propagate unwrapped -- getStorage() has " +
                 "nothing to fall back on and nothing to wrap it with at true cold start");
   }

   /**
    * Review round 4's finding: {@code getStorage()}'s round-3 fallback (return the previous,
    * still-closed instance rather than throwing) is safe for a point read/write, but {@code init()}
    * also calls {@code getStorage()} and then unconditionally enumerated it via
    * {@code loadFromStorage()} -- which calls {@code stream()}/{@code keys()}, gated only by
    * {@code isClosed()}, not {@code isLoaded()}. Proceeding with a closed fallback instance there
    * silently wipes {@code internalProperties}, reopening the exact "silently wiped properties"
    * mechanism this whole bug exists to fix, just reached through a new trigger (a failed
    * self-heal fetch during a reload) instead of the original one (a bare eviction during a
    * reload). This test mirrors the reviewer's own falsifying repro: save a property through the
    * real storage, then force a reload (matching what {@code ChangeTask.run()} does on every
    * debounced property change) while {@code kvStorage} is closed and its replacement fetch fails
    * -- the property must survive, via {@code init()}'s own existing Bug #76979 "keep the previous
    * properties on a failed reload" guard.
    */
   @Test
   void initReloadKeepsThePreviousPropertiesWhenTheFallbackInstanceIsClosed() throws Exception {
      String key = "test77177.reloadSurvives." + System.nanoTime();

      try {
         // written through the real, still-working storage before anything is swapped in
         engine.setProperty(key, "should-survive-a-degraded-reload");
         engine.save();
         assertEquals("should-survive-a-degraded-reload", engine.getProperty(key),
                      "sanity check: the property must be set before the reload is forced");

         // simulates an eviction having closed the previously-held instance
         ClosedStorage previous = new ClosedStorage();
         setKvStorage(previous);

         KeyValueStorageManager spyManager = spy(originalManager);
         doThrow(new RuntimeException("simulated genuinely broken storage engine"))
            .when(spyManager).getStorage(STORAGE_ID);
         setManagerField(spyManager);

         // exactly what ChangeTask.run() does on a debounced property-change reload
         assertDoesNotThrow(() -> engine.init(true),
                             "a reload with no usable storage must not throw out past init(), " +
                             "it must be absorbed by init()'s own failed-reload handling");

         assertEquals("should-survive-a-degraded-reload", engine.getProperty(key),
                      "a reload that could not refresh a closed storage instance wiped a " +
                      "previously-saved property instead of keeping it (Bug #76979)");
      }
      finally {
         // clean up the real, persisted key regardless of how the test went, using the real
         // manager/storage restored below (tearDown() would otherwise leave it behind)
         setManagerField(originalManager);
         setKvStorage(null);
         engine.clear();
         engine.init();
         engine.remove(key);
         engine.save();
      }
   }

   @SuppressWarnings("unchecked")
   private KeyValueStorageManager getManagerField() throws Exception {
      return (KeyValueStorageManager) managerField().get(engine);
   }

   private void setManagerField(KeyValueStorageManager manager) throws Exception {
      managerField().set(engine, manager);
   }

   private static Field managerField() throws Exception {
      Field field = PropertiesEngine.class.getDeclaredField("keyValueStorageManager");
      field.setAccessible(true);
      return field;
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
    * A {@link KeyValueStorage} double standing in for a previous instance that was evicted and
    * closed -- only {@code isClosed()} matters here, to force {@code getStorage()}'s next call
    * down the fetch branch.
    */
   private static final class ClosedStorage extends InMemoryKeyValueStorage<String> {
      @Override
      public boolean isClosed() {
         return true;
      }
   }

   private PropertiesEngine engine;
   private KeyValueStorageManager originalManager;
   private static final String STORAGE_ID = "sreeProperties";
}
