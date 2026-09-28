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
package inetsoft.report.composition.execution;

import inetsoft.test.*;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.LockRestoreException;
import inetsoft.util.UpgradableReadWriteLock;
import inetsoft.util.script.JavaScriptEngine;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77153: a nested restoreLocks() that times out while a thread refreshes the viewsheet
 * under the sandbox write lock (CoreLifecycleService.refreshViewsheet() ->
 * updateAssembly() -> refreshMetaData() -> getData() -> doExecuteData()) left the refresh
 * running without the lock, because refreshMetaData() swallowed the error (silently while
 * refreshing). The refresh must fail visibly, and the owner's cleanup must still run.
 *
 * <p>The real refreshMetaData() and the real bounded lock (500ms) are used. The nested frames
 * of getData()/doExecuteData() are replayed in the assembly's update(), which refreshMetaData()
 * calls inside its try.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetSandboxLockLostTest {
   ViewsheetSandbox box;
   ExecutorService pool;

   @BeforeEach
   void setUp() throws Exception {
      box = new ViewsheetSandbox(new Viewsheet(), AbstractSheet.SHEET_RUNTIME_MODE, null, false,
                                 null);
      Constructor<UpgradableReadWriteLock> c = UpgradableReadWriteLock.class
         .getDeclaredConstructor(long.class, BooleanSupplier.class);
      c.setAccessible(true);
      BooleanSupplier nb = JavaScriptEngine::isScriptThread;
      Field f = ViewsheetSandbox.class.getDeclaredField("thisLock");
      f.setAccessible(true);
      f.set(box, c.newInstance(500L, nb));
      pool = Executors.newFixedThreadPool(2, r -> {
         Thread t = new Thread(r);
         t.setDaemon(true);
         return t;
      });
   }

   @AfterEach
   void tearDown() {
      pool.shutdownNow();
   }

   /** refreshMetaData() rethrows the restore failure, refreshing or not. */
   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void refreshFailsVisiblyAndCleanupRuns(boolean refreshing) throws Exception {
      Result r = refresh(refreshing, false);
      assertInstanceOf(LockRestoreException.class, r.refreshError,
                       "refreshMetaData() swallowed the lost lock");
      assertTrue(r.cleanupRan, "the owner's finally cleanup (removeExecution) did not run");
      assertNull(r.ownerCheckError, "the owner check runs only when nothing else is thrown");
      assertLockUsableAfterwards();
   }

   /**
    * A generic catch below refreshMetaData() (any unaudited catch(Exception)) absorbs the
    * failure: the thread still fails fast on the next sandbox lock, and the owner's check
    * after its unlock and cleanup reports the loss.
    */
   @Test
   void swallowedFailureIsReportedByOwner() throws Exception {
      Result r = refresh(true, true);
      assertNull(r.refreshError);
      assertInstanceOf(LockRestoreException.class, r.nextLockError,
                       "the next nested lock call did not fail fast");
      assertTrue(r.cleanupRan);
      assertInstanceOf(LockRestoreException.class, r.ownerCheckError,
                       "the owner did not detect the lost lock");
      assertLockUsableAfterwards();
   }

   private void assertLockUsableAfterwards() throws Exception {
      box.lockWrite();
      box.unlockWrite();
      assertTrue(pool.submit(() -> {
         box.lockWrite();
         box.unlockWrite();
         return true;
      }).get(3, TimeUnit.SECONDS));
   }

   record Result(Throwable refreshError, Throwable nextLockError, boolean cleanupRan,
                 Throwable ownerCheckError) {}

   private Result refresh(boolean refreshing, boolean swallowInUpdate) throws Exception {
      CountDownLatch released = new CountDownLatch(1);
      CountDownLatch writerHas = new CountDownLatch(1);
      CountDownLatch restoreFailed = new CountDownLatch(1);
      Throwable[] seen = new Throwable[1];

      // another thread takes WRITE in the unlockAll() window and holds it past the bound
      Future<?> writer = pool.submit(() -> {
         assertTrue(released.await(10, TimeUnit.SECONDS));
         box.lockWrite();

         try {
            writerHas.countDown();
            restoreFailed.await(10, TimeUnit.SECONDS);
         }
         finally {
            box.unlockWrite();
         }

         return null;
      });

      VSAssembly assembly = Mockito.mock(VSAssembly.class);
      Mockito.doAnswer(inv -> {
         try {
            box.lockRead();                    // getData()

            try {
               box.lockWrite();                // doExecuteData()
               box.unlockWrite();
               box.unlockAll();

               try {
                  released.countDown();
                  assertTrue(writerHas.await(10, TimeUnit.SECONDS)); // query.getData()
               }
               finally {
                  try {
                     box.restoreLocks();
                  }
                  catch(Throwable t) {
                     seen[0] = t;
                     restoreFailed.countDown();
                     throw t;
                  }
               }
            }
            finally {
               box.unlockRead();
            }
         }
         catch(RuntimeException ex) {
            if(!swallowInUpdate) {
               throw ex;
            }
         }

         return null;
      }).when(assembly).update(Mockito.any());

      Method refreshMetaData =
         ViewsheetSandbox.class.getDeclaredMethod("refreshMetaData", VSAssembly.class);
      refreshMetaData.setAccessible(true);

      // the owner, shaped like CoreLifecycleService.refreshViewsheet()
      Throwable refreshError = null;
      Throwable nextLockError = null;
      Throwable ownerCheckError = null;
      boolean[] cleanupRan = new boolean[1];
      long lostLocks = box.getLockLostCount();
      box.lockWrite();

      try {
         try {
            box.setRefreshing(refreshing);
            refreshMetaData.invoke(box, assembly);

            // the next assembly in the refresh loop
            try {
               box.lockRead();
               box.unlockRead();
            }
            catch(LockRestoreException ex) {
               nextLockError = ex;
            }
         }
         catch(InvocationTargetException ex) {
            refreshError = ex.getCause();
         }
         finally {
            box.setRefreshing(false);
         }
      }
      finally {
         restoreFailed.countDown();
         box.unlockWrite();
         cleanupRan[0] = true; // viewsheetService.removeExecution(id)

         if(refreshError == null) {
            try {
               box.checkLockNotLostSince(lostLocks);
            }
            catch(LockRestoreException ex) {
               ownerCheckError = ex;
            }
         }
      }

      writer.get(10, TimeUnit.SECONDS);
      assertInstanceOf(LockRestoreException.class, seen[0], "the restore did not fail");
      return new Result(refreshError, nextLockError, cleanupRan[0], ownerCheckError);
   }
}
