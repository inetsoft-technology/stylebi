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
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.LockRestoreException;
import inetsoft.util.UpgradableReadWriteLock;
import inetsoft.util.script.JavaScriptEngine;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77153: when the bounded restoreLocks() in doExecuteData() fails on a thread that is
 * not running a script (e.g. the refresh thread holding the sandbox write lock), the real
 * getData() must not cache a NULL result for the assembly. Before the fix the skipped-lock
 * guard in getData()'s finally ran only when inExec, so a lock failure on a normal thread
 * poisoned the data map with NULL.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ViewsheetSandboxLockLostCacheTest {
   @Test
   void getDataDoesNotCacheAfterLostLock() throws Exception {
      Viewsheet vs = new Viewsheet();
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      vs.addAssembly(text);
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                        AssetEntry.Type.VIEWSHEET, "LockLost77153", null);
      ViewsheetSandbox box = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null,
                                                  false, entry);
      Constructor<UpgradableReadWriteLock> c = UpgradableReadWriteLock.class
         .getDeclaredConstructor(long.class, BooleanSupplier.class);
      c.setAccessible(true);
      BooleanSupplier nb = JavaScriptEngine::isScriptThread;
      Field f = ViewsheetSandbox.class.getDeclaredField("thisLock");
      f.setAccessible(true);
      f.set(box, c.newInstance(300L, nb));

      ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
         Thread t = new Thread(r);
         t.setDaemon(true);
         return t;
      });
      CountDownLatch writerHas = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(1);

      VSAQuery query = Mockito.mock(VSAQuery.class);
      // query.getData() runs after doExecuteData()'s unlockAll(): another thread takes
      // WRITE and holds it past the bound, so the restore fails
      Mockito.when(query.getData()).thenAnswer(inv -> {
         pool.submit(() -> {
            box.lockWrite();

            try {
               writerHas.countDown();
               done.await(10, TimeUnit.SECONDS);
            }
            finally {
               box.unlockWrite();
            }

            return null;
         });
         assertTrue(writerHas.await(10, TimeUnit.SECONDS));
         return null;
      });

      Field dmapField = ViewsheetSandbox.class.getDeclaredField("dmap");
      dmapField.setAccessible(true);
      DataMap dmap = (DataMap) dmapField.get(box);

      try(MockedStatic<VSAQuery> st =
             Mockito.mockStatic(VSAQuery.class, Mockito.CALLS_REAL_METHODS))
      {
         st.when(() -> VSAQuery.createVSAQuery(Mockito.any(), Mockito.any(),
                                               Mockito.anyInt())).thenReturn(query);
         long lost0 = box.getLockLostCount();
         box.lockWrite(); // outer owner, e.g. refreshViewsheet()

         try {
            assertThrows(LockRestoreException.class, () -> box.getData("Text1"));
         }
         finally {
            done.countDown();
            box.unlockWrite();
         }

         assertEquals(2, box.getLockLostCount() - lost0);
      }
      finally {
         done.countDown();
         pool.shutdown();
         assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
      }

      assertNull(dmap.get("Text1", DataMap.NORMAL),
                 "getData() cached a result computed after the sandbox lock was lost");
   }
}
