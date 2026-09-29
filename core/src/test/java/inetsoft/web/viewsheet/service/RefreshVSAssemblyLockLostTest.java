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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.LockRestoreException;
import inetsoft.util.UpgradableReadWriteLock;
import inetsoft.util.script.JavaScriptEngine;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77227: a bounded restoreLocks() that timed out while a chart was initialized for its
 * annotations in the export refresh's initTables() phase (refreshVSAssembly() ->
 * AnnotationVSUtil.refreshAllAnnotations() -> getVGraphPair() -> ... -> restoreLocks()) was
 * swallowed by refreshAnnotations(), and refreshVSAssembly() had no lost-lock check, so the
 * export went on and silently wrote a file without the chart. Both must fail visibly.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RefreshVSAssemblyLockLostTest {
   ViewsheetSandbox box;
   ExecutorService pool;
   MockedStatic<VSEventUtil> eventUtil;

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
      pool = Executors.newFixedThreadPool(1, r -> {
         Thread t = new Thread(r);
         t.setDaemon(true);
         return t;
      });
      eventUtil = mockStatic(VSEventUtil.class);
   }

   @AfterEach
   void tearDown() {
      eventUtil.close();
      pool.shutdownNow();
   }

   /** A lost write lock swallowed below refreshVSAssembly() is reported after its unlock. */
   @Test
   void refreshVSAssemblyFailsWhenANestedFrameLostTheLock() throws Exception {
      Throwable error = refreshVSAssembly(null);
      assertInstanceOf(LockRestoreException.class, error,
                       "refreshVSAssembly() returned normally after losing its write lock");
      assertLockUsableAfterwards();
   }

   /** The check doesn't replace an exception that is already on its way out. */
   @Test
   void refreshVSAssemblyKeepsTheOriginalFailure() throws Exception {
      IllegalArgumentException original = new IllegalArgumentException("refresh failed");
      assertSame(original, refreshVSAssembly(original));
      assertLockUsableAfterwards();
   }

   @Test
   void refreshVSAssemblySucceedsWithoutALostLock() throws Exception {
      CoreLifecycleService service = lifecycleService();
      RuntimeViewsheet rvs = runtimeViewsheet(false, box);
      VSAssembly assembly = mock(VSAssembly.class);
      stubAssemblyInfo(mock(VSAssemblyInfo.class));
      doNothing().when(service).refreshVSObject(any(), any(), any(), any(), any());

      service.refreshVSAssembly(rvs, assembly, mock(CommandDispatcher.class));
      verify(service).refreshVSObject(same(assembly), same(rvs), any(), same(box), any());
   }

   /** refreshAnnotations() rethrows a lost lock but still ignores a chart that isn't ready. */
   @Test
   void refreshAnnotationsRethrowsLockRestoreException() throws Exception {
      ViewsheetSandbox sandbox = mock(ViewsheetSandbox.class);
      RuntimeViewsheet rvs = runtimeViewsheet(true, sandbox);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getAbsoluteName()).thenReturn("Chart1");
      ChartVSAssemblyInfo info = mock(ChartVSAssemblyInfo.class);
      when(info.getAnnotations()).thenReturn(List.of("Annotation1"));
      when(info.isVisible()).thenReturn(true);
      stubAssemblyInfo(info);
      eventUtil.when(() -> VSEventUtil.isVisibleInTab(any(VSAssemblyInfo.class))).thenReturn(true);

      when(sandbox.getVGraphPair("Chart1"))
         .thenThrow(new LockRestoreException("restore timed out"));
      assertThrows(LockRestoreException.class,
                   () -> AnnotationVSUtil.refreshAllAnnotations(rvs, chart, null, null));

      reset(sandbox);
      when(sandbox.getVGraphPair("Chart1")).thenThrow(new IllegalStateException("not ready"));
      assertDoesNotThrow(() -> AnnotationVSUtil.refreshAllAnnotations(rvs, chart, null, null));
   }

   /**
    * The annotation data lookups used by the export write phase and the annotation events
    * rethrow a lost lock but still treat any other failure as "no data / no target".
    */
   @Test
   void annotationDataLookupsRethrowLockRestoreException() throws Exception {
      ViewsheetSandbox sandbox = mock(ViewsheetSandbox.class);
      TableVSAssembly table = mock(TableVSAssembly.class);
      when(table.getAbsoluteName()).thenReturn("Table1");
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getAbsoluteName()).thenReturn("Chart1");
      AnnotationCellValue value = mock(AnnotationCellValue.class);
      when(value.getValues()).thenReturn(new String[] { "0", "0" });
      AnnotationVSAssemblyInfo ainfo = mock(AnnotationVSAssemblyInfo.class);
      when(ainfo.getValue()).thenReturn(value);
      when(ainfo.getType()).thenReturn(AnnotationVSAssemblyInfo.DATA);

      when(sandbox.getTableData("Table1")).thenThrow(new LockRestoreException("lost"));
      when(sandbox.getVGraphPair("Chart1")).thenThrow(new LockRestoreException("lost"));
      assertThrows(LockRestoreException.class,
                   () -> AnnotationVSUtil.getAnnotationDataValue(sandbox, table, 0, 0, null));
      assertThrows(LockRestoreException.class,
                   () -> AnnotationVSUtil.getRuntimeIndex(sandbox, table, null, ainfo));
      assertThrows(LockRestoreException.class,
                   () -> AnnotationVSUtil.getAnnotationDataValue(sandbox, chart, 0, 0, null));

      reset(sandbox);
      when(sandbox.getTableData("Table1")).thenThrow(new IllegalStateException("no data"));
      assertNull(AnnotationVSUtil.getAnnotationDataValue(sandbox, table, 0, 0, null));
      assertNull(AnnotationVSUtil.getRuntimeIndex(sandbox, table, null, ainfo));
   }

   private Throwable refreshVSAssembly(RuntimeException nestedFailure) throws Exception {
      CoreLifecycleService service = lifecycleService();
      RuntimeViewsheet rvs = runtimeViewsheet(false, box);
      VSAssembly assembly = mock(VSAssembly.class);
      stubAssemblyInfo(mock(VSAssemblyInfo.class));

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

      // the nested frames (getVGraphPair() -> getData() -> doExecuteData()) under the write
      // lock refreshVSAssembly() holds, and a generic catch that swallows the failure, as
      // refreshAnnotations() did
      doAnswer(inv -> {
         try {
            box.unlockAll();

            try {
               released.countDown();
               assertTrue(writerHas.await(10, TimeUnit.SECONDS));
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
         catch(LockRestoreException ex) {
            // swallowed
         }

         if(nestedFailure != null) {
            throw nestedFailure;
         }

         return null;
      }).when(service).refreshVSObject(any(), any(), any(), any(), any());

      Throwable error = null;

      try {
         service.refreshVSAssembly(rvs, assembly, mock(CommandDispatcher.class));
      }
      catch(Throwable ex) {
         error = ex;
      }
      finally {
         restoreFailed.countDown();
      }

      writer.get(10, TimeUnit.SECONDS);
      assertInstanceOf(LockRestoreException.class, seen[0], "the restore did not fail");
      return error;
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

   private CoreLifecycleService lifecycleService() {
      return mock(CoreLifecycleService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
   }

   private RuntimeViewsheet runtimeViewsheet(boolean runtime, ViewsheetSandbox sandbox) {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.isRuntime()).thenReturn(runtime);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(sandbox));
      when(rvs.getViewsheet()).thenReturn(mock(Viewsheet.class));
      return rvs;
   }

   private void stubAssemblyInfo(VSAssemblyInfo info) {
      eventUtil.when(() -> VSEventUtil.isVisible(any(RuntimeViewsheet.class),
                                                 any(VSAssembly.class)))
         .thenReturn(true);
      eventUtil.when(() -> VSEventUtil.getAssemblyInfo(any(RuntimeViewsheet.class),
                                                       any(VSAssembly.class)))
         .thenReturn(info);
   }
}
